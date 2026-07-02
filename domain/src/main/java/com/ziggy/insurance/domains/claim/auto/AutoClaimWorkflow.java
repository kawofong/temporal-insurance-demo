// Workflow interface for the auto claim lifecycle: intake, coverage, assessment,
// human-in-the-loop approval, and payment.
package com.ziggy.insurance.domains.claim.auto;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AutoClaimWorkflow {

    @WorkflowMethod
    AutoClaimState run(AutoClaimInput input);

    @SignalMethod
    void adjusterApproval(AdjusterApprovalRequest request);

    @SignalMethod
    void submitDamageAssessment(DamageAssessmentResult assessment);

    @QueryMethod
    AutoClaimState getClaim();
}
