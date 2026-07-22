// Service layer that facades Temporal WorkflowClient interactions for auto claims.
// Translates HTTP-level operations into workflow start, signal, and query calls.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.auto.AutoClaimInput;
import com.ziggy.insurance.domains.claim.auto.AutoClaimState;
import com.ziggy.insurance.domains.claim.auto.AutoClaimWorkflow;
import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.AdjusterDenialRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
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
public class ClaimService {

    private static final String TASK_QUEUE = TaskQueues.CLAIM_TASK_QUEUE;
    private static final String WORKFLOW_ID_PREFIX = "claim/auto/";

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final WorkflowClient workflowClient;

    public ClaimService(WorkflowClient workflowClient) {
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
    public FnolResponse submitAutoClaim(FnolRequest req) {
        if (!hasText(req.vehicleVin())) {
            throw new IllegalArgumentException("vehicleVin is required");
        }
        if (req.incidentDate() <= 0) {
            throw new IllegalArgumentException("incidentDate must be positive");
        }

        String claimId = generateClaimId();
        AutoClaimInput input = new AutoClaimInput(
            claimId, req.policyId(), req.policyHolderId(),
            req.incidentDescription(), req.incidentDate(), req.incidentLocation(),
            req.vehicleVin(), req.vehicleMake(), req.vehicleModel(), req.vehicleYear());

        AutoClaimWorkflow wf = workflowClient.newWorkflowStub(
            AutoClaimWorkflow.class,
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

    public AutoClaimState getAutoClaim(String claimId) {
        return workflowClient.newWorkflowStub(AutoClaimWorkflow.class, workflowId(claimId))
            .getClaim();
    }

    public void approveAutoClaim(String claimId, AdjusterApprovalRequest request) {
        workflowClient.newWorkflowStub(AutoClaimWorkflow.class, workflowId(claimId))
            .adjusterApproval(request);
    }

    public void denyAutoClaim(String claimId, AdjusterDenialRequest request) {
        workflowClient.newWorkflowStub(AutoClaimWorkflow.class, workflowId(claimId))
            .adjusterDenial(request);
    }

    public void submitDamageAssessment(String claimId, DamageAssessmentResult assessment) {
        workflowClient.newWorkflowStub(AutoClaimWorkflow.class, workflowId(claimId))
            .submitDamageAssessment(assessment);
    }

    // Pages through the visibility results: only the current page is hydrated via Query,
    // so a large backlog does not stall the caller. nextPageToken is null when done.
    public AutoClaimListResponse listClaims(
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

        List<AutoClaimState> claims = new ArrayList<>();
        for (WorkflowExecutionInfo info : response.getExecutionsList()) {
            String wfId = info.getExecution().getWorkflowId();
            try {
                claims.add(workflowClient.newWorkflowStub(AutoClaimWorkflow.class, wfId).getClaim());
            } catch (WorkflowQueryException e) {
                // A workflow that cannot answer a query (e.g. one terminated mid-workflow-task,
                // which Temporal cannot safely replay) must not fail the whole page. Fall back to
                // the last-known state mirrored in visibility search attributes.
                log.warn("getClaim query failed for {}; using visibility fallback", wfId, e);
                claims.add(claimFromVisibility(info));
            }
        }
        return new AutoClaimListResponse(claims, PageTokens.encode(response.getNextPageToken()));
    }

    // Reconstructs a partial claim state from the visibility search attributes the workflow mirrors
    // as it progresses (claimStatus, policyId, policyHolderId), plus the claim id embedded in the
    // workflow id. Used when the live getClaim query is unavailable; richer fields stay unset.
    static AutoClaimState claimFromVisibility(WorkflowExecutionInfo info) {
        AutoClaimState state = new AutoClaimState();
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
        String query = "WorkflowType = '" + AutoClaimWorkflow.class.getSimpleName() + "'";
        if (hasText(policyHolderId)) {
            query += " AND " + ClaimSearchAttributes.POLICY_HOLDER_ID + " = '" + policyHolderId + "'";
        }
        if (hasText(policyId)) {
            query += " AND " + ClaimSearchAttributes.POLICY_ID + " = '" + policyId + "'";
        }
        boolean nonTerminalStatus = false;
        if (hasText(status)) {
            if (!isValidClaimStatus(status)) {
                throw new IllegalArgumentException("Unknown claim status: " + status);
            }
            query += " AND " + ClaimSearchAttributes.CLAIM_STATUS + " = '" + status + "'";
            nonTerminalStatus = !ClaimStatus.valueOf(status).isTerminal();
        }
        // A claim's search attributes are a snapshot the workflow itself upserts; a workflow that
        // died (terminated, timed out, or was canceled) while parked at a non-terminal status
        // leaves that status behind forever, even though no signal can ever reach it again.
        // Requiring a Running execution for a non-terminal status keeps queues like the adjuster
        // queues from showing claims no one can actually act on. Terminal statuses — and the
        // unfiltered list — instead just exclude Terminated, since a Terminated execution's
        // history cannot always be safely replayed to answer the per-claim getClaim query.
        query += nonTerminalStatus ? " AND ExecutionStatus = 'Running'" : " AND ExecutionStatus != 'Terminated'";
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
