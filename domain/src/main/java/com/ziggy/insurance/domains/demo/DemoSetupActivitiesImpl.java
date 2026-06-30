// Activity implementation for demo environment setup.
// Uses WorkflowClient to start policy entity workflows, catching duplicates for idempotency.
package com.ziggy.insurance.domains.demo;

import com.ziggy.insurance.domains.policy.TaskQueues;
import com.ziggy.insurance.domains.policy.auto.AutoPolicyWorkflow;
import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyWorkflow;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "policy-task-queue")
public class DemoSetupActivitiesImpl implements DemoSetupActivities {

    private final WorkflowClient workflowClient;

    public DemoSetupActivitiesImpl(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public String createAutoPolicyIfAbsent(DemoAutoPolicy request) {
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId(request.workflowId())
                .build());
        try {
            WorkflowClient.start(wf::run, request.input());
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Policy already exists; idempotent success
        }
        return request.workflowId();
    }

    @Override
    public String createPropertyPolicyIfAbsent(DemoPropertyPolicy request) {
        PropertyPolicyWorkflow wf = workflowClient.newWorkflowStub(
            PropertyPolicyWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId(request.workflowId())
                .build());
        try {
            WorkflowClient.start(wf::run, request.input());
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Policy already exists; idempotent success
        }
        return request.workflowId();
    }

    @Override
    public String createCommercialPolicyIfAbsent(DemoCommercialPolicy request) {
        CommercialPolicyWorkflow wf = workflowClient.newWorkflowStub(
            CommercialPolicyWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId(request.workflowId())
                .build());
        try {
            WorkflowClient.start(wf::run, request.input());
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Policy already exists; idempotent success
        }
        return request.workflowId();
    }
}
