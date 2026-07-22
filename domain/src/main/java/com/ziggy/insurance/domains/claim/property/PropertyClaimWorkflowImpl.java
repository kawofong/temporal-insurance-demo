// Entity workflow implementation for property claims.
// Owns the claim lifecycle from intake through adjuster approval to payment.
// Structurally identical to AutoClaimWorkflowImpl; only the insurance-domain details
// (property address / peril instead of vehicle VIN) differ.
package com.ziggy.insurance.domains.claim.property;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.AdjusterDenialRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.property.agents.AgentClaimDecisionRequest;
import com.ziggy.insurance.domains.claim.property.agents.AgentFieldAdjusterRequest;
import com.ziggy.insurance.domains.claim.property.agents.AgentMappers;
import com.ziggy.insurance.domains.claim.property.agents.ClaimDecisionReport;
import com.ziggy.insurance.domains.claim.property.agents.FieldAdjusterReport;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationService;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.payment.PaymentNexus;
import com.ziggy.insurance.domains.payment.PaymentService;
import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.ChildWorkflowStub;
import io.temporal.workflow.NexusServiceOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.time.Duration;

@WorkflowImpl(taskQueues = "claim-task-queue")
public class PropertyClaimWorkflowImpl implements PropertyClaimWorkflow {

    private PropertyClaimState state;
    private boolean damageAssessed = false;
    private boolean adjusterApproved = false;
    private boolean adjusterDenied = false;
    private PropertyClaimActivities activities;

    // Notifications live in their own domain; the claim reaches them across a Nexus boundary
    // rather than calling a local activity. The endpoint routes to the notifications task queue.
    private final NotificationService notifications = Workflow.newNexusServiceStub(
        NotificationService.class,
        NexusServiceOptions.newBuilder()
            .setEndpoint(NotificationsNexus.ENDPOINT)
            .build());

    // Payments also live in their own domain; the claim triggers a payout across the Nexus
    // boundary instead of owning payment logic. The operation is backed by a payment workflow
    // that retries the flaky gateway to success, so a generous schedule-to-close timeout covers
    // that retry backoff.
    private final PaymentService payments = Workflow.newNexusServiceStub(
        PaymentService.class,
        NexusServiceOptions.newBuilder()
            .setEndpoint(PaymentNexus.ENDPOINT)
            .build());

    // @WorkflowInit guarantees state is set before any Query (e.g. an early GET) or Signal runs.
    @WorkflowInit
    public PropertyClaimWorkflowImpl(PropertyClaimInput input) {
        this.state = PropertyClaimState.fromInput(input);
    }

    @Override
    public PropertyClaimState run(PropertyClaimInput input) {

        // Upsert search attributes so claims are filterable by policy and holder in Visibility.
        ClaimSearchAttributes.upsertPolicyId(input.policyId());
        ClaimSearchAttributes.upsertPolicyHolderId(input.policyHolderId());

        // Local claim activities (coverage, adjuster). Payment lives in the payment domain
        // and is triggered over Nexus, not through this stub.
        this.activities = Workflow.newActivityStub(
            PropertyClaimActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(5))
                .build());

        // The constructor set SUBMITTED for early queries; notify the policyholder here.
        updateStatus(ClaimStatus.SUBMITTED);

        CoverageVerificationResult coverage =
            activities.verifyCoverage(state.getPolicyId(), state.getPropertyAddress());

        if (!coverage.covered()) {
            state.setRejectionReason(coverage.rejectionReason());
            state.setClosedAt(Workflow.currentTimeMillis());
            updateStatus(ClaimStatus.REJECTED);
            return this.state;
        }

        state.setCoverageType(coverage.coverageType());
        state.setDeductible(coverage.deductible());
        updateStatus(ClaimStatus.COVERAGE_VERIFIED);

        String adjusterId = activities.assignAdjuster(state.getClaimId());
        state.setAssignedAdjusterId(adjusterId);

        activities.dispatchFieldAdjuster(state.getClaimId(), adjusterId);

        updateStatus(ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
        Workflow.await(() -> damageAssessed || state.isAiAdjusterEnabled());
        if (!damageAssessed && state.isAiAdjusterEnabled()) {
            runFieldAdjusterAgent(coverage);
        }

        updateStatus(ClaimStatus.PENDING_APPROVAL);
        Workflow.await(() -> adjusterApproved || adjusterDenied || state.isAiAdjusterEnabled());
        if (!adjusterApproved && !adjusterDenied && state.isAiAdjusterEnabled()) {
            runClaimAdjusterAgent(coverage);
        }

        // Denial is terminal: the rejection reason is already on state; close out as REJECTED,
        // reusing the same terminal + notification path as a coverage denial at intake.
        if (adjusterDenied) {
            state.setClosedAt(Workflow.currentTimeMillis());
            updateStatus(ClaimStatus.REJECTED);
            return this.state;
        }

        updateStatus(ClaimStatus.PAYMENT_PROCESSING);
        PaymentResult payment = payments.processPayment(new PaymentRequest(
            state.getClaimId(), state.getPolicyHolderId(), state.getApprovedPayoutAmount()));
        state.setPaymentReference(payment.paymentReference());

        state.setClosedAt(Workflow.currentTimeMillis());
        updateStatus(ClaimStatus.CLOSED);
        return this.state;
    }

    @Override
    public void adjusterApproval(AdjusterApprovalRequest request) {
        applyApproval(request);
    }

    @Override
    public void adjusterDenial(AdjusterDenialRequest request) {
        applyDenial(request);
    }

    @Override
    public void submitDamageAssessment(DamageAssessmentResult assessment) {
        applyDamageAssessment(assessment);
    }

    @Override
    public void enableAiAdjuster() {
        // Idempotent one-way enable. Setting the flag breaks whichever Workflow.await the claim
        // is currently parked on (Seam A or B), routing the still-pending decision to the agents.
        state.setAiAdjusterEnabled(true);
    }

    @Override
    public PropertyClaimState getClaim() {
        return state;
    }

    // ── AI adjuster invocation ─────────────────────────────────────────────────────────────
    // The Python agents are workflows on a different SDK and task queue, so they are invoked as
    // untyped child workflows. Everything they need is passed as the child argument (§4/§6.3);
    // no activity fetches state. They render as children in the CAT tree for the demo walkthrough.

    // Runs the field-adjuster agent for the damage assessment and stamps its result into the same
    // state fields a human submitDamageAssessment signal would write. The agent's recommended
    // `approval` is informational only — the claim adjuster makes the binding decision (§4).
    private void runFieldAdjusterAgent(CoverageVerificationResult coverage) {
        ChildWorkflowStub fieldAdjuster = Workflow.newUntypedChildWorkflowStub(
            "FieldAdjusterWorkflow", agentChildOptions("field-adjuster/" + state.getClaimId()));
        FieldAdjusterReport report = fieldAdjuster.execute(
            FieldAdjusterReport.class,
            new AgentFieldAdjusterRequest(
                AgentMappers.toAgentClaim(state), AgentMappers.toAgentCoverage(coverage)));
        applyDamageAssessment(new DamageAssessmentResult(
            report.assessment().summary(), report.assessment().estimatedCost()));
    }

    // Runs the claim-adjuster agent for the binding approve/deny decision from the claim, the
    // verified coverage, and the damage assessment currently on state (human- or AI-produced).
    // Both branches funnel through the same apply* helpers the human signals use, so the audit
    // trail and downstream code are identical for human and AI (§6.2).
    private void runClaimAdjusterAgent(CoverageVerificationResult coverage) {
        ChildWorkflowStub claimAdjuster = Workflow.newUntypedChildWorkflowStub(
            "ClaimAdjusterWorkflow", agentChildOptions("claim-adjuster/" + state.getClaimId()));
        ClaimDecisionReport decision = claimAdjuster.execute(
            ClaimDecisionReport.class,
            new AgentClaimDecisionRequest(
                AgentMappers.toAgentClaim(state),
                AgentMappers.toAgentCoverage(coverage),
                AgentMappers.toAgentAssessment(state)));
        if (decision.approved()) {
            applyApproval(decision.toApprovalRequest());
        } else {
            applyDenial(decision.toDenialRequest());
        }
    }

    private static ChildWorkflowOptions agentChildOptions(String workflowId) {
        return ChildWorkflowOptions.newBuilder()
            .setTaskQueue(TaskQueues.AI_AGENTS_TASK_QUEUE)
            .setWorkflowId(workflowId)
            .build();
    }

    // ── Shared state mutations ─────────────────────────────────────────────────────────────
    // Both the human signal handlers and the AI path funnel through these so a claim's state
    // (and thus its audit trail) is written identically no matter which adjudicated it.

    private void applyDamageAssessment(DamageAssessmentResult assessment) {
        state.setDamageAssessment(assessment.summary());
        state.setEstimatedRepairCost(assessment.estimatedCost());
        damageAssessed = true;
    }

    private void applyApproval(AdjusterApprovalRequest request) {
        state.setApprovedByAdjusterId(request.adjusterId());
        state.setApprovedPayoutAmount(request.approvedPayoutAmount());
        state.setApprovedAt(Workflow.currentTimeMillis());
        adjusterApproved = true;
    }

    private void applyDenial(AdjusterDenialRequest request) {
        state.setRejectionReason(request.reason());
        adjusterDenied = true;
    }

    // Advances the claim to a new status and notifies the policyholder about the change.
    // The notification is delivered by the notifications domain over Nexus, which fans the
    // message out across whatever channels (email/app/text) the policyholder prefers.
    private void updateStatus(ClaimStatus status) {
        state.setStatus(status);
        ClaimSearchAttributes.upsertClaimStatus(status);
        notifications.sendNotification(new NotificationRequest(
            state.getPolicyHolderId(),
            "Claim " + state.getClaimId() + " update",
            "Your claim " + state.getClaimId() + " is now " + status + ".",
            state.getClaimId()));
    }
}
