// Represents a driver listed on an auto policy.
// Identified by driverId.
package com.example.insurance.domains.policy.models;

public record Driver(
    String driverId,
    String name,
    String licenseNumber
) {}
