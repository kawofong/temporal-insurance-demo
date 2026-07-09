// Returned immediately after the CATEventWorkflow is started.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.cat.CATEventLifecycle;

public record DeclareCATEventResponse(
    String catEventId,
    CATEventLifecycle status,   // DECLARED at declaration
    int totalClaimsExpected
) {}
