// Unit tests for AutoClaimWorkflow entity workflow.
// Covers coverage-approved happy path, coverage-denied rejection, and adjuster approval Signal.
package com.ziggy.insurance.domains.claim.auto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class AutoClaimWorkflowTest {

    private AutoClaimInput testInput(String claimId, String vin) {
        return new AutoClaimInput(
            claimId, "demo-auto-001", "PH-001",
            "Rear-ended at a stoplight", 1750000000L, "Chicago, IL",
            vin, "Honda", "Civic", 2022);
    }

    private WorkflowOptions workflowOptions(String workflowId) {
        return WorkflowOptions.newBuilder()
            .setTaskQueue(TaskQueues.CLAIM_TASK_QUEUE)
            .setWorkflowId(workflowId)
            .build();
    }

    private void registerSearchAttributes(TestWorkflowEnvironment env) {
        env.registerSearchAttribute(ClaimSearchAttributes.POLICY_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute(ClaimSearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
    }

    @Test
    void submittedStateObservableBeforeFirstWorkflowTask() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new AutoClaimActivitiesImpl());
            env.start();

            AutoClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoClaimWorkflow.class, workflowOptions("claim/auto/test-submitted"));
            WorkflowClient.start(wf::run, testInput("CLM-SUBMIT-001", "1HGFE2F59NH000001"));

            AutoClaimState state = wf.getClaim();
            assertThat(state.getClaimId()).isEqualTo("CLM-SUBMIT-001");
            assertThat(state.getPolicyId()).isEqualTo("demo-auto-001");
        }
    }

    @Test
    void coverageApprovedHappyPathReachesClosed() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new AutoClaimActivitiesImpl());
            env.start();

            AutoClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoClaimWorkflow.class, workflowOptions("claim/auto/test-happy-path"));
            WorkflowClient.start(wf::run, testInput("CLM-HAPPY-001", "1HGFE2F59NH000001"));

            // The field adjuster submits their assessment via Signal; only then does the
            // claim advance from PENDING_DAMAGE_ASSESSMENT to PENDING_APPROVAL.
            awaitStatus(wf, ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
            wf.submitDamageAssessment(new DamageAssessmentResult(
                "Moderate front-end collision damage.", 4200));

            AutoClaimState pending = awaitStatus(wf, ClaimStatus.PENDING_APPROVAL);
            assertThat(pending.getCoverageType()).isEqualTo("COLLISION");
            assertThat(pending.getAssignedAdjusterId()).isNotBlank();
            assertThat(pending.getDamageAssessment()).isNotBlank();

            wf.adjusterApproval(new AdjusterApprovalRequest("ADJ-SARAH", 4200, "Approved after review"));

            // Block on completion so the time-skipping test server fast-forwards through the
            // payment retry backoff instead of waiting in real time.
            AutoClaimState closed = WorkflowStub.fromTyped(wf).getResult(AutoClaimState.class);
            assertThat(closed.getApprovedByAdjusterId()).isEqualTo("ADJ-SARAH");
            assertThat(closed.getApprovedPayoutAmount()).isEqualTo(4200);
            assertThat(closed.getPaymentReference()).isEqualTo("PAY-CLM-HAPPY-001");
            assertThat(closed.getRejectionReason()).isNull();
        }
    }

    @Test
    void coverageDeniedRejectsWithReason() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new AutoClaimActivitiesImpl());
            env.start();

            AutoClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoClaimWorkflow.class, workflowOptions("claim/auto/test-denied"));
            WorkflowClient.start(wf::run, testInput("CLM-DENY-001", null));

            AutoClaimState rejected = awaitStatus(wf, ClaimStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("No vehicle VIN on the claim");
        }
    }

    // Polls the Query until the workflow reaches the expected status. The test server
    // runs real activity code (including the mock heartbeat sleeps), so a short poll
    // loop is used instead of a fixed sleep.
    private AutoClaimState awaitStatus(AutoClaimWorkflow wf, ClaimStatus expected) {
        long deadline = System.currentTimeMillis() + 10_000;
        AutoClaimState state = wf.getClaim();
        while (state.getStatus() != expected && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            state = wf.getClaim();
        }
        assertThat(state.getStatus()).isEqualTo(expected);
        return state;
    }
}
