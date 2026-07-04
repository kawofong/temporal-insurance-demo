// Unit tests for the property-claim-list visibility query builder.
// The time-skipping test server does not implement ListWorkflowExecutions, so the full
// list/search path is verified end-to-end against a real Temporal dev server; here we
// cover the query construction that scopes results by policyholder, policy, and/or status.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropertyClaimServiceQueryTest {

    private static final String BASE_QUERY = "WorkflowType = 'PropertyClaimWorkflow'";

    @Test
    void queryWithoutFiltersScopesByClaimWorkflowType() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, null, null)).isEqualTo(BASE_QUERY);
    }

    @Test
    void blankFiltersAreTreatedAsNoFilter() {
        assertThat(PropertyClaimService.buildClaimListQuery("   ", "   ", "   ")).isEqualTo(BASE_QUERY);
    }

    @Test
    void queryWithHolderFiltersByPolicyHolderIdSearchAttribute() {
        assertThat(PropertyClaimService.buildClaimListQuery("PH-001", null, null))
            .isEqualTo(BASE_QUERY + " AND policyHolderId = 'PH-001'");
    }

    @Test
    void queryWithPolicyIdFiltersByPolicyIdSearchAttribute() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, "demo-property-001", null))
            .isEqualTo(BASE_QUERY + " AND policyId = 'demo-property-001'");
    }

    @Test
    void queryWithBothFiltersCombinesBothSearchAttributes() {
        assertThat(PropertyClaimService.buildClaimListQuery("PH-001", "demo-property-001", null))
            .isEqualTo(BASE_QUERY
                + " AND policyHolderId = 'PH-001'"
                + " AND policyId = 'demo-property-001'");
    }

    @Test
    void queryWithStatusFiltersByClaimStatusSearchAttribute() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, null, "PENDING_DAMAGE_ASSESSMENT"))
            .isEqualTo(BASE_QUERY + " AND claimStatus = 'PENDING_DAMAGE_ASSESSMENT'");
    }

    @Test
    void queryWithStatusComposesWithPolicyFilters() {
        assertThat(PropertyClaimService.buildClaimListQuery("PH-001", "demo-property-001", "PENDING_APPROVAL"))
            .isEqualTo(BASE_QUERY
                + " AND policyHolderId = 'PH-001'"
                + " AND policyId = 'demo-property-001'"
                + " AND claimStatus = 'PENDING_APPROVAL'");
    }

    @Test
    void queryWithInvalidStatusThrowsIllegalArgumentException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> PropertyClaimService.buildClaimListQuery(null, null, "NOT_A_REAL_STATUS"));
    }
}
