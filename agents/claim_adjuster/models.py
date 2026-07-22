# Data model for the claim-adjuster agent.
# The claim adjuster makes the binding approve/deny decision from the claim, its verified
# coverage, and the field adjuster's damage assessment — everything the Java claim workflow
# already holds. It does NOT take policy state (coverage was verified upstream), so no policy
# models live here. Field names are snake_case; they are the cross-language wire contract with
# the Java mirror records in domain/.../claim/property/agents/ (see the spec, §6.3/§6.4).
from __future__ import annotations

from enum import Enum

from pydantic import BaseModel


class DamageTier(str, Enum):
    """Severity band assigned to a catastrophe claim (null for portal-filed claims)."""

    TOTAL_LOSS = "TOTAL_LOSS"
    MAJOR_DAMAGE = "MAJOR_DAMAGE"
    MINOR_DAMAGE = "MINOR_DAMAGE"


class PropertyClaimInput(BaseModel):
    """Intake payload for a property claim being adjudicated. Timestamps are epoch millis."""

    claim_id: str
    policy_id: str
    policy_holder_id: str
    cat_event_id: str | None = None
    damage_tier: DamageTier | None = None
    incident_description: str
    incident_date: int
    property_address: str
    property_type: str  # SINGLE_FAMILY | CONDO | RENTER


class CoverageVerificationResult(BaseModel):
    """The already-verified coverage. rejection_reason is null when covered."""

    covered: bool
    coverage_type: str  # e.g. HO3 | HO6 | RENTERS
    deductible: int
    rejection_reason: str | None = None


class DamageAssessmentResult(BaseModel):
    """The field adjuster's damage assessment. estimated_cost is whole dollars."""

    summary: str
    estimated_cost: int


class ClaimAdjudicationRequest(BaseModel):
    """Workflow input: the claim, its verified coverage, and the damage assessment."""

    claim: PropertyClaimInput
    coverage: CoverageVerificationResult
    assessment: DamageAssessmentResult


class ClaimAdjudicationReport(BaseModel):
    """The agent's binding approve/deny decision.

    On approval, approved_payout_amount is the repair cost minus the deductible, clamped at
    zero, and rejection_reason is null. On denial, approved_payout_amount is 0 and
    rejection_reason explains why.
    """

    approved: bool
    approved_payout_amount: int  # estimated_cost - deductible, >= 0; 0 when denied
    adjuster_id: str = "adj-ai-agent"
    notes: str  # justification when approved
    rejection_reason: str | None = None  # populated when approved is False
    rationale: str
