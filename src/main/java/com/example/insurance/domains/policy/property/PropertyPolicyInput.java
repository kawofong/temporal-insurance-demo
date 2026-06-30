// Input parameters for starting a PropertyPolicyWorkflow.
// Contains initial policy data, insured property, and optional loss payees.
package com.example.insurance.domains.policy.property;

import com.example.insurance.domains.policy.models.InsuredProperty;
import com.example.insurance.domains.policy.models.LossPayee;
import java.util.List;

public record PropertyPolicyInput(
    String policyId,
    long effectiveDate,
    long expiryDate,
    InsuredProperty property,
    List<LossPayee> lossPayees
) {}
