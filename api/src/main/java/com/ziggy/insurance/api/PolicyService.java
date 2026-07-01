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
import com.ziggy.insurance.domains.policy.search.PolicySearchAttributes;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest;
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    // Generates a system-assigned identifier for a policy line item (driver, loss
    // payee, additional insured). Callers no longer need to supply these ids.
    private static String generateEntityId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
        Driver toAdd = hasText(driver.driverId())
            ? driver
            : new Driver(generateEntityId("D"), driver.name(), driver.licenseNumber());
        AutoPolicyWorkflow wf = workflowClient.newWorkflowStub(
            AutoPolicyWorkflow.class, workflowId("auto", policyId));
        wf.addDriver(toAdd);
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
        LossPayee toAdd = hasText(lossPayee.lossPayeeId())
            ? lossPayee
            : new LossPayee(generateEntityId("LP"), lossPayee.name(), lossPayee.loanNumber());
        PropertyPolicyWorkflow wf = workflowClient.newWorkflowStub(
            PropertyPolicyWorkflow.class, workflowId("property", policyId));
        return wf.addLossPayee(toAdd);
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
        AdditionalInsured toAdd = hasText(additionalInsured.additionalInsuredId())
            ? additionalInsured
            : new AdditionalInsured(
                generateEntityId("AI"), additionalInsured.name(), additionalInsured.relationship());
        CommercialPolicyWorkflow wf = workflowClient.newWorkflowStub(
            CommercialPolicyWorkflow.class, workflowId("commercial", policyId));
        return wf.addAdditionalInsured(toAdd);
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

    // --- List policies ---

    public PolicyListResponse listAllPolicies() {
        return listPolicies(null);
    }

    // Lists policies belonging to a single policyholder, filtered server-side
    // via the policyHolderId search attribute.
    public PolicyListResponse listPoliciesByPolicyHolder(String policyHolderId) {
        return listPolicies(policyHolderId);
    }

    // Aggregates policy workflows of every execution status from Temporal visibility, optionally
    // scoped to a single policyholder. Workflow ids are classified by type prefix, then each
    // policy's current state is fetched via query.
    private PolicyListResponse listPolicies(String policyHolderId) {
        List<AutoPolicyState> autoPolicies = new ArrayList<>();
        List<PropertyPolicyState> propertyPolicies = new ArrayList<>();
        List<CommercialPolicyState> commercialPolicies = new ArrayList<>();

        String namespace = workflowClient.getOptions().getNamespace();
        ListWorkflowExecutionsRequest request = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(namespace)
            .setQuery(buildPolicyListQuery(policyHolderId))
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

    // Builds the Temporal visibility query for policy workflows of every execution status,
    // scoped by workflow type to the three policy entity workflows. When a policyholder is
    // supplied, results are further scoped to that holder via the policyHolderId search attribute.
    static String buildPolicyListQuery(String policyHolderId) {
        String query = "WorkflowType IN ('"
            + AutoPolicyWorkflow.class.getSimpleName() + "', '"
            + PropertyPolicyWorkflow.class.getSimpleName() + "', '"
            + CommercialPolicyWorkflow.class.getSimpleName() + "')";
        if (hasText(policyHolderId)) {
            query += " AND " + PolicySearchAttributes.POLICY_HOLDER_ID
                + " = '" + policyHolderId + "'";
        }
        return query;
    }
}
