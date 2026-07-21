# Temporal workflow that runs the claim-adjuster agent.
# Orchestrates an OpenAI Agents SDK agent that adjudicates a claim against its policy.
from __future__ import annotations

from temporalio import workflow

with workflow.unsafe.imports_passed_through():
    from agents import Agent, Runner

    from claim_adjuster.models import ClaimAdjudicationReport, ClaimAdjudicationRequest

INSTRUCTIONS = """
You are an experienced property insurance claim adjuster. Given a claim and the policy on
file, determine whether the claim is covered and produce a coverage determination.

Apply these rules:
1. The policy must be ACTIVE. A SUSPENDED, RENEWAL_PENDING, or CANCELLED policy is not covered.
2. The incident date must fall on or between the policy effective date and expiry date
   (all are epoch-millisecond timestamps).
3. The claim's property address must match the policy's insured property address.

If all checks pass, set covered=true and:
- coverage_type by property type: SINGLE_FAMILY -> HO3, CONDO -> HO6, RENTER -> RENTERS
- deductible to 1000
- rejection_reason to null

If any check fails, set covered=false, coverage_type to "N/A", deductible to 0, and put a
clear explanation in rejection_reason. Always explain your reasoning in the rationale field.
""".strip()


def _build_prompt(request: ClaimAdjudicationRequest) -> str:
    """Deterministically render the claim and policy into the agent's input text."""
    claim = request.claim
    policy = request.policy
    prop = policy.property
    tier = claim.damage_tier.value if claim.damage_tier else "N/A (portal-filed)"
    return (
        "POLICY ON FILE\n"
        f"Policy ID: {policy.policy_id}\n"
        f"Policy holder: {policy.policy_holder_id}\n"
        f"Status: {policy.status.value}\n"
        f"Effective date (epoch ms): {policy.effective_date}\n"
        f"Expiry date (epoch ms): {policy.expiry_date}\n"
        f"Insured property address: {prop.address}\n"
        f"Insured property type: {prop.property_type}\n\n"
        "CLAIM TO ADJUDICATE\n"
        f"Claim ID: {claim.claim_id}\n"
        f"Policy ID on claim: {claim.policy_id}\n"
        f"Incident date (epoch ms): {claim.incident_date}\n"
        f"Property address on claim: {claim.property_address}\n"
        f"Property type on claim: {claim.property_type}\n"
        f"Damage tier: {tier}\n"
        f"Incident description: {claim.incident_description}\n"
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
