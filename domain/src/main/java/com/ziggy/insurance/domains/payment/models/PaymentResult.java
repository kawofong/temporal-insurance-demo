// Output of the payment Nexus service: the outcome of disbursing one claim payment.
//
// paymentReference is the (mock) gateway handle for the settled payment; callers persist it for
// traceability. claimId echoes the request so the result is self-describing.
package com.ziggy.insurance.domains.payment.models;

public record PaymentResult(
    String claimId,
    String paymentReference,
    PaymentStatus status
) {}
