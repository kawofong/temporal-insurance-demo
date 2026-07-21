// Java-side mirror of the Python agents' CoverageVerificationResult Pydantic model.
// rejection_reason is null when covered. See AgentPropertyClaim for the wire-contract rationale.
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentCoverage(
    @JsonProperty("covered") boolean covered,
    @JsonProperty("coverage_type") String coverageType,
    @JsonProperty("deductible") int deductible,
    @JsonProperty("rejection_reason") String rejectionReason) {}
