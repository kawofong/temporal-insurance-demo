// REST request body for a property FNOL submission — no claimId (the service generates it)
// and no catEventId/damageTier (only CATEventWorkflow sets those). Mirrors FnolRequest.
package com.ziggy.insurance.api;

public record PropertyFnolRequest(
    String policyId,
    String policyHolderId,
    String incidentDescription,
    long incidentDate,
    String propertyAddress,
    String propertyType,        // SINGLE_FAMILY | CONDO | RENTER
    // Optional: open the claim already in AI-adjuster mode (fully-autonomous demo). Absent in
    // JSON → false (Jackson defaults the missing primitive), i.e. the default human path.
    boolean aiAdjusterEnabled
) {}
