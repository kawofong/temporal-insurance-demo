// Tests the payment domain end-to-end over Nexus: a caller workflow invokes the processPayment
// operation, which starts the payment workflow that drives the flaky gateway activity to success.
// We assert the settlement outcome and the never-double-pay workflow id.
package com.ziggy.insurance.domains.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import com.ziggy.insurance.domains.payment.models.PaymentStatus;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private static final String CALLER_TASK_QUEUE = "test-pay-caller-task-queue";

    // Stands up the payment domain: a worker hosting the Nexus service handler plus the workflow
    // and activity it starts, the endpoint callers target, and a caller worker.
    private TestWorkflowEnvironment startedEnv(TestWorkflowEnvironment env) {
        Worker paymentWorker = env.newWorker(PaymentNexus.TASK_QUEUE);
        paymentWorker.registerNexusServiceImplementation(new PaymentServiceImpl());
        paymentWorker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        paymentWorker.registerActivitiesImplementations(new PaymentActivitiesImpl());
        env.createNexusEndpoint(PaymentNexus.ENDPOINT, PaymentNexus.TASK_QUEUE);

        Worker callerWorker = env.newWorker(CALLER_TASK_QUEUE);
        callerWorker.registerWorkflowImplementationTypes(PayCallerWorkflowImpl.class);

        env.start();
        return env;
    }

    private PayCallerWorkflow caller(TestWorkflowEnvironment env, String workflowId) {
        return env.getWorkflowClient().newWorkflowStub(
            PayCallerWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(CALLER_TASK_QUEUE)
                .setWorkflowId(workflowId)
                .build());
    }

    @Test
    void disbursesPaymentAndReturnsSettlement() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            startedEnv(env);
            PayCallerWorkflow wf = caller(env, "pay/caller-1");

            // The gateway is flaky (fails the first attempts); the time-skipping test server
            // fast-forwards through the retry backoff until the payment settles.
            PaymentResult result = wf.pay(new PaymentRequest("CLM-1", "PH-001", 4200));

            assertThat(result.claimId()).isEqualTo("CLM-1");
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(result.paymentReference()).isEqualTo("pay-CLM-1");
        }
    }

    @Test
    void paymentWorkflowIdIsDerivedFromClaimId() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            startedEnv(env);
            PayCallerWorkflow wf = caller(env, "pay/caller-2");

            wf.pay(new PaymentRequest("CLM-2", "PH-002", 900));

            // The backing payment workflow is started with a claimId-derived id so a retried
            // operation dedupes onto one run and never double-pays.
            assertThat(env.getWorkflowClient()
                    .newUntypedWorkflowStub("payment/CLM-2")
                    .getResult(PaymentResult.class))
                .extracting(PaymentResult::paymentReference)
                .isEqualTo("pay-CLM-2");
        }
    }
}
