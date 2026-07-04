// Nexus service handler for the notifications domain.
//
// @ServiceImpl binds this handler to the NotificationService contract; @NexusServiceImpl lets the
// Spring Boot worker auto-discover it and register it on the notifications task queue.
// sendNotification is backed by a workflow (NotificationWorkflow): the operation starts a workflow
// run that resolves the recipient's channels and dispatches on each in parallel, giving the fan-out
// durability and per-activity retries. The caller blocks on the operation until that run completes.
package com.ziggy.insurance.domains.notifications;

import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import com.ziggy.insurance.domains.notifications.models.NotificationResult;
import io.nexusrpc.handler.OperationHandler;
import io.nexusrpc.handler.OperationImpl;
import io.nexusrpc.handler.ServiceImpl;
import io.temporal.client.WorkflowOptions;
import io.temporal.nexus.Nexus;
import io.temporal.nexus.WorkflowRunOperation;
import io.temporal.spring.boot.NexusServiceImpl;
import org.springframework.stereotype.Component;

@Component
@ServiceImpl(service = NotificationService.class)
@NexusServiceImpl(taskQueues = NotificationsNexus.TASK_QUEUE)
public class NotificationServiceImpl {

    @OperationImpl
    public OperationHandler<NotificationRequest, NotificationResult> sendNotification() {
        // The Nexus request id is the workflow id so retries of the same operation dedupe onto
        // one workflow run (callers reuse referenceId across many notifications, so it can't be it).
        return WorkflowRunOperation.fromWorkflowMethod(
            (context, details, request) ->
                Nexus.getOperationContext()
                    .getWorkflowClient()
                    .newWorkflowStub(
                        NotificationWorkflow.class,
                        WorkflowOptions.newBuilder().setWorkflowId(details.getRequestId()).build())
                    ::sendNotification);
    }
}
