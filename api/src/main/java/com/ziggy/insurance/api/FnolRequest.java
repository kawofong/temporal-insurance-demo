// REST request body for FNOL submission (no claimId — the service generates it).
package com.ziggy.insurance.api;

public record FnolRequest(
    String policyId,
    String policyHolderId,
    String incidentDescription,
    long incidentDate,
    String incidentLocation,
    String vehicleVin,
    String vehicleMake,
    String vehicleModel,
    int vehicleYear
) {}
