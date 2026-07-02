// Lifecycle states for an auto claim, from intake through payout.
// CLOSED (paid) and REJECTED (coverage denied) are the terminal states that complete the workflow.
package com.ziggy.insurance.domains.claim.models;

public enum ClaimStatus {
    SUBMITTED,                  // Workflow started; coverage not yet verified
    REJECTED,                   // terminal — coverage denied at intake
    COVERAGE_VERIFIED,          // Coverage confirmed
    PENDING_DAMAGE_ASSESSMENT,  // Field adjuster dispatched; awaiting their assessment Signal
    PENDING_APPROVAL,           // Damage assessed; awaiting the adjuster's approval Signal
    PAYMENT_PROCESSING,         // Payment activity running
    CLOSED                      // terminal — claim paid and workflow completes
}
