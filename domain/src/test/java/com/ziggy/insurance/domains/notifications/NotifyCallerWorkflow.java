// Test-only caller workflow: exercises the notifications domain across the Nexus boundary
// exactly as a real domain (e.g. claims) would.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.notifications.models.NotificationResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface NotifyCallerWorkflow {

    @WorkflowMethod
    NotificationResult notify(NotificationRequest request);
}
