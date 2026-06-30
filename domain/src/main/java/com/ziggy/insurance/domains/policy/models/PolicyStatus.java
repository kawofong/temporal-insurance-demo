// Lifecycle states shared by all policy workflow types.
// CANCELLED is the terminal state that causes workflow completion.
package com.ziggy.insurance.domains.policy.models;

public enum PolicyStatus {
    ACTIVE,
    SUSPENDED,
    RENEWAL_PENDING,
    CANCELLED
}
