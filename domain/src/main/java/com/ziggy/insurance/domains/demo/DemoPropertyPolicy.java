// Request record for creating a demo property policy via activity.
// Wraps PropertyPolicyInput with its deterministic workflow ID.
package com.ziggy.insurance.domains.demo;

import com.ziggy.insurance.domains.policy.property.PropertyPolicyInput;

public record DemoPropertyPolicy(
    String workflowId,
    PropertyPolicyInput input
) {}
