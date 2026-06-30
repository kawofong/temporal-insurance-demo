// Service layer that facades Temporal WorkflowClient interactions.
// Translates HTTP-level operations into workflow start, signal, update, and query calls.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.policy.auto.AutoPolicyInput;
import com.ziggy.insurance.domains.policy.auto.AutoPolicyState;
import com.ziggy.insurance.domains.policy.auto.AutoPolicyWorkflow;
import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyInput;
import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyState;
import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyWorkflow;
import com.ziggy.insurance.domains.demo.DemoSetupResult;
import com.ziggy.insurance.domains.demo.SetupDemoEnvironmentWorkflow;
import com.ziggy.insurance.domains.policy.models.AdditionalInsured;
import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.LossPayee;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyInput;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyState;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyWorkflow;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PolicyService {

    private final WorkflowClient workflowClient;

    public PolicyService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    // --- Workflow ID conventions ---

    public static String workflowId(String type, String policyId) {
        return "policy/" + type + "/" + policyId;
    }

    // --- Auto policy ---

    public String createAutoPolicy(AutoPolicyInput input) {
        String wfId = workflowId("auto", input.policyId());
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId(wfId)
                .build());
        WorkflowClient.start(wf::run, input);
        return wfId;
    }

    public AutoPolicyState getAutoPolicy(String policyId) {
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class, workflowId("auto", policyId));
        return wf.getPolicy();
    }

    public int addVehicle(String policyId, Vehicle vehicle) {
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class, workflowId("auto", policyId));
        return wf.addVehicle(vehicle);
    }

    public void removeVehicle(String policyId, String vehicleId) {
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class, workflowId("auto", policyId));
        wf.removeVehicle(vehicleId);
    }

    public void addDriver(String policyId, Driver driver) {
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class, workflowId("auto", policyId));
        wf.addDriver(driver);
    }

    public void removeDriver(String policyId, String driverId) {
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class, workflowId("auto", policyId));
        wf.removeDriver(driverId);
    }

    // --- Property policy ---

    public String createPropertyPolicy(PropertyPolicyInput input) {
        String wfId = workflowId("property", input.policyId());
        PropertyPolicyWorkflow wf = workflowClient.newWorkflowStub(
            PropertyPolicyWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId(wfId)
                .build());
        WorkflowClient.start(wf::run, input);
        return wfId;
    }

    public PropertyPolicyState getPropertyPolicy(String policyId) {
        PropertyPolicyWorkflow wf = workflowClient.newWorkflowStub(
            PropertyPolicyWorkflow.class, workflowId("property", policyId));
        return wf.getPolicy();
    }

    public int addLossPayee(String policyId, LossPayee lossPayee) {
        PropertyPolicyWorkflow wf = workflowClient.newWorkflowStub(
            PropertyPolicyWorkflow.class, workflowId("property", policyId));
        return wf.addLossPayee(lossPayee);
    }

    public void removeLossPayee(String policyId, String lossPayeeId) {
        PropertyPolicyWorkflow wf = workflowClient.newWorkflowStub(
            PropertyPolicyWorkflow.class, workflowId("property", policyId));
        wf.removeLossPayee(lossPayeeId);
    }

    // --- Commercial policy ---

    public String createCommercialPolicy(CommercialPolicyInput input) {
        String wfId = workflowId("commercial", input.policyId());
        CommercialPolicyWorkflow wf = workflowClient.newWorkflowStub(
            CommercialPolicyWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId(wfId)
                .build());
        WorkflowClient.start(wf::run, input);
        return wfId;
    }

    public CommercialPolicyState getCommercialPolicy(String policyId) {
        CommercialPolicyWorkflow wf = workflowClient.newWorkflowStub(
            CommercialPolicyWorkflow.class, workflowId("commercial", policyId));
        return wf.getPolicy();
    }

    public int addAdditionalInsured(String policyId, AdditionalInsured additionalInsured) {
        CommercialPolicyWorkflow wf = workflowClient.newWorkflowStub(
            CommercialPolicyWorkflow.class, workflowId("commercial", policyId));
        return wf.addAdditionalInsured(additionalInsured);
    }

    public void removeAdditionalInsured(String policyId, String additionalInsuredId) {
        CommercialPolicyWorkflow wf = workflowClient.newWorkflowStub(
            CommercialPolicyWorkflow.class, workflowId("commercial", policyId));
        wf.removeAdditionalInsured(additionalInsuredId);
    }

    // --- Lifecycle signals (shared across all types) ---

    public void suspendAutoPolicy(String policyId, String reason) {
        workflowClient.newWorkflowStub(AutoPolicyWorkflow.class,
            workflowId("auto", policyId)).suspendPolicy(reason);
    }

    public void reactivateAutoPolicy(String policyId) {
        workflowClient.newWorkflowStub(AutoPolicyWorkflow.class,
            workflowId("auto", policyId)).reactivatePolicy();
    }

    public void cancelAutoPolicy(String policyId, String reason) {
        workflowClient.newWorkflowStub(AutoPolicyWorkflow.class,
            workflowId("auto", policyId)).cancelPolicy(reason);
    }

    public void initiateAutoRenewal(String policyId) {
        workflowClient.newWorkflowStub(AutoPolicyWorkflow.class,
            workflowId("auto", policyId)).initiateRenewal();
    }

    public void completeAutoRenewal(String policyId) {
        workflowClient.newWorkflowStub(AutoPolicyWorkflow.class,
            workflowId("auto", policyId)).completeRenewal();
    }

    public void suspendPropertyPolicy(String policyId, String reason) {
        workflowClient.newWorkflowStub(PropertyPolicyWorkflow.class,
            workflowId("property", policyId)).suspendPolicy(reason);
    }

    public void reactivatePropertyPolicy(String policyId) {
        workflowClient.newWorkflowStub(PropertyPolicyWorkflow.class,
            workflowId("property", policyId)).reactivatePolicy();
    }

    public void cancelPropertyPolicy(String policyId, String reason) {
        workflowClient.newWorkflowStub(PropertyPolicyWorkflow.class,
            workflowId("property", policyId)).cancelPolicy(reason);
    }

    public void initiatePropertyRenewal(String policyId) {
        workflowClient.newWorkflowStub(PropertyPolicyWorkflow.class,
            workflowId("property", policyId)).initiateRenewal();
    }

    public void completePropertyRenewal(String policyId) {
        workflowClient.newWorkflowStub(PropertyPolicyWorkflow.class,
            workflowId("property", policyId)).completeRenewal();
    }

    public void suspendCommercialPolicy(String policyId, String reason) {
        workflowClient.newWorkflowStub(CommercialPolicyWorkflow.class,
            workflowId("commercial", policyId)).suspendPolicy(reason);
    }

    public void reactivateCommercialPolicy(String policyId) {
        workflowClient.newWorkflowStub(CommercialPolicyWorkflow.class,
            workflowId("commercial", policyId)).reactivatePolicy();
    }

    public void cancelCommercialPolicy(String policyId, String reason) {
        workflowClient.newWorkflowStub(CommercialPolicyWorkflow.class,
            workflowId("commercial", policyId)).cancelPolicy(reason);
    }

    public void initiateCommercialRenewal(String policyId) {
        workflowClient.newWorkflowStub(CommercialPolicyWorkflow.class,
            workflowId("commercial", policyId)).initiateRenewal();
    }

    public void completeCommercialRenewal(String policyId) {
        workflowClient.newWorkflowStub(CommercialPolicyWorkflow.class,
            workflowId("commercial", policyId)).completeRenewal();
    }

    // --- Demo setup ---

    public DemoSetupResult setupDemoEnvironment() {
        SetupDemoEnvironmentWorkflow wf = workflowClient.newWorkflowStub(
            SetupDemoEnvironmentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.POLICY_TASK_QUEUE)
                .setWorkflowId("demo/setup")
                .build());
        return wf.run();
    }

    // --- List all policies ---

    public PolicyListResponse listAllPolicies() {
        List<AutoPolicyState> autoPolicies = new ArrayList<>();
        List<PropertyPolicyState> propertyPolicies = new ArrayList<>();
        List<CommercialPolicyState> commercialPolicies = new ArrayList<>();

        String namespace = workflowClient.getOptions().getNamespace();
        String query = "TaskQueue = '" + TaskQueues.POLICY_TASK_QUEUE
            + "' AND ExecutionStatus = 'Running'";
        ListWorkflowExecutionsRequest request = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(namespace)
            .setQuery(query)
            .build();

        ListWorkflowExecutionsResponse response = workflowClient.getWorkflowServiceStubs()
            .blockingStub()
            .listWorkflowExecutions(request);

        for (WorkflowExecutionInfo info : response.getExecutionsList()) {
            String wfId = info.getExecution().getWorkflowId();
            if (wfId.startsWith("policy/auto/")) {
                AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
                    AutoPolicyWorkflow.class, wfId);
                autoPolicies.add(wf.getPolicy());
            } else if (wfId.startsWith("policy/property/")) {
                PropertyPolicyWorkflow wf = workflowClient.newWorkflowStub(
                    PropertyPolicyWorkflow.class, wfId);
                propertyPolicies.add(wf.getPolicy());
            } else if (wfId.startsWith("policy/commercial/")) {
                CommercialPolicyWorkflow wf = workflowClient.newWorkflowStub(
                    CommercialPolicyWorkflow.class, wfId);
                commercialPolicies.add(wf.getPolicy());
            }
        }

        return new PolicyListResponse(autoPolicies, propertyPolicies, commercialPolicies);
    }
}
