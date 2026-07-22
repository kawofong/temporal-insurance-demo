// Unit tests for CATEventActivitiesImpl.
// Verifies a shard starts the expected PropertyClaimWorkflow executions, and that filing the
// same shard twice is idempotent (WorkflowExecutionAlreadyStarted is caught, not re-thrown).
package com.ziggy.insurance.domains.cat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflow;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflowImpl;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationActivitiesImpl;
import com.ziggy.insurance.domains.notifications.NotificationServiceImpl;
import com.ziggy.insurance.domains.notifications.NotificationWorkflowImpl;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.payment.PaymentNexus;
import com.ziggy.insurance.domains.payment.PaymentServiceImpl;
import com.ziggy.insurance.domains.payment.PaymentWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class CATEventActivitiesImplTest {

    @Test
    void fileClaimShardStartsEachClaimInRangeAndIsIdempotentOnRetry() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            env.registerSearchAttribute(ClaimSearchAttributes.POLICY_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(ClaimSearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            env.registerSearchAttribute(ClaimSearchAttributes.CLAIM_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);

            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new CATEventWorkflowTest.FastPropertyClaimActivities());

            Worker notificationsWorker = env.newWorker(NotificationsNexus.TASK_QUEUE);
            notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
            notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
            notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
            env.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);

            Worker paymentWorker = env.newWorker(PaymentNexus.TASK_QUEUE);
            paymentWorker.registerNexusServiceImplementation(new PaymentServiceImpl());
            paymentWorker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
            paymentWorker.registerActivitiesImplementations(new CATEventWorkflowTest.FastPaymentActivities());
            env.createNexusEndpoint(PaymentNexus.ENDPOINT, PaymentNexus.TASK_QUEUE);

            env.start();

            CATEventActivitiesImpl activities = new CATEventActivitiesImpl(env.getWorkflowClient());
            CATEventShardInput shardInput = new CATEventShardInput("EVT-TEST", "Test Region", 0, 5);

            int started = activities.fileClaimShard(shardInput);
            assertThat(started).isEqualTo(5);

            for (int i = 0; i < 5; i++) {
                PropertyClaimWorkflow claim = env.getWorkflowClient().newWorkflowStub(
                    PropertyClaimWorkflow.class, "claim/property/EVT-TEST-" + i);
                PropertyClaimState claimState = claim.getClaim();
                assertThat(claimState.getCatEventId()).isEqualTo("EVT-TEST");
            }

            // Retrying the same shard must not throw — WorkflowExecutionAlreadyStarted is caught.
            int startedAgain = activities.fileClaimShard(shardInput);
            assertThat(startedAgain).isEqualTo(5);
        }
    }
}
