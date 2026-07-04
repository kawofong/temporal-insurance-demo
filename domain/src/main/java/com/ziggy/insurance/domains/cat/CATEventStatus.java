// Query response describing a CAT event's live progress.
// percentComplete is derived from the carried counter, which survives continue-as-new.
package com.ziggy.insurance.domains.cat;

public record CATEventStatus(
    String catEventId,
    String eventName,
    String affectedRegion,
    CATEventLifecycle status,
    int totalClaimsExpected,
    int totalClaimsOpened,
    double percentComplete,
    long declaredAt
) {}
