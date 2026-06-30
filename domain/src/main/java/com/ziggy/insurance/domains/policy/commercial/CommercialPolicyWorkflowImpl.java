// Entity workflow implementation for commercial policies.
// Lifecycle state machine runs in-memory; all mutations via signals and updates.
package com.ziggy.insurance.domains.policy.commercial;

import com.ziggy.insurance.domains.policy.models.AdditionalInsured;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class CommercialPolicyWorkflowImpl implements CommercialPolicyWorkflow {

    private CommercialPolicyState state;
    private boolean cancelled = false;

    @Override
    public void run(CommercialPolicyInput input) {
        this.state = CommercialPolicyState.fromInput(input);
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

    // --- Additional insured updates (with validators) ---

    @Override
    public int addAdditionalInsured(AdditionalInsured additionalInsured) {
        state.getAdditionalInsureds().add(additionalInsured);
        return state.getAdditionalInsureds().size();
    }

    @Override
    public void validateAddAdditionalInsured(AdditionalInsured additionalInsured) {
        boolean duplicate = state.getAdditionalInsureds().stream()
            .anyMatch(ai -> ai.additionalInsuredId().equals(additionalInsured.additionalInsuredId()));
        if (duplicate) {
            throw new IllegalArgumentException(
                "Additional insured " + additionalInsured.additionalInsuredId() + " already on this policy");
        }
    }

    @Override
    public void removeAdditionalInsured(String additionalInsuredId) {
        state.getAdditionalInsureds().removeIf(ai -> ai.additionalInsuredId().equals(additionalInsuredId));
    }

    @Override
    public void validateRemoveAdditionalInsured(String additionalInsuredId) {
        boolean exists = state.getAdditionalInsureds().stream()
            .anyMatch(ai -> ai.additionalInsuredId().equals(additionalInsuredId));
        if (!exists) {
            throw new IllegalArgumentException(
                "No additional insured " + additionalInsuredId + " on this policy");
        }
    }

    // --- Query ---

    @Override
    public CommercialPolicyState getPolicy() {
        return state;
    }
}
