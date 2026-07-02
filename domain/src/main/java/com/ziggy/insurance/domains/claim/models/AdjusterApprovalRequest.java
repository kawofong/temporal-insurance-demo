// Signal payload carrying the adjuster's approval decision for a claim.
package com.ziggy.insurance.domains.claim.models;

public record AdjusterApprovalRequest(
    String adjusterId,
    int approvedPayoutAmount,
    String notes
) {}
