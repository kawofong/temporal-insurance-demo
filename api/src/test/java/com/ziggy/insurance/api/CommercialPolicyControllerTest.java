// Integration tests for CommercialPolicyController.
// Verifies HTTP endpoints map correctly to Temporal workflow operations.
package com.ziggy.insurance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import com.ziggy.insurance.domains.policy.search.PolicySearchAttributes;
import io.temporal.api.enums.v1.IndexedValueType;
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

@WebMvcTest(CommercialPolicyController.class)
@Import({PolicyService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommercialPolicyControllerTest {

    private static final String POLICY_ID = "COMM-TEST-001";
    private static final String BASE_URL = "/api/v1/policies/commercial";

    static TestWorkflowEnvironment testEnv;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestWorkflowEnvironment testWorkflowEnvironment() {
            testEnv = TestWorkflowEnvironment.newInstance();
            testEnv.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            testEnv.registerSearchAttribute(PolicySearchAttributes.POLICY_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = testEnv.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(CommercialPolicyWorkflowImpl.class);
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
    void createCommercialPolicy() throws Exception {
        String body = """
            {
                "policyId": "%s",
                "policyHolderId": "PH-001",
                "effectiveDate": 1700000000,
                "expiryDate": 1731536000,
                "businessName": "Jake's Pixel Repair Shop",
                "additionalInsureds": []
            }
            """.formatted(POLICY_ID);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.policyId").value(POLICY_ID))
            .andExpect(jsonPath("$.workflowId").value("policy/commercial/" + POLICY_ID));
    }

    @Test
    @Order(2)
    void getCommercialPolicy() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyId").value(POLICY_ID))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.businessName").value("Jake's Pixel Repair Shop"));
    }

    @Test
    @Order(3)
    void addAdditionalInsured() throws Exception {
        String body = """
            {
                "additionalInsuredId": "AI-001",
                "name": "Landmark Properties LLC",
                "relationship": "LANDLORD"
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/additional-insureds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @Order(4)
    void addAdditionalInsuredDuplicateReturns400() throws Exception {
        String body = """
            {
                "additionalInsuredId": "AI-001",
                "name": "Duplicate Entry",
                "relationship": "CLIENT"
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/additional-insureds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(5)
    void removeAdditionalInsured() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + POLICY_ID + "/additional-insureds/AI-001"))
            .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.additionalInsureds").isEmpty());
    }

    @Test
    @Order(6)
    void removeAdditionalInsuredNotFoundReturns400() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + POLICY_ID + "/additional-insureds/AI-NONEXISTENT"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(7)
    void lifecycleTransitions() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "reason": "audit" }
                    """))
            .andExpect(status().isAccepted());
        awaitStatus("SUSPENDED");

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/reactivate"))
            .andExpect(status().isAccepted());
        awaitStatus("ACTIVE");

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/initiate-renewal"))
            .andExpect(status().isAccepted());
        awaitStatus("RENEWAL_PENDING");

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/complete-renewal"))
            .andExpect(status().isAccepted());
        awaitStatus("ACTIVE");
    }

    @Test
    @Order(8)
    void cancelPolicy() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "reason": "business closed" }
                    """))
            .andExpect(status().isAccepted());
    }

    @Test
    @Order(50)
    void addAdditionalInsuredWithoutIdGeneratesId() throws Exception {
        String genPolicyId = "COMM-GEN-001";
        String createBody = """
            {
                "policyId": "%s",
                "policyHolderId": "PH-001",
                "effectiveDate": 1700000000,
                "expiryDate": 1731536000,
                "businessName": "Generated Ventures LLC",
                "additionalInsureds": []
            }
            """.formatted(genPolicyId);
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated());

        // Client omits the additional insured id; the system assigns one.
        mockMvc.perform(post(BASE_URL + "/" + genPolicyId + "/additional-insureds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Generated Partner", "relationship": "CLIENT" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get(BASE_URL + "/" + genPolicyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.additionalInsureds[0].name").value("Generated Partner"))
            .andExpect(jsonPath("$.additionalInsureds[0].additionalInsuredId").isNotEmpty());
    }

    // Polls the policy status until the signalled transition is observable, so async signals settle
    // without a fixed real-time wait. Returns once the status matches or rethrows the assertion
    // error at the deadline for a clean failure.
    private void awaitStatus(String expectedStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
                    .andExpect(jsonPath("$.status").value(expectedStatus));
                return;
            } catch (AssertionError e) {
                if (System.currentTimeMillis() >= deadline) {
                    throw e;
                }
                Thread.sleep(25);
            }
        }
    }
}
