// Integration tests for CATEventController.
// Verifies HTTP endpoints map correctly to Temporal workflow start and query.
// A tiny event (4 claims) keeps the child fan-out cheap while still crossing a batch.
package com.ziggy.insurance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziggy.insurance.domains.cat.CATEventActivitiesImpl;
import com.ziggy.insurance.domains.cat.CATEventLifecycle;
import com.ziggy.insurance.domains.cat.CATEventLimits;
import com.ziggy.insurance.domains.cat.CATEventStatus;
import com.ziggy.insurance.domains.cat.CATEventWorkflowImpl;
import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.claim.property.PropertyClaimActivities;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflowImpl;
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
import org.junit.jupiter.api.AfterAll;
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

@WebMvcTest(CATEventController.class)
@Import({CATEventService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "spring.temporal.test-server.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CATEventControllerTest {

    private static final String BASE_URL = "/api/v1/cat";
    private static final String CAT_EVENT_ID = "EVT-2025-WILDFIRE-CA";

    static TestWorkflowEnvironment testEnv;

    // Delay-free stand-in so the child claims spawned by the fan-out complete instantly.
    static class FastPropertyClaimActivities implements PropertyClaimActivities {
        @Override
        public CoverageVerificationResult verifyCoverage(String policyId, String propertyAddress) {
            return new CoverageVerificationResult(true, "HO3", 1000, null);
        }

        @Override
        public String assignAdjuster(String claimId) {
            return "adj-sarah";
        }

        @Override
        public void dispatchFieldAdjuster(String claimId, String adjusterId) {}
    }

    // Delay-free, non-flaky payment stand-in so each child claim's payout (over Nexus) settles
    // instantly instead of running the real gateway's retry backoff across the fan-out.
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
            Worker catWorker = testEnv.newWorker(TaskQueues.CAT_TASK_QUEUE);
            catWorker.registerWorkflowImplementationTypes(CATEventWorkflowImpl.class);
            catWorker.registerActivitiesImplementations(
                new CATEventActivitiesImpl(testEnv.getWorkflowClient()));
            Worker worker = testEnv.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
            worker.registerActivitiesImplementations(new FastPropertyClaimActivities());

            // Each child claim notifies the policyholder over Nexus; stand up the
            // notifications domain (Nexus handler + workflow + activities) and endpoint.
            Worker notificationsWorker = testEnv.newWorker(NotificationsNexus.TASK_QUEUE);
            notificationsWorker.registerNexusServiceImplementation(new NotificationServiceImpl());
            notificationsWorker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
            notificationsWorker.registerActivitiesImplementations(new NotificationActivitiesImpl());
            testEnv.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);

            // Each child claim disburses its payout over Nexus; stand up the payment domain
            // (Nexus handler + workflow + fast activity) and endpoint.
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
    @Order(1)
    void declareReturnsDeclaredStatus() throws Exception {
        String body = """
            {
                "catEventId": "%s",
                "eventName": "Butte County Wildfire",
                "affectedRegion": "Northern California",
                "totalClaimsToGenerate": 4
            }
            """.formatted(CAT_EVENT_ID);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.catEventId").value(CAT_EVENT_ID))
            .andExpect(jsonPath("$.status").value("DECLARED"))
            .andExpect(jsonPath("$.totalClaimsExpected").value(4));
    }

    @Test
    @Order(2)
    void declareWithNonPositiveTotalReturns400() throws Exception {
        String body = """
            {
                "catEventId": "EVT-BAD",
                "eventName": "Bad Event",
                "affectedRegion": "Nowhere",
                "totalClaimsToGenerate": 0
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
    void getReturnsEventStatusWithAllClaimsOpened() throws Exception {
        awaitCompleted(CAT_EVENT_ID);
        mockMvc.perform(get(BASE_URL + "/" + CAT_EVENT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.catEventId").value(CAT_EVENT_ID))
            .andExpect(jsonPath("$.totalClaimsExpected").value(4))
            .andExpect(jsonPath("$.totalClaimsOpened").value(4))
            .andExpect(jsonPath("$.percentComplete").value(100.0))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @Order(4)
    void declareWithExcessiveTotalReturns400() throws Exception {
        String body = """
            {
                "catEventId": "EVT-TOO-BIG",
                "eventName": "Too Big Event",
                "affectedRegion": "Nowhere",
                "totalClaimsToGenerate": %d
            }
            """.formatted(CATEventLimits.MAX_CLAIMS_PER_EVENT + 1);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    // Polls until the event reaches its terminal COMPLETED state — i.e. every claim has been
    // filed and the workflow has completed (the counter hits the total one CAN hop earlier).
    private void awaitCompleted(String catEventId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        CATEventStatus status = null;
        while (System.currentTimeMillis() < deadline) {
            var result = mockMvc.perform(get(BASE_URL + "/" + catEventId)).andReturn();
            if (result.getResponse().getStatus() == 200) {
                status = objectMapper.readValue(
                    result.getResponse().getContentAsString(), CATEventStatus.class);
                if (status.status() == CATEventLifecycle.COMPLETED) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        org.assertj.core.api.Assertions.assertThat(status)
            .extracting(CATEventStatus::status)
            .isEqualTo(CATEventLifecycle.COMPLETED);
    }
}
