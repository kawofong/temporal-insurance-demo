// Service layer that facades Temporal WorkflowClient interactions for auto claims.
// Translates HTTP-level operations into workflow start, signal, and query calls.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.auto.AutoClaimInput;
import com.ziggy.insurance.domains.claim.auto.AutoClaimState;
import com.ziggy.insurance.domains.claim.auto.AutoClaimWorkflow;
import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.policy.TaskQueues;
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
public class ClaimService {

    private static final String TASK_QUEUE = TaskQueues.CLAIM_TASK_QUEUE;

    private final WorkflowClient workflowClient;

    public ClaimService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public static String workflowId(String claimId) {
        return "claim/auto/" + claimId;
    }

    private static String generateClaimId() {
        return "CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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

    public void submitDamageAssessment(String claimId, DamageAssessmentResult assessment) {
        workflowClient.newWorkflowStub(AutoClaimWorkflow.class, workflowId(claimId))
            .submitDamageAssessment(assessment);
    }

    public AutoClaimListResponse listClaims(String policyHolderId, String policyId, String status) {
        String namespace = workflowClient.getOptions().getNamespace();
        ListWorkflowExecutionsRequest request = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(namespace)
            .setQuery(buildClaimListQuery(policyHolderId, policyId, status))
            .build();
        ListWorkflowExecutionsResponse response = workflowClient.getWorkflowServiceStubs()
            .blockingStub().listWorkflowExecutions(request);

        List<AutoClaimState> claims = new ArrayList<>();
        for (WorkflowExecutionInfo info : response.getExecutionsList()) {
            String wfId = info.getExecution().getWorkflowId();
            claims.add(workflowClient.newWorkflowStub(AutoClaimWorkflow.class, wfId).getClaim());
        }
        return new AutoClaimListResponse(claims);
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
