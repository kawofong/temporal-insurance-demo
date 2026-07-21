// Unit tests for AutoPolicyWorkflow entity workflow.
// Covers lifecycle transitions, vehicle updates, driver signals, and query.
package com.ziggy.insurance.domains.policy.auto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.models.Vehicle;
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

class AutoPolicyWorkflowTest {

    private static final Vehicle CIVIC = new Vehicle("V-001", "1HGFE2F59NH000001", "Honda", "Civic", 2022);
    private static final Vehicle ACCORD = new Vehicle("V-002", "1HGCV1F34LA000002", "Honda", "Accord", 2020);
    private static final Vehicle DUPLICATE_VIN = new Vehicle("V-003", "1HGFE2F59NH000001", "Honda", "Civic", 2023);
    private static final Driver JAKE = new Driver("D-001", "Jake", "DL-12345");
    private static final Driver SARAH = new Driver("D-002", "Sarah", "DL-67890");

    private AutoPolicyInput testInput() {
        return new AutoPolicyInput("POL-AUTO-001", "PH-001", 1700000000L, 1731536000L, List.of(), List.of());
    }

    private WorkflowOptions workflowOptions(String workflowId) {
        return WorkflowOptions.newBuilder()
            .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
            .setWorkflowId(workflowId)
            .build();
    }

    @Test
    void startsPolicyInActiveState() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-active"));
            WorkflowClient.start(wf::run, testInput());

            AutoPolicyState state = wf.getPolicy();
            assertThat(state.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            assertThat(state.getPolicyId()).isEqualTo("POL-AUTO-001");
            assertThat(state.getPolicyHolderId()).isEqualTo("PH-001");

            wf.cancelPolicy("done");
        }
    }

    @Test
    void suspendsPolicyFromActive() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-suspend"));
            WorkflowClient.start(wf::run, testInput());

            wf.suspendPolicy("non-payment");

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.SUSPENDED);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void reactivatesPolicyFromSuspended() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-reactivate"));
            WorkflowClient.start(wf::run, testInput());

            wf.suspendPolicy("non-payment");
            wf.reactivatePolicy();

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void reactivateIgnoredWhenNotSuspended() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-reactivate-noop"));
            WorkflowClient.start(wf::run, testInput());

            wf.reactivatePolicy();

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void initiatesRenewalFromActive() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-renewal-init"));
            WorkflowClient.start(wf::run, testInput());

            wf.initiateRenewal();

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.RENEWAL_PENDING);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void initiateRenewalIgnoredWhenNotActive() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-renewal-noop"));
            WorkflowClient.start(wf::run, testInput());

            wf.suspendPolicy("non-payment");
            wf.initiateRenewal();

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.SUSPENDED);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void completesRenewalFromPending() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-renewal-complete"));
            WorkflowClient.start(wf::run, testInput());

            wf.initiateRenewal();
            wf.completeRenewal();

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void completeRenewalIgnoredWhenNotPending() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-complete-noop"));
            WorkflowClient.start(wf::run, testInput());

            wf.completeRenewal();

            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void cancelPolicyCompletesWorkflow() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-cancel"));
            WorkflowClient.start(wf::run, testInput());

            wf.cancelPolicy("policyholder request");

            WorkflowStub untypedStub = WorkflowStub.fromTyped(wf);
            untypedStub.getResult(Void.class);
        }
    }

    @Test
    void suspendIgnoredWhenCancelled() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-suspend-after-cancel"));
            WorkflowClient.start(wf::run, testInput());

            wf.suspendPolicy("non-payment");
            assertThat(wf.getPolicy().getStatus()).isEqualTo(PolicyStatus.SUSPENDED);

            wf.cancelPolicy("done");
            WorkflowStub.fromTyped(wf).getResult(Void.class);
        }
    }

    @Test
    void addVehicleReturnsUpdatedCount() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-add-vehicle"));
            WorkflowClient.start(wf::run, testInput());

            int count1 = wf.addVehicle(CIVIC);
            int count2 = wf.addVehicle(ACCORD);

            assertThat(count1).isEqualTo(1);
            assertThat(count2).isEqualTo(2);
            assertThat(wf.getPolicy().getInsuredVehicles()).hasSize(2);

            wf.cancelPolicy("done");
        }
    }

    @Test
    void addVehicleRejectsDuplicateVin() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-dup-vin"));
            WorkflowClient.start(wf::run, testInput());

            wf.addVehicle(CIVIC);

            assertThatThrownBy(() -> wf.addVehicle(DUPLICATE_VIN))
                .isInstanceOf(io.temporal.client.WorkflowUpdateException.class)
                .hasRootCauseInstanceOf(io.temporal.failure.ApplicationFailure.class)
                .rootCause().hasMessageContaining("already insured");

            assertThat(wf.getPolicy().getInsuredVehicles()).hasSize(1);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void removeVehicleSucceeds() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-remove-vehicle"));
            WorkflowClient.start(wf::run, testInput());

            wf.addVehicle(CIVIC);
            wf.removeVehicle("V-001");

            assertThat(wf.getPolicy().getInsuredVehicles()).isEmpty();
            wf.cancelPolicy("done");
        }
    }

    @Test
    void removeVehicleRejectsMissingId() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-remove-missing"));
            WorkflowClient.start(wf::run, testInput());

            assertThatThrownBy(() -> wf.removeVehicle("V-NONEXISTENT"))
                .isInstanceOf(io.temporal.client.WorkflowUpdateException.class)
                .hasRootCauseInstanceOf(io.temporal.failure.ApplicationFailure.class)
                .rootCause().hasMessageContaining("No vehicle");

            wf.cancelPolicy("done");
        }
    }

    @Test
    void addDriverSignalAddsDriver() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-add-driver"));
            WorkflowClient.start(wf::run, testInput());

            wf.addDriver(JAKE);
            wf.addDriver(SARAH);

            assertThat(wf.getPolicy().getListedDrivers()).hasSize(2);
            wf.cancelPolicy("done");
        }
    }

    @Test
    void removeDriverSignalRemovesDriver() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = env.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
            env.start();

            AutoPolicyWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                AutoPolicyWorkflow.class, workflowOptions("policy/auto/test-remove-driver"));
            WorkflowClient.start(wf::run, testInput());

            wf.addDriver(JAKE);
            wf.removeDriver("D-001");

            assertThat(wf.getPolicy().getListedDrivers()).isEmpty();
            wf.cancelPolicy("done");
        }
    }
}
