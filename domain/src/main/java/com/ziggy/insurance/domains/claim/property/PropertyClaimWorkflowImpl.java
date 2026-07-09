// Entity workflow implementation for property claims.
// Owns the claim lifecycle from intake through adjuster approval to payment.
// Structurally identical to AutoClaimWorkflowImpl; only the insurance-domain details
// (property address / peril instead of vehicle VIN) differ.
package com.ziggy.insurance.domains.claim.property;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationService;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.payment.PaymentNexus;
import com.ziggy.insurance.domains.payment.PaymentService;
import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.NexusOperationOptions;
import io.temporal.workflow.NexusServiceOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.time.Duration;

@WorkflowImpl(taskQueues = "claim-task-queue")
public class PropertyClaimWorkflowImpl implements PropertyClaimWorkflow {

    private PropertyClaimState state;
    private boolean damageAssessed = false;
    private boolean adjusterApproved = false;
    private PropertyClaimActivities activities;

    // Notifications live in their own domain; the claim reaches them across a Nexus boundary
    // rather than calling a local activity. The endpoint routes to the notifications task queue.
    private final NotificationService notifications = Workflow.newNexusServiceStub(
        NotificationService.class,
        NexusServiceOptions.newBuilder()
            .setEndpoint(NotificationsNexus.ENDPOINT)
            .setOperationOptions(
                NexusOperationOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(2))
                    .build())
            .build());

    // Payments also live in their own domain; the claim triggers a payout across the Nexus
    // boundary instead of owning payment logic. The operation is backed by a payment workflow
    // that retries the flaky gateway to success, so a generous schedule-to-close timeout covers
    // that retry backoff.
    private final PaymentService payments = Workflow.newNexusServiceStub(
        PaymentService.class,
        NexusServiceOptions.newBuilder()
            .setEndpoint(PaymentNexus.ENDPOINT)
            .setOperationOptions(
                NexusOperationOptions.newBuilder()
                    .setScheduleToCloseTimeout(Duration.ofMinutes(5))
                    .build())
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

        // Local claim activities (coverage, adjuster). Payment now lives in the payment domain
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

        // Durable wait: the field adjuster submits their assessment via a Signal.
        updateStatus(ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
        Workflow.await(() -> damageAssessed);

        // Durable wait: the claim can sit here for minutes or days holding no resources.
        updateStatus(ClaimStatus.PENDING_APPROVAL);
        Workflow.await(() -> adjusterApproved);

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
        state.setApprovedByAdjusterId(request.adjusterId());
        state.setApprovedPayoutAmount(request.approvedPayoutAmount());
        state.setApprovedAt(Workflow.currentTimeMillis());
        adjusterApproved = true;
    }

    @Override
    public void submitDamageAssessment(DamageAssessmentResult assessment) {
        state.setDamageAssessment(assessment.summary());
        state.setEstimatedRepairCost(assessment.estimatedCost());
        damageAssessed = true;
    }

    @Override
    public PropertyClaimState getClaim() {
        return state;
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
