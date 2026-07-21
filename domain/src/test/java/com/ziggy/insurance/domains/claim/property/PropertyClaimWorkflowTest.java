// Unit tests for PropertyClaimWorkflow entity workflow.
// Covers coverage-approved happy path, coverage-denied rejection, adjuster approval Signal,
// and early-query observability of the SUBMITTED state (verifies @WorkflowInit).
package com.ziggy.insurance.domains.claim.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.AdjusterDenialRequest;
import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationActivitiesImpl;
import com.ziggy.insurance.domains.notifications.NotificationServiceImpl;
import com.ziggy.insurance.domains.notifications.NotificationWorkflowImpl;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.payment.PaymentActivitiesImpl;
import com.ziggy.insurance.domains.payment.PaymentNexus;
import com.ziggy.insurance.domains.payment.PaymentServiceImpl;
import com.ziggy.insurance.domains.payment.PaymentWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class PropertyClaimWorkflowTest {

    private PropertyClaimInput testInput(String claimId, String propertyAddress) {
        return new PropertyClaimInput(
            claimId, "demo-property-001", "PH-001",
            null, null,
            "Kitchen fire from a grease flare-up", 1750000000L,
            propertyAddress, "SINGLE_FAMILY");
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
        env.registerSearchAttribute(ClaimSearchAttributes.CLAIM_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
    }

    // Stands up the notifications domain the claim workflow calls over Nexus: a worker hosting
    // the Nexus service handler plus the workflow and activities it starts on the notifications
    // task queue, and the endpoint the claim workflow's stub targets. Without this the
    // workflow's sendNotification call would fail.
    private void registerNotifications(TestWorkflowEnvironment env) {
        Worker notificationsWorker = env.newWorker(NotificationsNexus.TASK_QUEUE);
        notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
        notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
        notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
        env.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);
    }

    // Stands up the payment domain the claim workflow calls over Nexus to disburse the payout: a
    // worker hosting the Nexus service handler plus the workflow and activity it starts on the
    // payment task queue, and the endpoint the claim workflow's stub targets. Without this the
    // workflow's processPayment call would hang at PAYMENT_PROCESSING.
    private void registerPayment(TestWorkflowEnvironment env) {
        Worker paymentWorker = env.newWorker(PaymentNexus.TASK_QUEUE);
        paymentWorker.registerNexusServiceImplementation(new PaymentServiceImpl());
        paymentWorker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        paymentWorker.registerActivitiesImplementations(new PaymentActivitiesImpl());
        env.createNexusEndpoint(PaymentNexus.ENDPOINT, PaymentNexus.TASK_QUEUE);
    }

    @Test
    void submittedStateObservableBeforeFirstWorkflowTask() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PropertyClaimActivitiesImpl());
            registerNotifications(env);
            registerPayment(env);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, workflowOptions("claim/property/test-submitted"));
            WorkflowClient.start(wf::run, testInput("CLM-SUBMIT-001", "742 Evergreen Terrace"));

            PropertyClaimState state = wf.getClaim();
            assertThat(state.getClaimId()).isEqualTo("CLM-SUBMIT-001");
            assertThat(state.getPolicyId()).isEqualTo("demo-property-001");
        }
    }

    @Test
    void coverageApprovedHappyPathReachesClosed() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PropertyClaimActivitiesImpl());
            registerNotifications(env);
            registerPayment(env);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, workflowOptions("claim/property/test-happy-path"));
            WorkflowClient.start(wf::run, testInput("CLM-HAPPY-001", "742 Evergreen Terrace"));

            // The field adjuster submits their assessment via Signal; only then does the
            // claim advance from PENDING_DAMAGE_ASSESSMENT to PENDING_APPROVAL.
            awaitStatus(wf, ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
            wf.submitDamageAssessment(new DamageAssessmentResult(
                "Moderate smoke and fire damage to the kitchen.", 18500));

            PropertyClaimState pending = awaitStatus(wf, ClaimStatus.PENDING_APPROVAL);
            assertThat(pending.getCoverageType()).isEqualTo("HO3");
            assertThat(pending.getAssignedAdjusterId()).isNotBlank();
            assertThat(pending.getDamageAssessment()).isNotBlank();

            wf.adjusterApproval(new AdjusterApprovalRequest("adj-sarah", 18500, "Approved after review"));

            // Block on completion so the time-skipping test server fast-forwards through the
            // payment retry backoff instead of waiting in real time.
            PropertyClaimState closed = WorkflowStub.fromTyped(wf).getResult(PropertyClaimState.class);
            assertThat(closed.getStatus()).isEqualTo(ClaimStatus.CLOSED);
            assertThat(closed.getApprovedByAdjusterId()).isEqualTo("adj-sarah");
            assertThat(closed.getApprovedPayoutAmount()).isEqualTo(18500);
            assertThat(closed.getPaymentReference()).isEqualTo("pay-CLM-HAPPY-001");
            assertThat(closed.getRejectionReason()).isNull();
        }
    }

    @Test
    void coverageDeniedRejectsWithReason() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PropertyClaimActivitiesImpl());
            registerNotifications(env);
            registerPayment(env);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, workflowOptions("claim/property/test-denied"));
            WorkflowClient.start(wf::run, testInput("CLM-DENY-001", null));

            PropertyClaimState rejected = awaitStatus(wf, ClaimStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("No property address on the claim");
        }
    }

    @Test
    void adjusterDenialRejectsWithReason() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PropertyClaimActivitiesImpl());
            registerNotifications(env);
            registerPayment(env);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, workflowOptions("claim/property/test-adjuster-denied"));
            WorkflowClient.start(wf::run, testInput("CLM-ADJ-DENY-001", "742 Evergreen Terrace"));

            // Advance to PENDING_APPROVAL, then the claims adjuster denies instead of approving.
            awaitStatus(wf, ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
            wf.submitDamageAssessment(new DamageAssessmentResult(
                "Moderate smoke and fire damage to the kitchen.", 18500));
            awaitStatus(wf, ClaimStatus.PENDING_APPROVAL);

            wf.adjusterDenial(new AdjusterDenialRequest("adj-sarah", "Peril not covered under HO3"));

            PropertyClaimState rejected = WorkflowStub.fromTyped(wf).getResult(PropertyClaimState.class);
            assertThat(rejected.getStatus()).isEqualTo(ClaimStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("Peril not covered under HO3");
            assertThat(rejected.getPaymentReference()).isNull();
            assertThat(rejected.getClosedAt()).isPositive();
        }
    }

    // Polls the Query until the workflow reaches the expected status. The test server
    // runs real activity code (including the mock delay sleeps), so a short poll loop
    // is used instead of a fixed sleep.
    private PropertyClaimState awaitStatus(PropertyClaimWorkflow wf, ClaimStatus expected) {
        long deadline = System.currentTimeMillis() + 10_000;
        PropertyClaimState state = wf.getClaim();
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
