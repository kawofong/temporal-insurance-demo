// Input parameters for starting an AutoPolicyWorkflow.
// Contains initial policy data and optional pre-existing vehicles/drivers.
package com.ziggy.insurance.domains.policy.auto;

import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import java.util.List;

public record AutoPolicyInput(
    String policyId,
    long effectiveDate,
    long expiryDate,
    List<Vehicle> insuredVehicles,
    List<Driver> listedDrivers
) {}
