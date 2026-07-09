// Shared constants for the payment domain's Nexus wiring.
// The task queue hosts the Nexus service handler; the endpoint is what callers target.
package com.ziggy.insurance.domains.payment;

public final class PaymentNexus {

    // Task queue the payment worker polls and where the Nexus service handler runs.
    public static final String TASK_QUEUE = "payment-task-queue";

    // Nexus endpoint name callers route to. Must be created on the Temporal server (see AGENTS.md)
    // and, in production, point at TASK_QUEUE in this namespace.
    public static final String ENDPOINT = "payment-ep";

    private PaymentNexus() {
    }
}
