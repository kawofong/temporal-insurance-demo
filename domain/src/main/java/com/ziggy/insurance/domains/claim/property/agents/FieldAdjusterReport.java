// Java-side mirror of the Python field-adjuster agent's FieldAdjusterReport Pydantic model.
// Deserialized from the FieldAdjusterWorkflow child result. The workflow consumes `assessment`
// (the damage summary + estimated repair cost); `approval` is the field adjuster's recommendation
// and is informational only — the claim adjuster makes the binding decision (§4).
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldAdjusterReport(
    @JsonProperty("assessment") AgentDamageAssessment assessment,
    @JsonProperty("approval") AgentApprovalRecommendation approval) {}
