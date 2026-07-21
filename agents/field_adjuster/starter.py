# Starter script for the field-adjuster agent.
# Submits a sample property claim to the workflow and prints the adjuster's report.
import asyncio
from datetime import timedelta

from temporalio.client import Client
from temporalio.contrib.openai_agents import ModelActivityParameters, OpenAIAgentsPlugin

from field_adjuster.agent_workflow import FieldAdjusterWorkflow
from field_adjuster.config import (
    TASK_QUEUE,
    OllamaModelProvider,
    configure_openai_for_ollama,
)
from field_adjuster.models import (
    CoverageVerificationResult,
    DamageTier,
    FieldAdjusterRequest,
    PropertyClaimInput,
)

# A representative hurricane-damage claim with coverage already verified (HO3, $1000 deductible),
# matching the demo defaults in the Java PropertyClaimActivitiesImpl.
SAMPLE_REQUEST = FieldAdjusterRequest(
    claim=PropertyClaimInput(
        claim_id="clm-a1b2c3d4",
        policy_id="pol-100200",
        policy_holder_id="ph-42",
        cat_event_id="cat-hurricane-2026",
        damage_tier=DamageTier.MAJOR_DAMAGE,
        incident_description=(
            "Hurricane-force winds tore off roughly half the roof shingles and shattered two "
            "second-floor windows. Rain intrusion soaked the ceiling and drywall in the primary "
            "bedroom and hallway. No structural framing damage observed."
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
)


async def main() -> None:
    configure_openai_for_ollama()

    client = await Client.connect(
        "localhost:7233",
        namespace="default",
        plugins=[
            OpenAIAgentsPlugin(
                model_provider=OllamaModelProvider(),
                model_params=ModelActivityParameters(
                    start_to_close_timeout=timedelta(seconds=120)
                ),
            ),
        ],
    )

    report = await client.execute_workflow(
        FieldAdjusterWorkflow.run,
        SAMPLE_REQUEST,
        id=f"field-adjuster-{SAMPLE_REQUEST.claim.claim_id}",
        task_queue=TASK_QUEUE,
    )

    print("\n=== Field Adjuster Report ===")
    print(f"Claim:            {SAMPLE_REQUEST.claim.claim_id}")
    print("\n-- Damage Assessment --")
    print(f"Summary:          {report.assessment.summary}")
    print(f"Estimated cost:   ${report.assessment.estimated_cost}")
    print("\n-- Approval Decision --")
    print(f"Adjuster:         {report.approval.adjuster_id}")
    print(f"Approved payout:  ${report.approval.approved_payout_amount}")
    print(f"Notes:            {report.approval.notes}")


if __name__ == "__main__":
    asyncio.run(main())
