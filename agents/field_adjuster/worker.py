# Worker process for the field-adjuster agent.
# Runs the agent workflow with the OpenAI Agents plugin pointed at local Ollama.
import asyncio
from datetime import timedelta

from temporalio.client import Client
from temporalio.contrib.openai_agents import ModelActivityParameters, OpenAIAgentsPlugin
from temporalio.worker import Worker

from field_adjuster.agent_workflow import FieldAdjusterWorkflow
from field_adjuster.config import (
    TASK_QUEUE,
    OllamaModelProvider,
    configure_openai_for_ollama,
)


async def main() -> None:
    configure_openai_for_ollama()

    client = await Client.connect(
        "localhost:7233",
        namespace="default",
        plugins=[
            OpenAIAgentsPlugin(
                model_provider=OllamaModelProvider(),
                # A custom model provider requires an explicit timeout; 120s covers the
                # cold-load time of a local Ollama model on first invocation.
                model_params=ModelActivityParameters(
                    start_to_close_timeout=timedelta(seconds=120)
                ),
            ),
        ],
    )

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=[FieldAdjusterWorkflow],
    )
    print(f"Field adjuster worker started, polling task queue '{TASK_QUEUE}'. Ctrl+C to exit.")
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
