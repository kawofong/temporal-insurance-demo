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
[Ollama](https://ollama.com) model. It is standalone and does not integrate with the
Java services.

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

An AI agent that automates the property claim-adjuster role: given a claim and the policy
on file, it adjudicates coverage and produces a coverage determination (covered, coverage
type, deductible, and — when denied — a rejection reason) plus a rationale. It checks that
the policy is active, that the incident falls inside the policy term, and that the claim's
property address matches the insured property. It reuses the same shared runtime and worker,
and is standalone (no integration with the Java services).

## Layout

```
claim_adjuster/
├── models.py          # policy + claim data model, ported from the Java property-policy domain
├── agent_workflow.py  # ClaimAdjusterWorkflow (runs the agent)
└── starter.py         # submits a sample claim + policy, prints the determination
```

## Run

With the shared worker running (`mise run agents:worker`) and Ollama available, submit the
sample claim + policy:

```bash
mise run agents:claim:starter
```

The starter prints a Claim Adjudication Report with the coverage determination and rationale.
