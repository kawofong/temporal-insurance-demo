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

    # Activity slots gate concurrent agent LLM calls (each runs as a model activity), so cap them
    # to Ollama's OLLAMA_NUM_PARALLEL to avoid queueing requests the server can't serve at once.
    # Workflow-task slots are set far higher so many parked agent workflows can make progress while
    # those few activities run — e.g. a batch drain fanning out hundreds of claims at once.
    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=WORKFLOWS,
        max_concurrent_activities=8,
        max_concurrent_workflow_tasks=200,
    )
    print(f"Agents worker started, polling task queue '{TASK_QUEUE}'. Ctrl+C to exit.")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
