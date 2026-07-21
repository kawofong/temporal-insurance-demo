// Java-side mirror of the Python claim-adjuster agent's ClaimAdjudicationRequest Pydantic model.
// Passed as the untyped child-workflow argument to ClaimAdjusterWorkflow. Carries the claim, its
// verified coverage, and the damage assessment — all already held by the claim workflow, whether
// the assessment came from a human signal or the field-adjuster agent (§6.2, mixed mode).
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentClaimDecisionRequest(
    @JsonProperty("claim") AgentPropertyClaim claim,
    @JsonProperty("coverage") AgentCoverage coverage,
    @JsonProperty("assessment") AgentDamageAssessment assessment) {}
