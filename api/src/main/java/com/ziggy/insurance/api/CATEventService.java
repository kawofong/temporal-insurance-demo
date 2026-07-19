// Service layer that facades Temporal WorkflowClient interactions for CAT events.
// Starts the CATEventWorkflow and queries its live progress.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.cat.CATEventInput;
import com.ziggy.insurance.domains.cat.CATEventLifecycle;
import com.ziggy.insurance.domains.cat.CATEventStatus;
import com.ziggy.insurance.domains.cat.CATEventWorkflow;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.Priority;
import org.springframework.stereotype.Service;

@Service
public class CATEventService {

    private static final String TASK_QUEUE = TaskQueues.CLAIM_TASK_QUEUE;

    // CAT events are the most urgent work on the claim task queue; priority key 1
    // is the highest of Temporal's [1, 5] range, so their tasks are scheduled ahead
    // of routine claim processing.
    private static final int CAT_EVENT_PRIORITY_KEY = 1;

    private final WorkflowClient workflowClient;

    public CATEventService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public static String workflowId(String catEventId) {
        return "cat/" + catEventId;
    }

    public DeclareCATEventResponse declareCATEvent(DeclareCATEventRequest req) {
        if (req.totalClaimsToGenerate() <= 0) {
            throw new IllegalArgumentException("totalClaimsToGenerate must be positive");
        }
        // Batch-iterator cursor and carried facts start at zero; the workflow owns
        // BATCH_SIZE and stamps declaredAt on its first run.
        CATEventInput input = new CATEventInput(
            req.catEventId(), req.eventName(), req.affectedRegion(),
            req.totalClaimsToGenerate(), 0, 0, 0);

        CATEventWorkflow wf = workflowClient.newWorkflowStub(
            CATEventWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId(workflowId(req.catEventId()))
                .setPriority(Priority.newBuilder()
                    .setPriorityKey(CAT_EVENT_PRIORITY_KEY)
                    .build())
                .build());
        WorkflowClient.start(wf::run, input);

        return new DeclareCATEventResponse(
            req.catEventId(), CATEventLifecycle.DECLARED, req.totalClaimsToGenerate());
    }

    public CATEventStatus getCATEventStatus(String catEventId) {
        return workflowClient.newWorkflowStub(CATEventWorkflow.class, workflowId(catEventId))
            .getCATEventStatus();
    }
}
