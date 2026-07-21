// Workflow interface for the property claim lifecycle: intake, coverage, assessment,
// human-in-the-loop approval, and payment. Structurally identical to AutoClaimWorkflow.
package com.ziggy.insurance.domains.claim.property;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.AdjusterDenialRequest;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PropertyClaimWorkflow {

    @WorkflowMethod
    PropertyClaimState run(PropertyClaimInput input);

    @SignalMethod
    void adjusterApproval(AdjusterApprovalRequest request);

    @SignalMethod
    void adjusterDenial(AdjusterDenialRequest request);

    @SignalMethod
    void submitDamageAssessment(DamageAssessmentResult assessment);

    @QueryMethod
    PropertyClaimState getClaim();
}
