# Temporal workflow that runs the claim-adjuster agent.
# Orchestrates an OpenAI Agents SDK agent that makes the binding approve/deny decision for a
# claim whose coverage has already been verified and whose damage has already been assessed.
from __future__ import annotations

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from agents import Agent, Runner

    from claim_adjuster.models import ClaimAdjudicationReport, ClaimAdjudicationRequest

INSTRUCTIONS = """
You are an experienced property insurance claim (desk) adjuster. Coverage has already been
verified and a field adjuster has already assessed the damage. Given the claim, the verified
coverage, and the damage assessment, make the binding decision to approve or deny the payout.

Apply these rules:
1. If coverage is verified (covered=true), approve the claim. Set approved=true and
   approved_payout_amount to the estimated repair cost minus the coverage deductible, never
   below zero. Put a short justification in notes and set rejection_reason to null.
2. If coverage is not verified (covered=false), or the assessment shows the loss is not a
   payable claim, deny it. Set approved=false, approved_payout_amount to 0, and put a clear
   explanation in rejection_reason.

Always use the adjuster id "adj-ai-agent" and explain your reasoning in the rationale field.
Return only whole-dollar integer amounts.
""".strip()


def _build_prompt(request: ClaimAdjudicationRequest) -> str:
    """Deterministically render the claim, coverage, and assessment into the agent input."""
    claim = request.claim
    coverage = request.coverage
    assessment = request.assessment
    tier = claim.damage_tier.value if claim.damage_tier else "N/A (portal-filed)"
    return (
        "CLAIM\n"
        f"Claim ID: {claim.claim_id}\n"
        f"Policy ID: {claim.policy_id}\n"
        f"Property type: {claim.property_type}\n"
        f"Property address: {claim.property_address}\n"
        f"Damage tier: {tier}\n"
        f"Incident description: {claim.incident_description}\n\n"
        "VERIFIED COVERAGE\n"
        f"Covered: {coverage.covered}\n"
        f"Coverage type: {coverage.coverage_type}\n"
        f"Deductible: ${coverage.deductible}\n"
        f"Rejection reason: {coverage.rejection_reason}\n\n"
        "DAMAGE ASSESSMENT\n"
        f"Summary: {assessment.summary}\n"
        f"Estimated repair cost: ${assessment.estimated_cost}\n"
    )


@workflow.defn
class ClaimAdjusterWorkflow:
    @workflow.run
    async def run(self, request: ClaimAdjudicationRequest) -> ClaimAdjudicationReport:
        agent = Agent(
            name="Claim Adjuster",
            instructions=INSTRUCTIONS,
            output_type=ClaimAdjudicationReport,
        )
        result = await Runner.run(agent, input=_build_prompt(request))
        return result.final_output
