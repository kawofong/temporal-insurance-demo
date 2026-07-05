// A recipient's stored notification preference: the channels they want to be reached on.
package com.ziggy.insurance.domains.notifications.models;

import java.util.List;

public record NotificationPreference(
    String recipientId,
    List<NotificationChannel> channels
) {}
