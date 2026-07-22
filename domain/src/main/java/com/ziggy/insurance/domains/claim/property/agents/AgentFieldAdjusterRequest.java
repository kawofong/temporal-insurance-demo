// Java-side mirror of the Python field-adjuster agent's FieldAdjusterRequest Pydantic model.
// Passed as the untyped child-workflow argument to FieldAdjusterWorkflow. Both members are
// already held by the claim workflow (claim state + the verified coverage result), so the agent
// fetches nothing (§4).
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentFieldAdjusterRequest(
    @JsonProperty("claim") AgentPropertyClaim claim,
    @JsonProperty("coverage") AgentCoverage coverage) {}
