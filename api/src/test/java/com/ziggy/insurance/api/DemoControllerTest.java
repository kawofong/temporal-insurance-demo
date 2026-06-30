// Integration tests for DemoController.
// Verifies the demo setup endpoint starts the workflow and returns created policy IDs.
package com.ziggy.insurance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ziggy.insurance.domains.policy.TaskQueues;
import com.ziggy.insurance.domains.demo.DemoAutoPolicy;
import com.ziggy.insurance.domains.demo.DemoCommercialPolicy;
import com.ziggy.insurance.domains.demo.DemoPropertyPolicy;
import com.ziggy.insurance.domains.demo.DemoSetupActivities;
import com.ziggy.insurance.domains.demo.SetupDemoEnvironmentWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemoController.class)
@Import({PolicyService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoControllerTest {

    static TestWorkflowEnvironment testEnv;

    // Activity stub for test — returns workflow IDs without starting real workflows.
    static class StubDemoSetupActivities implements DemoSetupActivities {
        @Override
        public String createAutoPolicyIfAbsent(DemoAutoPolicy request) {
            return request.workflowId();
        }

        @Override
        public String createPropertyPolicyIfAbsent(DemoPropertyPolicy request) {
            return request.workflowId();
        }

        @Override
        public String createCommercialPolicyIfAbsent(DemoCommercialPolicy request) {
            return request.workflowId();
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @Bean
        public TestWorkflowEnvironment testWorkflowEnvironment() {
            testEnv = TestWorkflowEnvironment.newInstance();
            Worker worker = testEnv.newWorker(TaskQueues.POLICY_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(SetupDemoEnvironmentWorkflowImpl.class);
            worker.registerActivitiesImplementations(new StubDemoSetupActivities());
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
    void setupDemoReturnsCreatedPolicies() throws Exception {
        mockMvc.perform(post("/api/v1/demo/setup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdWorkflowIds[0]").value("policy/auto/demo-auto-001"))
            .andExpect(jsonPath("$.createdWorkflowIds[1]").value("policy/property/demo-prop-001"))
            .andExpect(jsonPath("$.createdWorkflowIds[2]").value("policy/commercial/demo-comm-001"))
            .andExpect(jsonPath("$.createdWorkflowIds.length()").value(3));
    }
}
