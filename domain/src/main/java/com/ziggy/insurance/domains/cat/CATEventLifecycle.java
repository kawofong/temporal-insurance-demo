// Lifecycle states for a catastrophe (CAT) event workflow.
// COMPLETED is the terminal state, reached once every synthetic claim has been filed.
package com.ziggy.insurance.domains.cat;

public enum CATEventLifecycle {
    DECLARED,   // first run, before any batch
    SPAWNING,   // starting batches of child claims; carried across continue-as-new
    COMPLETED   // terminal — all claims filed and the event workflow completes
}
