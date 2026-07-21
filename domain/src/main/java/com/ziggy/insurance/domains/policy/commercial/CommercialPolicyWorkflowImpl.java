// Entity workflow implementation for commercial policies.
// Lifecycle state machine runs in-memory; all mutations via signals and updates.
package com.ziggy.insurance.domains.policy.commercial;

import com.ziggy.insurance.domains.policy.models.AdditionalInsured;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.search.PolicySearchAttributes;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class CommercialPolicyWorkflowImpl implements CommercialPolicyWorkflow {

    private CommercialPolicyState state;
    private boolean cancelled = false;

    @Override
    public void run(CommercialPolicyInput input) {
        this.state = CommercialPolicyState.fromInput(input);
        PolicySearchAttributes.upsertPolicyHolderId(input.policyHolderId());
        PolicySearchAttributes.upsertPolicyStatus(state.getStatus());
        Workflow.await(() -> cancelled);
        updateStatus(PolicyStatus.CANCELLED);
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

    // Every status change is mirrored into the policyStatus search attribute so policies stay
    // filterable by status in Visibility (the list-policies query).
    private void updateStatus(PolicyStatus status) {
        state.setStatus(status);
        PolicySearchAttributes.upsertPolicyStatus(status);
    }

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

    @Override
    public CommercialPolicyState getPolicy() {
        return state;
    }
}
