// Mock activity implementations for the notifications domain. No external system calls — each
// method stands in for a downstream system (a preferences service, an e-mail/push/SMS gateway)
// so the domain can be demoed end-to-end without any real integration.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.ChannelDelivery;
import com.ziggy.insurance.domains.notifications.models.NotificationChannel;
import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import io.temporal.spring.boot.ActivityImpl;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = NotificationsNexus.TASK_QUEUE)
public class NotificationActivitiesImpl implements NotificationActivities {

    @Override
    public List<NotificationChannel> getPreferredChannels(String recipientId) {
        // Artificial 100-500 ms delay so the demo shows realistic downstream latency.
        simulateProcessingDelay();
        // Demo stand-in: a real impl would query a preferences service. This mock always
        // returns every supported channel so the parallel fan-out is visible in the demo.
        return List.of(NotificationChannel.values());
    }

    @Override
    public ChannelDelivery dispatch(NotificationChannel channel, NotificationRequest request) {
        // Artificial 100-500 ms delay so the demo shows realistic downstream latency.
        simulateProcessingDelay();
        // Demo stand-in: a real impl would call the e-mail/push/SMS provider for this channel.
        String reference = channel.name().toLowerCase() + "-" + request.recipientId()
            + "-" + request.referenceId();
        return new ChannelDelivery(channel, true, reference);
    }

    // Sleeps a random 100-500 ms to mimic downstream provider latency. Demo only — this makes
    // the parallel per-channel dispatch visible in the timeline.
    private static void simulateProcessingDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
