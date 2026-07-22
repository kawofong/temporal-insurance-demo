// Nexus service contract for the notifications domain.
//
// This is the well-defined API other domains (e.g. claims) call across the Nexus boundary
// instead of reaching into notification internals. Today it exposes a single operation;
// new notification capabilities are added as new @Operation methods here.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.notifications.models.NotificationResult;
import io.nexusrpc.Operation;
import io.nexusrpc.Service;

@Service
public interface NotificationService {

    // Sends a notification to a recipient across the channels resolved from their preference
    // and returns the per-channel outcome.
    @Operation
    NotificationResult sendNotification(NotificationRequest request);
}
