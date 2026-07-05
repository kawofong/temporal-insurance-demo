// Workflow that backs the notifications Nexus service. It owns delivering one notification:
// resolving the recipient's channels, then fanning the message out across all of them.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.notifications.models.NotificationResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface NotificationWorkflow {

    @WorkflowMethod
    NotificationResult sendNotification(NotificationRequest request);
}
