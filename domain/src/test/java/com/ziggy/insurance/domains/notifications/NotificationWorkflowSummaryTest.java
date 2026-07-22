// Verifies the notification workflow attaches per-activity UI summaries so the Temporal timeline
// distinguishes each step (preference lookup and the per-channel dispatches) rather than showing
// identical activity-type labels. Asserts the summaries land in workflow history.
package com.ziggy.insurance.domains.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.notifications.models.NotificationRequest;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.converter.DataConverter;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationWorkflowSummaryTest {

    private static final String TASK_QUEUE = NotificationsNexus.TASK_QUEUE;
    private static final String WORKFLOW_ID = "notify/summary-test";

    @Test
    void attachesDescriptiveSummariesToEachActivity() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
            worker.registerActivitiesImplementations(new NotificationActivitiesImpl());
            env.start();

            NotificationWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                NotificationWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowId(WORKFLOW_ID)
                    .build());
            wf.sendNotification(new NotificationRequest(
                "PH-001", "Claim CLM-1 update", "Your claim is now CLOSED.", "CLM-1"));

            List<String> summaries = scheduledActivitySummaries(env);

            // Preference lookup plus one dispatch per channel, each individually labelled.
            assertThat(summaries).contains(
                "Resolve notification preferences",
                "Send EMAIL to PH-001",
                "Send APP to PH-001",
                "Send SMS to PH-001");
        }
    }

    private List<String> scheduledActivitySummaries(TestWorkflowEnvironment env) {
        GetWorkflowExecutionHistoryResponse history = env.getWorkflowClient()
            .getWorkflowServiceStubs()
            .blockingStub()
            .getWorkflowExecutionHistory(
                GetWorkflowExecutionHistoryRequest.newBuilder()
                    .setNamespace(env.getNamespace())
                    .setExecution(WorkflowExecution.newBuilder().setWorkflowId(WORKFLOW_ID).build())
                    .build());

        DataConverter dataConverter = DataConverter.getDefaultInstance();
        List<String> summaries = new ArrayList<>();
        for (HistoryEvent event : history.getHistory().getEventsList()) {
            if (event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED
                && event.hasUserMetadata()
                && event.getUserMetadata().hasSummary()) {
                summaries.add(dataConverter.fromPayload(
                    event.getUserMetadata().getSummary(), String.class, String.class));
            }
        }
        return summaries;
    }
}
