// Service layer that facades Temporal WorkflowClient interactions for property claims.
// Mirrors ClaimService method for method; the claimId is generated here, before the
// Workflow starts — no Early Return.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.property.PropertyClaimInput;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflow;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.policy.TaskQueues;
import com.google.protobuf.ByteString;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PropertyClaimService {

    private static final String TASK_QUEUE = TaskQueues.CLAIM_TASK_QUEUE;

    private final WorkflowClient workflowClient;

    public PropertyClaimService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public static String workflowId(String claimId) {
        return "claim/property/" + claimId;
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
            claims.add(workflowClient.newWorkflowStub(PropertyClaimWorkflow.class, wfId).getClaim());
        }
        return new PropertyClaimListResponse(claims, PageTokens.encode(response.getNextPageToken()));
    }

    // Pure, unit-testable (the test server does not implement ListWorkflowExecutions).
    static String buildClaimListQuery(String policyHolderId, String policyId, String status) {
        String query = "WorkflowType = '" + PropertyClaimWorkflow.class.getSimpleName() + "'";
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
