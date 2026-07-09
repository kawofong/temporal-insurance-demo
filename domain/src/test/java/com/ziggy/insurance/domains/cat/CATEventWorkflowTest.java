// Unit tests for CATEventWorkflow.
// Verifies the Batch Iterator + continue-as-new fan-out: the carried counter reaches the
// total across runs, child PropertyClaimWorkflow executions are actually started (and keep
// running independently under ABANDON), and the event completes on its own once all are filed.
package com.ziggy.insurance.domains.cat;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class CATEventWorkflowTest {

    // Total crosses the (test-shrunk) batch-size boundary so the fan-out exercises the
    // continue-as-new hop, while keeping the child count small so it doesn't strain the test JVM.
    private static final int TOTAL_CLAIMS = 50;
    private static final int TEST_BATCH_SIZE = 20;

    // Fast, delay-free stand-in so the child claims don't run the real 500-1000 ms mock sleeps.
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

    // Delay-free, non-flaky payment stand-in so each child claim's payout (over Nexus) settles
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

    // Stands up the notifications domain each child claim calls over Nexus: a worker hosting the
    // Nexus service handler plus the workflow and activities it starts on the notifications task
    // queue, and the endpoint the claim workflow's stub targets.
    private void registerNotifications(TestWorkflowEnvironment env) {
        Worker notificationsWorker = env.newWorker(NotificationsNexus.TASK_QUEUE);
        notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
        notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
        notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
        env.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);
    }

    // Stands up the payment domain each child claim calls over Nexus to disburse its payout: a
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
    void fanOutFilesAllClaimsAcrossContinueAsNewThenCompletes() {
        // Shrink the workflow's batch size so TOTAL_CLAIMS still crosses the boundary and drives
        // continue-as-new, without fanning out hundreds of children.
        int originalBatchSize = CATEventWorkflowImpl.batchSize;
        CATEventWorkflowImpl.batchSize = TEST_BATCH_SIZE;
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(
                CATEventWorkflowImpl.class, PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new FastPropertyClaimActivities());
            registerNotifications(env);
            registerPayment(env);
            env.start();

            String catEventId = "EVT-2025-WILDFIRE-CA";
            CATEventWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                CATEventWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TaskQueues.CLAIM_TASK_QUEUE)
                    .setWorkflowId("cat/" + catEventId)
                    .build());
            CATEventInput input = new CATEventInput(
                catEventId, "Butte County Wildfire", "Northern California",
                TOTAL_CLAIMS, 0, 0, 0);
            WorkflowClient.start(wf::run, input);

            // The carried counter climbs across each continue-as-new hop until all claims
            // are filed, then the event completes on its own — no close signal needed.
            CATEventStatus completed = awaitState(wf, CATEventLifecycle.COMPLETED);
            assertThat(completed.totalClaimsExpected()).isEqualTo(TOTAL_CLAIMS);
            assertThat(completed.totalClaimsOpened()).isEqualTo(TOTAL_CLAIMS);
            assertThat(completed.percentComplete()).isEqualTo(100.0);
            assertThat(completed.declaredAt()).isPositive();

            // Children run PropertyClaimWorkflow and are still reachable by their id after the
            // parent has completed — proving the fan-out started durable executions that
            // ABANDON keeps running independently of the event workflow.
            PropertyClaimWorkflow child = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, "claim/property/" + catEventId + "-0");
            PropertyClaimState childState = child.getClaim();
            assertThat(childState.getCatEventId()).isEqualTo(catEventId);
        } finally {
            CATEventWorkflowImpl.batchSize = originalBatchSize;
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
