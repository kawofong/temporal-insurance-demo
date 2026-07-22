// Java-side mirror of the Python field-adjuster agent's AdjusterApprovalRequest Pydantic model.
// This is the field adjuster's *recommended* payout — informational only. The claim adjuster
// makes the binding decision (§4), so this value never closes the claim.
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentApprovalRecommendation(
    @JsonProperty("adjuster_id") String adjusterId,
    @JsonProperty("approved_payout_amount") int approvedPayoutAmount,
    @JsonProperty("notes") String notes) {}
