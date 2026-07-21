# Data model for the claim-adjuster agent, ported from the Java property-policy domain.
# Pydantic models mirror the Java records/enums so the agent adjudicates the same shapes.
from __future__ import annotations

from enum import Enum

from pydantic import BaseModel


class PolicyStatus(str, Enum):
    """Lifecycle states of a property policy, mirrored from the Java PolicyStatus enum."""

    ACTIVE = "ACTIVE"
    SUSPENDED = "SUSPENDED"
    RENEWAL_PENDING = "RENEWAL_PENDING"
    CANCELLED = "CANCELLED"


class DamageTier(str, Enum):
    """Severity band assigned to a catastrophe claim (null for portal-filed claims)."""

    TOTAL_LOSS = "TOTAL_LOSS"
    MAJOR_DAMAGE = "MAJOR_DAMAGE"
    MINOR_DAMAGE = "MINOR_DAMAGE"


class InsuredProperty(BaseModel):
    """The property insured under a policy. property_type: SINGLE_FAMILY | CONDO | RENTER."""

    property_id: str
    address: str
    property_type: str


class LossPayee(BaseModel):
    """A loss payee (typically a lender) on a property policy."""

    loss_payee_id: str
    name: str
    loan_number: str


class PropertyPolicyState(BaseModel):
    """The policy on file that a claim is adjudicated against. Dates are epoch millis."""

    policy_id: str
    policy_holder_id: str
    status: PolicyStatus
    effective_date: int
    expiry_date: int
    property: InsuredProperty
    loss_payees: list[LossPayee] = []


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
    """The adjuster's coverage determination. rejection_reason is null when covered."""

    covered: bool
    coverage_type: str  # e.g. HO3 | HO6 | RENTERS
    deductible: int
    rejection_reason: str | None = None


class ClaimAdjudicationRequest(BaseModel):
    """Workflow input: the claim plus the policy it is adjudicated against."""

    claim: PropertyClaimInput
    policy: PropertyPolicyState


class ClaimAdjudicationReport(BaseModel):
    """The agent's structured output: the coverage determination plus its rationale."""

    coverage: CoverageVerificationResult
    rationale: str
