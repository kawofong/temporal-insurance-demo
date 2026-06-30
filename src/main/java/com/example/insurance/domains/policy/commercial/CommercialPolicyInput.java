// Input parameters for starting a CommercialPolicyWorkflow.
// Contains initial policy data, business name, and optional additional insureds.
package com.example.insurance.domains.policy.commercial;

import com.example.insurance.domains.policy.models.AdditionalInsured;
import java.util.List;

public record CommercialPolicyInput(
    String policyId,
    long effectiveDate,
    long expiryDate,
    String businessName,
    List<AdditionalInsured> additionalInsureds
) {}
