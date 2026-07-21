// Java-side mirror of the Python claim-adjuster agent's ClaimAdjudicationReport Pydantic model.
// Deserialized from the ClaimAdjusterWorkflow child result: the binding approve/deny decision.
// The toApprovalRequest / toDenialRequest converters funnel the AI decision through the exact
// same signal payloads a human adjuster would send, so the claim workflow's downstream code and
// audit trail are identical for the human and AI paths (§6.2).
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.AdjusterDenialRequest;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimDecisionReport(
    @JsonProperty("approved") boolean approved,
    @JsonProperty("approved_payout_amount") int approvedPayoutAmount,
    @JsonProperty("adjuster_id") String adjusterId,
    @JsonProperty("notes") String notes,
    @JsonProperty("rejection_reason") String rejectionReason,
    @JsonProperty("rationale") String rationale) {

    // Maps an approval decision onto the same payload the adjusterApproval signal carries.
    public AdjusterApprovalRequest toApprovalRequest() {
        return new AdjusterApprovalRequest(adjusterId, approvedPayoutAmount, notes);
    }

    // Maps a denial decision onto the same payload the adjusterDenial signal carries.
    public AdjusterDenialRequest toDenialRequest() {
        return new AdjusterDenialRequest(adjusterId, rejectionReason);
    }
}
