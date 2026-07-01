// Unit tests for PropertyPolicyWorkflow entity workflow.
// Covers lifecycle transitions, loss payee updates, and query.
package com.ziggy.insurance.domains.policy.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ziggy.insurance.domains.policy.models.InsuredProperty;
import com.ziggy.insurance.domains.policy.models.LossPayee;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.TaskQueues;
import com.ziggy.insurance.domains.policy.search.PolicySearchAttributes;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropertyPolicyWorkflowTest {

    private static final InsuredProperty HOUSE = new InsuredProperty(
        "PROP-001", "742 Evergreen Terrace", "SINGLE_FAMILY");
    private static final LossPayee WELLS_FARGO = new LossPayee(
        "LP-001", "Wells Fargo Bank NA", "LOAN-12345");
    private static final LossPayee CHASE = new LossPayee(
        "LP-002", "Chase Home Lending", "LOAN-67890");
    private static final LossPayee DUPLICATE = new LossPayee(
        "LP-001", "Wells Fargo Duplicate", "LOAN-99999");

    private PropertyPolicyInput testInput() {
        return new PropertyPolicyInput(
            "POL-PROP-001", "PH-001", 1700000000L, 1731536000L, HOUSE, List.of());
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
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-active"));
            WorkflowClient.start(wf::run, testInput());

            PropertyPolicyState state = wf.getPolicy();
            assertThat(state.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            assertThat(state.getPolicyId()).isEqualTo("POL-PROP-001");
            assertThat(state.getPolicyHolderId()).isEqualTo("PH-001");

            wf.cancelPolicy("done");
        }
    }

    @Test
    void fullLifecycleTransition() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-lifecycle"));
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
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-cancel"));
            WorkflowClient.start(wf::run, testInput());

            wf.cancelPolicy("policyholder request");
            WorkflowStub.fromTyped(wf).getResult(Void.class);
        }
    }

    // --- Loss payee update tests ---

    @Test
    void addLossPayeeReturnsCount() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-add-lp"));
            WorkflowClient.start(wf::run, testInput());

            int count1 = wf.addLossPayee(WELLS_FARGO);
            int count2 = wf.addLossPayee(CHASE);

            assertThat(count1).isEqualTo(1);
            assertThat(count2).isEqualTo(2);
            assertThat(wf.getPolicy().getLossPayees()).hasSize(2);

            wf.cancelPolicy("done");
        }
    }

    @Test
    void addLossPayeeRejectsDuplicateId() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-dup-lp"));
            WorkflowClient.start(wf::run, testInput());

            wf.addLossPayee(WELLS_FARGO);

            assertThatThrownBy(() -> wf.addLossPayee(DUPLICATE))
                .isInstanceOf(io.temporal.client.WorkflowUpdateException.class)
                .hasRootCauseInstanceOf(io.temporal.failure.ApplicationFailure.class)
                .rootCause().hasMessageContaining("already on this policy");

            assertThat(wf.getPolicy().getLossPayees()).hasSize(1);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void removeLossPayeeSucceeds() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-remove-lp"));
            WorkflowClient.start(wf::run, testInput());

            wf.addLossPayee(WELLS_FARGO);
            wf.removeLossPayee("LP-001");

            assertThat(wf.getPolicy().getLossPayees()).isEmpty();
            wf.cancelPolicy("done");
        }
    }

    @Test
    void removeLossPayeeRejectsMissingId() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-remove-missing"));
            WorkflowClient.start(wf::run, testInput());

            assertThatThrownBy(() -> wf.removeLossPayee("LP-NONEXISTENT"))
                .isInstanceOf(io.temporal.client.WorkflowUpdateException.class)
                .hasRootCauseInstanceOf(io.temporal.failure.ApplicationFailure.class)
                .rootCause().hasMessageContaining("No loss payee");

            wf.cancelPolicy("done");
        }
    }

    @Test
    void propertyFieldAccessible() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
            env.start();

            PropertyPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyPolicyWorkflow.class, workflowOptions("policy/property/test-property"));
            WorkflowClient.start(wf::run, testInput());

            InsuredProperty prop = wf.getPolicy().getProperty();
            assertThat(prop.address()).isEqualTo("742 Evergreen Terrace");
            assertThat(prop.propertyType()).isEqualTo("SINGLE_FAMILY");

            wf.cancelPolicy("done");
        }
    }
}
