// Converts property-claim workflow state into the snake_case mirror records the Python agents
// consume. Everything the agents need is passed via workflow input — no activity fetches state
// (§4/§6.3). Pure functions so they are unit-testable without a workflow environment.
package com.ziggy.insurance.domains.claim.property.agents;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.claim.models.DamageTier;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;

public final class AgentMappers {

    private AgentMappers() {}

    // Renders the claim identity + incident context the agents reason over. catEventId and
    // damageTier are null for portal-filed claims; damageTier is sent as its enum name so it
    // matches the Python str-enum values (TOTAL_LOSS / MAJOR_DAMAGE / MINOR_DAMAGE).
    public static AgentPropertyClaim toAgentClaim(PropertyClaimState state) {
        DamageTier tier = state.getDamageTier();
        return new AgentPropertyClaim(
            state.getClaimId(),
            state.getPolicyId(),
            state.getPolicyHolderId(),
            state.getCatEventId(),
            tier == null ? null : tier.name(),
            state.getIncidentDescription(),
            state.getIncidentDate(),
            state.getPropertyAddress(),
            state.getPropertyType());
    }

    // Mirrors the already-verified coverage result the claim workflow holds from verifyCoverage.
    public static AgentCoverage toAgentCoverage(CoverageVerificationResult coverage) {
        return new AgentCoverage(
            coverage.covered(),
            coverage.coverageType(),
            coverage.deductible(),
            coverage.rejectionReason());
    }

    // Mirrors the damage assessment currently on state, regardless of whether it arrived via a
    // human submitDamageAssessment signal or the field-adjuster agent (§6.2, mixed mode).
    public static AgentDamageAssessment toAgentAssessment(PropertyClaimState state) {
        return new AgentDamageAssessment(
            state.getDamageAssessment(),
            state.getEstimatedRepairCost());
    }
}
