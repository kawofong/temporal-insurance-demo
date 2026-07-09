// Outcome of a payment. The payment workflow only completes once the (mocked) gateway settles,
// so a returned result is always SUCCEEDED; FAILED is modelled for completeness of the domain.
package com.ziggy.insurance.domains.payment.models;

public enum PaymentStatus {
    SUCCEEDED,  // funds disbursed; paymentReference is the gateway handle
    FAILED      // payment could not be settled
}
