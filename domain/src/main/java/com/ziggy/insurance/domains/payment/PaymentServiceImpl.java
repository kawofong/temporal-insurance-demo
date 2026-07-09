// Nexus service handler for the payment domain.
//
// @ServiceImpl binds this handler to the PaymentService contract; @NexusServiceImpl lets the
// Spring Boot worker auto-discover it and register it on the payment task queue.
// processPayment is backed by a workflow (PaymentWorkflow): the operation starts a workflow run
// that drives the payment activity to success through Temporal's retries. The caller blocks on
// the operation until that run completes.
package com.ziggy.insurance.domains.payment;

import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import io.nexusrpc.handler.OperationHandler;
import io.nexusrpc.handler.OperationImpl;
import io.nexusrpc.handler.ServiceImpl;
import io.temporal.client.WorkflowOptions;
import io.temporal.nexus.Nexus;
import io.temporal.nexus.WorkflowRunOperation;
import io.temporal.spring.boot.NexusServiceImpl;
import org.springframework.stereotype.Component;

@Component
@ServiceImpl(service = PaymentService.class)
@NexusServiceImpl(taskQueues = PaymentNexus.TASK_QUEUE)
public class PaymentServiceImpl {

    @OperationImpl
    public OperationHandler<PaymentRequest, PaymentResult> processPayment() {
        // The claim id is the payment workflow id: it is the business idempotency key, so a
        // retried operation (or a re-triggered payment for the same claim) deduping onto one
        // workflow run is exactly the "never double-pay" guarantee we want.
        return WorkflowRunOperation.fromWorkflowMethod(
            (context, details, request) ->
                Nexus.getOperationContext()
                    .getWorkflowClient()
                    .newWorkflowStub(
                        PaymentWorkflow.class,
                        WorkflowOptions.newBuilder()
                            .setWorkflowId("payment/" + request.claimId())
                            .build())
                    ::processPayment);
    }
}
