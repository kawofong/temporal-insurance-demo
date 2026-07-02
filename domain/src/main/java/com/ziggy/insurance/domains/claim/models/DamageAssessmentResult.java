// Result of the (mock) damage assessment activity.
package com.ziggy.insurance.domains.claim.models;

public record DamageAssessmentResult(
    String summary,
    int estimatedCost
) {}
