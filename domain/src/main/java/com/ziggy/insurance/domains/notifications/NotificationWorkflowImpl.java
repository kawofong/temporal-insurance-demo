// Workflow implementation behind the notifications Nexus service: resolves the recipient's
// preferred channels, then dispatches on each channel in parallel.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.ChannelDelivery;
import com.ziggy.insurance.domains.notifications.models.NotificationChannel;
import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.notifications.models.NotificationResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@WorkflowImpl(taskQueues = NotificationsNexus.TASK_QUEUE)
public class NotificationWorkflowImpl implements NotificationWorkflow {

    private static final Duration ACTIVITY_START_TO_CLOSE = Duration.ofSeconds(5);

    // Activity summaries surface in the Temporal UI timeline in place of the bare activity-type
    // name, so each step is readable there rather than showing an opaque method name.
    private final NotificationActivities preferences = Workflow.newActivityStub(
        NotificationActivities.class, activityOptions("Resolve notification preferences"));

    @Override
    public NotificationResult sendNotification(NotificationRequest request) {
        List<NotificationChannel> channels = preferences.getPreferredChannels(request.recipientId());

        List<Promise<ChannelDelivery>> pending = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            // A per-channel stub so each dispatch carries its own summary and the otherwise-identical
            // "Dispatch" entries stay distinguishable in the UI timeline.
            NotificationActivities dispatcher = Workflow.newActivityStub(
                NotificationActivities.class,
                activityOptions("Send " + channel + " to " + request.recipientId()));
            pending.add(Async.function(dispatcher::dispatch, channel, request));
        }
        Promise.allOf(pending).get();

        List<ChannelDelivery> deliveries = new ArrayList<>();
        for (Promise<ChannelDelivery> delivery : pending) {
            deliveries.add(delivery.get());
        }
        return new NotificationResult(request.recipientId(), request.referenceId(), deliveries);
    }

    private static ActivityOptions activityOptions(String summary) {
        return ActivityOptions.newBuilder()
            .setStartToCloseTimeout(ACTIVITY_START_TO_CLOSE)
            .setSummary(summary)
            .build();
    }
}
