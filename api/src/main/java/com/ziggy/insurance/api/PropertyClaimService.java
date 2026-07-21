// Service layer that facades Temporal WorkflowClient interactions for property claims.
// Mirrors ClaimService method for method; the claimId is generated here, before the
// Workflow starts — no Early Return.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.AdjusterDenialRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.property.PropertyClaimInput;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflow;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.policy.TaskQueues;
import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowQueryException;
import io.temporal.common.converter.GlobalDataConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PropertyClaimService {

    private static final String TASK_QUEUE = TaskQueues.CLAIM_TASK_QUEUE;
    private static final String WORKFLOW_ID_PREFIX = "claim/property/";

    private static final Logger log = LoggerFactory.getLogger(PropertyClaimService.class);

    private final WorkflowClient workflowClient;

    public PropertyClaimService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public static String workflowId(String claimId) {
        return WORKFLOW_ID_PREFIX + claimId;
    }

    private static String generateClaimId() {
        return "clm-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // The claim id exists before the Workflow starts, so intake returns immediately
    // after start — no Query polling.
    public FnolResponse submitPropertyClaim(PropertyFnolRequest req) {
        if (!hasText(req.propertyAddress())) {
            throw new IllegalArgumentException("propertyAddress is required");
        }
        if (req.incidentDate() <= 0) {
            throw new IllegalArgumentException("incidentDate must be positive");
        }

        String claimId = generateClaimId();
        PropertyClaimInput input = new PropertyClaimInput(
            claimId, req.policyId(), req.policyHolderId(),
            null,                       // catEventId — null for portal-filed claims
            null,                       // damageTier — null for portal-filed claims
            req.incidentDescription(), req.incidentDate(),
            req.propertyAddress(), req.propertyType());

        PropertyClaimWorkflow wf = workflowClient.newWorkflowStub(
            PropertyClaimWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId(workflowId(claimId))
                .build());
        WorkflowClient.start(wf::run, input);

        return new FnolResponse(
            claimId,
            ClaimStatus.SUBMITTED,
            "Your claim has been received. We will be in touch shortly.");
    }

    public PropertyClaimState getPropertyClaim(String claimId) {
        return workflowClient.newWorkflowStub(PropertyClaimWorkflow.class, workflowId(claimId))
            .getClaim();
    }

    public void approvePropertyClaim(String claimId, AdjusterApprovalRequest request) {
        workflowClient.newWorkflowStub(PropertyClaimWorkflow.class, workflowId(claimId))
            .adjusterApproval(request);
    }

    public void denyPropertyClaim(String claimId, AdjusterDenialRequest request) {
        workflowClient.newWorkflowStub(PropertyClaimWorkflow.class, workflowId(claimId))
            .adjusterDenial(request);
    }

    public void submitDamageAssessment(String claimId, DamageAssessmentResult assessment) {
        workflowClient.newWorkflowStub(PropertyClaimWorkflow.class, workflowId(claimId))
            .submitDamageAssessment(assessment);
    }

    // Pages through the visibility results: only the current page is hydrated via Query,
    // so a large backlog no longer stalls the caller. nextPageToken is null when done.
    public PropertyClaimListResponse listClaims(
            String policyHolderId, String policyId, String status, Integer pageSize, String pageToken) {
        String namespace = workflowClient.getOptions().getNamespace();
        ListWorkflowExecutionsRequest.Builder request = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(namespace)
            .setQuery(buildClaimListQuery(policyHolderId, policyId, status));
        if (pageSize != null && pageSize > 0) {
            request.setPageSize(pageSize);
        }
        ByteString token = PageTokens.decode(pageToken);
        if (!token.isEmpty()) {
            request.setNextPageToken(token);
        }
        ListWorkflowExecutionsResponse response = workflowClient.getWorkflowServiceStubs()
            .blockingStub().listWorkflowExecutions(request.build());

        List<PropertyClaimState> claims = new ArrayList<>();
        for (WorkflowExecutionInfo info : response.getExecutionsList()) {
            String wfId = info.getExecution().getWorkflowId();
            try {
                claims.add(
                    workflowClient.newWorkflowStub(PropertyClaimWorkflow.class, wfId).getClaim());
            } catch (WorkflowQueryException e) {
                // A workflow that cannot answer a query (e.g. one terminated mid-workflow-task,
                // which Temporal cannot safely replay) must not fail the whole page. Fall back to
                // the last-known state mirrored in visibility search attributes.
                log.warn("getClaim query failed for {}; using visibility fallback", wfId, e);
                claims.add(claimFromVisibility(info));
            }
        }
        return new PropertyClaimListResponse(claims, PageTokens.encode(response.getNextPageToken()));
    }

    // Reconstructs a partial claim state from the visibility search attributes the workflow mirrors
    // as it progresses (claimStatus, policyId, policyHolderId), plus the claim id embedded in the
    // workflow id. Used when the live getClaim query is unavailable; richer fields stay unset.
    static PropertyClaimState claimFromVisibility(WorkflowExecutionInfo info) {
        PropertyClaimState state = new PropertyClaimState();
        String wfId = info.getExecution().getWorkflowId();
        state.setClaimId(wfId.startsWith(WORKFLOW_ID_PREFIX)
            ? wfId.substring(WORKFLOW_ID_PREFIX.length()) : wfId);

        String status = readStringSearchAttribute(info, ClaimSearchAttributes.CLAIM_STATUS);
        if (hasText(status)) {
            state.setStatus(ClaimStatus.valueOf(status));
        }
        state.setPolicyId(readStringSearchAttribute(info, ClaimSearchAttributes.POLICY_ID));
        state.setPolicyHolderId(readStringSearchAttribute(info, ClaimSearchAttributes.POLICY_HOLDER_ID));
        return state;
    }

    // Decodes a single keyword/text search-attribute value, or null when it is absent.
    private static String readStringSearchAttribute(WorkflowExecutionInfo info, String key) {
        Payload payload = info.getSearchAttributes().getIndexedFieldsMap().get(key);
        if (payload == null) {
            return null;
        }
        return GlobalDataConverter.get().fromPayload(payload, String.class, String.class);
    }

    // Pure, unit-testable (the test server does not implement ListWorkflowExecutions).
    static String buildClaimListQuery(String policyHolderId, String policyId, String status) {
        // Exclude terminated workflows: termination can interrupt a workflow task, leaving a
        // history that cannot be safely replayed to answer the per-claim getClaim query.
        String query = "WorkflowType = '" + PropertyClaimWorkflow.class.getSimpleName() + "'"
            + " AND ExecutionStatus != 'Terminated'";
        if (hasText(policyHolderId)) {
            query += " AND " + ClaimSearchAttributes.POLICY_HOLDER_ID + " = '" + policyHolderId + "'";
        }
        if (hasText(policyId)) {
            query += " AND " + ClaimSearchAttributes.POLICY_ID + " = '" + policyId + "'";
        }
        if (hasText(status)) {
            if (!isValidClaimStatus(status)) {
                throw new IllegalArgumentException("Unknown claim status: " + status);
            }
            query += " AND " + ClaimSearchAttributes.CLAIM_STATUS + " = '" + status + "'";
        }
        return query;
    }

    private static boolean isValidClaimStatus(String status) {
        try {
            ClaimStatus.valueOf(status);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
