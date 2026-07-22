// Response body returned when a policy workflow is created.
package com.ziggy.insurance.api;

public record CreatePolicyResponse(String policyId, String workflowId) {}
