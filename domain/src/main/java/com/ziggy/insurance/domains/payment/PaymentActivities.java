// Activity interface for the payment domain. The single activity stands in for a real payment
// gateway; its implementation is a mock. claimId is the idempotency key so retries after a
// crash never double-pay.
package com.ziggy.insurance.domains.payment;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface PaymentActivities {

    // Disburses the payout to the policyholder and returns the gateway's payment reference.
    @ActivityMethod
    String disburse(String claimId, String policyHolderId, int amount);
}
