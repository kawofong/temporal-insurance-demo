// Result of the (mock) coverage verification activity.
// rejectionReason is null when covered.
package com.ziggy.insurance.domains.claim.models;

public record CoverageVerificationResult(
    boolean covered,
    String coverageType,     // COLLISION | COMPREHENSIVE
    int deductible,
    String rejectionReason
) {}
