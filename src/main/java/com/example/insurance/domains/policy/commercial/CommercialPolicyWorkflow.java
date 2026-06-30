// Workflow interface for commercial policy entity management.
// Supports additional insured updates and lifecycle state transitions.
package com.example.insurance.domains.policy.commercial;

import com.example.insurance.domains.policy.models.AdditionalInsured;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface CommercialPolicyWorkflow {

    @WorkflowMethod
    void run(CommercialPolicyInput input);

    // Updates — additional insured management

    @UpdateMethod
    int addAdditionalInsured(AdditionalInsured additionalInsured);

    @UpdateValidatorMethod(updateName = "addAdditionalInsured")
    void validateAddAdditionalInsured(AdditionalInsured additionalInsured);

    @UpdateMethod
    void removeAdditionalInsured(String additionalInsuredId);

    @UpdateValidatorMethod(updateName = "removeAdditionalInsured")
    void validateRemoveAdditionalInsured(String additionalInsuredId);

    // Signals — lifecycle transitions

    @SignalMethod
    void activatePolicy();

    @SignalMethod
    void suspendPolicy(String reason);

    @SignalMethod
    void reactivatePolicy();

    @SignalMethod
    void cancelPolicy(String reason);

    @SignalMethod
    void initiateRenewal();

    @SignalMethod
    void completeRenewal();

    // Query

    @QueryMethod
    CommercialPolicyState getPolicy();
}
