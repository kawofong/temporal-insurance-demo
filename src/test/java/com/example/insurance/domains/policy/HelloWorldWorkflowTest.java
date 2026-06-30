package com.example.insurance.domains.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.workers.TaskQueues;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class HelloWorldWorkflowTest {

    @Test
    void completesHelloWorldWorkflow() {
        try (TestWorkflowEnvironment testEnvironment = TestWorkflowEnvironment.newInstance()) {
            Worker worker = testEnvironment.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(HelloWorldWorkflowImpl.class);
            testEnvironment.start();

            WorkflowClient workflowClient = testEnvironment.getWorkflowClient();
            HelloWorldWorkflow workflow = workflowClient.newWorkflowStub(
                HelloWorldWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                    .setWorkflowId("hello-world-policy-test")
                    .build()
            );

            assertThat(workflow.sayHello("Policy")).isEqualTo("Hello, Policy!");
        }
    }
}
