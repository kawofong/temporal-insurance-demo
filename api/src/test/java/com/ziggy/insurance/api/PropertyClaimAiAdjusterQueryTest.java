// Unit tests for the batch-enableAiAdjuster Visibility query builder (spec §6.5 / §9).
// The time-skipping test server does not implement the batch/Visibility path, so it is verified
// end-to-end on a real dev server; here we cover the query construction that scopes the batch
// signal to every Running property claim.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropertyClaimAiAdjusterQueryTest {

    @Test
    void queryTargetsAllRunningPropertyClaims() {
        assertThat(PropertyClaimService.buildAiAdjusterBatchQuery())
            .isEqualTo("WorkflowType = 'PropertyClaimWorkflow' AND ExecutionStatus = 'Running'");
    }
}
