// Activities the notification workflow drives: resolving a recipient's channel preference
// and dispatching a single notification on one channel. Both are mocked stand-ins.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.ChannelDelivery;
import com.ziggy.insurance.domains.notifications.models.NotificationChannel;
import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.List;

@ActivityInterface
public interface NotificationActivities {

    // Resolves the channels a recipient wants to be reached on. Mocked to always return every
    // supported channel; a real impl would look the recipient up in a preferences service.
    @ActivityMethod
    List<NotificationChannel> getPreferredChannels(String recipientId);

    // Dispatches one notification on one channel and returns the (mock) delivery outcome.
    @ActivityMethod
    ChannelDelivery dispatch(NotificationChannel channel, NotificationRequest request);
}
