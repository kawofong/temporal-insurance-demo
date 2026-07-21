# Temporal Python Agents

Python AI agents for the insurance demo, managed with
[`uv`](https://docs.astral.sh/uv/).

A single shared worker hosts every agent workflow. Shared runtime lives at the top level;
each agent is its own package.

```
agents/
├── agent_runtime.py   # shared Ollama model provider, plugin config, client factory, task queue
├── worker.py          # shared worker — registers every agent workflow (see WORKFLOWS)
├── field_adjuster/    # the field-adjuster agent (see below)
└── claim_adjuster/    # the claim-adjuster agent (see below)
```

To add a new agent: create its package with an `agent_workflow.py` and a `starter.py`, then
add its workflow to the `WORKFLOWS` list in `worker.py`.

# Field Adjuster Agent

An AI agent that automates the property field-adjuster role: given a claim and its
verified coverage, it produces a damage assessment (summary + estimated repair cost)
and an approval decision (approved payout + notes). It uses the OpenAI Agents SDK,
orchestrated durably by Temporal's `OpenAIAgentsPlugin`, running against a local
[Ollama](https://ollama.com) model. It can run standalone via its starter, and is also
invoked by the Java `PropertyClaimWorkflow` on the AI path (see "Integration" below).

## Layout

```
field_adjuster/
├── models.py          # claim data model, ported from the Java property-claim domain
├── agent_workflow.py  # FieldAdjusterWorkflow (runs the agent)
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
(`AGENTS_MODEL`, default `minicpm-v4.6:1b`) can be overridden via env vars.

## Run

In one terminal, start the shared agents worker:

```bash
mise run agents:worker
```

In another terminal, submit the sample claim:

```bash
mise run agents:adjuster:starter
```

The starter prints a Field Adjuster Report with the damage assessment and approval
decision. Inspect the execution in the Temporal UI (`http://localhost:8233`) to see the
model call run as an activity.

# Claim Adjuster Agent

An AI agent that automates the property claim (desk) adjuster role: given a claim, its already
-verified coverage, and the field adjuster's damage assessment, it makes the binding approve/deny
decision. On approval it pays the estimated repair cost minus the deductible (never below zero)
with a justification; on denial it records a rejection reason. It emits `approved`,
`approved_payout_amount`, `adjuster_id` (`adj-ai-agent`), `notes`, `rejection_reason`, and a
`rationale`. It needs no policy state — coverage was verified upstream. It reuses the same shared
runtime and worker.

## Layout

```
claim_adjuster/
├── models.py          # {claim, coverage, assessment} request + approve/deny report
├── agent_workflow.py  # ClaimAdjusterWorkflow (runs the agent)
└── starter.py         # submits a sample claim + coverage + assessment, prints the decision
```

## Run

With the shared worker running (`mise run agents:worker`) and Ollama available, submit the
sample claim + coverage + assessment:

```bash
mise run agents:claim:starter
```

The starter prints a Claim Adjudication Report with the approve/deny decision and rationale.

# Integration with the property claim workflow

Both agents are also invoked by the Java `PropertyClaimWorkflow` as untyped child workflows when
a claim is switched to AI adjustment (the `enableAiAdjuster` signal, or the `aiAdjusterEnabled`
intake flag). The field-adjuster agent fills the damage-assessment seam and the claim-adjuster
agent fills the approve/deny seam; everything they need is passed as the child-workflow argument,
so no agent fetches state. The Java↔Python wire format is the `snake_case` JSON these Pydantic
models define, mirrored by the Java records in
`domain/.../claim/property/agents/` and guarded by a serialization contract test on each side. See
`docs/ai-claim-adjustment-spec.md` for the full design, and the `demo:ai-adjuster` mise task
for a runnable end-to-end scenario (needs this worker running against Ollama).

# Tests

Run the Python unit tests (models, prompt builders, and the cross-language serialization contract):

```bash
mise run agents:test
```
