// Test-only caller workflow implementation. Mirrors how AutoClaimWorkflowImpl targets the
// notifications Nexus service: a stub bound to the endpoint, calling sendNotification.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.notifications.models.NotificationResult;
import io.temporal.workflow.NexusOperationOptions;
import io.temporal.workflow.NexusServiceOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class NotifyCallerWorkflowImpl implements NotifyCallerWorkflow {

    private final NotificationService notifications = Workflow.newNexusServiceStub(
        NotificationService.class,
        NexusServiceOptions.newBuilder()
            .setEndpoint(NotificationsNexus.ENDPOINT)
            .setOperationOptions(
                NexusOperationOptions.newBuilder()
                    .setScheduleToCloseTimeout(Duration.ofSeconds(10))
                    .build())
            .build());

    @Override
    public NotificationResult notify(NotificationRequest request) {
        return notifications.sendNotification(request);
    }
}
