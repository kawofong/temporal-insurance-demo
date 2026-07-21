package com.ziggy.insurance.domains.claim.search;

import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import io.temporal.workflow.Workflow;
import java.util.Map;

public final class ClaimSearchAttributes {
    public static final String POLICY_ID = "policyId";
    public static final String POLICY_HOLDER_ID = "policyHolderId";
    public static final String CLAIM_STATUS = "claimStatus";
    // Set only for CATEventWorkflow-spawned claims; lets a batch enableAiAdjuster be scoped to a
    // single catastrophe event's claims via a Visibility query (§6.5).
    public static final String CAT_EVENT_ID = "catEventId";

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

    public static void upsertClaimStatus(ClaimStatus status) {
        if (status != null) {
            Workflow.upsertSearchAttributes(Map.of(CLAIM_STATUS, status.name()));
        }
    }

    public static void upsertCatEventId(String catEventId) {
        if (catEventId != null && !catEventId.isBlank()) {
            Workflow.upsertSearchAttributes(Map.of(CAT_EVENT_ID, catEventId));
        }
    }
}
