// Result record returned by SetupDemoEnvironmentWorkflow upon completion.
// Contains the workflow IDs for each created or already-running policy.
package com.ziggy.insurance.domains.demo;

import java.util.List;

public record DemoSetupResult(
    List<String> createdWorkflowIds
) {}
