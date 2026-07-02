// Entity workflow implementation for auto claims.
// Owns the claim lifecycle from intake through adjuster approval to payment.
package com.ziggy.insurance.domains.claim.auto;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.time.Duration;

@WorkflowImpl(taskQueues = "claim-task-queue")
public class AutoClaimWorkflowImpl implements AutoClaimWorkflow {

    private AutoClaimState state;
    private boolean damageAssessed = false;
    private boolean adjusterApproved = false;
    private AutoClaimActivities activities;

    // @WorkflowInit guarantees state is set before any Query (e.g. an early GET) or Signal runs.
    @WorkflowInit
    public AutoClaimWorkflowImpl(AutoClaimInput input) {
        this.state = AutoClaimState.fromInput(input);
    }

    @Override
    public AutoClaimState run(AutoClaimInput input) {

        // Upsert search attributes so claims are filterable by policy and holder in Visibility.
        ClaimSearchAttributes.upsertPolicyId(input.policyId());
        ClaimSearchAttributes.upsertPolicyHolderId(input.policyHolderId());

        // No RetryOptions set — the default retry policy is what drives processPayment to success.
        this.activities = Workflow.newActivityStub(
            AutoClaimActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(5))
                .build());

        // The constructor set SUBMITTED for early queries; notify the policyholder here.
        updateStatus(ClaimStatus.SUBMITTED);

        CoverageVerificationResult coverage =
            activities.verifyCoverage(state.getPolicyId(), state.getVehicleVin());

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
        String paymentRef = activities.processPayment(
            state.getClaimId(), state.getPolicyHolderId(), state.getApprovedPayoutAmount());
        state.setPaymentReference(paymentRef);

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
    public AutoClaimState getClaim() {
        return state;
    }

    // Advances the claim to a new status and emails the policyholder about the change.
    private void updateStatus(ClaimStatus status) {
        state.setStatus(status);
        ClaimSearchAttributes.upsertClaimStatus(status);
        activities.sendEmailNotification(
            state.getPolicyHolderId(),
            state.getClaimId(),
            "Your claim " + state.getClaimId() + " is now " + status + ".");
    }
}
