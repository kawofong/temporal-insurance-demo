package com.ziggy.insurance.domains.claim.search;

import io.temporal.workflow.Workflow;
import java.util.Map;

public final class ClaimSearchAttributes {
    public static final String POLICY_ID = "policyId";
    public static final String POLICY_HOLDER_ID = "policyHolderId";

    private ClaimSearchAttributes() {}

    public static void upsertPolicyId(String policyId) {
        if (policyId != null && !policyId.isBlank()) {
            Workflow.upsertSearchAttributes(Map.of(POLICY_ID, policyId));
        }
    }

    public static void upsertPolicyHolderId(String policyHolderId) {
        if (policyHolderId != null && !policyHolderId.isBlank()) {
            Workflow.upsertSearchAttributes(Map.of(POLICY_HOLDER_ID, policyHolderId));
        }
    }
}
