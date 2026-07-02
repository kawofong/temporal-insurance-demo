# Auto Claim Workflow — Design Spec

**SDK:** Java (`io.temporal:temporal-sdk:1.36.0`, Spring Boot starter)
**Task queue:** `claim-task-queue`
**Pattern references:**
- [Approval / Human-in-the-Loop](https://temporal-design-patterns.fly.dev/approval.html)

---

## Decisions (defaults chosen; open to change)

These shape the rest of the spec and were chosen to fit this codebase:

- **D1 — Records vs POJO.** All immutable value types (inputs, requests, responses,
  activity results) are Java `record`s. `AutoClaimState` stays a **mutable POJO** with
  hand-written getters/setters, matching the existing `AutoPolicyState` — workflow state
  accumulates across the lifecycle, which records don't model cleanly. No Lombok anywhere
  (it is not a dependency in this repo).
- **D2 — No Update-with-Start; claim number issued at submission.** The **claim id is
  generated in the REST service before the Workflow is started** (mirroring how policies
  supply `policyId`), so it exists independently of the Workflow. `POST` starts the Workflow
  and returns the claim id **immediately** — `WorkflowClient.start` is fire-and-return, so
  there is **no Query polling and no coverage gating on the response**. Consequences: the
  `submitFnol` Update + its validator are removed, and the `generateClaimId` activity is
  removed. Coverage verification still runs inside the Workflow (Phase 1) and is observed
  asynchronously via `GET /claims/auto/{claimId}`.
- **D3 — Mock coverage.** `verifyCoverage` is a **self-contained mock** (see impl). It does
  not query the PolicyWorkflow, because the policy model has no coverage fields.
- **D4 — Search attributes.** The Workflow upserts `policyId` and `policyHolderId` (both
  `Keyword`). `policyHolderId` is already registered in this project; **`policyId` is new**
  and must be registered (dev server + every test env).
- **D5 — No UI integration.** No portal/dashboard changes; the portal's static "Recent
  Claims" is untouched. Claims are observable via the REST API and the Temporal UI only.
- **D6 — `sendClaimClosedNotification` is a mock notification.** It does not signal the
  PolicyWorkflow to "link" the claim (the policy workflows have no such handler; adding one
  is out of scope here).

---

## The Design Principle: Two Simultaneous Layers

Every step in this workflow has two readings:

- **Business layer** — what the CTO sees: a claim moving through familiar insurance stages.
- **Temporal layer** — what engineering leads see: a named primitive demonstrated in context.

---

## Four Temporal Concepts, Four Demo Moments

| # | Temporal Concept | Demo Moment | Business Language |
|---|---|---|---|
| 1 | **Asynchronous intake** (service-issued id + Workflow start) | FNOL submitted | Customer gets a claim number immediately; the claim then processes on its own |
| 2 | **Human-in-the-Loop** (Signal + `Workflow.await`) | Adjuster reviews and approves | Sarah approves — workflow resumes instantly |
| 3 | **Durability** (Activity retry + heartbeat) | Any activity failure / worker crash | System retries transparently — claim keeps moving |
| 4 | **Observability** (Query + Search Attributes + Temporal UI) | Any point during demo | Full claim history, always queryable and filterable |

---

## Claim Lifecycle States

```java
public enum ClaimStatus {
    SUBMITTED,           // Workflow started; coverage not yet verified
    COVERAGE_VERIFIED,   // Phase 1 complete (coverage confirmed); observable via GET
    UNDER_REVIEW,        // Adjuster assigned; damage assessment running
    PENDING_APPROVAL,    // Damage assessed; waiting for adjuster Signal
    APPROVED,            // Adjuster approved; payment can proceed
    PAYMENT_PROCESSING,  // Payment activity running
    CLOSED               // terminal — workflow completes (paid, or coverage denied)
}
```

```
SUBMITTED
   │  (verifyCoverage  ← mock activity)
   │   coverage denied ─────────────► CLOSED (rejectionReason set)
   │   coverage ok
   ▼
COVERAGE_VERIFIED         ◄──── observable via GET /claims/auto/{claimId}
   │  (assignAdjuster activity)
   ▼
UNDER_REVIEW
   │  (assessDamage activity — heartbeating)
   ▼
PENDING_APPROVAL          ◄──── adjuster reviews (Temporal UI / API)
   │  (adjusterApproval Signal received)
   ▼
APPROVED
   │  (processPayment activity)
   ▼
PAYMENT_PROCESSING
   │  (sendClaimClosedNotification activity — mock)
   ▼
CLOSED  (workflow completes)
```

---

## Claim Intake — Claim Number Issued at Submission (no Update-with-Start)

The claim number exists **before** the Workflow runs: the REST service generates the claim
id, uses it as the Workflow ID, and returns it to the caller immediately after
`WorkflowClient.start`. `start` is fire-and-return, so the `POST` responds without waiting
for anything — **no Query polling, no coverage gating on the response**. Coverage
verification and the rest of the lifecycle run asynchronously inside the Workflow and are
read later via `GET /claims/auto/{claimId}`.

```
Customer portal / caller          ClaimService (REST)              ClaimWorkflow
      │                                  │                              │
      │  POST /api/v1/claims/auto        │                              │
      │ ────────────────────────────────►                              │
      │                                  │  claimId = generate()        │
      │                                  │  start(run, AutoClaimInput)  │
      │                                  │ ────────────────────────────►│  @WorkflowInit sets state
      │◄──── FnolResponse{claimId,       │  (returns immediately)       │  upsert SAs(policyId, holder)
      │        SUBMITTED}                 │                              │  verifyCoverage()  ← mock
      │  (customer sees claim number)    │                              │  assignAdjuster() ← continues async
      │                                  │                              │  assessDamage()
      │  GET /claims/auto/{claimId} ─────► getClaim() (Query) ──────────►│  await(adjusterApproved)
      │◄──── AutoClaimState (live) ──────│◄─────────────────────────────│  ...
```

`@WorkflowInit` sets `state` in the constructor, so a `GET` issued immediately after the
`POST` (before the first Workflow Task runs) still returns a valid `SUBMITTED` snapshot
rather than null.

---

## Workflow State: `AutoClaimState` (mutable POJO — see D1)

```java
// Mutable workflow state for an auto claim entity.
// Tracks lifecycle status, coverage, assessment, approval, and payment.
public class AutoClaimState {

    // ── Identity ──────────────────────────────────────────────────
    private String claimId;           // e.g. CLM-A1B2C3D4 (generated by the service)
    private String policyId;          // links back to policy/auto/{policyId}
    private String policyHolderId;

    // ── Status ────────────────────────────────────────────────────
    private ClaimStatus status;
    private String rejectionReason;   // set only when coverage is denied

    // ── Incident Details ──────────────────────────────────────────
    private String incidentDescription;
    private long incidentDate;
    private String incidentLocation;

    // ── Vehicle ───────────────────────────────────────────────────
    private String vehicleVin;
    private String vehicleMake;
    private String vehicleModel;
    private int vehicleYear;

    // ── Coverage (populated by verifyCoverage) ────────────────────
    private String coverageType;      // COLLISION | COMPREHENSIVE
    private int deductible;

    // ── Assessment (populated by assessDamage) ────────────────────
    private String assignedAdjusterId;
    private String damageAssessment;
    private int estimatedRepairCost;

    // ── Approval (populated by adjusterApproval Signal) ───────────
    private String approvedByAdjusterId;
    private int approvedPayoutAmount;
    private long approvedAt;

    // ── Payment (populated by processPayment) ─────────────────────
    private String paymentReference;
    private long closedAt;

    public AutoClaimState() {}

    public static AutoClaimState fromInput(AutoClaimInput input) {
        AutoClaimState s = new AutoClaimState();
        s.claimId = input.claimId();
        s.policyId = input.policyId();
        s.policyHolderId = input.policyHolderId();
        s.status = ClaimStatus.SUBMITTED;
        s.incidentDescription = input.incidentDescription();
        s.incidentDate = input.incidentDate();
        s.incidentLocation = input.incidentLocation();
        s.vehicleVin = input.vehicleVin();
        s.vehicleMake = input.vehicleMake();
        s.vehicleModel = input.vehicleModel();
        s.vehicleYear = input.vehicleYear();
        return s;
    }

    // ... getters and setters for every field ...
}
```

---

## Supporting Types (Java records — see D1)

```java
public record AutoClaimInput(
    String claimId,
    String policyId,
    String policyHolderId,
    String incidentDescription,
    long incidentDate,
    String incidentLocation,
    String vehicleVin,
    String vehicleMake,
    String vehicleModel,
    int vehicleYear
) {}

// REST request body for FNOL submission (no claimId — the service generates it).
public record FnolRequest(
    String policyId,
    String policyHolderId,
    String incidentDescription,
    long incidentDate,
    String incidentLocation,
    String vehicleVin,
    String vehicleMake,
    String vehicleModel,
    int vehicleYear
) {}

// Returned immediately when the claim is accepted (before async processing).
public record FnolResponse(
    String claimId,       // stable id the customer can track (always set)
    ClaimStatus status,   // SUBMITTED at intake; later states observed via GET
    String message
) {}

public record AdjusterApprovalRequest(
    String adjusterId,
    int approvedPayoutAmount,
    String notes
) {}

public record CoverageVerificationResult(
    boolean covered,
    String coverageType,     // COLLISION | COMPREHENSIVE
    int deductible,
    String rejectionReason   // null if covered
) {}

public record DamageAssessmentResult(
    String summary,
    int estimatedCost
) {}
```

---

## Workflow Interface: `AutoClaimWorkflow`

```java
@WorkflowInterface
public interface AutoClaimWorkflow {

    // Entry point. Receives the full FNOL as input (no Update-with-Start).
    @WorkflowMethod
    void run(AutoClaimInput input);

    // Human-in-the-loop: run() durably blocks at PENDING_APPROVAL via
    // Workflow.await(() -> adjusterApproved) until this Signal arrives.
    @SignalMethod
    void adjusterApproval(AdjusterApprovalRequest request);

    // Read-only claim state. Powers the REST GET / list endpoints.
    @QueryMethod
    AutoClaimState getClaim();
}
```

---

## Search Attributes: `ClaimSearchAttributes`

Mirrors `PolicySearchAttributes`. Both attributes are `Keyword`. `policyHolderId` is already
registered project-wide; **`policyId` is new** (see Registration below).

```java
public final class ClaimSearchAttributes {
    public static final String POLICY_ID = "policyId";
    public static final String POLICY_HOLDER_ID = "policyHolderId";

    private ClaimSearchAttributes() {}

    public static void upsertPolicyId(String policyId) {
        if (policyId != null && !policyId.isBlank()) {
            Workflow.upsertSearchAttributes(Map.of(POLICY_ID, policyId));
        }
    }

    public static void upsertPolicyHolderId(String policyHolderId) {
        if (policyHolderId != null && !policyHolderId.isBlank()) {
            Workflow.upsertSearchAttributes(Map.of(POLICY_HOLDER_ID, policyHolderId));
        }
    }
}
```

**Registration (required or workflows fail / tests hang):**
- Dev server (`mise.toml` → `temporal:dev`): add `--search-attribute policyId=Keyword`
  (alongside the existing `policyHolderId` and `policyStatus`).
- Every `TestWorkflowEnvironment` that runs the claim workflow must call
  `env.registerSearchAttribute("policyId", INDEXED_VALUE_TYPE_KEYWORD)` and the same for
  `policyHolderId`.

---

## Activities: `AutoClaimActivities`

```java
@ActivityInterface
public interface AutoClaimActivities {

    // Mock coverage check (see impl). Phase 1 of the workflow.
    @ActivityMethod
    CoverageVerificationResult verifyCoverage(String policyId, String vehicleVin);

    // Routes the claim to an available adjuster.
    @ActivityMethod
    String assignAdjuster(String claimId);

    // Runs a damage estimate (mock). Heartbeats during analysis — visible in the
    // Temporal UI. If the worker dies, the next worker resumes from the last heartbeat.
    @ActivityMethod
    DamageAssessmentResult assessDamage(String claimId, String vehicleVin);

    // Processes the approved payout. Uses claimId as an idempotency key so retries
    // after a worker crash never double-pay.
    @ActivityMethod
    String processPayment(String claimId, String policyHolderId, int amount);

    // Mock notification to the policyholder (see D6 — does not signal the PolicyWorkflow).
    @ActivityMethod
    void sendClaimClosedNotification(String claimId, String policyHolderId,
                                     String paymentReference);
}
```

### Mock implementation: `verifyCoverage` (see D3)

Self-contained and deterministic — no external I/O, no PolicyWorkflow query. Covers by
default; denies on a missing VIN or an "excluded"/"flood" incident so the demo can show the
rejection path.

```java
@Component
@ActivityImpl(taskQueues = "claim-task-queue")
public class AutoClaimActivitiesImpl implements AutoClaimActivities {

    private static final int DEFAULT_DEDUCTIBLE = 500;

    @Override
    public CoverageVerificationResult verifyCoverage(String policyId, String vehicleVin) {
        // Mock rule set: deny when the VIN is missing or the incident is excluded.
        if (vehicleVin == null || vehicleVin.isBlank()) {
            return new CoverageVerificationResult(
                false, null, 0, "No vehicle VIN on the claim");
        }
        // (Demo hook: a real impl would query coverage; here we approve collision cover.)
        return new CoverageVerificationResult(
            true, "COLLISION", DEFAULT_DEDUCTIBLE, null);
    }

    // assignAdjuster, assessDamage (heartbeating), processPayment,
    // sendClaimClosedNotification — mock implementations.
}
```

> Note: `assessDamage` must call `Activity.getExecutionContext().heartbeat(...)` periodically
> for the heartbeat/durability demo to be real.

---

## Workflow Implementation: `AutoClaimWorkflowImpl`

```java
@WorkflowImpl(taskQueues = "claim-task-queue")
public class AutoClaimWorkflowImpl implements AutoClaimWorkflow {

    private AutoClaimState state;
    private boolean adjusterApproved = false;

    // @WorkflowInit guarantees state is set before any Query (e.g. an early GET) or Signal runs.
    @WorkflowInit
    public AutoClaimWorkflowImpl(AutoClaimInput input) {
        this.state = AutoClaimState.fromInput(input);
    }

    @Override
    public void run(AutoClaimInput input) {

        // ── Visibility: filterable by policyId and policyHolderId (see D4) ──
        ClaimSearchAttributes.upsertPolicyId(input.policyId());
        ClaimSearchAttributes.upsertPolicyHolderId(input.policyHolderId());

        AutoClaimActivities activities = Workflow.newActivityStub(
            AutoClaimActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(2))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(5)
                    .setInitialInterval(Duration.ofSeconds(2))
                    .setBackoffCoefficient(2.0)
                    .build())
                .build());

        AutoClaimActivities heartbeatingActivities = Workflow.newActivityStub(
            AutoClaimActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(5))
                .setHeartbeatTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(5)
                    .setInitialInterval(Duration.ofSeconds(2))
                    .setBackoffCoefficient(2.0)
                    .build())
                .build());

        // ── Phase 1: Coverage verification (mock) ─────────────────────
        CoverageVerificationResult coverage =
            activities.verifyCoverage(state.getPolicyId(), state.getVehicleVin());

        if (!coverage.covered()) {
            state.setRejectionReason(coverage.rejectionReason());
            state.setStatus(ClaimStatus.CLOSED);
            state.setClosedAt(Workflow.currentTimeMillis());
            return; // GET observes CLOSED + rejectionReason
        }

        state.setCoverageType(coverage.coverageType());
        state.setDeductible(coverage.deductible());
        state.setStatus(ClaimStatus.COVERAGE_VERIFIED); // observable via GET

        // ── Phase 2: async processing ─────────────────────────────────
        String adjusterId = activities.assignAdjuster(state.getClaimId());
        state.setAssignedAdjusterId(adjusterId);
        state.setStatus(ClaimStatus.UNDER_REVIEW);

        DamageAssessmentResult assessment =
            heartbeatingActivities.assessDamage(state.getClaimId(), state.getVehicleVin());
        state.setDamageAssessment(assessment.summary());
        state.setEstimatedRepairCost(assessment.estimatedCost());
        state.setStatus(ClaimStatus.PENDING_APPROVAL);

        // ── Human-in-the-Loop: durable pause until the adjuster Signal ─
        Workflow.await(() -> adjusterApproved);

        // ── Payment ────────────────────────────────────────────────────
        state.setStatus(ClaimStatus.PAYMENT_PROCESSING);
        String paymentRef = activities.processPayment(
            state.getClaimId(), state.getPolicyHolderId(), state.getApprovedPayoutAmount());
        state.setPaymentReference(paymentRef);

        activities.sendClaimClosedNotification(
            state.getClaimId(), state.getPolicyHolderId(), paymentRef);

        state.setClosedAt(Workflow.currentTimeMillis());
        state.setStatus(ClaimStatus.CLOSED);
    }

    @Override
    public void adjusterApproval(AdjusterApprovalRequest request) {
        state.setApprovedByAdjusterId(request.adjusterId());
        state.setApprovedPayoutAmount(request.approvedPayoutAmount());
        state.setApprovedAt(Workflow.currentTimeMillis());
        state.setStatus(ClaimStatus.APPROVED);
        adjusterApproved = true;
    }

    @Override
    public AutoClaimState getClaim() {
        return state;
    }
}
```

---

## REST Layer

Modeled on `AutoPolicyController` + `PolicyService`. The API module is a Temporal **client
only** (no worker); the claim workflow/activities run in the `workers` module on
`claim-task-queue`.

### Endpoints

| Method | Path | Purpose | Success |
|---|---|---|---|
| `POST` | `/api/v1/claims/auto` | Submit FNOL. Generates the claim id, starts the workflow, returns immediately. | `201 Created` + `FnolResponse` |
| `GET` | `/api/v1/claims/auto/{claimId}` | Full claim state (Query). | `200 OK` + `AutoClaimState` |
| `POST` | `/api/v1/claims/auto/{claimId}/approve` | Adjuster approval (Signal). | `202 Accepted` |
| `GET` | `/api/v1/claims/auto?policyHolderId={id}&policyId={id}` | List claims, filterable by holder and/or policy (Search Attributes). | `200 OK` + `AutoClaimListResponse` |

Validation (previously the Update validator) moves to the service/controller: reject a
blank `vehicleVin` or non-positive `incidentDate` with `400` via the existing
`GlobalExceptionHandler` (`VALIDATION_FAILED`). A missing claim on GET/approve returns `404`
(`NOT_FOUND`), consistent with the policy controllers.

### Controller: `AutoClaimController`

```java
@RestController
@RequestMapping("/api/v1/claims/auto")
public class AutoClaimController {

    private final ClaimService claimService;

    public AutoClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<FnolResponse> submit(@RequestBody FnolRequest request) {
        FnolResponse response = claimService.submitAutoClaim(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{claimId}")
    public AutoClaimState get(@PathVariable String claimId) {
        return claimService.getAutoClaim(claimId);
    }

    @PostMapping("/{claimId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable String claimId, @RequestBody AdjusterApprovalRequest request) {
        claimService.approveAutoClaim(claimId, request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public AutoClaimListResponse list(
            @RequestParam(required = false) String policyHolderId,
            @RequestParam(required = false) String policyId) {
        return claimService.listClaims(policyHolderId, policyId);
    }
}
```

```java
public record AutoClaimListResponse(List<AutoClaimState> claims) {}
```

### Service: `ClaimService`

```java
@Service
public class ClaimService {

    private static final String TASK_QUEUE = TaskQueues.CLAIM_TASK_QUEUE; // "claim-task-queue"

    private final WorkflowClient workflowClient;

    public ClaimService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public static String workflowId(String claimId) {
        return "claim/auto/" + claimId;
    }

    private static String generateClaimId() {
        return "CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // The claim id exists before the Workflow starts, so intake returns immediately
    // after start — no Query polling.
    public FnolResponse submitAutoClaim(FnolRequest req) {
        String claimId = generateClaimId();
        AutoClaimInput input = new AutoClaimInput(
            claimId, req.policyId(), req.policyHolderId(),
            req.incidentDescription(), req.incidentDate(), req.incidentLocation(),
            req.vehicleVin(), req.vehicleMake(), req.vehicleModel(), req.vehicleYear());

        AutoClaimWorkflow wf = workflowClient.newWorkflowStub(
            AutoClaimWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId(workflowId(claimId))
                .build());
        WorkflowClient.start(wf::run, input);

        return new FnolResponse(
            claimId,
            ClaimStatus.SUBMITTED,
            "Your claim has been received. We will be in touch shortly.");
    }

    public AutoClaimState getAutoClaim(String claimId) {
        return workflowClient.newWorkflowStub(AutoClaimWorkflow.class, workflowId(claimId))
            .getClaim();
    }

    public void approveAutoClaim(String claimId, AdjusterApprovalRequest request) {
        workflowClient.newWorkflowStub(AutoClaimWorkflow.class, workflowId(claimId))
            .adjusterApproval(request);
    }

    // Visibility list, filterable by the policyHolderId / policyId search attributes.
    public AutoClaimListResponse listClaims(String policyHolderId, String policyId) {
        String namespace = workflowClient.getOptions().getNamespace();
        ListWorkflowExecutionsRequest request = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(namespace)
            .setQuery(buildClaimListQuery(policyHolderId, policyId))
            .build();
        ListWorkflowExecutionsResponse response = workflowClient.getWorkflowServiceStubs()
            .blockingStub().listWorkflowExecutions(request);

        List<AutoClaimState> claims = new ArrayList<>();
        for (WorkflowExecutionInfo info : response.getExecutionsList()) {
            String wfId = info.getExecution().getWorkflowId();
            claims.add(workflowClient.newWorkflowStub(AutoClaimWorkflow.class, wfId).getClaim());
        }
        return new AutoClaimListResponse(claims);
    }

    // Pure, unit-testable (the test server does not implement ListWorkflowExecutions).
    static String buildClaimListQuery(String policyHolderId, String policyId) {
        String query = "WorkflowType = '" + AutoClaimWorkflow.class.getSimpleName() + "'";
        if (policyHolderId != null && !policyHolderId.isBlank()) {
            query += " AND " + ClaimSearchAttributes.POLICY_HOLDER_ID + " = '" + policyHolderId + "'";
        }
        if (policyId != null && !policyId.isBlank()) {
            query += " AND " + ClaimSearchAttributes.POLICY_ID + " = '" + policyId + "'";
        }
        return query;
    }
}
```

> Intake is asynchronous: `submitAutoClaim` returns as soon as the Workflow is started. The
> coverage decision and all later states are read via `GET /claims/auto/{claimId}`.

---

## Task Queue & Worker Registration

- Add the constant to `TaskQueues`:
  ```java
  public static final String CLAIM_TASK_QUEUE = "claim-task-queue";
  ```
- The claim workflow/activity impls live under `com.ziggy.insurance.domains.*` so the
  `workers` module auto-discovers them (`workers-auto-discovery.packages`).
- Register a worker for the new queue in `workers/src/main/resources/application.yml`:
  ```yaml
  spring:
    temporal:
      workers:
        - task-queue: policy-task-queue
        - task-queue: claim-task-queue   # add
  ```
  (Confirm on a running worker whether auto-discovery alone creates the queue's worker or
  the explicit list is required; add it explicitly to be safe.)

---

## Workflow ID

```
claim/auto/{claimId}      e.g.  claim/auto/CLM-A1B2C3D4
```

The `claim/auto/` prefix mirrors the policy convention and, combined with the `policyId` /
`policyHolderId` search attributes, makes visibility filtering natural:

```
WorkflowType = 'AutoClaimWorkflow'                                  → all auto claims
WorkflowType = 'AutoClaimWorkflow' AND policyHolderId = '{id}'      → one holder's claims
WorkflowType = 'AutoClaimWorkflow' AND policyId = 'demo-auto-001'   → one policy's claims
```

The claim id is the same value used in the Workflow ID and returned to the caller, so a
claim is fetchable by id (`GET /api/v1/claims/auto/{claimId}`) without any extra mapping.

---

## Testing Notes

- **Unit-test `buildClaimListQuery`** directly (no server, no mocks). The time-skipping
  `TestWorkflowEnvironment` does **not** implement `ListWorkflowExecutions`, so the list
  endpoint can't be integration-tested there — verify listing against a real dev server.
- **Register search attributes** (`policyId`, `policyHolderId`) in every test env that starts
  the claim workflow, or the upsert fails and the test hangs.
- **Register the `claim-task-queue` worker** (workflow + activity impls) in each test env.
- Cover: coverage-approved happy path through `CLOSED`; coverage-denied sets `CLOSED` +
  `rejectionReason` (observed via `GET`); the adjuster Signal transitions
  `PENDING_APPROVAL → APPROVED`; intake returns the claim id immediately with `SUBMITTED`.

---

## Temporal Concept Map

| What the CTO Sees | Temporal Concept | Where in the Code |
|---|---|---|
| Claim number returned immediately at submission | Service-issued id + Workflow start | `ClaimService.submitAutoClaim` |
| Coverage decision | Mock Activity | `verifyCoverage` (`AutoClaimActivitiesImpl`) |
| Claims filterable by holder / policy | Search Attributes + Visibility | `ClaimSearchAttributes`, `buildClaimListQuery` |
| API failure — claim keeps moving | Activity Retry Policy | `RetryOptions` on the Phase 2 stubs |
| Sarah approved — workflow resumed | Signal (Human-in-the-Loop) | `adjusterApproval` + `Workflow.await(() -> adjusterApproved)` |
| Worker crash — claim resumes where it left off | Durable Execution + heartbeat | `assessDamage` with `HeartbeatTimeout` |
| Full claim history, queryable any time | Query + Event History | `getClaim()` |
```
