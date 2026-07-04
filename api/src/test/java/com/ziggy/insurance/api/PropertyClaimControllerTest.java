// Integration tests for PropertyClaimController.
// Verifies HTTP endpoints map correctly to Temporal workflow start, query, and signal.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziggy.insurance.domains.claim.property.PropertyClaimActivitiesImpl;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflowImpl;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationActivitiesImpl;
import com.ziggy.insurance.domains.notifications.NotificationServiceImpl;
import com.ziggy.insurance.domains.notifications.NotificationWorkflowImpl;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(PropertyClaimController.class)
@org.springframework.context.annotation.Import({PropertyClaimService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PropertyClaimControllerTest {

    private static final String BASE_URL = "/api/v1/claims/property";

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
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PropertyClaimActivitiesImpl());

            // The claim workflow notifies the policyholder over Nexus; stand up the
            // notifications domain (Nexus handler + workflow + activities) and endpoint.
            Worker notificationsWorker = testEnv.newWorker(NotificationsNexus.TASK_QUEUE);
            notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
            notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
            notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
            testEnv.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);

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
                "policyId": "demo-property-001",
                "policyHolderId": "PH-001",
                "incidentDescription": "Kitchen fire from a grease flare-up",
                "incidentDate": 1750000000,
                "propertyAddress": "742 Evergreen Terrace",
                "propertyType": "SINGLE_FAMILY"
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
        assertThat(claimId).startsWith("clm-");
    }

    @Test
    @Order(2)
    void submitFnolWithBlankAddressReturns400() throws Exception {
        String body = """
            {
                "policyId": "demo-property-001",
                "policyHolderId": "PH-001",
                "incidentDescription": "Kitchen fire from a grease flare-up",
                "incidentDate": 1750000000,
                "propertyAddress": "",
                "propertyType": "SINGLE_FAMILY"
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
                "policyId": "demo-property-001",
                "policyHolderId": "PH-001",
                "incidentDescription": "Kitchen fire from a grease flare-up",
                "incidentDate": 0,
                "propertyAddress": "742 Evergreen Terrace",
                "propertyType": "SINGLE_FAMILY"
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

        PropertyClaimState state = objectMapper.readValue(
            result.getResponse().getContentAsString(), PropertyClaimState.class);
        assertThat(state.getPolicyId()).isEqualTo("demo-property-001");
    }

    @Test
    @Order(5)
    void approveClaimTransitionsToClosed() throws Exception {
        // The field adjuster submits their assessment (Signal) so the claim leaves
        // PENDING_DAMAGE_ASSESSMENT for PENDING_APPROVAL.
        awaitStatus(claimId, "PENDING_DAMAGE_ASSESSMENT");
        mockMvc.perform(post(BASE_URL + "/" + claimId + "/damage-assessment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "summary": "Moderate smoke and fire damage to the kitchen.", "estimatedCost": 18500 }
                    """))
            .andExpect(status().isAccepted());

        awaitStatus(claimId, "PENDING_APPROVAL");

        String body = """
            {
                "adjusterId": "adj-sarah",
                "approvedPayoutAmount": 18500,
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
            .andExpect(jsonPath("$.approvedByAdjusterId").value("adj-sarah"))
            .andExpect(jsonPath("$.paymentReference").value("pay-" + claimId));
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
