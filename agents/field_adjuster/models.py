# Data model for the field-adjuster agent, ported from the Java property-claim domain.
# Pydantic models mirror the Java records/enums so the agent works the same claim shape.
from __future__ import annotations

from enum import Enum

from pydantic import BaseModel


class DamageTier(str, Enum):
    """Severity band assigned to a catastrophe claim (null for portal-filed claims)."""

    TOTAL_LOSS = "TOTAL_LOSS"
    MAJOR_DAMAGE = "MAJOR_DAMAGE"
    MINOR_DAMAGE = "MINOR_DAMAGE"


class ClaimStatus(str, Enum):
    """Lifecycle states of a property claim, mirrored from the Java ClaimStatus enum."""

    SUBMITTED = "SUBMITTED"
    REJECTED = "REJECTED"
    COVERAGE_VERIFIED = "COVERAGE_VERIFIED"
    PENDING_DAMAGE_ASSESSMENT = "PENDING_DAMAGE_ASSESSMENT"
    PENDING_APPROVAL = "PENDING_APPROVAL"
    PAYMENT_PROCESSING = "PAYMENT_PROCESSING"
    CLOSED = "CLOSED"


class PropertyClaimInput(BaseModel):
    """Intake payload for a property claim. Timestamps are epoch milliseconds."""

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
    """Outcome of coverage verification. Deductible is whole dollars."""

    covered: bool
    coverage_type: str  # e.g. HO3 | HO6 | RENTERS
    deductible: int
    rejection_reason: str | None = None


class DamageAssessmentResult(BaseModel):
    """The field adjuster's damage assessment. estimated_cost is whole dollars."""

    summary: str
    estimated_cost: int


class AdjusterApprovalRequest(BaseModel):
    """The field adjuster's payout decision. approved_payout_amount is whole dollars."""

    adjuster_id: str
    approved_payout_amount: int
    notes: str


class FieldAdjusterRequest(BaseModel):
    """Workflow input: the claim plus its already-verified coverage."""

    claim: PropertyClaimInput
    coverage: CoverageVerificationResult


class FieldAdjusterReport(BaseModel):
    """The agent's structured output, mirroring the two field-adjuster signals."""

    assessment: DamageAssessmentResult
    approval: AdjusterApprovalRequest
