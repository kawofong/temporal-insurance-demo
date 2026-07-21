"""Unit tests for the repurposed claim-adjuster agent (spec §6.4).

The approve/deny decision itself is made by the LLM, so it is exercised end-to-end by the mise
demo tasks. Here we cover the deterministic, non-LLM pieces: the request model shape (no policy
state), the report model's defaults, and the prompt builder surfacing the facts the agent needs.
"""

from __future__ import annotations

from claim_adjuster.agent_workflow import _build_prompt
from claim_adjuster.models import (
    ClaimAdjudicationReport,
    ClaimAdjudicationRequest,
    CoverageVerificationResult,
    DamageAssessmentResult,
    DamageTier,
    PropertyClaimInput,
)


def _request() -> ClaimAdjudicationRequest:
    return ClaimAdjudicationRequest(
        claim=PropertyClaimInput(
            claim_id="clm-xyz",
            policy_id="pol-1",
            policy_holder_id="ph-1",
            cat_event_id="cat-2026",
            damage_tier=DamageTier.MAJOR_DAMAGE,
            incident_description="Wind and water damage to the roof.",
            incident_date=1_760_000_000_000,
            property_address="1 Main St",
            property_type="SINGLE_FAMILY",
        ),
        coverage=CoverageVerificationResult(
            covered=True, coverage_type="HO3", deductible=1000
        ),
        assessment=DamageAssessmentResult(summary="Roof damage.", estimated_cost=24_500),
    )


def test_request_carries_claim_coverage_assessment_and_no_policy() -> None:
    request = _request()
    assert request.claim.claim_id == "clm-xyz"
    assert request.coverage.covered is True
    assert request.assessment.estimated_cost == 24_500
    # The repurposed request must not carry policy state (feedback #4 / §6.4).
    assert "policy" not in ClaimAdjudicationRequest.model_fields


def test_report_defaults() -> None:
    report = ClaimAdjudicationReport(
        approved=True,
        approved_payout_amount=23_500,
        notes="Approved.",
        rationale="Covered and above deductible.",
    )
    assert report.adjuster_id == "adj-ai-agent"
    assert report.rejection_reason is None


def test_prompt_includes_coverage_and_assessment_facts() -> None:
    prompt = _build_prompt(_request())
    assert "HO3" in prompt
    assert "$1000" in prompt  # deductible
    assert "$24500" in prompt  # estimated repair cost
    assert "MAJOR_DAMAGE" in prompt
    assert "Roof damage." in prompt


def test_prompt_handles_portal_claim_without_tier() -> None:
    request = _request()
    request.claim.damage_tier = None
    request.claim.cat_event_id = None
    prompt = _build_prompt(request)
    assert "N/A (portal-filed)" in prompt
