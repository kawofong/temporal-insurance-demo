// Lifecycle states for an auto claim, from intake through payout.
// CLOSED is the terminal state that causes workflow completion.
package com.ziggy.insurance.domains.claim.models;

public enum ClaimStatus {
    SUBMITTED,           // Workflow started; coverage not yet verified
    COVERAGE_VERIFIED,   // Coverage confirmed
    UNDER_REVIEW,        // Adjuster assigned; damage assessment running
    PENDING_APPROVAL,    // Damage assessed; waiting for adjuster Signal
    APPROVED,            // Adjuster approved; payment can proceed
    PAYMENT_PROCESSING,  // Payment activity running
    CLOSED               // terminal — workflow completes (paid, or coverage denied)
}
