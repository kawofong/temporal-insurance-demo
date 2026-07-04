// Tests the notifications domain end-to-end over Nexus: a caller workflow invokes the
// sendNotification operation, which starts the notification workflow that resolves the
// recipient's channels and dispatches on each in parallel. We assert the per-channel delivery.
package com.ziggy.insurance.domains.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.notifications.models.ChannelDelivery;
import com.ziggy.insurance.domains.notifications.models.NotificationChannel;
import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.notifications.models.NotificationResult;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final String CALLER_TASK_QUEUE = "test-notify-caller-task-queue";

    // Stands up the notifications domain: a worker hosting the Nexus service handler plus the
    // workflow and activities it starts, the endpoint callers target, and a caller worker.
    private TestWorkflowEnvironment startedEnv(TestWorkflowEnvironment env) {
        Worker notificationsWorker = env.newWorker(NotificationsNexus.TASK_QUEUE);
        notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
        notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
        notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
        env.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);

        Worker callerWorker = env.newWorker(CALLER_TASK_QUEUE);
        callerWorker.registerWorkflowImplementationTypes(NotifyCallerWorkflowImpl.class);

        env.start();
        return env;
    }

    private NotifyCallerWorkflow caller(TestWorkflowEnvironment env, String workflowId) {
        return env.getWorkflowClient().newWorkflowStub(
            NotifyCallerWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(CALLER_TASK_QUEUE)
                .setWorkflowId(workflowId)
                .build());
    }

    private List<NotificationChannel> deliveredChannels(NotificationResult result) {
        return result.deliveries().stream()
            .filter(ChannelDelivery::delivered)
            .map(ChannelDelivery::channel)
            .toList();
    }

    @Test
    void dispatchesOnEveryPreferredChannel() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            startedEnv(env);
            NotifyCallerWorkflow wf = caller(env, "notify/all-channels");

            NotificationResult result = wf.notify(new NotificationRequest(
                "PH-001", "Claim CLM-1 update", "Your claim is now CLOSED.", "CLM-1"));

            assertThat(result.recipientId()).isEqualTo("PH-001");
            assertThat(result.referenceId()).isEqualTo("CLM-1");
            assertThat(deliveredChannels(result)).containsExactlyInAnyOrder(
                NotificationChannel.EMAIL, NotificationChannel.APP, NotificationChannel.SMS);
        }
    }

    @Test
    void deliveryCarriesChannelAndProviderReference() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            startedEnv(env);
            NotifyCallerWorkflow wf = caller(env, "notify/reference");

            NotificationResult result = wf.notify(new NotificationRequest(
                "PH-002", "Claim CLM-2 update", "Your claim is now SUBMITTED.", "CLM-2"));

            assertThat(result.deliveries()).allSatisfy(delivery -> {
                assertThat(delivery.delivered()).isTrue();
                assertThat(delivery.reference())
                    .isEqualTo(delivery.channel().name().toLowerCase() + "-PH-002-CLM-2");
            });
        }
    }
}
