// Nexus service contract for the payment domain.
//
// This is the well-defined API other domains (e.g. claims) call across the Nexus boundary to
// disburse customer payments instead of owning payment logic themselves. Today it exposes a
// single operation; new payment capabilities are added as new @Operation methods here.
package com.ziggy.insurance.domains.payment;

import com.ziggy.insurance.domains.payment.models.PaymentRequest;
import com.ziggy.insurance.domains.payment.models.PaymentResult;
import io.nexusrpc.Operation;
import io.nexusrpc.Service;

@Service
public interface PaymentService {

    // Disburses a payment to a policyholder and returns the settlement outcome. Backed by a
    // workflow so the (retryable) gateway interaction is durable and exactly-once per claim.
    @Operation
    PaymentResult processPayment(PaymentRequest request);
}
