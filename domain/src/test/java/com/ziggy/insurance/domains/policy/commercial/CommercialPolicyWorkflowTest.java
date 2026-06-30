// Unit tests for CommercialPolicyWorkflow entity workflow.
// Covers lifecycle transitions, additional insured updates, and query.
package com.ziggy.insurance.domains.policy.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ziggy.insurance.domains.policy.models.AdditionalInsured;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommercialPolicyWorkflowTest {

    private static final AdditionalInsured LANDLORD = new AdditionalInsured(
        "AI-001", "Landmark Properties LLC", "LANDLORD");
    private static final AdditionalInsured CLIENT = new AdditionalInsured(
        "AI-002", "Acme Corp", "CLIENT");
    private static final AdditionalInsured DUPLICATE = new AdditionalInsured(
        "AI-001", "Landmark Duplicate", "CONTRACTOR");

    private CommercialPolicyInput testInput() {
        return new CommercialPolicyInput(
            "POL-COMM-001", "PH-001", 1700000000L, 1731536000L,
            "Jake's Pixel Repair Shop", List.of());
    }

    private WorkflowOptions workflowOptions(String workflowId) {
        return WorkflowOptions.newBuilder()
            .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
            .setWorkflowId(workflowId)
            .build();
    }

    // --- Lifecycle tests ---

    @Test
    void startsPolicyInActiveState() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-active"));
            WorkflowClient.start(wf::run, testInput());

            CommercialPolicyState state = wf.getPolicy();
            assertThat(state.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            assertThat(state.getPolicyId()).isEqualTo("POL-COMM-001");
            assertThat(state.getPolicyHolderId()).isEqualTo("PH-001");

            wf.cancelPolicy("done");
        }
    }

    @Test
    void fullLifecycleTransition() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-lifecycle"));
            WorkflowClient.start(wf::run, testInput());

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);

            wf.suspendPolicy("non-payment");
            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.SUSPENDED);

            wf.reactivatePolicy();
            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);

            wf.initiateRenewal();
            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.RENEWAL_PENDING);

            wf.completeRenewal();
            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);

            wf.cancelPolicy("done");
            WorkflowStub.fromTyped(wf).getResult(Void.class);
        }
    }

    @Test
    void cancelPolicyCompletesWorkflow() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-cancel"));
            WorkflowClient.start(wf::run, testInput());

            wf.cancelPolicy("policyholder request");
            WorkflowStub.fromTyped(wf).getResult(Void.class);
        }
    }

    // --- Additional insured update tests ---

    @Test
    void addAdditionalInsuredReturnsCount() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-add-ai"));
            WorkflowClient.start(wf::run, testInput());

            int count1 = wf.addAdditionalInsured(LANDLORD);
            int count2 = wf.addAdditionalInsured(CLIENT);

            assertThat(count1).isEqualTo(1);
            assertThat(count2).isEqualTo(2);
            assertThat(wf.getPolicy().getAdditionalInsureds()).hasSize(2);

            wf.cancelPolicy("done");
        }
    }

    @Test
    void addAdditionalInsuredRejectsDuplicateId() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-dup-ai"));
            WorkflowClient.start(wf::run, testInput());

            wf.addAdditionalInsured(LANDLORD);

            assertThatThrownBy(() -> wf.addAdditionalInsured(DUPLICATE))
                .isInstanceOf(io.temporal.client.WorkflowUpdateException.class)
                .hasRootCauseInstanceOf(io.temporal.failure.ApplicationFailure.class)
                .rootCause().hasMessageContaining("already on this policy");

            assertThat(wf.getPolicy().getAdditionalInsureds()).hasSize(1);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void removeAdditionalInsuredSucceeds() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-remove-ai"));
            WorkflowClient.start(wf::run, testInput());

            wf.addAdditionalInsured(LANDLORD);
            wf.removeAdditionalInsured("AI-001");

            assertThat(wf.getPolicy().getAdditionalInsureds()).isEmpty();
            wf.cancelPolicy("done");
        }
    }

    @Test
    void removeAdditionalInsuredRejectsMissingId() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-remove-missing"));
            WorkflowClient.start(wf::run, testInput());

            assertThatThrownBy(() -> wf.removeAdditionalInsured("AI-NONEXISTENT"))
                .isInstanceOf(io.temporal.client.WorkflowUpdateException.class)
                .hasRootCauseInstanceOf(io.temporal.failure.ApplicationFailure.class)
                .rootCause().hasMessageContaining("No additional insured");

            wf.cancelPolicy("done");
        }
    }

    @Test
    void businessNameAccessible() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
            env.start();

            CommercialPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CommercialPolicyWorkflow.class, workflowOptions("policy/commercial/test-biz-name"));
            WorkflowClient.start(wf::run, testInput());

            assertThat(wf.getPolicy().getBusinessName()).isEqualTo("Jake's Pixel Repair Shop");

            wf.cancelPolicy("done");
        }
    }
}
