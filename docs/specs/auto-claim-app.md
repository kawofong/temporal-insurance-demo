# Auto Claim App Integration Spec

Wire the React portal to the existing REST API and Temporal auto-claim workflow so that:

1. A policyholder can **start a new auto claim** (FNOL).
2. A policyholder can **see their existing claims**.
3. A policyholder can **drill into a claim's details** and track its status.
4. The admin panel lets a **field adjuster** submit damage assessments and a **claims adjuster** approve payouts, each working a queue of claims waiting on them.

## Scope

This spec covers **auto claims only** — the workflow, controller, and search attributes that already exist for auto. Property and commercial claims are out of scope. Policy management flows (already wired) are unchanged.

## Current State

The backend is almost entirely in place; the work is concentrated in the frontend plus one small API enhancement.

| Layer | Status |
|---|---|
| `AutoClaimWorkflow` + activities (`domain/`) | ✅ Exists — full lifecycle, signals, query |
| `AutoClaimController` + `ClaimService` (`api/`) | ✅ Exists — submit, get, list, approve, damage-assessment |
| `claimStatus` / `policyId` / `policyHolderId` search attributes | ✅ Exists |
| List-by-status filtering | ❌ **Gap** — list filters by policy only, not status |
| Portal: FNOL form, real claims list, claim drill-down | ❌ Missing (claims are hardcoded mock data) |
| Admin panel: field adjuster + claims adjuster tabs | ❌ Stubs ("coming soon") |

## Architecture & Data Flow

```
React (app/, Vite :5173)
  │  fetch()  — /api proxied to :8080 (vite.config.js)
  ▼
Spring Boot REST API (api/, :8080)  — Temporal client only
  │  start / query / signal / listExecutions
  ▼
Temporal (:7233, namespace "default")
  │  claim-task-queue
  ▼
Worker (workers/) — AutoClaimWorkflowImpl + AutoClaimActivitiesImpl
```

Every claim is one `AutoClaimWorkflow` execution with workflow ID `claim/auto/{claimId}` (e.g. `claim/auto/CLM-A1B2C3D4`). The API is a thin Temporal client: it **starts** the workflow (FNOL), **queries** it (`getClaim`), **signals** it (`submitDamageAssessment`, `adjusterApproval`), and **lists** executions via the visibility API. There is no separate claims database — workflow state is the source of truth.

### Claim lifecycle (the state machine the UI reflects)

```
SUBMITTED ──▶ COVERAGE_VERIFIED ──▶ PENDING_DAMAGE_ASSESSMENT ──▶ PENDING_APPROVAL ──▶ PAYMENT_PROCESSING ──▶ CLOSED
    │                                        ▲ (field adjuster signal)     ▲ (adjuster signal)
    └──▶ REJECTED (terminal, coverage denied)
```

- `PENDING_DAMAGE_ASSESSMENT` — workflow is blocked on `Workflow.await(() -> damageAssessed)`, released by the `submitDamageAssessment` signal. **This is the field adjuster queue.**
- `PENDING_APPROVAL` — workflow is blocked on `Workflow.await(() -> adjusterApproved)`, released by the `adjusterApproval` signal. **This is the claims adjuster queue.**
- `REJECTED` and `CLOSED` are terminal.

## REST API Contract

Base path: `/api/v1/claims/auto` (`AutoClaimController`). All bodies are JSON.

### Existing endpoints (no change)

| Method | Path | Body | Response | Temporal op |
|---|---|---|---|---|
| POST | `/api/v1/claims/auto` | `FnolRequest` | `201` `FnolResponse` | `WorkflowClient.start` |
| GET | `/api/v1/claims/auto/{claimId}` | — | `200` `AutoClaimState` | `getClaim()` query |
| GET | `/api/v1/claims/auto?policyHolderId=&policyId=` | — | `200` `AutoClaimListResponse` | `ListWorkflowExecutions` + `getClaim()` |
| POST | `/api/v1/claims/auto/{claimId}/damage-assessment` | `DamageAssessmentResult` | `202` | `submitDamageAssessment` signal |
| POST | `/api/v1/claims/auto/{claimId}/approve` | `AdjusterApprovalRequest` | `202` | `adjusterApproval` signal |

### Required backend change: filter the list by status

The admin queues need "all claims in `PENDING_DAMAGE_ASSESSMENT`" and "all claims in `PENDING_APPROVAL`". The `claimStatus` search attribute already exists and is upserted on every status transition; only the query builder and controller need to expose it.

- Add an optional `status` query param to `GET /api/v1/claims/auto`:
  `GET /api/v1/claims/auto?status=PENDING_DAMAGE_ASSESSMENT`
- In `ClaimService.buildClaimListQuery(...)`, append `AND claimStatus = '<status>'` when `status` is present (validate it against the `ClaimStatus` enum; reject unknown values with `400 VALIDATION_FAILED`).
- Keep it composable with the existing `policyHolderId` / `policyId` filters.

> Rationale: filtering server-side via the visibility store is the intended Temporal pattern and avoids the admin panel fetching-then-querying every claim. Client-side filtering is a viable fallback only if the visibility store is unavailable.

### DTO shapes (verbatim from code — use these field names)

**`FnolRequest`** (POST body)
```
policyId: String, policyHolderId: String,
incidentDescription: String, incidentDate: long (epoch millis), incidentLocation: String,
vehicleVin: String, vehicleMake: String, vehicleModel: String, vehicleYear: int
```

**`FnolResponse`** (201)
```
claimId: String   // e.g. "CLM-A1B2C3D4"
status: ClaimStatus  // always SUBMITTED at intake
message: String
```

**`AutoClaimState`** (GET / list items)
```
claimId, policyId, policyHolderId: String
status: ClaimStatus, rejectionReason: String
incidentDescription: String, incidentDate: long, incidentLocation: String
vehicleVin, vehicleMake, vehicleModel: String, vehicleYear: int
coverageType: String, deductible: int
assignedAdjusterId: String, damageAssessment: String, estimatedRepairCost: int
approvedByAdjusterId: String, approvedPayoutAmount: int, approvedAt: long
paymentReference: String, closedAt: long
```

**`AutoClaimListResponse`**: `{ claims: AutoClaimState[] }`

**`DamageAssessmentResult`** (field adjuster body): `{ summary: String, estimatedCost: int }`

**`AdjusterApprovalRequest`** (adjuster body): `{ adjusterId: String, approvedPayoutAmount: int, notes: String }`

**`ErrorResponse`** (4xx): `{ error: String, message: String }` — `NOT_FOUND` (404), `VALIDATION_FAILED` (400).

## Frontend Work

All new fetch calls go through `/api` (already proxied). Follow the existing `policyHelpers.js` convention: a constants + helpers module, bare `fetch`, no new HTTP library.

### 1. `claimHelpers.js` (new) — client + shared logic

Mirror `policyHelpers.js`:

- `CLAIM_ENDPOINT = "/api/v1/claims/auto"`
- `submitFnol(payload)` → POST, returns `FnolResponse`
- `fetchClaim(claimId)` → GET one
- `listClaims({ policyHolderId, policyId, status })` → GET list, returns `claims[]`
- `submitDamageAssessment(claimId, { summary, estimatedCost })` → POST 202
- `approveClaim(claimId, { adjusterId, approvedPayoutAmount, notes })` → POST 202
- Reuse `readApiError(response, fallback)` from `policyHelpers.js`.

**Status display model** — the backend enum differs from the current mock strings (`Approved`/`Paid`/`In Review`/`Denied`). Add a single mapping used everywhere:

| `ClaimStatus` | Label | CSS class | Bucket |
|---|---|---|---|
| `SUBMITTED` | Submitted | `in-review` | open |
| `COVERAGE_VERIFIED` | Coverage Verified | `in-review` | open |
| `PENDING_DAMAGE_ASSESSMENT` | Awaiting Damage Assessment | `in-review` | open |
| `PENDING_APPROVAL` | Awaiting Approval | `in-review` | open |
| `PAYMENT_PROCESSING` | Processing Payment | `approved` | open |
| `CLOSED` | Closed / Paid | `paid` | terminal |
| `REJECTED` | Rejected | `denied` | terminal |

Add a `formatClaimStatus(status)` and `claimStatusClass(status)` helper. Format currency from the integer cent/dollar fields (`estimatedRepairCost`, `approvedPayoutAmount`) and dates from the `long` epoch fields (reuse `formatDate`).

### 2. Start a new claim (FNOL) — policyholder

**Entry point:** a "File a Claim" button on the auto policy detail page (`PolicyDetails.jsx` when `policyType === "auto"`), where vehicle context is available. Optionally also surface it from the Portal claims section.

**Form fields** → `FnolRequest`:
- `incidentDescription` (textarea, required)
- `incidentDate` (date picker → epoch millis, required)
- `incidentLocation` (text, required)
- `vehicleVin`, `vehicleMake`, `vehicleModel`, `vehicleYear` — **pre-filled** from the policy's `insuredVehicles[0]` when available; editable. `vehicleVin` is required (the workflow rejects coverage if VIN is blank).
- `policyId` = current policy id; `policyHolderId` = logged-in `policyholder.memberId`.

**On submit:** POST to `CLAIM_ENDPOINT`. On `201`, show the returned `claimId` immediately (this is the Early-Return / start-with-query moment — no waiting on back-office processing) and route the user to the claim detail view for that `claimId`. Surface `ErrorResponse.message` on `400`.

### 3. See existing claims — policyholder

Replace the hardcoded `recentClaims` in `Portal.jsx` with a live fetch:

- On load, `listClaims({ policyHolderId: policyholder.memberId })`.
- Map each `AutoClaimState` into the existing claims table columns: `claimId`, type ("Auto"), `formatDate(incidentDate)`, `incidentDescription`, amount (`approvedPayoutAmount` if set else `estimatedRepairCost`, else "—"), and `formatClaimStatus(status)` with `claimStatusClass(status)`.
- Each row links to the claim detail route (below).
- Handle empty (no claims), loading, and error states like the existing policies fetch does (`AbortController`, error banner).

### 4. Drill into claim details + status tracker — policyholder

**New route:** `/portal/claims/:claimId` → new `ClaimDetails.jsx` (sibling of `PolicyDetails.jsx`).

- Fetch via `fetchClaim(claimId)`.
- **Status tracker**: a stepper rendering the lifecycle (Submitted → Coverage Verified → Damage Assessment → Approval → Payment → Closed), highlighting the current `status`. If `REJECTED`, show the terminal rejected state with `rejectionReason`.
- **Detail sections:** Incident (`incidentDescription`, `incidentDate`, `incidentLocation`), Vehicle (`vehicleYear make model`, `vehicleVin`), Coverage (`coverageType`, `deductible`), Assessment (`assignedAdjusterId`, `damageAssessment`, `estimatedRepairCost`), Approval/Payment (`approvedPayoutAmount`, `approvedByAdjusterId`, `approvedAt`, `paymentReference`, `closedAt`) — only render sections whose fields are populated.
- Reuse `readApiError`; `404` → "Claim not found".

### 5. Admin — Field Adjuster tab

Replace the stub in `AdminPanel.jsx`. This persona works the `PENDING_DAMAGE_ASSESSMENT` queue.

- **Queue:** `listClaims({ status: "PENDING_DAMAGE_ASSESSMENT" })`, refreshed on tab open and after each action. Show `claimId`, incident summary, vehicle, `incidentLocation`.
- **Action:** selecting a claim opens a **damage assessment form** — `summary` (textarea), `estimatedCost` (number). Submit calls `submitDamageAssessment(claimId, ...)` (POST → `202`).
- After submit, show a success notice and re-fetch the queue after a short delay (reuse the `SIGNAL_REFRESH_DELAY_MS = 900ms` pattern — the signal is async and the workflow needs a beat to advance to `PENDING_APPROVAL`).

### 6. Admin — Claims Adjuster tab

Replace the stub. This persona works the `PENDING_APPROVAL` queue.

- **Queue:** `listClaims({ status: "PENDING_APPROVAL" })`.
- Show each claim with the field adjuster's `damageAssessment` and `estimatedRepairCost` for context.
- **Action:** an **approval form** — `approvedPayoutAmount` (number, pre-filled from `estimatedRepairCost`), `notes` (textarea), and `adjusterId` (default to the demo adjuster, e.g. `ADJ-SARAH`). Submit calls `approveClaim(claimId, ...)` (POST → `202`).
- Re-fetch the queue after `SIGNAL_REFRESH_DELAY_MS`; the claim moves to `PAYMENT_PROCESSING`/`CLOSED`.

> Note: `processPayment` is intentionally flaky (fails until attempt 6) to demo Temporal's automatic retries. The claim will sit in `PAYMENT_PROCESSING` briefly before reaching `CLOSED` — the UI should treat this as normal, not an error.

## Error Handling & Edge Cases

- **Async signals (202):** damage-assessment and approve return `202` before the workflow advances. Always re-query after `SIGNAL_REFRESH_DELAY_MS` rather than assuming the new state; never optimistically mutate the queue.
- **Blank VIN:** FNOL with a blank `vehicleVin` leads to `REJECTED` (coverage denied). Require VIN client-side and surface the rejection in claim detail.
- **Not found / already terminal:** signalling a `CLOSED`/`REJECTED` claim, or querying a nonexistent one, yields `404 NOT_FOUND` — show a friendly message and refresh the queue.
- **Visibility lag:** `ListWorkflowExecutions` is eventually consistent; a just-submitted claim may not appear in a list for a moment. This is expected; the detail view (direct query by ID) is always current.
- **Empty queues:** admin tabs must render an explicit "No claims waiting" empty state.

## Testing Strategy

Per project TDD policy, all three test levels are required.

- **Unit (backend):** `ClaimServiceQueryTest`-style tests for the enhanced `buildClaimListQuery` — verify the `status` filter composes correctly with policy filters and rejects invalid enum values. *(The visibility `ListWorkflowExecutions` endpoint is not implemented in `TestWorkflowEnvironment`, so the query string must be unit-tested independently of a running list call.)*
- **Unit (frontend):** `claimHelpers.js` — status mapping, currency/date formatting, request payload construction.
- **Integration (backend):** `AutoClaimControllerTest`-style tests against `TestWorkflowEnvironment` for submit → query → damage-assessment signal → approve signal → closed. **Custom search attributes (`policyId`, `policyHolderId`, `claimStatus`) must be registered in the test environment**, or workflows that upsert them hang.
- **E2E:** the list-by-status admin flow must be verified against a **real Temporal dev server** (`mise run temporal:dev`) because the test server can't exercise the visibility query. Drive the full path: portal FNOL → field adjuster assessment → adjuster approval → `CLOSED`, asserting the claim appears in and leaves each admin queue.

## Assumptions & Decisions

- **Auth stays hardcoded.** Login remains "Jake" (`memberId: "jake-from-state-farm"`); `policyHolderId` on FNOL = that `memberId`. No real authentication is introduced (matches the existing portal). The admin "role" is simply which AdminPanel tab is open.
- **FNOL entry lives on the auto policy detail page**, so vehicle fields can be pre-filled and `policyId` is unambiguous.
- **Status filtering is done server-side** via the existing `claimStatus` search attribute (the one backend change), not client-side.
- **Adjuster identity is a demo default** (`ADJ-SARAH`) rather than a logged-in identity.
- Property/commercial claims, claim photo upload, payment detail beyond `paymentReference`, and claim cancellation are **out of scope**.

## Open Questions

1. Should the policyholder claims list also show terminal (`CLOSED`/`REJECTED`) claims, or only open ones? *(Default: show all, sorted newest first.)*
2. Should the field adjuster and claims adjuster be separated by any access control, or is the shared AdminPanel toggle sufficient for the demo? *(Default: shared toggle, no access control.)*

## Implementation Milestones

1. **Backend:** add `status` filter to `GET /api/v1/claims/auto` + unit test for the query builder.
2. **Frontend foundation:** `claimHelpers.js` (client, status model, formatters) + unit tests.
3. **Policyholder read paths:** live claims list on Portal + `ClaimDetails.jsx` with status tracker.
4. **Policyholder write path:** FNOL form on auto policy detail → submit → redirect to claim detail.
5. **Admin field adjuster tab:** queue + damage assessment form.
6. **Admin claims adjuster tab:** queue + approval form.
7. **E2E pass** on a real dev server across the full lifecycle.
