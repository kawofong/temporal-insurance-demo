// Workflow interface for property policy entity management.
// Supports loss payee updates and lifecycle state transitions.
package com.ziggy.insurance.domains.policy.property;

import com.ziggy.insurance.domains.policy.models.LossPayee;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PropertyPolicyWorkflow {

    @WorkflowMethod
    void run(PropertyPolicyInput input);

    // Updates — caller gets return value or rejection

    @UpdateMethod
    int addLossPayee(LossPayee lossPayee);

    @UpdateValidatorMethod(updateName = "addLossPayee")
    void validateAddLossPayee(LossPayee lossPayee);

    @UpdateMethod
    void removeLossPayee(String lossPayeeId);

    @UpdateValidatorMethod(updateName = "removeLossPayee")
    void validateRemoveLossPayee(String lossPayeeId);

    // Signals — fire-and-forget

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

    // Query — read-only state retrieval

    @QueryMethod
    PropertyPolicyState getPolicy();
}
