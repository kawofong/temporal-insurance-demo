// REST request body for declaring a CAT event.
// batchSize is a workflow constant, so it is not accepted here.
package com.ziggy.insurance.api;

public record DeclareCATEventRequest(
    String catEventId,
    String eventName,
    String affectedRegion,
    int totalClaimsToGenerate
) {}
