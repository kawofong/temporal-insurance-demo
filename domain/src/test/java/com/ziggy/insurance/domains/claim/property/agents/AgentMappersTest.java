// Unit tests for AgentMappers: converting claim workflow state into the agents' mirror records
// (spec §9). Covers the CAT-claim case (catEventId + damageTier populated) and the portal case
// (both null), plus the coverage and assessment mappers.
package com.ziggy.insurance.domains.claim.property.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.claim.models.CoverageVerificationResult;
import com.ziggy.insurance.domains.claim.models.DamageTier;
import com.ziggy.insurance.domains.claim.property.PropertyClaimInput;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import org.junit.jupiter.api.Test;

class AgentMappersTest {

    private PropertyClaimState catClaimState() {
        PropertyClaimState state = PropertyClaimState.fromInput(new PropertyClaimInput(
            "cat-hurricane-2026-7", "policy/property/syn-7", "holder-99",
            "cat-hurricane-2026", DamageTier.MAJOR_DAMAGE,
            "CAT event damage — MAJOR_DAMAGE", 1_760_000_000_000L,
            "Synthetic address in Florida", "SINGLE_FAMILY"));
        state.setDamageAssessment("Roof torn off; water intrusion.");
        state.setEstimatedRepairCost(24_500);
        return state;
    }

    @Test
    void toAgentClaimCopiesCatEventContext() {
        AgentPropertyClaim claim = AgentMappers.toAgentClaim(catClaimState());
        assertThat(claim.claimId()).isEqualTo("cat-hurricane-2026-7");
        assertThat(claim.policyId()).isEqualTo("policy/property/syn-7");
        assertThat(claim.policyHolderId()).isEqualTo("holder-99");
        assertThat(claim.catEventId()).isEqualTo("cat-hurricane-2026");
        assertThat(claim.damageTier()).isEqualTo("MAJOR_DAMAGE");
        assertThat(claim.incidentDate()).isEqualTo(1_760_000_000_000L);
        assertThat(claim.propertyAddress()).isEqualTo("Synthetic address in Florida");
        assertThat(claim.propertyType()).isEqualTo("SINGLE_FAMILY");
    }

    @Test
    void toAgentClaimLeavesCatContextNullForPortalClaim() {
        PropertyClaimState state = PropertyClaimState.fromInput(new PropertyClaimInput(
            "clm-portal-1", "demo-property-001", "PH-001",
            null, null,
            "Kitchen fire", 1_750_000_000L,
            "742 Evergreen Terrace", "SINGLE_FAMILY"));

        AgentPropertyClaim claim = AgentMappers.toAgentClaim(state);
        assertThat(claim.catEventId()).isNull();
        assertThat(claim.damageTier()).isNull();
        assertThat(claim.claimId()).isEqualTo("clm-portal-1");
        assertThat(claim.propertyType()).isEqualTo("SINGLE_FAMILY");
    }

    @Test
    void toAgentCoverageMirrorsVerifiedCoverage() {
        AgentCoverage coverage = AgentMappers.toAgentCoverage(
            new CoverageVerificationResult(true, "HO3", 1000, null));
        assertThat(coverage.covered()).isTrue();
        assertThat(coverage.coverageType()).isEqualTo("HO3");
        assertThat(coverage.deductible()).isEqualTo(1000);
        assertThat(coverage.rejectionReason()).isNull();
    }

    @Test
    void toAgentAssessmentReadsWhicheverAssessmentIsOnState() {
        AgentDamageAssessment assessment = AgentMappers.toAgentAssessment(catClaimState());
        assertThat(assessment.summary()).isEqualTo("Roof torn off; water intrusion.");
        assertThat(assessment.estimatedCost()).isEqualTo(24_500);
    }
}
