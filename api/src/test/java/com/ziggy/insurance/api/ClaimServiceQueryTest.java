// Unit tests for the claim-list visibility query builder.
// The time-skipping test server does not implement ListWorkflowExecutions, so the full
// list/search path is verified end-to-end against a real Temporal dev server; here we
// cover the query construction that scopes results by policyholder and/or policy.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaimServiceQueryTest {

    private static final String BASE_QUERY = "WorkflowType = 'AutoClaimWorkflow'";

    @Test
    void queryWithoutFiltersScopesByClaimWorkflowType() {
        assertThat(ClaimService.buildClaimListQuery(null, null)).isEqualTo(BASE_QUERY);
    }

    @Test
    void blankFiltersAreTreatedAsNoFilter() {
        assertThat(ClaimService.buildClaimListQuery("   ", "   ")).isEqualTo(BASE_QUERY);
    }

    @Test
    void queryWithHolderFiltersByPolicyHolderIdSearchAttribute() {
        assertThat(ClaimService.buildClaimListQuery("PH-001", null))
            .isEqualTo(BASE_QUERY + " AND policyHolderId = 'PH-001'");
    }

    @Test
    void queryWithPolicyIdFiltersByPolicyIdSearchAttribute() {
        assertThat(ClaimService.buildClaimListQuery(null, "demo-auto-001"))
            .isEqualTo(BASE_QUERY + " AND policyId = 'demo-auto-001'");
    }

    @Test
    void queryWithBothFiltersCombinesBothSearchAttributes() {
        assertThat(ClaimService.buildClaimListQuery("PH-001", "demo-auto-001"))
            .isEqualTo(BASE_QUERY
                + " AND policyHolderId = 'PH-001'"
                + " AND policyId = 'demo-auto-001'");
    }
}
