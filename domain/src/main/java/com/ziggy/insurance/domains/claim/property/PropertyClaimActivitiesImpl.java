// Mock activity implementations for the property claim lifecycle. No external system calls —
// each method stands in for a downstream system so the workflow can be demoed end-to-end.
package com.ziggy.insurance.domains.claim.property;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.common.DemoLatency;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "claim-task-queue")
public class PropertyClaimActivitiesImpl implements PropertyClaimActivities {

    private static final int DEFAULT_DEDUCTIBLE = 1000;
    private static final String DEFAULT_ADJUSTER_ID = "adj-sarah";

    @Override
    public CoverageVerificationResult verifyCoverage(String policyId, String propertyAddress) {
        simulateProcessingDelay();
        if (propertyAddress == null || propertyAddress.isBlank()) {
            return new CoverageVerificationResult(
                false, null, 0, "No property address on the claim");
        }
        // Demo stand-in: a real impl would confirm the peril (fire, wind, hail) is covered by
        // querying the property PolicyWorkflow; this mock always approves an HO3 dwelling policy.
        return new CoverageVerificationResult(
            true, "HO3", DEFAULT_DEDUCTIBLE, null);
    }

    @Override
    public String assignAdjuster(String claimId) {
        simulateProcessingDelay();
        // Demo stand-in: a real impl would assign an adjuster; here we use a fixed id.
        return DEFAULT_ADJUSTER_ID;
    }

    @Override
    public void dispatchFieldAdjuster(String claimId, String adjusterId) {
        simulateProcessingDelay();
        // Demo stand-in: a real impl would notify the field adjuster app to inspect the property.
    }

    // Sleeps a random 500-1000 ms to mimic downstream system latency. Demo only — this makes
    // activity execution visible in the timeline; a real activity would do actual work instead.
    private static void simulateProcessingDelay() {
        DemoLatency.simulate(500, 1001);
    }
}
