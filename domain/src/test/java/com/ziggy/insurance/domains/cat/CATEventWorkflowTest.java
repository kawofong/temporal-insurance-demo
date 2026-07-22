// Unit tests for CATEventWorkflow.
// Verifies the concurrent-shard fan-out: claims are filed across multiple shards run in
// parallel, real PropertyClaimWorkflow executions are actually started (and keep running
// independently), the event completes on its own once all are filed, and a total exceeding
// the per-event cap fails the workflow before any claims are filed.
package com.ziggy.insurance.domains.cat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.claim.property.PropertyClaimActivities;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflow;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflowImpl;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationActivitiesImpl;
import com.ziggy.insurance.domains.notifications.NotificationServiceImpl;
import com.ziggy.insurance.domains.notifications.NotificationWorkflowImpl;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.payment.PaymentActivities;
import com.ziggy.insurance.domains.payment.PaymentNexus;
import com.ziggy.insurance.domains.payment.PaymentServiceImpl;
import com.ziggy.insurance.domains.payment.PaymentWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class CATEventWorkflowTest {

    private static final int TOTAL_CLAIMS = 50;

    // Shrunk so the test proves multiple shards run concurrently without spawning hundreds
    // of real workflows.
    private static final int TEST_MAX_CONCURRENT_ACTIVITIES = 5;

    // Fast, delay-free stand-in so the claims don't run the real 500-1000 ms mock sleeps.
    static class FastPropertyClaimActivities implements PropertyClaimActivities {
        @Override
        public CoverageVerificationResult verifyCoverage(String policyId, String propertyAddress) {
            return new CoverageVerificationResult(true, "HO3", 1000, null);
        }

        @Override
        public String assignAdjuster(String claimId) {
            return "adj-sarah";
        }

        @Override
        public void dispatchFieldAdjuster(String claimId, String adjusterId) {}
    }

    // Delay-free, non-flaky payment stand-in so each claim's payout (over Nexus) settles
    // instantly instead of running the real gateway's retry backoff across the fan-out.
    static class FastPaymentActivities implements PaymentActivities {
        @Override
        public String disburse(String claimId, String policyHolderId, int amount) {
            return "pay-" + claimId;
        }
    }

    private void registerSearchAttributes(TestWorkflowEnvironment env) {
        env.registerSearchAttribute(ClaimSearchAttributes.POLICY_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute(ClaimSearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute(ClaimSearchAttributes.CLAIM_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
    }

    // Stands up the notifications domain each claim calls over Nexus: a worker hosting the
    // Nexus service handler plus the workflow and activities it starts on the notifications task
    // queue, and the endpoint the claim workflow's stub targets.
    private void registerNotifications(TestWorkflowEnvironment env) {
        Worker notificationsWorker = env.newWorker(NotificationsNexus.TASK_QUEUE);
        notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
        notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
        notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
        env.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);
    }

    // Stands up the payment domain each claim calls over Nexus to disburse its payout: a
    // worker hosting the Nexus service handler plus the workflow and (fast) activity it starts on
    // the payment task queue, and the endpoint the claim workflow's stub targets.
    private void registerPayment(TestWorkflowEnvironment env) {
        Worker paymentWorker = env.newWorker(PaymentNexus.TASK_QUEUE);
        paymentWorker.registerNexusServiceImplementation(new PaymentServiceImpl());
        paymentWorker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        paymentWorker.registerActivitiesImplementations(new FastPaymentActivities());
        env.createNexusEndpoint(PaymentNexus.ENDPOINT, PaymentNexus.TASK_QUEUE);
    }

    @Test
    void fanOutFilesAllClaimsAcrossConcurrentShardsThenCompletes() {
        // Shrink the shard cap so TOTAL_CLAIMS still spans multiple concurrent shards, without
        // fanning out hundreds of children.
        int originalMaxConcurrentActivities = CATEventWorkflowImpl.maxConcurrentActivities;
        CATEventWorkflowImpl.maxConcurrentActivities = TEST_MAX_CONCURRENT_ACTIVITIES;
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker catWorker = env.newWorker(TaskQueues.CAT_TASK_QUEUE);
            catWorker.registerWorkflowImplementationTypes(CATEventWorkflowImpl.class);
            catWorker.registerActivitiesImplementations(
                new CATEventActivitiesImpl(env.getWorkflowClient()));
            Worker claimWorker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            claimWorker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            claimWorker.registerActivitiesImplementations(new FastPropertyClaimActivities());
            registerNotifications(env);
            registerPayment(env);
            env.start();

            String catEventId = "EVT-2025-WILDFIRE-CA";
            CATEventWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CATEventWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TaskQueues.CAT_TASK_QUEUE)
                    .setWorkflowId("cat/" + catEventId)
                    .build());
            CATEventInput input = new CATEventInput(
                catEventId, "Butte County Wildfire", "Northern California", TOTAL_CLAIMS);
            WorkflowClient.start(wf::run, input);

            // The carried counter climbs across shard completions until all claims are filed,
            // then the event completes on its own — no close signal needed.
            CATEventStatus completed = awaitState(wf, CATEventLifecycle.COMPLETED);
            assertThat(completed.totalClaimsExpected()).isEqualTo(TOTAL_CLAIMS);
            assertThat(completed.totalClaimsOpened()).isEqualTo(TOTAL_CLAIMS);
            assertThat(completed.percentComplete()).isEqualTo(100.0);
            assertThat(completed.declaredAt()).isPositive();

            // Claims run PropertyClaimWorkflow and are still reachable by their id after the
            // event has completed — proving the fan-out started durable executions that keep
            // running independently of the CAT event workflow.
            PropertyClaimWorkflow child = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, "claim/property/" + catEventId + "-0");
            PropertyClaimState childState = child.getClaim();
            assertThat(childState.getCatEventId()).isEqualTo(catEventId);
        } finally {
            CATEventWorkflowImpl.maxConcurrentActivities = originalMaxConcurrentActivities;
        }
    }

    @Test
    void totalExceedingCapFailsWorkflowBeforeFilingAnyClaims() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker catWorker = env.newWorker(TaskQueues.CAT_TASK_QUEUE);
            catWorker.registerWorkflowImplementationTypes(CATEventWorkflowImpl.class);
            catWorker.registerActivitiesImplementations(
                new CATEventActivitiesImpl(env.getWorkflowClient()));
            Worker claimWorker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            claimWorker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            claimWorker.registerActivitiesImplementations(new FastPropertyClaimActivities());
            registerNotifications(env);
            registerPayment(env);
            env.start();

            String catEventId = "EVT-2025-TOO-BIG";
            CATEventWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CATEventWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TaskQueues.CAT_TASK_QUEUE)
                    .setWorkflowId("cat/" + catEventId)
                    .build());
            CATEventInput input = new CATEventInput(
                catEventId, "Too Big Event", "Nowhere",
                CATEventLimits.MAX_CLAIMS_PER_EVENT + 1);
            WorkflowClient.start(wf::run, input);

            WorkflowStub stub = WorkflowStub.fromTyped(wf);
            WorkflowFailedException failure =
                assertThrows(WorkflowFailedException.class, () -> stub.getResult(Void.class));
            assertThat(failure.getCause()).isInstanceOf(ApplicationFailure.class);
            assertThat(((ApplicationFailure) failure.getCause()).getType())
                .isEqualTo("CATEventLimitExceeded");
        }
    }

    private CATEventStatus awaitState(CATEventWorkflow wf, CATEventLifecycle expected) {
        long deadline = System.currentTimeMillis() + 60_000;
        CATEventStatus status = wf.getCATEventStatus();
        while (status.status() != expected && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            status = wf.getCATEventStatus();
        }
        assertThat(status.status()).isEqualTo(expected);
        return status;
    }
}
