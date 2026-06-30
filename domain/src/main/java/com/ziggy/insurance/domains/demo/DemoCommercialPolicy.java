// Request record for creating a demo commercial policy via activity.
// Wraps CommercialPolicyInput with its deterministic workflow ID.
package com.ziggy.insurance.domains.demo;

import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyInput;

public record DemoCommercialPolicy(
    String workflowId,
    CommercialPolicyInput input
) {}
