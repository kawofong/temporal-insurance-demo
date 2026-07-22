// Activity implementation for CAT event claim fan-out.
// Uses WorkflowClient to start PropertyClaimWorkflow executions directly (no parent-child
// relationship), catching duplicates for idempotency across activity retries.
package com.ziggy.insurance.domains.cat;

import com.ziggy.insurance.domains.claim.models.DamageTier;
import com.ziggy.insurance.domains.claim.property.PropertyClaimInput;
import com.ziggy.insurance.domains.claim.property.PropertyClaimWorkflow;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.Priority;
import io.temporal.spring.boot.ActivityImpl;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = "cat-task-queue")
public class CATEventActivitiesImpl implements CATEventActivities {

    // Property claims run at Temporal's default priority key (3, the middle of the [1, 5]
    // range) so they sit below higher-priority work on the claim task queue they run on.
    private static final int CLAIM_PRIORITY_KEY = 3;

    private final WorkflowClient workflowClient;

    public CATEventActivitiesImpl(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public int fileClaimShard(CATEventShardInput input) {
        int started = 0;
        for (int i = input.startIndex(); i < input.endIndex(); i++) {
            PropertyClaimInput claimInput = generateSyntheticClaim(input, i);
            PropertyClaimWorkflow wf = workflowClient.newWorkflowStub(
                PropertyClaimWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TaskQueues.CLAIM_TASK_QUEUE)
                    .setWorkflowId("claim/property/" + input.catEventId() + "-" + i)
                    .setPriority(Priority.newBuilder()
                        .setPriorityKey(CLAIM_PRIORITY_KEY)
                        .build())
                    .build());
            try {
                WorkflowClient.start(wf::run, claimInput);
            } catch (WorkflowExecutionAlreadyStarted e) {
                // Already filed by a prior (retried) attempt of this shard; idempotent no-op.
            }
            started++;
        }
        return started;
    }

    // Plain-Java generation — runs once in an Activity (not replayed), so Workflow.* APIs
    // (which require workflow-thread context) cannot be used here.
    private PropertyClaimInput generateSyntheticClaim(CATEventShardInput input, int claimIndex) {
        DamageTier tier = randomDamageTier();
        String claimId = input.catEventId() + "-" + claimIndex;
        return new PropertyClaimInput(
            claimId,
            "policy/property/syn-" + claimIndex,
            "holder-" + UUID.randomUUID(),
            input.catEventId(),
            tier,
            "CAT event damage — " + tier,
            System.currentTimeMillis(),
            "Synthetic address in " + input.affectedRegion(),
            "SINGLE_FAMILY");
    }

    // 10% TOTAL_LOSS, 40% MAJOR_DAMAGE, 50% MINOR_DAMAGE.
    private DamageTier randomDamageTier() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 10) return DamageTier.TOTAL_LOSS;
        if (roll < 50) return DamageTier.MAJOR_DAMAGE;
        return DamageTier.MINOR_DAMAGE;
    }
}
