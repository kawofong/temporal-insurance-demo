// Signal payload carrying the claims adjuster's denial decision for a claim.
package com.ziggy.insurance.domains.claim.models;

public record AdjusterDenialRequest(
    String adjusterId,
    String reason
) {}
