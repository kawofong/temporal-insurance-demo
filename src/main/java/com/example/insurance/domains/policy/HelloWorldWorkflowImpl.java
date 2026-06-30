package com.example.insurance.domains.policy;

import io.temporal.spring.boot.WorkflowImpl;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class HelloWorldWorkflowImpl implements HelloWorldWorkflow {

    @Override
    public String sayHello(String name) {
        String recipient = (name == null || name.isBlank()) ? "Temporal" : name.trim();
        return "Hello, " + recipient + "!";
    }
}
