"""Cross-language serialization contract, Python side (spec §6.3 / §9).

These assert that each Pydantic model's JSON dump matches the same fixtures the Java mirror
records are checked against (domain/src/test/resources/agents/*.json). Together with the Java
AgentSerializationContractTest, this catches wire-format drift on either side: change a field
name or type in the Pydantic models (or the Java records) without updating the fixture and a
build breaks.

Null-valued fields are normalised away before comparison, matching the Java test: an omitted key
and an explicit null are equivalent on the wire (the receiving side defaults the missing optional).
"""

from __future__ import annotations

import json
from pathlib import Path

from claim_adjuster.models import (
    ClaimAdjudicationReport,
    ClaimAdjudicationRequest,
)
from claim_adjuster.models import CoverageVerificationResult as ClaimCoverage
from claim_adjuster.models import DamageAssessmentResult as ClaimAssessment
from claim_adjuster.models import DamageTier as ClaimDamageTier
from claim_adjuster.models import PropertyClaimInput as ClaimClaim
from field_adjuster.models import (
    AdjusterApprovalRequest,
    CoverageVerificationResult,
    DamageAssessmentResult,
    DamageTier,
    FieldAdjusterReport,
    FieldAdjusterRequest,
    PropertyClaimInput,
)

FIXTURES = (
    Path(__file__).resolve().parents[2] / "domain" / "src" / "test" / "resources" / "agents"
)


def _load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text())


def _strip_nulls(value):
    if isinstance(value, dict):
        return {k: _strip_nulls(v) for k, v in value.items() if v is not None}
    return value


def _dump(model) -> dict:
    return _strip_nulls(model.model_dump(mode="json"))


_CLAIM_KWARGS = dict(
    claim_id="clm-a1b2c3d4",
    policy_id="pol-100200",
    policy_holder_id="ph-42",
    cat_event_id="cat-hurricane-2026",
    incident_description="Hurricane-force winds tore off roughly half the roof shingles.",
    incident_date=1_760_000_000_000,
    property_address="742 Evergreen Terrace, Springfield",
    property_type="SINGLE_FAMILY",
)
_ASSESSMENT_SUMMARY = "Roof and window damage with water intrusion to the ceiling and drywall."


def test_field_adjuster_request_matches_fixture() -> None:
    request = FieldAdjusterRequest(
        claim=PropertyClaimInput(damage_tier=DamageTier.MAJOR_DAMAGE, **_CLAIM_KWARGS),
        coverage=CoverageVerificationResult(
            covered=True, coverage_type="HO3", deductible=1000, rejection_reason=None
        ),
    )
    assert _dump(request) == _strip_nulls(_load("field_adjuster_request.json"))


def test_field_adjuster_report_matches_fixture() -> None:
    report = FieldAdjusterReport(
        assessment=DamageAssessmentResult(summary=_ASSESSMENT_SUMMARY, estimated_cost=24_500),
        approval=AdjusterApprovalRequest(
            adjuster_id="adj-ai-agent",
            approved_payout_amount=23_500,
            notes="Estimated repair cost minus the policy deductible.",
        ),
    )
    assert _dump(report) == _strip_nulls(_load("field_adjuster_report.json"))


def test_claim_decision_request_matches_fixture() -> None:
    request = ClaimAdjudicationRequest(
        claim=ClaimClaim(damage_tier=ClaimDamageTier.MAJOR_DAMAGE, **_CLAIM_KWARGS),
        coverage=ClaimCoverage(
            covered=True, coverage_type="HO3", deductible=1000, rejection_reason=None
        ),
        assessment=ClaimAssessment(summary=_ASSESSMENT_SUMMARY, estimated_cost=24_500),
    )
    assert _dump(request) == _strip_nulls(_load("claim_decision_request.json"))


def test_claim_decision_report_matches_fixture() -> None:
    report = ClaimAdjudicationReport(
        approved=True,
        approved_payout_amount=23_500,
        adjuster_id="adj-ai-agent",
        notes="Covered peril; repair cost exceeds the deductible.",
        rejection_reason=None,
        rationale=(
            "Coverage is verified and the assessed repair cost exceeds the deductible, "
            "so the payout is approved."
        ),
    )
    assert _dump(report) == _strip_nulls(_load("claim_decision_report.json"))
