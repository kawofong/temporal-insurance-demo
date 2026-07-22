# Shared runtime for the Temporal AI agents.
# Provides the Ollama-backed model provider, plugin configuration, shared task queue,
# and a Temporal client factory reused by the shared worker and each agent starter.
from __future__ import annotations

import os
from datetime import timedelta

from temporalio.client import Client
from temporalio.common import RetryPolicy
from temporalio.contrib.openai_agents import ModelActivityParameters, OpenAIAgentsPlugin

from agents import (
    AsyncOpenAI,
    Model,
    ModelProvider,
    OpenAIChatCompletionsModel,
    set_default_openai_api,
    set_tracing_disabled,
)

# Single task queue shared by every agent workflow hosted on the shared worker.
TASK_QUEUE = "ai-agents-task-queue"

# Temporal connection (overridable for Cloud or alternate namespaces).
TEMPORAL_TARGET = os.environ.get("TEMPORAL_TARGET", "localhost:7233")
TEMPORAL_NAMESPACE = os.environ.get("TEMPORAL_NAMESPACE", "default")

# Ollama's OpenAI-compatible endpoint. The trailing /v1 is required by the client.
OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434/v1")

# Default local Ollama model used for agent reasoning.
MODEL_NAME = os.environ.get("AGENTS_MODEL", "minicpm-v4.6:1b")


class OllamaModelProvider(ModelProvider):
    """Resolves every agent model to the local Ollama server.

    The AsyncOpenAI client is constructed inside get_model, which the plugin invokes
    from within the model activity, keeping the client out of the workflow sandbox.
    """

    def get_model(self, model_name: str | None) -> Model:
        return OpenAIChatCompletionsModel(
            model=model_name or MODEL_NAME,
            openai_client=AsyncOpenAI(
                base_url=OLLAMA_BASE_URL,
                api_key="ollama",  # required by the client, ignored by Ollama
                max_retries=0,
            ),
        )


def configure_openai_for_ollama() -> None:
    """Adjust global OpenAI Agents SDK settings for a local Ollama backend.

    Tracing is disabled because there is no OpenAI key to export traces with, and the
    Chat Completions API is forced because Ollama does not implement the Responses API.
    """
    set_tracing_disabled(True)
    set_default_openai_api("chat_completions")


async def connect() -> Client:
    """Connect a Temporal client wired with the Ollama-backed OpenAI Agents plugin.

    The same plugin configuration must be used by the worker and every starter.
    """
    configure_openai_for_ollama()
    return await Client.connect(
        TEMPORAL_TARGET,
        namespace=TEMPORAL_NAMESPACE,
        plugins=[
            OpenAIAgentsPlugin(
                model_provider=OllamaModelProvider(),
                # A custom model provider requires an explicit timeout; 120s covers the
                # cold-load time of a local Ollama model on first invocation.
                model_params=ModelActivityParameters(
                    start_to_close_timeout=timedelta(seconds=300),
                    retry_policy=RetryPolicy(
                        maximum_attempts=0,
                    ),
                ),
            ),
        ],
    )
