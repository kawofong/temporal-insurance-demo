// Orchestrates demo environment setup by creating sample policy workflows.
// Uses activities to interact with the Temporal server for idempotent policy creation.
package com.ziggy.insurance.domains.demo;

import com.ziggy.insurance.domains.policy.TaskQueues;
import com.ziggy.insurance.domains.policy.auto.AutoPolicyInput;
import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyInput;
import com.ziggy.insurance.domains.policy.models.AdditionalInsured;
import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.InsuredProperty;
import com.ziggy.insurance.domains.policy.models.LossPayee;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyInput;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@WorkflowImpl(taskQueues = "policy-task-queue")
public class SetupDemoEnvironmentWorkflowImpl implements SetupDemoEnvironmentWorkflow {

    // --- Static demo data ---

    private static final String POLICY_HOLDER_ID = "jake-from-state-farm";

    private static final String AUTO_POLICY_ID = "demo-auto-001";
    private static final String AUTO_WORKFLOW_ID = "policy/auto/" + AUTO_POLICY_ID;
    private static final AutoPolicyInput AUTO_INPUT = new AutoPolicyInput(
        AUTO_POLICY_ID,
        POLICY_HOLDER_ID,
        1704067200L,  // 2024-01-01 UTC
        1735689600L,  // 2025-01-01 UTC
        List.of(
            new Vehicle("V-DEMO-001", "1HGCV1F34LA000001", "Honda", "Civic", 2024)
        ),
        List.of(
            new Driver("D-DEMO-001", "Jake", "DL-SF-00001"),
            new Driver("D-DEMO-002", "Jessica", "DL-SF-00002")
        )
    );

    private static final String PROPERTY_POLICY_ID = "demo-prop-001";
    private static final String PROPERTY_WORKFLOW_ID = "policy/property/" + PROPERTY_POLICY_ID;
    private static final PropertyPolicyInput PROPERTY_INPUT = new PropertyPolicyInput(
        PROPERTY_POLICY_ID,
        POLICY_HOLDER_ID,
        1704067200L,  // 2024-01-01 UTC
        1735689600L,  // 2025-01-01 UTC
        new InsuredProperty("PROP-DEMO-001", "100 State Farm Blvd, Bloomington, IL 61710", "SINGLE_FAMILY"),
        List.of(
            new LossPayee("LP-DEMO-001", "Bloomington National Bank", "LOAN-00001")
        )
    );

    private static final String COMMERCIAL_POLICY_ID = "demo-comm-001";
    private static final String COMMERCIAL_WORKFLOW_ID = "policy/commercial/" + COMMERCIAL_POLICY_ID;
    private static final CommercialPolicyInput COMMERCIAL_INPUT = new CommercialPolicyInput(
        COMMERCIAL_POLICY_ID,
        POLICY_HOLDER_ID,
        1704067200L,  // 2024-01-01 UTC
        1735689600L,  // 2025-01-01 UTC
        "State Farm Insurance Agency - Bloomington",
        List.of(
            new AdditionalInsured("AI-DEMO-001", "Landmark Properties LLC", "LANDLORD")
        )
    );

    // --- Activity stub ---

    private final DemoSetupActivities activities = Workflow.newActivityStub(
        DemoSetupActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .build()
    );

    @Override
    public DemoSetupResult run() {
        List<String> createdWorkflowIds = new ArrayList<>();

        String autoWfId = activities.createAutoPolicyIfAbsent(
            new DemoAutoPolicy(AUTO_WORKFLOW_ID, AUTO_INPUT));
        createdWorkflowIds.add(autoWfId);

        String propertyWfId = activities.createPropertyPolicyIfAbsent(
            new DemoPropertyPolicy(PROPERTY_WORKFLOW_ID, PROPERTY_INPUT));
        createdWorkflowIds.add(propertyWfId);

        String commercialWfId = activities.createCommercialPolicyIfAbsent(
            new DemoCommercialPolicy(COMMERCIAL_WORKFLOW_ID, COMMERCIAL_INPUT));
        createdWorkflowIds.add(commercialWfId);

        return new DemoSetupResult(createdWorkflowIds);
    }
}
