package com.ziggy.insurance.domains.policy.search;

import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import io.temporal.workflow.Workflow;
import java.util.Map;

public final class PolicySearchAttributes {
    public static final String POLICY_HOLDER_ID = "policyHolderId";
    public static final String POLICY_STATUS = "policyStatus";

    private PolicySearchAttributes() {}

    public static void upsertPolicyHolderId(String policyHolderId) {
        if (policyHolderId != null && !policyHolderId.isBlank()) {
            Workflow.upsertSearchAttributes(Map.of(POLICY_HOLDER_ID, policyHolderId));
        }
    }

    public static void upsertPolicyStatus(PolicyStatus status) {
        if (status != null) {
            Workflow.upsertSearchAttributes(Map.of(POLICY_STATUS, status.name()));
        }
    }
}
