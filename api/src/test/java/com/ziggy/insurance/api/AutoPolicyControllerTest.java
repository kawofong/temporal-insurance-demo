// Integration tests for AutoPolicyController.
// Verifies HTTP endpoints map correctly to Temporal workflow operations.
package com.ziggy.insurance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ziggy.insurance.domains.policy.auto.AutoPolicyState;
import com.ziggy.insurance.domains.policy.auto.AutoPolicyWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import com.ziggy.insurance.domains.policy.search.PolicySearchAttributes;
import io.temporal.api.enums.v1.IndexedValueType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
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

@WebMvcTest(AutoPolicyController.class)
@Import({PolicyService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AutoPolicyControllerTest {

    private static final String POLICY_ID = "AUTO-TEST-001";
    private static final String BASE_URL = "/api/v1/policies/auto";

    static TestWorkflowEnvironment testEnv;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        public TestWorkflowEnvironment testWorkflowEnvironment() {
            testEnv = TestWorkflowEnvironment.newInstance();
            testEnv.registerSearchAttribute(PolicySearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
            Worker worker = testEnv.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AutoPolicyWorkflowImpl.class);
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
    void createAutoPolicy() throws Exception {
        String body = """
            {
                "policyId": "%s",
                "policyHolderId": "PH-001",
                "effectiveDate": 1700000000,
                "expiryDate": 1731536000,
                "insuredVehicles": [],
                "listedDrivers": []
            }
            """.formatted(POLICY_ID);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.policyId").value(POLICY_ID))
            .andExpect(jsonPath("$.workflowId").value("policy/auto/" + POLICY_ID));
    }

    @Test
    @Order(2)
    void getAutoPolicy() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyId").value(POLICY_ID))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();

        AutoPolicyState state = objectMapper.readValue(
            result.getResponse().getContentAsString(), AutoPolicyState.class);
        assertThat(state.getInsuredVehicles()).isEmpty();
        assertThat(state.getListedDrivers()).isEmpty();
    }

    @Test
    @Order(3)
    void addVehicle() throws Exception {
        String body = """
            {
                "vehicleId": "V-001",
                "vin": "1HGCM82633A004352",
                "make": "Honda",
                "model": "Accord",
                "year": 2024
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/vehicles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @Order(4)
    void addVehicleDuplicateVinReturns400() throws Exception {
        String body = """
            {
                "vehicleId": "V-002",
                "vin": "1HGCM82633A004352",
                "make": "Toyota",
                "model": "Camry",
                "year": 2025
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/vehicles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(5)
    void removeVehicle() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + POLICY_ID + "/vehicles/V-001"))
            .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.insuredVehicles").isEmpty());
    }

    @Test
    @Order(6)
    void removeVehicleNotFoundReturns400() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + POLICY_ID + "/vehicles/V-NONEXISTENT"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @Order(7)
    void addDriver() throws Exception {
        String body = """
            {
                "driverId": "D-001",
                "name": "Jake",
                "licenseNumber": "DL-12345"
            }
            """;

        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/drivers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listedDrivers[0].driverId").value("D-001"));
    }

    @Test
    @Order(8)
    void removeDriver() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/" + POLICY_ID + "/drivers/D-001"))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listedDrivers").isEmpty());
    }

    @Test
    @Order(9)
    void suspendPolicy() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/suspend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "reason": "non-payment" }
                    """))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @Order(10)
    void reactivatePolicy() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/reactivate"))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(11)
    void initiateRenewal() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/initiate-renewal"))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("RENEWAL_PENDING"));
    }

    @Test
    @Order(12)
    void completeRenewal() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/complete-renewal"))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + POLICY_ID))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(13)
    void cancelPolicy() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + POLICY_ID + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "reason": "policyholder request" }
                    """))
            .andExpect(status().isAccepted());
    }

    @Test
    @Order(50)
    void addDriverWithoutIdGeneratesId() throws Exception {
        String genPolicyId = "AUTO-GEN-001";
        String createBody = """
            {
                "policyId": "%s",
                "policyHolderId": "PH-001",
                "effectiveDate": 1700000000,
                "expiryDate": 1731536000,
                "insuredVehicles": [],
                "listedDrivers": []
            }
            """.formatted(genPolicyId);
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated());

        // Client omits the driver id; the system assigns one.
        mockMvc.perform(post(BASE_URL + "/" + genPolicyId + "/drivers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Morgan", "licenseNumber": "DL-GEN-001" }
                    """))
            .andExpect(status().isAccepted());

        Thread.sleep(500);
        mockMvc.perform(get(BASE_URL + "/" + genPolicyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listedDrivers[0].name").value("Morgan"))
            .andExpect(jsonPath("$.listedDrivers[0].driverId").isNotEmpty());
    }

    @Test
    @Order(100)
    void getNonExistentPolicyReturns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/DOES-NOT-EXIST"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
