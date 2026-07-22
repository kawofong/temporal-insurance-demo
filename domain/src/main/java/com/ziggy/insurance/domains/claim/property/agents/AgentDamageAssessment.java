// Java-side mirror of the Python agents' DamageAssessmentResult Pydantic model.
// estimated_cost is whole dollars. Deserialized from the field-adjuster agent's report and
// sent back into the claim-adjuster agent, so it is both produced and consumed here.
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDamageAssessment(
    @JsonProperty("summary") String summary,
    @JsonProperty("estimated_cost") int estimatedCost) {}
