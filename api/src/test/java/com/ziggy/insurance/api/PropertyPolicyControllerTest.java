// Integration tests for PropertyPolicyController.
// Verifies HTTP endpoints map correctly to Temporal workflow operations.
package com.ziggy.insurance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ziggy.insurance.domains.policy.property.PropertyPolicyWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PropertyPolicyController.class)
@Import({PolicyService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PropertyPolicyControllerTest {

    private static final String POLICY_ID = "PROP-TEST-001";
    private static final String BASE_URL = "/api/v1/policies/property";

    static TestWorkflowEnvironment testEnv;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestWorkflowEnvironment testWorkflowEnvironment() {
            testEnv = TestWorkflowEnvironment.newInstance();
            Worker worker = testEnv.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyPolicyWorkflowImpl.class);
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

    @AfterAll
    void tearDown() {
        if (testEnv != null) {
            testEnv.close();
        }
    }

    @Test
    @Order(1)
    void createPropertyPolicy() throws Exception {
        String body = """
            {
                "policyId": "%s",
                "policyHolderId": "PH-001",
                "effectiveDate": 1700000000,
                "expiryDate": 1731536000,
                "property": {
                    "propertyId": "P-001",
                    "address": "123 Main St",
                    "propertyType": "SINGLE_FAMILY"
                },
                "lossPayees": []
            }
            """.formatted(POLICY_ID);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.policyId").value(POLICY_ID))
            .andExpect(jsonPath("$.workflowId").value("policy/property/" + POLICY_ID));
    }

    @Test
    @Order(2)
    void getPropertyPolicy() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyId").value(POLICY_ID))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.property.address").value("123 Main St"));
    }

    @Test
    @Order(3)
    void addLossPayee() throws Exception {
        String body = """
            {
                "lossPayeeId": "LP-001",
                "name": "First National Bank",
                "loanNumber": "LN-98765"
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/loss-payees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @Order(4)
    void addLossPayeeDuplicateReturns400() throws Exception {
        String body = """
            {
                "lossPayeeId": "LP-001",
                "name": "Duplicate Bank",
                "loanNumber": "LN-00000"
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/loss-payees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(5)
    void removeLossPayee() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + POLICY_ID + "/loss-payees/LP-001"))
            .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.lossPayees").isEmpty());
    }

    @Test
    @Order(6)
    void removeLossPayeeNotFoundReturns400() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + POLICY_ID + "/loss-payees/LP-NONEXISTENT"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(7)
    void suspendPolicy() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "reason": "inspection required" }
                    """))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @Order(8)
    void reactivatePolicy() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/reactivate"))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(9)
    void initiateAndCompleteRenewal() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/initiate-renewal"))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("RENEWAL_PENDING"));

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/complete-renewal"))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(10)
    void cancelPolicy() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "reason": "sold property" }
                    """))
            .andExpect(status().isAccepted());
    }
}
