// Standard error response body returned by all API error handlers.
// Contains an error code string and a human-readable message.
package com.ziggy.insurance.api;

public record ErrorResponse(String error, String message) {}
