# Shared worker for the Temporal AI agents.
# Registers every agent workflow and runs them against the local dev server.
import asyncio

from temporalio.worker import Worker

from agent_runtime import TASK_QUEUE, connect
from claim_adjuster.agent_workflow import ClaimAdjusterWorkflow
from field_adjuster.agent_workflow import FieldAdjusterWorkflow

# Agent workflows hosted by this worker. Add new agent workflows here as they are built.
WORKFLOWS = [FieldAdjusterWorkflow, ClaimAdjusterWorkflow]


async def main() -> None:
    client = await connect()

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=WORKFLOWS,
    )
    print(f"Agents worker started, polling task queue '{TASK_QUEUE}'. Ctrl+C to exit.")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
