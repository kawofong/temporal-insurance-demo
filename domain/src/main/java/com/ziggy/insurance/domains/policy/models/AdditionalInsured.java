// Represents an additional insured party on a commercial policy.
// relationship values: LANDLORD, CLIENT, CONTRACTOR.
package com.ziggy.insurance.domains.policy.models;

public record AdditionalInsured(
    String additionalInsuredId,
    String name,
    String relationship
) {}
