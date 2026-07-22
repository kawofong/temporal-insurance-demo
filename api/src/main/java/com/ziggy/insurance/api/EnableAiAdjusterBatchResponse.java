// Response for a batch enableAiAdjuster request: the Temporal batch job id, which can be
// inspected via `temporal batch describe --job-id <jobId>`.
package com.ziggy.insurance.api;

public record EnableAiAdjusterBatchResponse(String jobId) {}
