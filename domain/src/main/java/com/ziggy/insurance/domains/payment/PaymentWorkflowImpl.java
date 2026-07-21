// Workflow implementation behind the payment Nexus service: calls the payment gateway activity,
// relying on the default retry policy to drive the flaky gateway to eventual success.
package com.ziggy.insurance.domains.payment;

import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import com.ziggy.insurance.domains.payment.models.PaymentStatus;
import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;

@WorkflowImpl(taskQueues = PaymentNexus.TASK_QUEUE)
public class PaymentWorkflowImpl implements PaymentWorkflow {

    // No RetryOptions set — the default retry policy is what drives the flaky gateway to eventual success.
    private final PaymentActivities activities = Workflow.newActivityStub(
        PaymentActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .setSummary("Disburse claim payment")
            .build());

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        String paymentReference = activities.disburse(
            request.claimId(), request.policyHolderId(), request.amount());
        return new PaymentResult(request.claimId(), paymentReference, PaymentStatus.SUCCEEDED);
    }
}
