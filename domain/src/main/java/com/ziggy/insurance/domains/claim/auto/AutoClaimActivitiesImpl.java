// Mock activity implementations for the auto claim lifecycle. No external system calls —
// each method stands in for a downstream system so the workflow can be demoed end-to-end.
package com.ziggy.insurance.domains.claim.auto;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import io.temporal.spring.boot.ActivityImpl;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "claim-task-queue")
public class AutoClaimActivitiesImpl implements AutoClaimActivities {

    private static final int DEFAULT_DEDUCTIBLE = 500;
    private static final String DEFAULT_ADJUSTER_ID = "adj-sarah";

    @Override
    public CoverageVerificationResult verifyCoverage(String policyId, String vehicleVin) {
        // Artificial 100-500 ms delay so the demo shows realistic downstream latency.
        simulateProcessingDelay();
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
        // Artificial 100-500 ms delay so the demo shows realistic downstream latency.
        simulateProcessingDelay();
        // Demo stand-in: a real impl would assign an adjuster; here we use a fixed id.
        return DEFAULT_ADJUSTER_ID;
    }

    @Override
    public void dispatchFieldAdjuster(String claimId, String adjusterId) {
        // Artificial 100-500 ms delay so the demo shows realistic downstream latency.
        simulateProcessingDelay();
        // Demo stand-in: a real impl would notify the field adjuster app to inspect the vehicle.
    }

    // Sleeps a random 100-500 ms to mimic downstream system latency. Demo only — this makes
    // activity execution visible in the timeline; a real activity would do actual work instead.
    private static void simulateProcessingDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1001));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
