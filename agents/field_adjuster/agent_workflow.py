# Temporal workflow that runs the field-adjuster agent.
# Orchestrates a single OpenAI Agents SDK agent that assesses damage and decides a payout.
from __future__ import annotations

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from agents import Agent, Runner

    from field_adjuster.models import FieldAdjusterReport, FieldAdjusterRequest

INSTRUCTIONS = """
You are an experienced property insurance field adjuster. Given a property claim and its
verified coverage, produce two things:

1. A damage assessment: a concise plain-language summary of the damage and an estimated
   repair cost as a whole-dollar integer, informed by the incident description and, when
   present, the catastrophe damage tier (TOTAL_LOSS > MAJOR_DAMAGE > MINOR_DAMAGE).
2. An approval decision: set approved_payout_amount to the estimated repair cost minus the
   policy deductible, never below zero. Record a short justification in notes. Use the
   assigned adjuster id "adj-ai-agent".

Be realistic and conservative. Return only whole-dollar integer amounts.
""".strip()


def _build_prompt(request: FieldAdjusterRequest) -> str:
    """Deterministically render the claim and coverage into the agent's input text."""
    claim = request.claim
    coverage = request.coverage
    tier = claim.damage_tier.value if claim.damage_tier else "N/A (portal-filed)"
    return (
        f"Claim ID: {claim.claim_id}\n"
        f"Policy ID: {claim.policy_id}\n"
        f"Property type: {claim.property_type}\n"
        f"Property address: {claim.property_address}\n"
        f"Damage tier: {tier}\n"
        f"Incident description: {claim.incident_description}\n"
        f"Coverage type: {coverage.coverage_type}\n"
        f"Deductible: ${coverage.deductible}\n"
    )


@workflow.defn
class FieldAdjusterWorkflow:
    @workflow.run
    async def run(self, request: FieldAdjusterRequest) -> FieldAdjusterReport:
        agent = Agent(
            name="Field Adjuster",
            instructions=INSTRUCTIONS,
            output_type=FieldAdjusterReport,
        )
        result = await Runner.run(agent, input=_build_prompt(request))
        return result.final_output
