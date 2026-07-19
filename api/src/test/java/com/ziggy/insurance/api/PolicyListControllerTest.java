// Unit tests for the policy-list visibility query builder.
// The time-skipping test server does not implement ListWorkflowExecutions, so the full
// list/search path is verified end-to-end against a real Temporal dev server; here we
// cover the query construction that scopes results to a policyholder.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PolicyListControllerTest {

    private static final String BASE_QUERY =
        "WorkflowType IN ('AutoPolicyWorkflow', 'PropertyPolicyWorkflow', 'CommercialPolicyWorkflow')"
        + " AND ExecutionStatus = 'Running'";

    @Test
    void queryWithoutHolderScopesByPolicyWorkflowTypeAndRunningStatus() {
        String query = PolicyService.buildPolicyListQuery(null);
        assertThat(query).isEqualTo(BASE_QUERY);
    }

    @Test
    void blankHolderIsTreatedAsNoFilter() {
        assertThat(PolicyService.buildPolicyListQuery("   "))
            .doesNotContain("policyHolderId");
    }

    @Test
    void queryWithHolderFiltersByPolicyHolderIdSearchAttribute() {
        String query = PolicyService.buildPolicyListQuery("jake-from-state-farm");
        assertThat(query).isEqualTo(
            BASE_QUERY + " AND policyHolderId = 'jake-from-state-farm'");
    }
}
