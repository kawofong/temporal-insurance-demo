package com.example.insurance.domains.policy;

import com.example.insurance.workers.TaskQueues;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HelloWorldWorkflowRunner implements ApplicationRunner {

    private final WorkflowClient workflowClient;

    public HelloWorldWorkflowRunner(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("run-hello-world")) {
            return;
        }

        String name = args.getOptionValues("run-hello-world").isEmpty()
            ? "Temporal"
            : args.getOptionValues("run-hello-world").getFirst();
        HelloWorldWorkflow workflow = workflowClient.newWorkflowStub(
            HelloWorldWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId("policy-hello-world-" + Instant.now().toEpochMilli())
                .build()
        );
        String greeting = workflow.sayHello(name);
        System.out.println("HelloWorldWorkflow completed: " + greeting);
    }
}
