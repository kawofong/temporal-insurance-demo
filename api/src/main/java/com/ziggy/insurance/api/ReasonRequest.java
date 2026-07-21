// Request body for lifecycle operations that require a reason (suspend, cancel).
package com.ziggy.insurance.api;

public record ReasonRequest(String reason) {}
