# Spec: AI-Assisted Field & Claim Adjuster Support for Property Claims

Status: Draft · Owner: Ka Wo · Last updated: 2026-07-21

## 1. Summary

Today a property claim durably waits for a **human** adjuster to assess damage and then a
**human** to approve/deny the payout. This spec adds an opt-in path where, when signalled,
the `PropertyClaimWorkflow` routes those two decisions to the existing OpenAI-Agents-SDK
adjuster workflows instead of blocking on a human signal:

- **Field adjuster agent** (`field_adjuster/agent_workflow.py`) → the **damage assessment**
  wait (`PENDING_DAMAGE_ASSESSMENT`).
- **Claim adjuster agent** (`claim_adjuster/agent_workflow.py`) → the **approval / denial**
  wait (`PENDING_APPROVAL`).

Coverage verification is unchanged — it never involved a human and stays a plain activity.
The behaviour is controlled by a single `enableAiAdjuster` signal that can arrive **at any
point while the claim is open and running**, so the same workflow orchestrates a human path
and an AI path with an identical audit trail. This is the code behind **Act 4 — "AI
Takeover"** in `docs/presentations/demo-guide.md`.

## 2. Goals / Non-goals

**Goals**
- Default behaviour is unchanged: claims wait for human adjusters.
- A running claim can be switched to AI adjustment **at any point** via a signal —
  including while it is durably parked at `PENDING_DAMAGE_ASSESSMENT` or `PENDING_APPROVAL`.
- The Java claim workflow passes the agents **everything they need through workflow input**;
  no agent fetches state via an activity.
- The AI decision is stamped into the same claim-state fields a human would write.
- Runnable end-to-end from `mise` tasks (human demo and AI demos).

**Non-goals**
- **Workflow versioning / patching is out of scope** for this spec.
- Coverage verification is **not** changed.
- No confidence-threshold auto-escalation from AI back to a human (demo beat 4.3;
  follow-up in §11).
- No change to the payment/notification Nexus flows.
- No new AI model wiring — the agents keep the existing Ollama-backed `OpenAIAgentsPlugin`
  from `agents/agent_runtime.py`.

## 3. Background — current property claim flow

Source of truth: `domain/.../claim/property/PropertyClaimWorkflowImpl.java`
(task queue `claim-task-queue`).

```
SUBMITTED
  └─ activities.verifyCoverage(policyId, propertyAddress)        ← coverage decision (activity; NO human, UNCHANGED)
        ├─ not covered → REJECTED (terminal)
        └─ covered → COVERAGE_VERIFIED
  └─ activities.assignAdjuster(claimId) + dispatchFieldAdjuster(...)
PENDING_DAMAGE_ASSESSMENT
  └─ Workflow.await(() -> damageAssessed)                        ← HUMAN signal: submitDamageAssessment
PENDING_APPROVAL
  └─ Workflow.await(() -> adjusterApproved || adjusterDenied)    ← HUMAN signal: adjusterApproval / adjusterDenial
        ├─ denied → REJECTED (terminal)
        └─ approved → PAYMENT_PROCESSING → (Nexus payment) → CLOSED
```

Existing signals (interface `PropertyClaimWorkflow.java`):
`submitDamageAssessment(DamageAssessmentResult)`, `adjusterApproval(AdjusterApprovalRequest)`,
`adjusterDenial(AdjusterDenialRequest)`; query `getClaim()`.

Human signals are sent today via REST → `api/.../PropertyClaimService.java`.

## 4. The AI agents

Both are Temporal **workflows** on task queue `ai-agents-task-queue`
(registered in `agents/worker.py`).

| Agent | Workflow type | Role in this design | Change needed |
|---|---|---|---|
| Field adjuster | `FieldAdjusterWorkflow` | Damage assessment (`PENDING_DAMAGE_ASSESSMENT`) | **None** |
| Claim adjuster | `ClaimAdjusterWorkflow` | Approve/deny decision (`PENDING_APPROVAL`) | **Updated** (§6.4) |

This mirrors the real insurance division of labour: the **field adjuster** inspects and
estimates the damage; the **claim (desk) adjuster** reviews that assessment against coverage
and makes the binding payout decision.

- **Field adjuster** input `FieldAdjusterRequest { claim, coverage }`, output
  `FieldAdjusterReport { assessment, approval }`. The workflow consumes `assessment`;
  `approval` is the field adjuster's *recommendation* and does not close the claim — the
  claim adjuster makes the binding decision. Both inputs are already held by the workflow
  (claim state + the coverage result from the unchanged activity), so nothing is fetched.
- **Claim adjuster** must decide approve-or-deny from the claim, the verified coverage, and
  the damage assessment — all already on the workflow. It currently takes a full
  `PropertyPolicyState` and emits a coverage determination; that is repurposed in §6.4 so it
  needs **no policy state** and emits an approval/denial decision.

## 5. User journey (target)

1. Policyholder opens a claim (unchanged intake).
2. Claim advances to `PENDING_DAMAGE_ASSESSMENT`.
3. **Default:** the workflow waits for a human field adjuster's `submitDamageAssessment`
   signal, then a human claim adjuster's approve/deny.
4. **At any time while the claim is open and running**, the workflow may receive
   `enableAiAdjuster`.
5. When AI is enabled, the workflow routes the still-pending decisions to the field-adjuster
   and/or claim-adjuster agents instead of waiting on a human, and closes the claim.

## 6. Design

### 6.1 Enablement surface

Add one signal to `PropertyClaimWorkflow`:

```java
@SignalMethod
void enableAiAdjuster();   // one-way enable; drives both agents; safe to send anytime
```

- Backing field `private boolean aiAdjusterEnabled;`, initialised `false`.
- The handler just sets the flag `true`. It is idempotent and safe to send before or during
  either wait — satisfying "AI may be enabled when a claim has opened and is running".
- Optional convenience: `PropertyClaimInput` may carry `boolean aiAdjusterEnabled` (default
  `false`) so a claim can be opened already in AI mode for the fully-autonomous demo. The
  signal remains the primary mechanism.
- Add `aiAdjusterEnabled` to `PropertyClaimState` so dashboards/tests can observe the mode
  (the demo's "AI-processed vs human-processed" indicators).

There is no "disable" — enablement is one-way. A decision already taken by an agent is not
un-done (documented default, §10).

### 6.2 Routing logic (the two human-in-the-loop seams)

Both `Workflow.await` conditions gain an `|| aiAdjusterEnabled` term so a claim parked on a
human wait breaks out the instant the signal arrives. No versioning wrappers (§2).

**Seam A — damage assessment (field adjuster):**

```java
updateStatus(ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
Workflow.await(() -> damageAssessed || aiAdjusterEnabled);

if (!damageAssessed && aiAdjusterEnabled) {
    FieldAdjusterReport report = fieldAdjuster.execute(         // untyped child workflow, §6.3
        FieldAdjusterReport.class,
        new AgentFieldAdjusterRequest(toAgentClaim(state), toAgentCoverage(coverage)));
    applyDamageAssessment(report.assessment());   // sets damageAssessment + estimatedRepairCost
    damageAssessed = true;
}
```

**Seam B — approval / denial (claim adjuster):**

```java
updateStatus(ClaimStatus.PENDING_APPROVAL);
Workflow.await(() -> adjusterApproved || adjusterDenied || aiAdjusterEnabled);

if (!adjusterApproved && !adjusterDenied && aiAdjusterEnabled) {
    ClaimDecisionReport decision = claimAdjuster.execute(       // untyped child workflow, §6.3
        ClaimDecisionReport.class,
        new AgentClaimDecisionRequest(
            toAgentClaim(state), toAgentCoverage(coverage), report.assessment()));
    if (decision.approved()) {
        applyApproval(decision.toApprovalRequest());   // approvedByAdjusterId="adj-ai-agent", payout, approvedAt
        adjusterApproved = true;
    } else {
        applyDenial(decision.toDenialRequest());       // rejectionReason
        adjusterDenied = true;
    }
}
```

Notes:
- The existing signal handlers and the AI path both funnel through the same private
  state-mutation helpers (`applyDamageAssessment` / `applyApproval` / `applyDenial`), so the
  audit trail and downstream code are identical for human and AI.
- Mixed mode works: e.g. a human submits the assessment, then AI is enabled and the claim
  adjuster agent makes the approval decision using that human assessment.
- If AI is enabled at Seam A, the flag is still `true` at Seam B, so the claim flows straight
  through both agents to `PAYMENT_PROCESSING`.

### 6.3 Cross-language invocation contract

The Python agents are workflows on a **different task queue and a different SDK**, so the
Java claim workflow invokes them as **untyped child workflows**:

```java
var opts = ChildWorkflowOptions.newBuilder()
    .setTaskQueue("ai-agents-task-queue")
    .setWorkflowId("field-adjuster/" + state.getClaimId())   // deterministic, greppable
    .setWorkflowExecutionTimeout(Duration.ofMinutes(5))       // LLM latency headroom
    .build();
var fieldAdjuster = Workflow.newUntypedChildWorkflowStub("FieldAdjusterWorkflow", opts);
```

Child workflows (not activities) are correct: the agents need their own durable execution +
plugin-managed activities, and they render as children in the CAT tree — good for the
Temporal-UI walkthrough in the demo.

**Everything the agents need is passed as the child-workflow argument** (per feedback #4) —
no activity fetches policy or any other state.

**Serialization contract (decision):** keep the wire format `snake_case` (the Pydantic
convention the agents already use) and introduce small Java-side mirror records in a new
package `domain/.../claim/property/agents/`, annotated so Jackson emits/consumes the exact
`snake_case` names:

```java
record AgentPropertyClaim(
    @JsonProperty("claim_id") String claimId,
    @JsonProperty("policy_id") String policyId,
    @JsonProperty("policy_holder_id") String policyHolderId,
    @JsonProperty("cat_event_id") String catEventId,
    @JsonProperty("damage_tier") String damageTier,
    @JsonProperty("incident_description") String incidentDescription,
    @JsonProperty("incident_date") long incidentDate,
    @JsonProperty("property_address") String propertyAddress,
    @JsonProperty("property_type") String propertyType) {}
// + AgentCoverage, AgentDamageAssessment,
//   AgentFieldAdjusterRequest, FieldAdjusterReport,
//   AgentClaimDecisionRequest, ClaimDecisionReport
```

A unit test asserts the JSON of each Java request matches a fixture captured from the
corresponding Python model, so cross-language drift is caught (§9). `toAgentClaim` /
`toAgentCoverage` convert workflow state into these records.

### 6.4 Claim-adjuster agent update

The claim-adjuster agent is repurposed from *coverage adjudication* (which needed policy
state) to the *approval/denial decision* (which needs only what the workflow already holds).
Files: `agents/claim_adjuster/models.py`, `agents/claim_adjuster/agent_workflow.py`.

- **Input** `ClaimAdjudicationRequest` → `{ claim, coverage, assessment }`
  (drop `policy: PropertyPolicyState` entirely → no policy state required, satisfying #4).
- **Output** `ClaimAdjudicationReport` → a decision, e.g.:
  ```python
  class ClaimAdjudicationReport(BaseModel):
      approved: bool
      approved_payout_amount: int      # estimated_cost - deductible, >= 0; 0 when denied
      adjuster_id: str = "adj-ai-agent"
      notes: str                       # justification when approved
      rejection_reason: str | None     # populated when approved is False
      rationale: str
  ```
- **Instructions** change from "determine coverage against the policy" to "given the verified
  coverage and the field adjuster's damage assessment, decide whether to approve the payout
  (repair cost minus deductible, never below zero) or deny, with a short justification."
- The models it no longer needs (`PolicyStatus`, `InsuredProperty`, `LossPayee`,
  `PropertyPolicyState`) are removed from `claim_adjuster/models.py`.
- Its `starter.py` and the `agents:claim:starter` mise task are updated to build the new
  `{claim, coverage, assessment}` request.

The **field adjuster agent is unchanged.**

### 6.5 Batch enablement — Temporal Batch Signal

To flip many in-flight claims to AI at once (Act 4 "one toggle drains the queue"), use a
**Temporal batch signal operation** driven by a Visibility query — not a client-side
list-and-loop. See the Batch Operations primer:
<https://keithtenzer.com/temporal/Temporal_Batch_Operations_Primer/#signal-batch-operation>.

The batch targets every matching execution and sends `enableAiAdjuster`, e.g. via CLI:

```bash
temporal workflow signal \
  --query 'WorkflowType="PropertyClaimWorkflow" AND ExecutionStatus="Running"' \
  --name enableAiAdjuster \
  --reason "AI takeover"
```

or programmatically from the API via `StartBatchOperation` with a signal operation over the
same query. The API trigger takes no input: it always targets **all running** property claims
(`WorkflowType="PropertyClaimWorkflow" AND ExecutionStatus="Running"`). The signal is idempotent
and only takes effect on claims parked on a human adjuster seam, so signalling every running
claim is safe. This scales to the 10k-claim case noted in `docs/todos.md`. Expose a thin API
trigger: `POST /api/v1/claims/property/ai-adjuster:enable-batch`.

## 7. Data-model & API changes

**Domain (`domain/`)**
- `PropertyClaimWorkflow`: add `enableAiAdjuster()` signal.
- `PropertyClaimInput`: add optional `boolean aiAdjusterEnabled` (default false).
- `PropertyClaimState`: add `aiAdjusterEnabled` (observability).
- New package `claim/property/agents/`: Java mirror records + `toAgentClaim` /
  `toAgentCoverage` mappers.
- `verifyCoverage`, `assignAdjuster`, `dispatchFieldAdjuster` activities: **unchanged.**

**Agents (`agents/`)**
- `claim_adjuster/`: updated request/report models, instructions, prompt builder, starter
  (§6.4).
- `field_adjuster/`: **unchanged.**

**API (`api/`)**
- `PropertyClaimService`: `enableAiAdjuster(claimId)` (single) and `enableAiAdjusterBatch()`
  (no-input batch signal over all running claims, §6.5); `submitPropertyClaim` accepts the
  optional `aiAdjusterEnabled` flag.
- `PropertyClaimController`: `POST /claims/property/{id}/ai-adjuster` and the batch route.

**Workers (`workers/`)** — no change; `claim-task-queue` already hosts the workflow. The
Python `agents:worker` must be running for the AI path (documented prerequisite).

## 8. Demo entry points (`mise` tasks)

One task in `mise.toml`. Prerequisites (separate terminals): `temporal:dev`,
`temporal:worker` (Java), `api`, and `agents:worker` (needs Ollama). The scenario script lives
in `scripts/` and drives the REST API with `curl` + status polling.

| Task | Scenario |
|---|---|
| `demo:ai-adjuster` | Seed pending claims out of band, then **batch signal** `enableAiAdjuster` over the Visibility query of all running property claims (§6.5) → watch the claims parked on a human adjuster drain to `CLOSED`. |

```toml
[tasks."demo:ai-adjuster"]
description = "E2E: batch-signal enableAiAdjuster across all running property claims"
run = "./scripts/demo-adjuster.sh"
```

## 9. Testing (TDD)

Written test-first. Real Temporal test env + real agents (no mocks). Respect the known
constraints in memory: register custom search attributes in every test env; the test server
can't run Visibility `ListWorkflowExecutions` (so batch/visibility paths are unit-tested at
the query-string level and verified E2E on a real dev server); don't query Terminated
workflows.

**Unit**
- Serialization contract: JSON of each Java mirror record equals a fixture captured from the
  corresponding (updated) Pydantic model — guards cross-language drift (§6.3).
- `toAgentClaim` / `toAgentCoverage` mapping (incl. null `catEventId` / null `damageTier`).
- Batch signal Visibility query string builder.
- (Python) updated claim-adjuster: given coverage + assessment, approves with
  `payout = cost - deductible` clamped at 0; denies with a reason when appropriate.

**Integration (`TestWorkflowEnvironment`)**
- Default path still waits for and honours the human signals (regression).
- `enableAiAdjuster` sent while parked at `PENDING_DAMAGE_ASSESSMENT` → workflow invokes the
  `FieldAdjusterWorkflow` then `ClaimAdjusterWorkflow` children and closes; state carries
  `adj-ai-agent` and the agent's payout. Use stub child workflows registered on
  `ai-agents-task-queue` returning canned reports (deterministic Temporal children, not LLM
  mocks).
- `enableAiAdjuster` sent while parked at `PENDING_APPROVAL` after a **human** assessment →
  only the claim-adjuster agent runs; the human assessment is preserved.
- Claim-adjuster "deny" report → claim closes `REJECTED` with the rejection reason.
- Claim opened with `aiAdjusterEnabled=true` → both agents run without any human signal.

**End-to-end**
- The `demo:ai-adjuster` task runs green against a real dev server with the Java worker,
  API, and the real Python `agents:worker` (Ollama). This is the acceptance gate.

## 10. Decisions made (defaults, flag if wrong)

1. **Agent-to-seam mapping:** field adjuster → damage assessment; claim adjuster → approval/
   denial. Coverage verification is untouched.
2. **Single one-way `enableAiAdjuster` signal** drives both agents; it may arrive any time
   the claim is open and running, including mid-wait. No disable.
3. **Claim-adjuster agent is repurposed** to make the approve/deny decision from
   `{claim, coverage, assessment}` and no longer takes policy state (per feedback #4). Field
   adjuster is unchanged; its recommended `approval` output is informational only.
4. **Everything an agent needs is passed via workflow input**; no activity fetches state.
5. **Child workflows, not activities**, to invoke the agents (durability + visibility), with
   the `snake_case` wire contract enforced on the Java side.
6. **Batch enablement uses Temporal batch signal** over a Visibility query, not client-side
   fan-out.

## 11. Follow-ups (out of scope)

- Confidence-threshold auto-escalation from AI back to a human queue (demo beat 4.3).
- Tuning the batch-signal path / Visibility for the 10k-claim case in `docs/todos.md`.
- Emitting an AI-vs-human search attribute for dashboard filtering.

## 12. Implementation order

1. Update the claim-adjuster agent (models, instructions, starter) + its unit tests (§6.4).
2. Java mirror records + `toAgentClaim`/`toAgentCoverage` + serialization unit tests.
3. Workflow: `aiAdjusterEnabled` field + `enableAiAdjuster` signal, Seam A, then Seam B, with
   integration tests alongside.
4. API service + controller (single + batch signal) + optional intake flag.
5. `scripts/demo-adjuster.sh` + the `demo:ai-adjuster` `mise` task.
6. E2E run of the drain task against a real dev server with the agents worker.
