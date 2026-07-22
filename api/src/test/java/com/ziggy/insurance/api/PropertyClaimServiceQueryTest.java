// Unit tests for the property-claim-list visibility query builder.
// The time-skipping test server does not implement ListWorkflowExecutions, so the full
// list/search path is verified end-to-end against a real Temporal dev server; here we
// cover the query construction that scopes results by policyholder, policy, and/or status.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropertyClaimServiceQueryTest {

    private static final String BASE_QUERY = "WorkflowType = 'PropertyClaimWorkflow'";
    private static final String NOT_TERMINATED = " AND ExecutionStatus != 'Terminated'";
    private static final String RUNNING = " AND ExecutionStatus = 'Running'";

    @Test
    void queryWithoutFiltersScopesByClaimWorkflowType() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, null, null))
            .isEqualTo(BASE_QUERY + NOT_TERMINATED);
    }

    @Test
    void blankFiltersAreTreatedAsNoFilter() {
        assertThat(PropertyClaimService.buildClaimListQuery("   ", "   ", "   "))
            .isEqualTo(BASE_QUERY + NOT_TERMINATED);
    }

    @Test
    void queryWithHolderFiltersByPolicyHolderIdSearchAttribute() {
        assertThat(PropertyClaimService.buildClaimListQuery("PH-001", null, null))
            .isEqualTo(BASE_QUERY + " AND policyHolderId = 'PH-001'" + NOT_TERMINATED);
    }

    @Test
    void queryWithPolicyIdFiltersByPolicyIdSearchAttribute() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, "demo-property-001", null))
            .isEqualTo(BASE_QUERY + " AND policyId = 'demo-property-001'" + NOT_TERMINATED);
    }

    @Test
    void queryWithBothFiltersCombinesBothSearchAttributes() {
        assertThat(PropertyClaimService.buildClaimListQuery("PH-001", "demo-property-001", null))
            .isEqualTo(BASE_QUERY
                + " AND policyHolderId = 'PH-001'"
                + " AND policyId = 'demo-property-001'"
                + NOT_TERMINATED);
    }

    @Test
    void queryWithNonTerminalStatusRequiresARunningExecution() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, null, "PENDING_DAMAGE_ASSESSMENT"))
            .isEqualTo(BASE_QUERY + " AND claimStatus = 'PENDING_DAMAGE_ASSESSMENT'" + RUNNING);
    }

    @Test
    void queryWithNonTerminalStatusComposesWithPolicyFilters() {
        assertThat(PropertyClaimService.buildClaimListQuery("PH-001", "demo-property-001", "PENDING_APPROVAL"))
            .isEqualTo(BASE_QUERY
                + " AND policyHolderId = 'PH-001'"
                + " AND policyId = 'demo-property-001'"
                + " AND claimStatus = 'PENDING_APPROVAL'"
                + RUNNING);
    }

    // Terminal statuses only ever land on a workflow as its very last act before completing, so
    // a Running execution is never required to trust them — unlike the non-terminal statuses.
    @Test
    void queryWithClosedStatusDoesNotRequireARunningExecution() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, null, "CLOSED"))
            .isEqualTo(BASE_QUERY + " AND claimStatus = 'CLOSED'" + NOT_TERMINATED);
    }

    @Test
    void queryWithRejectedStatusDoesNotRequireARunningExecution() {
        assertThat(PropertyClaimService.buildClaimListQuery(null, null, "REJECTED"))
            .isEqualTo(BASE_QUERY + " AND claimStatus = 'REJECTED'" + NOT_TERMINATED);
    }

    @Test
    void queryWithInvalidStatusThrowsIllegalArgumentException() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> PropertyClaimService.buildClaimListQuery(null, null, "NOT_A_REAL_STATUS"));
    }
}
