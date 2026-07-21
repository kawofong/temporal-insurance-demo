// Integration tests for the AI-adjuster REST surface of PropertyClaimController (spec §7).
// Covers the single enableAiAdjuster endpoint and the intake aiAdjusterEnabled flag, driving the
// claim through deterministic Temporal child stand-ins registered under the agents' workflow type
// names on ai-agents-task-queue (no LLM). The batch endpoint's Visibility path is not implemented
// by the time-skipping test server, so it is unit-tested via buildAiAdjusterBatchQuery instead.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziggy.insurance.domains.claim.property.PropertyClaimActivitiesImpl;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflowImpl;
import com.ziggy.insurance.domains.claim.property.agents.AgentApprovalRecommendation;
import com.ziggy.insurance.domains.claim.property.agents.AgentClaimDecisionRequest;
import com.ziggy.insurance.domains.claim.property.agents.AgentDamageAssessment;
import com.ziggy.insurance.domains.claim.property.agents.AgentFieldAdjusterRequest;
import com.ziggy.insurance.domains.claim.property.agents.ClaimDecisionReport;
import com.ziggy.insurance.domains.claim.property.agents.FieldAdjusterReport;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationActivitiesImpl;
import com.ziggy.insurance.domains.notifications.NotificationServiceImpl;
import com.ziggy.insurance.domains.notifications.NotificationWorkflowImpl;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.payment.PaymentActivities;
import com.ziggy.insurance.domains.payment.PaymentNexus;
import com.ziggy.insurance.domains.payment.PaymentServiceImpl;
import com.ziggy.insurance.domains.payment.PaymentWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(PropertyClaimController.class)
@Import({PropertyClaimService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyClaimAiAdjusterControllerTest {

    private static final String BASE_URL = "/api/v1/claims/property";

    static TestWorkflowEnvironment testEnv;

    @WorkflowInterface
    public interface FieldAdjusterWorkflow {
        @WorkflowMethod
        FieldAdjusterReport run(AgentFieldAdjusterRequest request);
    }

    public static class FieldAdjusterWorkflowImpl implements FieldAdjusterWorkflow {
        @Override
        public FieldAdjusterReport run(AgentFieldAdjusterRequest request) {
            return new FieldAdjusterReport(
                new AgentDamageAssessment("AI assessment for " + request.claim().claimId(), 20_000),
                new AgentApprovalRecommendation("adj-ai-agent", 19_000, "recommended"));
        }
    }

    @WorkflowInterface
    public interface ClaimAdjusterWorkflow {
        @WorkflowMethod
        ClaimDecisionReport run(AgentClaimDecisionRequest request);
    }

    public static class ClaimAdjusterWorkflowImpl implements ClaimAdjusterWorkflow {
        @Override
        public ClaimDecisionReport run(AgentClaimDecisionRequest request) {
            int payout = Math.max(0,
                request.assessment().estimatedCost() - request.coverage().deductible());
            return new ClaimDecisionReport(
                true, payout, "adj-ai-agent", "Approved by AI adjuster", null, "covered");
        }
    }

    static class FastPaymentActivities implements PaymentActivities {
        @Override
        public String disburse(String claimId, String policyHolderId, int amount) {
            return "pay-" + claimId;
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        public TestWorkflowEnvironment testWorkflowEnvironment() {
            testEnv = TestWorkflowEnvironment.newInstance();
            testEnv.registerSearchAttribute(ClaimSearchAttributes.POLICY_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            testEnv.registerSearchAttribute(ClaimSearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            testEnv.registerSearchAttribute(ClaimSearchAttributes.CLAIM_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            testEnv.registerSearchAttribute(ClaimSearchAttributes.CAT_EVENT_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);

            Worker worker = testEnv.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PropertyClaimActivitiesImpl());

            Worker agentsWorker = testEnv.newWorker(TaskQueues.AI_AGENTS_TASK_QUEUE);
            agentsWorker.registerWorkflowImplementationTypes(
                FieldAdjusterWorkflowImpl.class, ClaimAdjusterWorkflowImpl.class);

            Worker notificationsWorker = testEnv.newWorker(NotificationsNexus.TASK_QUEUE);
            notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
            notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
            notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
            testEnv.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);

            Worker paymentWorker = testEnv.newWorker(PaymentNexus.TASK_QUEUE);
            paymentWorker.registerNexusServiceImplementation(new PaymentServiceImpl());
            paymentWorker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
            paymentWorker.registerActivitiesImplementations(new FastPaymentActivities());
            testEnv.createNexusEndpoint(PaymentNexus.ENDPOINT, PaymentNexus.TASK_QUEUE);

            testEnv.start();
            return testEnv;
        }

        @Bean
        public WorkflowClient workflowClient(TestWorkflowEnvironment env) {
            return env.getWorkflowClient();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    void tearDown() {
        if (testEnv != null) {
            testEnv.close();
        }
    }

    @Test
    void enableSingleClaimRoutesParkedClaimToAiAndCloses() throws Exception {
        String claimId = submitClaim(false);

        awaitStatus(claimId, "PENDING_DAMAGE_ASSESSMENT");
        mockMvc.perform(post(BASE_URL + "/" + claimId + "/ai-adjuster"))
            .andExpect(status().isAccepted());

        awaitStatus(claimId, "CLOSED");
        mockMvc.perform(get(BASE_URL + "/" + claimId))
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.aiAdjusterEnabled").value(true))
            .andExpect(jsonPath("$.approvedByAdjusterId").value("adj-ai-agent"))
            .andExpect(jsonPath("$.approvedPayoutAmount").value(19000));
    }

    @Test
    void submitWithIntakeFlagRunsFullyAutonomously() throws Exception {
        String claimId = submitClaim(true);

        awaitStatus(claimId, "CLOSED");
        mockMvc.perform(get(BASE_URL + "/" + claimId))
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.aiAdjusterEnabled").value(true))
            .andExpect(jsonPath("$.approvedByAdjusterId").value("adj-ai-agent"));
    }

    private String submitClaim(boolean aiAdjusterEnabled) throws Exception {
        String body = """
            {
                "policyId": "demo-property-001",
                "policyHolderId": "PH-001",
                "incidentDescription": "Wind tore off roof shingles",
                "incidentDate": 1750000000,
                "propertyAddress": "742 Evergreen Terrace",
                "propertyType": "SINGLE_FAMILY",
                "aiAdjusterEnabled": %s
            }
            """.formatted(aiAdjusterEnabled);

        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(
            result.getResponse().getContentAsString(), FnolResponse.class).claimId();
    }

    private void awaitStatus(String claimId, String expectedStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        String actual = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get(BASE_URL + "/" + claimId)).andReturn();
            PropertyClaimState state = objectMapper.readValue(
                result.getResponse().getContentAsString(), PropertyClaimState.class);
            actual = state.getStatus().name();
            if (actual.equals(expectedStatus)) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(actual).isEqualTo(expectedStatus);
    }
}
