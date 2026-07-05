// Input to the notifications Nexus service. Describes who to notify and what to say.
//
// referenceId ties the notification back to a business entity (e.g. a claim id) for
// traceability. The channels to deliver on are resolved from the recipient's preference
// inside the notification workflow, not supplied by the caller.
package com.ziggy.insurance.domains.notifications.models;

public record NotificationRequest(
    String recipientId,
    String subject,
    String message,
    String referenceId
) {}
