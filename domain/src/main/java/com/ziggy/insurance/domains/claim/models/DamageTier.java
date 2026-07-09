// Severity tier for a property claim's damage.
// Set by CATEventWorkflow when it generates synthetic catastrophe claims; null for
// portal-filed claims where the tier is derived later from the field assessment.
package com.ziggy.insurance.domains.claim.models;

public enum DamageTier {
    TOTAL_LOSS,
    MAJOR_DAMAGE,
    MINOR_DAMAGE
}
