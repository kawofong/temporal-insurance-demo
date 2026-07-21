# Starter script for the claim-adjuster agent.
# Submits a sample claim + policy to the workflow and prints the coverage determination.
import asyncio

from agent_runtime import TASK_QUEUE, connect
from claim_adjuster.agent_workflow import ClaimAdjusterWorkflow
from claim_adjuster.models import (
    ClaimAdjudicationRequest,
    DamageTier,
    InsuredProperty,
    PolicyStatus,
    PropertyClaimInput,
    PropertyPolicyState,
)

# A clean "covered" scenario: an ACTIVE single-family policy whose insured address matches
# the claim, with the incident falling inside the policy term.
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
        incident_date=1_760_000_000_000,  # within the policy term below
        property_address="742 Evergreen Terrace, Springfield",
        property_type="SINGLE_FAMILY",
    ),
    policy=PropertyPolicyState(
        policy_id="pol-100200",
        policy_holder_id="ph-42",
        status=PolicyStatus.ACTIVE,
        effective_date=1_735_689_600_000,  # 2025-01-01
        expiry_date=1_767_225_600_000,  # 2026-01-01 (after the incident)
        property=InsuredProperty(
            property_id="prop-77",
            address="742 Evergreen Terrace, Springfield",
            property_type="SINGLE_FAMILY",
        ),
        loss_payees=[],
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
    print(f"Policy:           {SAMPLE_REQUEST.policy.policy_id}")
    print("\n-- Coverage Determination --")
    print(f"Covered:          {report.coverage.covered}")
    print(f"Coverage type:    {report.coverage.coverage_type}")
    print(f"Deductible:       ${report.coverage.deductible}")
    print(f"Rejection reason: {report.coverage.rejection_reason}")
    print(f"\nRationale:        {report.rationale}")


if __name__ == "__main__":
    asyncio.run(main())
