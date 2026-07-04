// Activity interface for the property claim lifecycle.
// Mirrors AutoClaimActivities; only verifyCoverage differs (property address / peril
// instead of VIN). All implementations are mocks standing in for downstream systems.
//
// The "Property" namePrefix disambiguates these activity type names from AutoClaimActivities'
// identical method names: both impls run on claim-task-queue, and a worker cannot register
// two activities under the same type name. Property activities register as
// PropertyVerifyCoverage, PropertyAssignAdjuster, etc.
package com.ziggy.insurance.domains.claim.property;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface(namePrefix = "Property")
public interface PropertyClaimActivities {

    @ActivityMethod
    CoverageVerificationResult verifyCoverage(String policyId, String propertyAddress);

    @ActivityMethod
    String assignAdjuster(String claimId);

    // Dispatches the assigned adjuster to the field; the assessment arrives later via Signal.
    @ActivityMethod
    void dispatchFieldAdjuster(String claimId, String adjusterId);

    // claimId is the idempotency key so retries after a crash never double-pay.
    @ActivityMethod
    String processPayment(String claimId, String policyHolderId, int amount);
}
