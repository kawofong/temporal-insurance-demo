// Input parameters for a CATEventWorkflow run.
package com.ziggy.insurance.domains.cat;

public record CATEventInput(
    String catEventId,          // e.g. EVT-2025-WILDFIRE-CA
    String eventName,           // e.g. "Butte County Wildfire"
    String affectedRegion,      // e.g. "Northern California"
    int totalClaimsToGenerate   // e.g. 100000
) {}
