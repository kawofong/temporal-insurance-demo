// Outcome of dispatching a single notification on one channel.
// reference is the mock provider's handle (e.g. a message id); null when delivery failed.
package com.ziggy.insurance.domains.notifications.models;

public record ChannelDelivery(
    NotificationChannel channel,
    boolean delivered,
    String reference
) {}
