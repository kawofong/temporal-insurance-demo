package com.ziggy.insurance.domains.policy;

public final class TaskQueues {

    public static final String POLICY_TASK_QUEUE = "policy-task-queue";
    public static final String CLAIM_TASK_QUEUE = "claim-task-queue";

    // Task queue hosting the Python OpenAI-Agents-SDK adjuster workflows (field + claim adjuster).
    // The Java claim workflow invokes those agents as untyped child workflows on this queue.
    public static final String AI_AGENTS_TASK_QUEUE = "ai-agents-task-queue";

    private TaskQueues() {
    }
}
