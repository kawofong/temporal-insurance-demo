// Mock activity implementations for the auto claim lifecycle. No external system calls —
// each method stands in for a downstream system so the workflow can be demoed end-to-end.
package com.ziggy.insurance.domains.claim.auto;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "claim-task-queue")
public class AutoClaimActivitiesImpl implements AutoClaimActivities {

    private static final int DEFAULT_DEDUCTIBLE = 500;
    private static final String DEFAULT_ADJUSTER_ID = "ADJ-SARAH";
    // processPayment fails on earlier attempts so the demo shows Temporal retrying to success.
    private static final int PAYMENT_SUCCEEDS_ON_ATTEMPT = 6;

    @Override
    public CoverageVerificationResult verifyCoverage(String policyId, String vehicleVin) {
        if (vehicleVin == null || vehicleVin.isBlank()) {
            return new CoverageVerificationResult(
                false, null, 0, "No vehicle VIN on the claim");
        }
        // Demo stand-in: a real impl would look up coverage; this mock always approves collision.
        return new CoverageVerificationResult(
            true, "COLLISION", DEFAULT_DEDUCTIBLE, null);
    }

    @Override
    public String assignAdjuster(String claimId) {
        // Demo stand-in: a real impl would assign an adjuster; here we use a fixed id.
        return DEFAULT_ADJUSTER_ID;
    }

    @Override
    public void dispatchFieldAdjuster(String claimId, String adjusterId) {
        // Demo stand-in: a real impl would notify the field adjuster app to inspect the vehicle.
    }

    @Override
    public String processPayment(String claimId, String policyHolderId, int amount) {
        // Simulate a flaky payment gateway: fail early attempts so the default retry policy
        // drives the activity to eventual success.
        int attempt = Activity.getExecutionContext().getInfo().getAttempt();
        if (attempt < PAYMENT_SUCCEEDS_ON_ATTEMPT) {
            throw new RuntimeException("Payment gateway unavailable (attempt " + attempt + ")");
        }
        return "PAY-" + claimId;
    }

    @Override
    public void sendEmailNotification(String policyHolderId, String claimId, String message) {
        // Demo stand-in: a real impl would email the policyholder.
    }
}
