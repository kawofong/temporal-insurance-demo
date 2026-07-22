# Starter script for the claim-adjuster agent.
# Submits a sample claim + verified coverage + damage assessment to the workflow and prints
# the approve/deny decision.
import asyncio

from agent_runtime import TASK_QUEUE, connect
from claim_adjuster.agent_workflow import ClaimAdjusterWorkflow
from claim_adjuster.models import (
    ClaimAdjudicationRequest,
    CoverageVerificationResult,
    DamageAssessmentResult,
    DamageTier,
    PropertyClaimInput,
)

# A clean "approve" scenario: coverage already verified (HO3, $1000 deductible) and a field
# adjuster's assessment of ~$24,500 in damage — the desk adjuster should approve cost - deductible.
SAMPLE_REQUEST = ClaimAdjudicationRequest(
    claim=PropertyClaimInput(
        claim_id="clm-a1b2c3d4",
        policy_id="pol-100200",
        policy_holder_id="ph-42",
        cat_event_id="cat-hurricane-2026",
        damage_tier=DamageTier.MAJOR_DAMAGE,
        incident_description=(
            "Hurricane-force winds tore off roughly half the roof shingles and shattered two "
            "second-floor windows, with rain intrusion soaking the ceiling and drywall."
        ),
        incident_date=1_760_000_000_000,
        property_address="742 Evergreen Terrace, Springfield",
        property_type="SINGLE_FAMILY",
    ),
    coverage=CoverageVerificationResult(
        covered=True,
        coverage_type="HO3",
        deductible=1000,
        rejection_reason=None,
    ),
    assessment=DamageAssessmentResult(
        summary=(
            "Roughly half the roof shingles torn off, two second-floor windows shattered, and "
            "water intrusion damage to the ceiling and drywall in the primary bedroom and hallway."
        ),
        estimated_cost=24_500,
    ),
)


async def main() -> None:
    client = await connect()

    report = await client.execute_workflow(
        ClaimAdjusterWorkflow.run,
        SAMPLE_REQUEST,
        id=f"claim-adjuster-{SAMPLE_REQUEST.claim.claim_id}",
        task_queue=TASK_QUEUE,
    )

    print("\n=== Claim Adjudication Report ===")
    print(f"Claim:            {SAMPLE_REQUEST.claim.claim_id}")
    print("\n-- Decision --")
    print(f"Approved:         {report.approved}")
    print(f"Approved payout:  ${report.approved_payout_amount}")
    print(f"Adjuster:         {report.adjuster_id}")
    print(f"Notes:            {report.notes}")
    print(f"Rejection reason: {report.rejection_reason}")
    print(f"\nRationale:        {report.rationale}")


if __name__ == "__main__":
    asyncio.run(main())
