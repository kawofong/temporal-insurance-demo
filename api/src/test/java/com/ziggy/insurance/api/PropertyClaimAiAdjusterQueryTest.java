// Unit tests for the batch-enableAiAdjuster Visibility query builder (spec §6.5 / §9).
// The time-skipping test server does not implement the batch/Visibility path, so it is verified
// end-to-end on a real dev server; here we cover the query construction that scopes the batch
// signal to Running property claims, optionally narrowed by claim status and/or CAT event.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropertyClaimAiAdjusterQueryTest {

    private static final String BASE_QUERY =
        "WorkflowType = 'PropertyClaimWorkflow' AND ExecutionStatus = 'Running'";

    @Test
    void queryWithoutFiltersTargetsAllRunningPropertyClaims() {
        assertThat(PropertyClaimService.buildAiAdjusterBatchQuery(null, null)).isEqualTo(BASE_QUERY);
    }

    @Test
    void blankFiltersAreTreatedAsNoFilter() {
        assertThat(PropertyClaimService.buildAiAdjusterBatchQuery("  ", "  ")).isEqualTo(BASE_QUERY);
    }

    @Test
    void queryWithStatusScopesByClaimStatus() {
        assertThat(PropertyClaimService.buildAiAdjusterBatchQuery("PENDING_DAMAGE_ASSESSMENT", null))
            .isEqualTo(BASE_QUERY + " AND claimStatus = 'PENDING_DAMAGE_ASSESSMENT'");
    }

    @Test
    void queryWithCatEventScopesByCatEventId() {
        assertThat(PropertyClaimService.buildAiAdjusterBatchQuery(null, "cat-hurricane-2026"))
            .isEqualTo(BASE_QUERY + " AND catEventId = 'cat-hurricane-2026'");
    }

    @Test
    void queryWithStatusAndCatEventComposesBoth() {
        assertThat(PropertyClaimService.buildAiAdjusterBatchQuery(
                "PENDING_APPROVAL", "cat-hurricane-2026"))
            .isEqualTo(BASE_QUERY
                + " AND claimStatus = 'PENDING_APPROVAL'"
                + " AND catEventId = 'cat-hurricane-2026'");
    }

    @Test
    void queryWithInvalidStatusThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> PropertyClaimService.buildAiAdjusterBatchQuery("NOT_A_STATUS", null));
    }
}
