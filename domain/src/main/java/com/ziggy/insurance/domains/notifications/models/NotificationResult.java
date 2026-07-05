// Output of the notifications Nexus service: the per-channel outcome of one notification.
package com.ziggy.insurance.domains.notifications.models;

import java.util.List;

public record NotificationResult(
    String recipientId,
    String referenceId,
    List<ChannelDelivery> deliveries
) {}
