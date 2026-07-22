// CAT event workflow implementation.
// Declares a catastrophe and fans out its synthetic property claims by firing up to
// maxConcurrentActivities Activities concurrently within a single run, each Activity starting
// its own slice of PropertyClaimWorkflow executions directly via WorkflowClient. Bounded by
// CATEventLimits.MAX_CLAIMS_PER_EVENT, the whole fan-out fits in one run's history — no
// continue-as-new needed.
package com.ziggy.insurance.domains.cat;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

@WorkflowImpl(taskQueues = "cat-task-queue")
public class CATEventWorkflowImpl implements CATEventWorkflow {

    private static final int DEFAULT_MAX_CONCURRENT_ACTIVITIES = 1000;

    // Upper bound on concurrent claim-filing shards. Owned by the workflow (deliberately not a
    // workflow input); package-private and non-final only so tests can shrink it to exercise
    // multi-shard fan-out without spawning hundreds of real workflows.
    static int maxConcurrentActivities = DEFAULT_MAX_CONCURRENT_ACTIVITIES;

    private static final Logger logger = Workflow.getLogger(CATEventWorkflowImpl.class);

    // Set max interval to 2 secs for fast retries.
    private final CATEventActivities activities = Workflow.newActivityStub(
        CATEventActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumInterval(Duration.ofSeconds(2))
                .setInitialInterval(Duration.ofSeconds(1))
                .build())
            .build());

    private CATEventState state;

    @WorkflowInit
    public CATEventWorkflowImpl(CATEventInput input) {
        this.state = CATEventState.fromInput(input);
    }

    @Override
    public void run(CATEventInput input) {
        if (input.totalClaimsToGenerate() > CATEventLimits.MAX_CLAIMS_PER_EVENT) {
            throw ApplicationFailure.newNonRetryableFailure(
                "totalClaimsToGenerate " + input.totalClaimsToGenerate()
                    + " exceeds the per-event cap of " + CATEventLimits.MAX_CLAIMS_PER_EVENT,
                "CATEventLimitExceeded");
        }

        state.setDeclaredAt(Workflow.currentTimeMillis());
        state.setStatus(CATEventLifecycle.SPAWNING);

        int total = input.totalClaimsToGenerate();
        int shardCount = Math.min(maxConcurrentActivities, total);
        int shardSize = Math.ceilDiv(total, shardCount);

        List<Promise<Integer>> shardCompletions = new ArrayList<>();
        for (int s = 0; s < shardCount; s++) {
            int start = s * shardSize;
            int end = Math.min(start + shardSize, total);
            CATEventShardInput shardInput =
                new CATEventShardInput(input.catEventId(), input.affectedRegion(), start, end);

            Promise<Integer> shardResult = Async.function(activities::fileClaimShard, shardInput);
            // Progress climbs incrementally as each shard finishes, not in one jump at the end.
            shardCompletions.add(shardResult.thenApply(count -> {
                state.setTotalClaimsOpened(state.getTotalClaimsOpened() + count);
                return count;
            }));
        }

        // A shard that ultimately fails (after Temporal's default activity retries are
        // exhausted) is logged and skipped so one bad shard never derails the rest of the CAT
        // event — mirroring the old per-claim resilience intent, now at shard granularity.
        for (Promise<Integer> shardCompletion : shardCompletions) {
            try {
                shardCompletion.get();
            } catch (ActivityFailure e) {
                logger.warn("Claim shard failed to file its claims, continuing: {}", e.getMessage());
            }
        }

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
}
