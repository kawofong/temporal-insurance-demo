// Represents a loss payee (typically a lender) on a property policy.
// Identified by lossPayeeId.
package com.example.insurance.domains.policy.models;

public record LossPayee(
    String lossPayeeId,
    String name,
    String loanNumber
) {}
