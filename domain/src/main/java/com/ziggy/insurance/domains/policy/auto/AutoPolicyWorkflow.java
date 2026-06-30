// Workflow interface for auto policy entity management.
// Supports vehicle updates, driver signals, and lifecycle state transitions.
package com.ziggy.insurance.domains.policy.auto;

import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AutoPolicyWorkflow {

    @WorkflowMethod
    void run(AutoPolicyInput input);

    // Updates — caller gets return value or rejection

    @UpdateMethod
    int addVehicle(Vehicle vehicle);

    @UpdateValidatorMethod(updateName = "addVehicle")
    void validateAddVehicle(Vehicle vehicle);

    @UpdateMethod
    void removeVehicle(String vehicleId);

    @UpdateValidatorMethod(updateName = "removeVehicle")
    void validateRemoveVehicle(String vehicleId);

    // Signals — fire-and-forget

    @SignalMethod
    void addDriver(Driver driver);

    @SignalMethod
    void removeDriver(String driverId);

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
    AutoPolicyState getPolicy();
}
