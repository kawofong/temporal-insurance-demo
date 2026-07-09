// CAT event workflow implementation.
// Declares a catastrophe, generates synthetic property-claim inputs, and fans them out as
// ABANDON child PropertyClaimWorkflow executions using the Batch Iterator pattern: one batch
// of BATCH_SIZE per run, then continue-as-new. This bounds each run's history so the event
// can scale to hundreds of thousands of claims without exceeding per-run history limits.
package com.ziggy.insurance.domains.cat;

import com.ziggy.insurance.domains.claim.models.DamageTier;
import com.ziggy.insurance.domains.claim.property.PropertyClaimInput;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflow;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.ParentClosePolicy;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

@WorkflowImpl(taskQueues = "claim-task-queue")
public class CATEventWorkflowImpl implements CATEventWorkflow {

    private static final int DEFAULT_BATCH_SIZE = 500;

    // Number of child claims fanned out per run before continue-as-new. Owned by the workflow
    // (deliberately not a workflow input); package-private and non-final only so tests can shrink
    // it to exercise the continue-as-new hop without fanning out hundreds of children.
    static int batchSize = DEFAULT_BATCH_SIZE;

    private static final Logger logger = Workflow.getLogger(CATEventWorkflowImpl.class);

    private CATEventState state;

    @WorkflowInit
    public CATEventWorkflowImpl(CATEventInput input) {
        this.state = CATEventState.fromInput(input);
    }

    @Override
    public void run(CATEventInput input) {
        if (input.nextClaimIndex() == 0) {
            state.setDeclaredAt(Workflow.currentTimeMillis());
        }

        if (input.nextClaimIndex() < input.totalClaimsToGenerate()) {
            state.setStatus(CATEventLifecycle.SPAWNING);

            int start = input.nextClaimIndex();
            int end = Math.min(start + batchSize, input.totalClaimsToGenerate());

            // Fire every child in the batch without blocking, collecting a start promise
            // for each. We then block only until every child has durably STARTED — never
            // until it completes. ABANDON means each claim runs to completion on its own,
            // so the parent can continue-as-new the moment the batch is safely started.
            List<Promise<WorkflowExecution>> childStarts = new ArrayList<>();
            for (int i = start; i < end; i++) {
                PropertyClaimInput claimInput = generateSyntheticClaim(input, i);
                PropertyClaimWorkflow child = Workflow.newChildWorkflowStub(
                    PropertyClaimWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                        .setWorkflowId("claim/property/" + input.catEventId() + "-" + i)
                        .setTaskQueue(TaskQueues.CLAIM_TASK_QUEUE)
                        .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
                        // Each claim index yields a unique workflow id; the default start
                        // behavior already fails on a running-duplicate id, so a re-declared
                        // batch never silently double-files a claim. (WorkflowIdConflictPolicy
                        // is a client-WorkflowOptions concept, not exposed on child options.)
                        .build());
                Async.function(child::run, claimInput);
                childStarts.add(Workflow.getWorkflowExecution(child));
            }
            // Resolves as soon as the child has started; does not wait for it to finish.
            // A start that fails (e.g. a running-duplicate workflow id) is logged and skipped
            // so one bad claim never derails the rest of the batch.
            for (Promise<WorkflowExecution> started : childStarts) {
                try {
                    started.get();
                } catch (ChildWorkflowFailure e) {
                    logger.warn("Child claim failed to start, continuing: {}", e.getMessage());
                }
            }

            state.setTotalClaimsOpened(input.totalClaimsOpened() + (end - start));

            // Batch-iterator checkpoint: hand the next offset + carried counter to a
            // fresh run. This bounds each run's history to ~batchSize child-start events.
            CATEventInput next = new CATEventInput(
                input.catEventId(), input.eventName(), input.affectedRegion(),
                input.totalClaimsToGenerate(), end, state.getTotalClaimsOpened(),
                state.getDeclaredAt());
            Workflow.continueAsNew(next);
        }

        // Reached only on the terminating run (all claims filed): the event completes.
        // The ABANDON child claims keep running independently.
        state.setStatus(CATEventLifecycle.COMPLETED);
    }

    @Override
    public CATEventStatus getCATEventStatus() {
        double pct = state.getTotalClaimsExpected() == 0
            ? 0.0
            : (double) state.getTotalClaimsOpened() / state.getTotalClaimsExpected() * 100.0;
        return new CATEventStatus(
            state.getCatEventId(), state.getEventName(), state.getAffectedRegion(),
            state.getStatus(), state.getTotalClaimsExpected(), state.getTotalClaimsOpened(),
            pct, state.getDeclaredAt());
    }

    // Deterministic synthetic claim generation — replay-safe RNG only.
    private PropertyClaimInput generateSyntheticClaim(CATEventInput input, int claimIndex) {
        DamageTier tier = randomDamageTier();
        String claimId = input.catEventId() + "-" + claimIndex;
        return new PropertyClaimInput(
            claimId,
            "policy/property/syn-" + claimIndex,
            "holder-" + Workflow.randomUUID(),
            input.catEventId(),
            tier,
            "CAT event damage — " + tier,
            Workflow.currentTimeMillis(),
            "Synthetic address in " + input.affectedRegion(),
            "SINGLE_FAMILY");
    }

    // 10% TOTAL_LOSS, 40% MAJOR_DAMAGE, 50% MINOR_DAMAGE — deterministic RNG.
    private DamageTier randomDamageTier() {
        int roll = Workflow.newRandom().nextInt(100);
        if (roll < 10) return DamageTier.TOTAL_LOSS;
        if (roll < 50) return DamageTier.MAJOR_DAMAGE;
        return DamageTier.MINOR_DAMAGE;
    }
}
