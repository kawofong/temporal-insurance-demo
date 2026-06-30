// Response body returned when a policy workflow is created.
// Contains the policy ID and the Temporal workflow ID.
package com.ziggy.insurance.api;

public record CreatePolicyResponse(String policyId, String workflowId) {}
