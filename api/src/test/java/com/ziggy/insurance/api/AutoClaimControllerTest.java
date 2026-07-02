// Integration tests for AutoClaimController.
// Verifies HTTP endpoints map correctly to Temporal workflow start, query, and signal.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziggy.insurance.domains.claim.auto.AutoClaimActivitiesImpl;
import com.ziggy.insurance.domains.claim.auto.AutoClaimState;
import com.ziggy.insurance.domains.claim.auto.AutoClaimWorkflowImpl;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AutoClaimController.class)
@Import({ClaimService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AutoClaimControllerTest {

    private static final String BASE_URL = "/api/v1/claims/auto";

    static TestWorkflowEnvironment testEnv;
    static String claimId;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        public TestWorkflowEnvironment testWorkflowEnvironment() {
            testEnv = TestWorkflowEnvironment.newInstance();
            testEnv.registerSearchAttribute(ClaimSearchAttributes.POLICY_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            testEnv.registerSearchAttribute(ClaimSearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            testEnv.registerSearchAttribute(ClaimSearchAttributes.CLAIM_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = testEnv.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new AutoClaimActivitiesImpl());
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
    @Order(1)
    void submitFnolReturnsClaimIdImmediately() throws Exception {
        String body = """
            {
                "policyId": "demo-auto-001",
                "policyHolderId": "PH-001",
                "incidentDescription": "Rear-ended at a stoplight",
                "incidentDate": 1750000000,
                "incidentLocation": "Chicago, IL",
                "vehicleVin": "1HGFE2F59NH000001",
                "vehicleMake": "Honda",
                "vehicleModel": "Civic",
                "vehicleYear": 2022
            }
            """;

        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.claimId").isNotEmpty())
            .andReturn();

        FnolResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), FnolResponse.class);
        claimId = response.claimId();
        assertThat(claimId).startsWith("CLM-");
    }

    @Test
    @Order(2)
    void submitFnolWithBlankVinReturns400() throws Exception {
        String body = """
            {
                "policyId": "demo-auto-001",
                "policyHolderId": "PH-001",
                "incidentDescription": "Rear-ended at a stoplight",
                "incidentDate": 1750000000,
                "incidentLocation": "Chicago, IL",
                "vehicleVin": "",
                "vehicleMake": "Honda",
                "vehicleModel": "Civic",
                "vehicleYear": 2022
            }
            """;

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(3)
    void submitFnolWithNonPositiveIncidentDateReturns400() throws Exception {
        String body = """
            {
                "policyId": "demo-auto-001",
                "policyHolderId": "PH-001",
                "incidentDescription": "Rear-ended at a stoplight",
                "incidentDate": 0,
                "incidentLocation": "Chicago, IL",
                "vehicleVin": "1HGFE2F59NH000002",
                "vehicleMake": "Honda",
                "vehicleModel": "Civic",
                "vehicleYear": 2022
            }
            """;

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(4)
    void getClaimReturnsLiveState() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE_URL + "/" + claimId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claimId").value(claimId))
            .andReturn();

        AutoClaimState state = objectMapper.readValue(
            result.getResponse().getContentAsString(), AutoClaimState.class);
        assertThat(state.getPolicyId()).isEqualTo("demo-auto-001");
    }

    @Test
    @Order(5)
    void approveClaimTransitionsToApprovedThenClosed() throws Exception {
        // The field adjuster submits their assessment (Signal) so the claim leaves
        // PENDING_DAMAGE_ASSESSMENT for PENDING_APPROVAL.
        awaitStatus(claimId, "PENDING_DAMAGE_ASSESSMENT");
        mockMvc.perform(post(BASE_URL + "/" + claimId + "/damage-assessment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "summary": "Moderate front-end collision damage.", "estimatedCost": 4200 }
                    """))
            .andExpect(status().isAccepted());

        awaitStatus(claimId, "PENDING_APPROVAL");

        String body = """
            {
                "adjusterId": "ADJ-SARAH",
                "approvedPayoutAmount": 4200,
                "notes": "Approved after review"
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + claimId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted());

        // processPayment fails until attempt 6; advance the time-skipping server past the
        // default retry backoff so the claim reaches CLOSED without a real-time wait.
        testEnv.sleep(Duration.ofSeconds(40));
        awaitStatus(claimId, "CLOSED");

        mockMvc.perform(get(BASE_URL + "/" + claimId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.approvedByAdjusterId").value("ADJ-SARAH"))
            .andExpect(jsonPath("$.paymentReference").value("PAY-" + claimId));
    }

    @Test
    @Order(100)
    void getNonExistentClaimReturns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/DOES-NOT-EXIST"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private void awaitStatus(String claimId, String expectedStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        String actual = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get(BASE_URL + "/" + claimId)).andReturn();
            AutoClaimState state = objectMapper.readValue(
                result.getResponse().getContentAsString(), AutoClaimState.class);
            actual = state.getStatus().name();
            if (actual.equals(expectedStatus)) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(actual).isEqualTo(expectedStatus);
    }
}
