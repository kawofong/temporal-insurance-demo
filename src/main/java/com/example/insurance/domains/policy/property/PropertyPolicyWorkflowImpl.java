// Entity workflow implementation for property policies.
// Lifecycle state machine runs in-memory; all mutations via signals and updates.
package com.example.insurance.domains.policy.property;

import com.example.insurance.domains.policy.models.LossPayee;
import com.example.insurance.domains.policy.models.PolicyStatus;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class PropertyPolicyWorkflowImpl implements PropertyPolicyWorkflow {

    private PropertyPolicyState state;
    private boolean cancelled = false;

    @Override
    public void run(PropertyPolicyInput input) {
        this.state = PropertyPolicyState.fromInput(input);
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

    // --- Loss payee updates (with validators) ---

    @Override
    public int addLossPayee(LossPayee lossPayee) {
        state.getLossPayees().add(lossPayee);
        return state.getLossPayees().size();
    }

    @Override
    public void validateAddLossPayee(LossPayee lossPayee) {
        boolean duplicate = state.getLossPayees().stream()
            .anyMatch(lp -> lp.lossPayeeId().equals(lossPayee.lossPayeeId()));
        if (duplicate) {
            throw new IllegalArgumentException(
                "Loss payee " + lossPayee.lossPayeeId() + " already on this policy");
        }
    }

    @Override
    public void removeLossPayee(String lossPayeeId) {
        state.getLossPayees().removeIf(lp -> lp.lossPayeeId().equals(lossPayeeId));
    }

    @Override
    public void validateRemoveLossPayee(String lossPayeeId) {
        boolean exists = state.getLossPayees().stream()
            .anyMatch(lp -> lp.lossPayeeId().equals(lossPayeeId));
        if (!exists) {
            throw new IllegalArgumentException(
                "No loss payee " + lossPayeeId + " on this policy");
        }
    }

    // --- Query ---

    @Override
    public PropertyPolicyState getPolicy() {
        return state;
    }
}
