package com.ziggy.insurance.domains.policy.search;

import io.temporal.workflow.Workflow;
import java.util.Map;

public final class PolicySearchAttributes {
    public static final String POLICY_HOLDER_ID = "policyHolderId";

    private PolicySearchAttributes() {}

    public static void upsertPolicyHolderId(String policyHolderId) {
        if (policyHolderId != null && !policyHolderId.isBlank()) {
            Workflow.upsertSearchAttributes(Map.of(POLICY_HOLDER_ID, policyHolderId));
        }
    }
}
