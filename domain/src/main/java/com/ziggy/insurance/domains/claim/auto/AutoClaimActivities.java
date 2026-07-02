// Activity interface for the auto claim lifecycle.
// All implementations are mocks standing in for downstream systems.
package com.ziggy.insurance.domains.claim.auto;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AutoClaimActivities {

    @ActivityMethod
    CoverageVerificationResult verifyCoverage(String policyId, String vehicleVin);

    @ActivityMethod
    String assignAdjuster(String claimId);

    // Dispatches the assigned adjuster to the field; the assessment arrives later via Signal.
    @ActivityMethod
    void dispatchFieldAdjuster(String claimId, String adjusterId);

    // claimId is the idempotency key so retries after a crash never double-pay.
    @ActivityMethod
    String processPayment(String claimId, String policyHolderId, int amount);

    // Mock email to the policyholder; the workflow calls this on every status change.
    @ActivityMethod
    void sendEmailNotification(String policyHolderId, String claimId, String message);
}
