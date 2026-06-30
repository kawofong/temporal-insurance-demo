// Represents the property insured under a property policy.
// propertyType values: SINGLE_FAMILY, CONDO, RENTER.
package com.example.insurance.domains.policy.models;

public record InsuredProperty(
    String propertyId,
    String address,
    String propertyType
) {}
