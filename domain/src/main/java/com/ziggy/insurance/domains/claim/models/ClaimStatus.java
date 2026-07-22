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
    CLOSED;                     // terminal — claim paid and workflow completes

    // A claim only reaches CLOSED/REJECTED as the workflow's last act before it completes, so a
    // Running execution is never required to trust these — unlike the non-terminal statuses,
    // which a workflow can leave behind forever if it dies (terminated/timed out/canceled) while
    // still parked there. Callers use this to decide whether a claim-status list query also needs
    // an ExecutionStatus = 'Running' filter (see ClaimService/PropertyClaimService).
    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED;
    }
}
