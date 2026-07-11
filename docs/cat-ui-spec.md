# Spec: Simulate a Catastrophe (CAT) Event from the Admin Panel

## 1. Summary

Add a **Catastrophe Event** tab to the existing Adjuster Admin Panel in the React
portal (`app/`) so a demo operator can declare a CAT event from the UI, then watch it
fan out synthetic property claims in real time.

The backend already exposes everything this needs — this feature is **frontend-only**.
No changes to `domain/`, `workers/`, or `api/` are required.

## 2. Background & current state

- The Adjuster Admin Panel (`app/src/pages/AdminPanel.jsx`) is a floating action button
  (🛠️) that opens a left-tabbed modal. It currently has two tabs, **Field Adjuster** and
  **Claims Adjuster**, each a stand-in for a real operator application acting on claims.
  It is mounted on the Portal, Policy Details, and Claim Details pages.
- The CAT event backend is complete and tested:
  - `POST /api/v1/cat` — declares a CAT event and starts `CATEventWorkflow`.
    Request body `DeclareCATEventRequest`: `{ catEventId, eventName, affectedRegion, totalClaimsToGenerate }`.
    Returns `201` with `DeclareCATEventResponse`: `{ catEventId, status, totalClaimsExpected }`.
    Rejects `totalClaimsToGenerate <= 0` (→ `400`).
  - `GET /api/v1/cat/{catEventId}` — queries live progress, returns `CATEventStatus`:
    `{ catEventId, eventName, affectedRegion, status, totalClaimsExpected, totalClaimsOpened, percentComplete, declaredAt }`.
  - `status` is a `CATEventLifecycle`: `DECLARED` → `SPAWNING` → `COMPLETED` (terminal).
  - The workflow fans out synthetic property claims as `ABANDON` child workflows and
    completes once all are filed; `batchSize` is a workflow constant and is **not** a
    request parameter.
- The portal calls the API through per-domain helper modules (see `claimHelpers.js`,
  `policyHelpers.js`) using `fetch` against relative `/api/...` paths (Vite proxies
  `/api` → `localhost:8080`). Errors are surfaced via `readApiError(response, fallback)`.

There is intentionally **no list/visibility endpoint** for CAT events, so the UI tracks
the event it just declared by id rather than listing all events.

## 3. Goals

- Let an operator declare a CAT event through the admin panel: fill a short form, submit,
  and get confirmation.
- Show live progress of the declared event (lifecycle state + a progress bar of
  `totalClaimsOpened / totalClaimsExpected`) until it reaches `COMPLETED`.
- Match the existing admin-panel look, structure, and data-layer conventions so the tab
  feels native to the panel.

## 4. Non-goals

- No backend changes (endpoints, workflow, DTOs already exist).
- No listing/history of past CAT events (no visibility endpoint exists; see §10).
- No cancel/terminate of an in-flight CAT event.
- No drill-down from the CAT tab into the individual generated child claims. The
  generated property claims already surface through existing flows (Claims Adjuster
  queue, portal claim lists) once they progress.

## 5. UX & flow

Add a third tab to the admin-panel modal:

```
[ 🔧 Field Adjuster ] [ ✅ Claims Adjuster ] [ 🌪️ Catastrophe Event ]
```

**Declare view (default when no event is active in this session):**

- A short intro paragraph explaining this stands in for the ops console that declares a
  regional catastrophe and mass-generates first-notice-of-loss claims.
- A form with fields (all required):
  | Field | Input | Notes |
  |---|---|---|
  | Event Name | text | `eventName`, e.g. `Butte County Wildfire` |
  | Affected Region | text | `affectedRegion`, e.g. `Northern California` |
  | Total Claims to Generate | number, `min=1` | `totalClaimsToGenerate` |
- **The event id is not entered by the operator — it is auto-generated** from the event
  name and today's date (see §8.1). Show it as a read-only, live-updating preview line
  below the Event Name field, e.g.:
  > Event ID: `evt-2026-07-10-butte-county-wildfire`

  The preview recomputes as the operator edits the Event Name so they can see the id that
  will be declared. The id sent to the backend is generated at submit time.
- Fields are **prefilled with demo defaults** (see §9) so the operator can declare with
  one click during a demo, but every editable field stays editable.
- A **Declare CAT Event** submit button. While the request is in flight it is disabled
  and reads `Declaring...`.
- Inline error area (reuses `policy-modal-notice policy-modal-notice--error`) for
  validation and API errors.

**Progress view (after a successful declare, and while an event is active):**

- Header line: event name + id + affected region.
- A lifecycle badge showing `DECLARED` / `SPAWNING` / `COMPLETED`.
- A progress bar rendering `percentComplete`, with a caption
  `{totalClaimsOpened} / {totalClaimsExpected} claims filed`.
- The view **polls** `GET /api/v1/cat/{catEventId}` on an interval and updates live.
- Once `status === "COMPLETED"`: stop polling, show a success notice
  (`policy-modal-notice`), and offer a **Declare another event** button that returns to
  the declare view with the form reset to defaults.

Switching tabs away and back, or closing/reopening the modal, must not lose an active
event's progress within the same browser session (see state model in §7).

## 6. Component design (`app/src/pages/AdminPanel.jsx`)

Follow the shape of the existing `FieldAdjusterPanel` / `AdjusterPanel` components.

- Add to the `TABS` array:
  ```js
  { id: "cat-event", label: "Catastrophe Event", icon: "🌪️" }
  ```
- Add a `CATEventPanel` component and render it from the tab switch in `AdminPanel`
  (replace the current binary ternary with a switch/map keyed on `activeTab`).
- `CATEventPanel` owns:
  - form field state (`eventName`, `affectedRegion`, `totalClaimsToGenerate`) — the event
    id is derived, not stored as an editable field,
  - a derived id preview: `generateCatEventId(eventName, new Date())` (see §8.1),
    recomputed on each render / event-name change,
  - the active event id (captured at submit) + latest fetched `CATEventStatus`,
  - `isBusy`, `notice`, `formError` (same pattern as the other panels).
- Extract a small `useCatEventProgress(catEventId)` hook (mirrors `useClaimQueue`) that
  polls status on `CAT_POLL_INTERVAL_MS` via `setInterval`, cancels on unmount / when the
  id changes / once terminal, and exposes `{ status, isLoading, error }`.
- Reuse existing CSS classes (`admin-tab-panel`, `admin-placeholder`, `policy-action-form`,
  `policy-form-actions`, `policy-modal-notice`, etc.). Add only the few new classes needed
  for the progress bar / lifecycle badge to `AdminPanel.css`
  (e.g. `.cat-progress`, `.cat-progress-bar`, `.cat-progress-fill`, `.cat-lifecycle-badge`).

## 7. State model

- **Session-scoped, in the component.** The active event id + form values live in
  `CATEventPanel` React state, so they persist while the panel/modal stays mounted and
  when switching tabs. The panel is mounted per-page (Portal / Policy / Claim details), so
  navigating between pages resets it — acceptable for the demo.
- Lifecycle: `null active id` → declare view; `active id set` → progress view.
- `COMPLETED` is terminal: the poll hook stops the interval; the panel keeps showing the
  final progress until the operator chooses **Declare another event**.
- Optional (low priority): persist the last-declared `catEventId` to `sessionStorage` so a
  page navigation can resume progress. Default: **not** implemented unless requested.

## 8. Data layer (`app/src/pages/catHelpers.js` — new)

New module mirroring `claimHelpers.js`, importing `readApiError` from `policyHelpers`.

```js
export const CAT_ENDPOINT = "/api/v1/cat";
// How often the CAT panel re-queries the workflow so progress appears live.
export const CAT_POLL_INTERVAL_MS = 3000;

// Backend CATEventLifecycle -> portal presentation (label + bucket).
export const CAT_LIFECYCLE_MAP = {
  DECLARED:  { label: "Declared",  bucket: "open" },
  SPAWNING:  { label: "Spawning claims", bucket: "open" },
  COMPLETED: { label: "Completed", bucket: "terminal" },
};

export function isTerminalCatStatus(status) { /* bucket === "terminal" */ }
export function formatCatStatus(status) { /* map label ?? status ?? "Unknown" */ }
// percentComplete arrives 0..100 (backend computes totalClaimsOpened/totalClaimsExpected*100).
// Clamp/round here so the bar is always a clean integer 0..100.
export function catProgressPercent(status) { /* -> integer 0..100 */ }

// See §8.1 — id is derived from the event name + date, never entered by the operator.
export function slugify(text) { /* lowercase, non-alphanumeric -> "-", collapse, trim "-" */ }
export function generateCatEventId(eventName, date) { /* -> "evt-YYYY-MM-DD-<slug>" */ }

export async function declareCatEvent({ catEventId, eventName, affectedRegion, totalClaimsToGenerate }) {
  // POST CAT_ENDPOINT; throw Error(readApiError(...)) on !ok; return response.json()
}

export async function fetchCatEventStatus(catEventId, options) {
  // GET `${CAT_ENDPOINT}/${catEventId}`; throw on !ok; return response.json()
}
```

Notes:
- `declareCatEvent` sends only the four request fields — never `batchSize` (a workflow
  constant, default 500). Its `catEventId` is the value produced by `generateCatEventId`,
  passed in by the panel at submit time.

### 8.1 Event id generation

The event id is **auto-generated, lowercase**, and derived from the declaration date and
the event name — the operator never types it. Format:

```
evt-<YYYY-MM-DD>-<slug(eventName)>
```

Example: event name `Butte County Wildfire` declared on 2026-07-10 →
`evt-2026-07-10-butte-county-wildfire`.

- **Date** is the local declaration date, formatted `YYYY-MM-DD` (zero-padded month/day).
- **`slugify(eventName)`** lowercases, replaces every run of non-alphanumeric characters
  with a single `-`, and trims leading/trailing `-`. So `Butte County Wildfire` →
  `butte-county-wildfire`; `Nor'easter — 2026!` → `nor-easter-2026`.
- `generateCatEventId(eventName, date)` is a **pure function** that takes the `Date`
  explicitly (the panel passes `new Date()`) so it stays deterministic and unit-testable.
- If the event name slug is empty (e.g. name is only punctuation), fall back to
  `evt-<YYYY-MM-DD>-event`. Client-side validation should require a non-empty name anyway.

## 9. Demo defaults (documented assumptions)

Prefill the editable fields with values matching the backend tests so a demo works out of
the box (the id is derived, not prefilled):

- `eventName`: `Butte County Wildfire`
- `affectedRegion`: `Northern California`
- `totalClaimsToGenerate`: `5` (small, so the fan-out completes quickly on a dev server;
  operator can raise it to show scale)
- derived `catEventId` preview: `evt-<today>-butte-county-wildfire`

Note on load: a declared CAT event fans out synthetic `PropertyClaimWorkflow` children,
which in turn drive the notifications and payment Nexus services — so even a small demo
generates many downstream workflow executions. The `notifications-ep` and `payment-ep`
Nexus endpoints must exist in the environment (`mise run temporal:nexus`) or the spawned
claims hang. Keep the default `totalClaimsToGenerate` small.

Because the id is derived from the event name + date, declaring the **same event name on
the same day twice** produces the same id (`cat/{catEventId}`) and collides with the still-
running workflow. For a repeat demo, the operator changes the event name (which changes the
slug). The UI does not append a uniquifying suffix by default; surface the backend error
clearly if a collision occurs (see §10).

## 10. Edge cases & error handling

- **Validation:** all fields required; `totalClaimsToGenerate` must be an integer `>= 1`.
  Block submit client-side and show an inline message; the backend also rejects `<= 0`.
- **Duplicate event id:** since the id is derived, re-declaring the same event name on the
  same day yields the same id and fails at the API (workflow already started). Show the
  returned error via `readApiError`; keep form values so the operator can tweak the event
  name (→ new slug → new id) and retry.
- **Poll errors:** a failed status poll shows a non-fatal inline error but keeps the last
  known progress and keeps trying until terminal (or the operator leaves).
- **No list endpoint:** the UI only ever tracks the id it just declared. Do not attempt to
  enumerate CAT events. (Backend note: `TestWorkflowEnvironment` does not implement
  `ListWorkflowExecutions`, which is one reason no list flow exists here.)
- **Unmount / tab switch:** the poll interval must be cleared on unmount and when the
  active id changes to avoid leaks and stale updates (use an `AbortController` on the fetch
  like `useClaimQueue`, plus `clearInterval`).

## 11. Testing

Per the repo's TDD practice, write tests before the implementation.

- **Unit (vitest, in `app/`)** — new `app/src/pages/catHelpers.test.js`, mirroring
  `claimHelpers.test.js`:
  - `formatCatStatus` maps each lifecycle value and falls back for unknown/empty.
  - `isTerminalCatStatus` is true only for `COMPLETED`.
  - `catProgressPercent` clamps/normalizes (0, mid, full, out-of-range, missing) to `0..100`.
  - `slugify` lowercases, collapses non-alphanumeric runs to single `-`, trims edges, and
    handles multi-space / punctuation / already-slugged / empty inputs.
  - `generateCatEventId(eventName, date)` produces `evt-YYYY-MM-DD-<slug>` for a fixed
    `Date` (zero-padded month/day; asserts the example
    `Butte County Wildfire` + 2026-07-10 → `evt-2026-07-10-butte-county-wildfire`), and
    falls back to `...-event` for a punctuation-only name.
  - Pure request-shaping in `declareCatEvent` sends exactly the four fields (mock `fetch`;
    assert body and that `batchSize` is absent). Assert error path throws with the
    `readApiError` message on a non-ok response.
- **Component (vitest + Testing Library, if available in `app/`):** rendering the CAT tab
  shows the form; a successful declare transitions to the progress view; reaching
  `COMPLETED` stops polling and shows the success notice. If Testing Library is not yet a
  dependency, keep coverage at the helper level and cover the transition manually in E2E.
- **End-to-end (manual, against a real dev server):** run the four processes per
  `README.md` (`temporal:dev`, `temporal:nexus`, `temporal:worker`, `api`, `portal:dev`),
  seed demo data, open the admin panel → Catastrophe Event tab, declare with defaults, and
  confirm the progress bar advances to 100% / `COMPLETED` and that generated property
  claims appear in the existing claim flows. This is the authoritative check because CAT
  progress depends on live workflow/child-workflow execution.

## 12. Files changed

| File | Change |
|---|---|
| `app/src/pages/catHelpers.js` | **new** — CAT API + presentation helpers |
| `app/src/pages/catHelpers.test.js` | **new** — unit tests for the helpers |
| `app/src/pages/AdminPanel.jsx` | add `CATEventPanel`, `useCatEventProgress` hook, new tab entry, tab switch |
| `app/src/pages/AdminPanel.css` | add progress-bar / lifecycle-badge styles |

No backend, worker, or domain changes.

## 13. Open questions

1. **Persist across page navigation?** Default is session/in-component state only
   (progress resets when navigating between portal pages). Add `sessionStorage` resume
   only if desired.
2. **Repeat-demo collisions** — the id is derived from event name + date, so re-declaring
   the same name on the same day collides. Default is to let the operator change the name.
   Do we instead want an automatic uniquifier (e.g. a trailing `-2`, or including a time
   component in the id) so the exact same name can be re-declared same-day?
3. **Progress display** — is a single progress bar + lifecycle badge enough, or do we also
   want elapsed-time / declaredAt shown?
