// Returned immediately when the claim is accepted (before async processing).
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.models.ClaimStatus;

public record FnolResponse(
    String claimId,       // stable id the customer can track (always set)
    ClaimStatus status,   // SUBMITTED at intake; later states observed via GET
    String message
) {}
