package com.ziggy.insurance.domains.policy;

public final class TaskQueues {

    public static final String POLICY_TASK_QUEUE = "policy-task-queue";
    public static final String CLAIM_TASK_QUEUE = "claim-task-queue";

    // Dedicated queue for the CAT event workflow and its claim-fan-out activities, isolating
    // the mass-fan-out orchestration from routine claim processing on CLAIM_TASK_QUEUE.
    public static final String CAT_TASK_QUEUE = "cat-task-queue";

    // Task queue hosting the Python OpenAI-Agents-SDK adjuster workflows (field + claim adjuster).
    // The Java claim workflow invokes those agents as untyped child workflows on this queue.
    public static final String AI_AGENTS_TASK_QUEUE = "ai-agents-task-queue";

    private TaskQueues() {
    }
}
