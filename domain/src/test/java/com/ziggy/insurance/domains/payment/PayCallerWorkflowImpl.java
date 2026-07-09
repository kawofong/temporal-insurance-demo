// Test-only caller workflow implementation. Mirrors how AutoClaimWorkflowImpl targets the payment
// Nexus service: a stub bound to the endpoint, calling processPayment.
package com.ziggy.insurance.domains.payment;

import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import io.temporal.workflow.NexusOperationOptions;
import io.temporal.workflow.NexusServiceOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class PayCallerWorkflowImpl implements PayCallerWorkflow {

    private final PaymentService payments = Workflow.newNexusServiceStub(
        PaymentService.class,
        NexusServiceOptions.newBuilder()
            .setEndpoint(PaymentNexus.ENDPOINT)
            .setOperationOptions(
                NexusOperationOptions.newBuilder()
                    .setScheduleToCloseTimeout(Duration.ofMinutes(5))
                    .build())
            .build());

    @Override
    public PaymentResult pay(PaymentRequest request) {
        return payments.processPayment(request);
    }
}
