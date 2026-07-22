// Shared limits for CAT events, enforced at the UI, API, and workflow layers.
package com.ziggy.insurance.domains.cat;

public final class CATEventLimits {

    public static final int MAX_CLAIMS_PER_EVENT = 100_000;

    private CATEventLimits() {}
}
