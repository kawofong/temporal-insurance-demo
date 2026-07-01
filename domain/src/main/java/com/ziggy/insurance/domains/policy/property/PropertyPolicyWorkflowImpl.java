// Entity workflow implementation for property policies.
// Lifecycle state machine runs in-memory; all mutations via signals and updates.
package com.ziggy.insurance.domains.policy.property;

import com.ziggy.insurance.domains.policy.models.LossPayee;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.search.PolicySearchAttributes;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class PropertyPolicyWorkflowImpl implements PropertyPolicyWorkflow {

    private PropertyPolicyState state;
    private boolean cancelled = false;

    @Override
    public void run(PropertyPolicyInput input) {
        this.state = PropertyPolicyState.fromInput(input);
        PolicySearchAttributes.upsertPolicyHolderId(input.policyHolderId());
        PolicySearchAttributes.upsertPolicyStatus(state.getStatus());
        Workflow.await(() -> cancelled);
        updateStatus(PolicyStatus.CANCELLED);
    }

    // --- Lifecycle signals ---

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
    public void cancelPolicy(String reason) {
        cancelled = true;
    }

    // Applies a lifecycle transition and mirrors the new status to the policyStatus search attribute.
    private void updateStatus(PolicyStatus status) {
        state.setStatus(status);
        PolicySearchAttributes.upsertPolicyStatus(status);
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
