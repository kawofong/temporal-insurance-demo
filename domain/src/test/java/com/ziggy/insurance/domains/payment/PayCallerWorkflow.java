// Test-only caller workflow: exercises the payment domain across the Nexus boundary exactly as
// a real domain (e.g. claims) would.
package com.ziggy.insurance.domains.payment;

import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PayCallerWorkflow {

    @WorkflowMethod
    PaymentResult pay(PaymentRequest request);
}
