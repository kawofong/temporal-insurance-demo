// Workflow that backs the payment Nexus service. It owns disbursing one claim payment:
// invoking the (mocked, flaky) payment gateway and letting Temporal retry it to success.
package com.ziggy.insurance.domains.payment;

import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PaymentWorkflow {

    @WorkflowMethod
    PaymentResult processPayment(PaymentRequest request);
}
