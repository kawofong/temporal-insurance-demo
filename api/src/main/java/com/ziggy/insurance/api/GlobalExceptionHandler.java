// Translates Temporal client exceptions into appropriate HTTP error responses.
// Handles workflow-not-found, update validation failures, and unexpected errors.
package com.ziggy.insurance.api;

import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowUpdateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WorkflowNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", "Policy workflow not found"));
    }

    @ExceptionHandler(WorkflowUpdateException.class)
    public ResponseEntity<ErrorResponse> handleUpdateFailure(WorkflowUpdateException ex) {
        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_FAILED", message));
    }
}
