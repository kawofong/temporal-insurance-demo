// Entity workflow implementation for auto policies.
// Lifecycle state machine runs in-memory; all mutations via signals and updates.
package com.ziggy.insurance.domains.policy.auto;

import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class AutoPolicyWorkflowImpl implements AutoPolicyWorkflow {

    private AutoPolicyState state;
    private boolean cancelled = false;

    @Override
    public void run(AutoPolicyInput input) {
        this.state = AutoPolicyState.fromInput(input);
        Workflow.await(() -> cancelled);
        state.setStatus(PolicyStatus.CANCELLED);
    }

    // --- Lifecycle signals ---

    @Override
    public void activatePolicy() {
        state.setStatus(PolicyStatus.ACTIVE);
    }

    @Override
    public void suspendPolicy(String reason) {
        if (state.getStatus() != PolicyStatus.CANCELLED) {
            state.setStatus(PolicyStatus.SUSPENDED);
        }
    }

    @Override
    public void reactivatePolicy() {
        if (state.getStatus() == PolicyStatus.SUSPENDED) {
            state.setStatus(PolicyStatus.ACTIVE);
        }
    }

    @Override
    public void initiateRenewal() {
        if (state.getStatus() == PolicyStatus.ACTIVE) {
            state.setStatus(PolicyStatus.RENEWAL_PENDING);
        }
    }

    @Override
    public void completeRenewal() {
        if (state.getStatus() == PolicyStatus.RENEWAL_PENDING) {
            state.setStatus(PolicyStatus.ACTIVE);
        }
    }

    @Override
    public void cancelPolicy(String reason) {
        cancelled = true;
    }

    // --- Vehicle updates (with validators) ---

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

    // --- Driver signals ---

    @Override
    public void addDriver(Driver driver) {
        state.getListedDrivers().add(driver);
    }

    @Override
    public void removeDriver(String driverId) {
        state.getListedDrivers().removeIf(d -> d.driverId().equals(driverId));
    }

    // --- Query ---

    @Override
    public AutoPolicyState getPolicy() {
        return state;
    }
}
