# Temporal Insurance Demo

A demo insurance platform (auto/property/commercial) where every policy and claim is a
long-running Temporal workflow. There is **no application database** — workflow state is the
source of truth, read via queries and the visibility API.

## Toolchain

This is a **Java 21 / Gradle multi-module** backend plus a **React (Vite) frontend**, all
driven through **mise** (`mise.toml`).

## Modules

- `domain/` — Temporal workflows, activities, models, and search attributes (plain Java, no Spring).
  Organized by domain under `com.ziggy.insurance.domains`: `policy`, `claim`, `notifications`,
  `payment`, `demo`.
  The `notifications` domain owns all customer/third-party notifications and exposes a **Nexus
  service** (`NotificationService.sendNotification`) that other domains call across the Nexus
  boundary. The operation is **backed by a workflow** (`NotificationWorkflow`) that resolves the
  recipient's channel **preference** (email / app / text) via an activity, then dispatches on each
  channel **in parallel** via activities. The claim workflow notifies policyholders on every status
  change through this service rather than a local activity. Preference lookup and dispatch are mocked
  activities (the preference lookup always returns every channel).
  The `payment` domain owns all customer payments and exposes a **Nexus service**
  (`PaymentService.processPayment`) that other domains call across the Nexus boundary instead of
  owning payment logic. The operation is **backed by a workflow** (`PaymentWorkflow`, started with
  workflow id `payment/{claimId}` so a retried operation dedupes onto one run and never double-pays)
  that drives a mocked, deliberately flaky payment-gateway activity to success via Temporal's default
  retries. The claim workflow triggers claim payouts through this service rather than a local activity.
- `api/` — Spring Boot REST API. **Temporal client only — it runs no workers.** It starts,
  queries, and signals workflows and lists them via the visibility API.
- `workers/` — Spring Boot worker process that actually executes workflows and activities.
- `app/` — React portal (Vite). The dev server proxies `/api` → `localhost:8080`.

## Running (each in its own terminal, in order)

```bash
mise run temporal:dev
mise run temporal:nexus     # once the dev server is up: create the Nexus endpoint
mise run temporal:worker
mise run api
mise run portal:dev
```

`temporal:nexus` creates the `notifications-ep` and `payment-ep` Nexus endpoints (idempotent). It
needs the dev server from `temporal:dev` already running, and they must exist before any claim
runs — otherwise the claim workflow's `sendNotification` / `processPayment` calls cannot be routed.

Then seed demo data (idempotent): `curl -X POST http://localhost:8080/api/v1/demo/setup`

First-time frontend setup: `mise run portal:install`.

## Tests

```bash
mise run test
```

## Code comments

Comments explain **WHY, not WHAT**. The code already says what it does; a comment earns its
place only by capturing intent the code cannot: rationale, non-obvious constraints, trade-offs,
gotchas, references to a spec/decision, units/nullability, or a warning that something
surprising is intentional.

- **Remove** comments that merely restate the code (`// increment i`, `// getter for status`,
  step-by-step narration of self-evident logic, section dividers that only label an obvious block).
- **Keep / add** WHY comments — especially Temporal-specific rationale: child-workflow-vs-activity
  choices, Nexus routing, search-attribute registration, and "looks wrong but is intentional" notes.
- **File headers stay.** Every code file opens with a brief 2-line comment describing what the file
  does; that is a separate, deliberate convention and is not subject to the WHY-not-WHAT rule.
- Keep comments **evergreen** — describe the code as it is, not how it changed ("now", "previously",
  "refactored to…", PR/ticket numbers).

## Project conventions & gotchas

- Temporal Workflow IDs: policies `policy/{auto|property|commercial}/{policyId}`,
  claims `claim/auto/{claimId}`, payments `payment/{claimId}`. Task queues: `policy-task-queue`,
  `claim-task-queue`, `notifications-task-queue`, `payment-task-queue`.
- **The `notifications-ep` and `payment-ep` Nexus endpoints must exist in every environment**, just
  like the custom search attributes. Locally, `mise run temporal:nexus` creates them (target task
  queues `notifications-task-queue` / `payment-task-queue`, same namespace). Tests create them
  in-process via `TestWorkflowEnvironment.createNexusEndpoint(...)`. Miss `notifications-ep` and any
  claim status change hangs on the Nexus call; miss `payment-ep` and the claim hangs at payout.
- Keep every workflow in the same Temporal namespace; Nexus is used for the cross-domain call
  boundary, not for crossing namespaces here.
- **Custom search attributes must be registered in every environment.** The dev server
  registers `policyHolderId`, `policyStatus`, `policyId`, and `claimStatus` via the
  `temporal:dev` mise task; the test environment must register them too, or workflows that
  upsert them hang.
- `TestWorkflowEnvironment` does **not** implement `ListWorkflowExecutions` — any
  visibility/list flow cannot be integration-tested there. Unit-test the query builder and
  verify list flows end-to-end against a real dev server.
- **Temporal Cloud:** `MISE_ENV=cloud` (VS Code "Start Cloud" task) loads `mise.cloud.toml`,
  which activates the Spring `cloud` profile (`application-cloud.yml` in `api/` and `workers/`:
  API key + `enable-https`) and reads the gitignored `.env.cloud` (`TEMPORAL_TARGET`,
  `TEMPORAL_NAMESPACE`, `TEMPORAL_API_KEY`; template `.env.cloud.example`).
- **Namespace provisioning uses different CLI families per environment**, driven by
  `scripts/demo-setup.sh --target local|cloud`. Local (`mise run demo:setup`) uses
  `temporal operator search-attribute create` / `operator nexus endpoint create` against the dev
  server (with `--tls=false`, since the dev server is plaintext). Cloud
  (`mise run temporal:cloud:setup`, which sources `.env.cloud`) uses
  `temporal cloud namespace search-attribute create` / `cloud nexus endpoint create` with
  `--api-key`, `--idempotent`, and — for Nexus — `--allow-namespace` (the caller namespace). The
  Cloud task requires the `temporal` Cloud CLI extension.
