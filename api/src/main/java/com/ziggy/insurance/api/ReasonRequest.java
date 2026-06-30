// Request body for lifecycle operations that require a reason (suspend, cancel).
// The reason field describes why the operation is being performed.
package com.ziggy.insurance.api;

public record ReasonRequest(String reason) {}
