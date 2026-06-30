// Represents a vehicle insured under an auto policy.
// Identified by vehicleId; VIN is used for duplicate detection.
package com.ziggy.insurance.domains.policy.models;

public record Vehicle(
    String vehicleId,
    String vin,
    String make,
    String model,
    int year
) {}
