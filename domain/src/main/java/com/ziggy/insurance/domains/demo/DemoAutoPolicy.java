// Request record for creating a demo auto policy via activity.
// Wraps AutoPolicyInput with its deterministic workflow ID.
package com.ziggy.insurance.domains.demo;

import com.ziggy.insurance.domains.policy.auto.AutoPolicyInput;

public record DemoAutoPolicy(
    String workflowId,
    AutoPolicyInput input
) {}
