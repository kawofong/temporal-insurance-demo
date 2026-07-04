# Temporal Insurance Demo

A demo insurance platform (auto/property/commercial) where every policy and claim is a
long-running Temporal workflow. There is **no application database** — workflow state is the
source of truth, read via queries and the visibility API.

## Toolchain

This is a **Java 21 / Gradle multi-module** backend plus a **React (Vite) frontend**, all
driven through **mise** (`mise.toml`).

## Modules

- `domain/` — Temporal workflows, activities, models, and search attributes (plain Java, no Spring).
  Organized by domain under `com.ziggy.insurance.domains`: `policy`, `claim`, `notifications`, `demo`.
  The `notifications` domain owns all customer/third-party notifications and exposes a **Nexus
  service** (`NotificationService.sendNotification`) that other domains call across the Nexus
  boundary. The operation is **backed by a workflow** (`NotificationWorkflow`) that resolves the
  recipient's channel **preference** (email / app / text) via an activity, then dispatches on each
  channel **in parallel** via activities. The claim workflow notifies policyholders on every status
  change through this service rather than a local activity. Preference lookup and dispatch are mocked
  activities (the preference lookup always returns every channel).
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

`temporal:nexus` creates the `notifications-ep` Nexus endpoint (idempotent). It needs the
dev server from `temporal:dev` already running, and must exist before any claim runs — otherwise
the claim workflow's `sendNotification` call cannot be routed.

Then seed demo data (idempotent): `curl -X POST http://localhost:8080/api/v1/demo/setup`

First-time frontend setup: `mise run portal:install`.

## Tests

```bash
mise run test
```

## Project conventions & gotchas

- Temporal Workflow IDs: policies `policy/{auto|property|commercial}/{policyId}`,
  claims `claim/auto/{claimId}`. Task queues: `policy-task-queue`, `claim-task-queue`,
  `notifications-task-queue`.
- **The `notifications-ep` Nexus endpoint must exist in every environment**, just like the
  custom search attributes. Locally, `mise run temporal:nexus` creates it (target task queue
  `notifications-task-queue`, same namespace). Tests create it in-process via
  `TestWorkflowEnvironment.createNexusEndpoint(...)`. Miss it and any claim status change hangs
  the workflow on the Nexus call.
- Keep every workflow in the same Temporal namespace; Nexus is used for the cross-domain call
  boundary, not for crossing namespaces here.
- **Custom search attributes must be registered in every environment.** The dev server
  registers `policyHolderId`, `policyStatus`, `policyId`, and `claimStatus` via the
  `temporal:dev` mise task; the test environment must register them too, or workflows that
  upsert them hang.
- `TestWorkflowEnvironment` does **not** implement `ListWorkflowExecutions` — any
  visibility/list flow cannot be integration-tested there. Unit-test the query builder and
  verify list flows end-to-end against a real dev server.
