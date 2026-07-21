# Ollama and task-queue configuration for the field-adjuster agent.
# Routes the OpenAI Agents SDK at a local Ollama server via a custom model provider.
from __future__ import annotations

import os

from agents import (
    AsyncOpenAI,
    Model,
    ModelProvider,
    OpenAIChatCompletionsModel,
    set_default_openai_api,
    set_tracing_disabled,
)

TASK_QUEUE = "field-adjuster-task-queue"

# Ollama's OpenAI-compatible endpoint. The trailing /v1 is required by the client.
OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434/v1")

# Local Ollama model that performs the adjuster's reasoning.
MODEL_NAME = os.environ.get("FIELD_ADJUSTER_MODEL", "minicpm-v4.6:1b")


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
            ),
        )


def configure_openai_for_ollama() -> None:
    """Adjust global OpenAI Agents SDK settings for a local Ollama backend.

    Tracing is disabled because there is no OpenAI key to export traces with, and the
    Chat Completions API is forced because Ollama does not implement the Responses API.
    """
    set_tracing_disabled(True)
    set_default_openai_api("chat_completions")
