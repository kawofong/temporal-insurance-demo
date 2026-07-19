// Unit tests for the visibility-search-attribute fallback used when a property claim
// workflow cannot answer the getClaim query (e.g. it was terminated mid-workflow-task).
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import io.temporal.api.common.v1.SearchAttributes;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.GlobalDataConverter;
import org.junit.jupiter.api.Test;

class PropertyClaimVisibilityFallbackTest {

    private static final DataConverter CONVERTER = GlobalDataConverter.get();

    @Test
    void reconstructsClaimFromSearchAttributes() {
        WorkflowExecutionInfo info = WorkflowExecutionInfo.newBuilder()
            .setExecution(WorkflowExecution.newBuilder()
                .setWorkflowId("claim/property/clm-a1b2c3d4").build())
            .setSearchAttributes(SearchAttributes.newBuilder()
                .putIndexedFields("claimStatus", CONVERTER.toPayload("PENDING_APPROVAL").get())
                .putIndexedFields("policyId", CONVERTER.toPayload("demo-prop-001").get())
                .putIndexedFields("policyHolderId", CONVERTER.toPayload("PH-001").get())
                .build())
            .build();

        PropertyClaimState state = PropertyClaimService.claimFromVisibility(info);

        assertThat(state.getClaimId()).isEqualTo("clm-a1b2c3d4");
        assertThat(state.getStatus()).isEqualTo(ClaimStatus.PENDING_APPROVAL);
        assertThat(state.getPolicyId()).isEqualTo("demo-prop-001");
        assertThat(state.getPolicyHolderId()).isEqualTo("PH-001");
    }

    @Test
    void toleratesMissingSearchAttributes() {
        WorkflowExecutionInfo info = WorkflowExecutionInfo.newBuilder()
            .setExecution(WorkflowExecution.newBuilder()
                .setWorkflowId("claim/property/clm-nodata").build())
            .build();

        PropertyClaimState state = PropertyClaimService.claimFromVisibility(info);

        assertThat(state.getClaimId()).isEqualTo("clm-nodata");
        assertThat(state.getStatus()).isNull();
        assertThat(state.getPolicyId()).isNull();
        assertThat(state.getPolicyHolderId()).isNull();
    }
}
