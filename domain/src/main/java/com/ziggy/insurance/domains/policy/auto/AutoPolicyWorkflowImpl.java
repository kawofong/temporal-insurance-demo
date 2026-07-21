// Entity workflow implementation for auto policies.
// Lifecycle state machine runs in-memory; all mutations via signals and updates.
package com.ziggy.insurance.domains.policy.auto;

import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import com.ziggy.insurance.domains.policy.search.PolicySearchAttributes;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class AutoPolicyWorkflowImpl implements AutoPolicyWorkflow {

    private AutoPolicyState state;
    private boolean cancelled;

    @Override
    public void run(AutoPolicyInput input) {
        this.state = AutoPolicyState.fromInput(input);
        PolicySearchAttributes.upsertPolicyHolderId(input.policyHolderId());
        PolicySearchAttributes.upsertPolicyStatus(state.getStatus());
        Workflow.await(() -> cancelled);
        updateStatus(PolicyStatus.CANCELLED);
    }

    @Override
    public int addVehicle(Vehicle vehicle) {
        state.getInsuredVehicles().add(vehicle);
        return state.getInsuredVehicles().size();
    }

    @Override
    public void validateAddVehicle(Vehicle vehicle) {
        boolean duplicate = state.getInsuredVehicles().stream()
            .anyMatch(v -> v.vin().equals(vehicle.vin()));
        if (duplicate) {
            throw new IllegalArgumentException(
                "Vehicle with VIN " + vehicle.vin() + " already insured");
        }
    }

    @Override
    public void removeVehicle(String vehicleId) {
        state.getInsuredVehicles().removeIf(v -> v.vehicleId().equals(vehicleId));
    }

    @Override
    public void validateRemoveVehicle(String vehicleId) {
        boolean exists = state.getInsuredVehicles().stream()
            .anyMatch(v -> v.vehicleId().equals(vehicleId));
        if (!exists) {
            throw new IllegalArgumentException(
                "No vehicle " + vehicleId + " on this policy");
        }
    }

    @Override
    public void addDriver(Driver driver) {
        state.getListedDrivers().add(driver);
    }

    @Override
    public void removeDriver(String driverId) {
        state.getListedDrivers().removeIf(d -> d.driverId().equals(driverId));
    }

    @Override
    public void activatePolicy() {
        updateStatus(PolicyStatus.ACTIVE);
    }

    @Override
    public void suspendPolicy(String reason) {
        if (state.getStatus() != PolicyStatus.CANCELLED) {
            updateStatus(PolicyStatus.SUSPENDED);
        }
    }

    @Override
    public void reactivatePolicy() {
        if (state.getStatus() == PolicyStatus.SUSPENDED) {
            updateStatus(PolicyStatus.ACTIVE);
        }
    }

    @Override
    public void cancelPolicy(String reason) {
        cancelled = true;
    }

    @Override
    public void initiateRenewal() {
        if (state.getStatus() == PolicyStatus.ACTIVE) {
            updateStatus(PolicyStatus.RENEWAL_PENDING);
        }
    }

    @Override
    public void completeRenewal() {
        if (state.getStatus() == PolicyStatus.RENEWAL_PENDING) {
            updateStatus(PolicyStatus.ACTIVE);
        }
    }

    @Override
    public AutoPolicyState getPolicy() {
        return state;
    }

    // Every status change is mirrored into the policyStatus search attribute so policies stay
    // filterable by status in Visibility (the list-policies query).
    private void updateStatus(PolicyStatus status) {
        state.setStatus(status);
        PolicySearchAttributes.upsertPolicyStatus(status);
    }
}
