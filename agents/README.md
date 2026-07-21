# Temporal Python Agents

Python AI agents for the insurance demo, managed with
[`uv`](https://docs.astral.sh/uv/).

# Field Adjuster Agent

An AI agent that automates the property field-adjuster role: given a claim and its
verified coverage, it produces a damage assessment (summary + estimated repair cost)
and an approval decision (approved payout + notes). It uses the OpenAI Agents SDK,
orchestrated durably by Temporal's `OpenAIAgentsPlugin`, running against a local
[Ollama](https://ollama.com) model. It is standalone and does not integrate with the
Java services.

## Layout

```
field_adjuster/
├── models.py          # claim data model, ported from the Java property-claim domain
├── config.py          # Ollama model provider + task queue
├── agent_workflow.py  # FieldAdjusterWorkflow (runs the agent)
├── worker.py          # worker with the OpenAI Agents plugin
└── starter.py         # submits a sample claim, prints the report
```

## Prerequisites

- Ollama running with the model pulled:

  ```bash
  ollama serve                      # if not already running
  ollama pull minicpm-v4.6:1b
  ollama list                       # confirm the model is present
  ```

- A running Temporal server: `mise run temporal:dev`.
- Dependencies installed: `mise run agents:install` (or `cd agents && uv sync`).

The Ollama endpoint (`OLLAMA_BASE_URL`, default `http://localhost:11434/v1`) and model
(`FIELD_ADJUSTER_MODEL`, default `minicpm-v4.6:1b`) can be overridden via env vars.

## Run

In one terminal, start the worker:

```bash
mise run agents:adjuster:worker
```

In another terminal, submit the sample claim:

```bash
mise run agents:adjuster:starter
```

The starter prints a Field Adjuster Report with the damage assessment and approval
decision. Inspect the execution in the Temporal UI (`http://localhost:8233`) to see the
model call run as an activity.
