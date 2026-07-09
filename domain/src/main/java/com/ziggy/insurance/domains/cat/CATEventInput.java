// Input parameters for a CATEventWorkflow run.
// Carries the batch-iterator cursor (nextClaimIndex) plus the durable facts that must
// survive each continue-as-new checkpoint: the carried counter (totalClaimsOpened) and the
// first-run declaration time (declaredAt). All are 0 on the first run.
package com.ziggy.insurance.domains.cat;

public record CATEventInput(
    String catEventId,          // e.g. EVT-2025-WILDFIRE-CA
    String eventName,           // e.g. "Butte County Wildfire"
    String affectedRegion,      // e.g. "Northern California"
    int totalClaimsToGenerate,  // e.g. 100000
    int nextClaimIndex,         // batch-iterator offset; 0 on first run
    int totalClaimsOpened,      // carried counter; 0 on first run
    long declaredAt             // first-run declaration time; 0 on first run, carried after
) {}
