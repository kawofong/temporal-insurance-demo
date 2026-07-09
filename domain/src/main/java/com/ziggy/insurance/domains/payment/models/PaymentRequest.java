// Input to the payment Nexus service. Describes a single payment to disburse to a policyholder.
//
// claimId is the business idempotency key: it ties the payment back to the claim that triggered
// it and, because the backing workflow is started with a claimId-derived id, guarantees a retried
// operation dedupes onto one payment run rather than paying twice.
package com.ziggy.insurance.domains.payment.models;

public record PaymentRequest(
    String claimId,
    String policyHolderId,
    int amount
) {}
