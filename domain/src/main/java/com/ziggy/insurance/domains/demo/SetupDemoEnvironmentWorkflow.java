// Workflow interface for setting up a demo environment with sample policies.
// Creates one auto, one property, and one commercial policy idempotently.
package com.ziggy.insurance.domains.demo;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface SetupDemoEnvironmentWorkflow {

    @WorkflowMethod
    DemoSetupResult run();
}
