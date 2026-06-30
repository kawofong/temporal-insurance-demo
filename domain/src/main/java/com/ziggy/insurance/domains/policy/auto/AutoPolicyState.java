// Mutable workflow state for an auto policy entity.
// Tracks lifecycle status, insured vehicles, and listed drivers.
package com.ziggy.insurance.domains.policy.auto;

import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import java.util.ArrayList;
import java.util.List;

public class AutoPolicyState {

    private String policyId;
    private String policyHolderId;
    private PolicyStatus status;
    private long effectiveDate;
    private long expiryDate;
    private List<Vehicle> insuredVehicles;
    private List<Driver> listedDrivers;

    public AutoPolicyState() {}

    public static AutoPolicyState fromInput(AutoPolicyInput input) {
        AutoPolicyState state = new AutoPolicyState();
        state.policyId = input.policyId();
        state.policyHolderId = input.policyHolderId();
        state.status = PolicyStatus.ACTIVE;
        state.effectiveDate = input.effectiveDate();
        state.expiryDate = input.expiryDate();
        state.insuredVehicles = input.insuredVehicles() != null
            ? new ArrayList<>(input.insuredVehicles())
            : new ArrayList<>();
        state.listedDrivers = input.listedDrivers() != null
            ? new ArrayList<>(input.listedDrivers())
            : new ArrayList<>();
        return state;
    }

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }

    public String getPolicyHolderId() { return policyHolderId; }
    public void setPolicyHolderId(String policyHolderId) { this.policyHolderId = policyHolderId; }

    public PolicyStatus getStatus() { return status; }
    public void setStatus(PolicyStatus status) { this.status = status; }

    public long getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(long effectiveDate) { this.effectiveDate = effectiveDate; }

    public long getExpiryDate() { return expiryDate; }
    public void setExpiryDate(long expiryDate) { this.expiryDate = expiryDate; }

    public List<Vehicle> getInsuredVehicles() { return insuredVehicles; }
    public void setInsuredVehicles(List<Vehicle> insuredVehicles) { this.insuredVehicles = insuredVehicles; }

    public List<Driver> getListedDrivers() { return listedDrivers; }
    public void setListedDrivers(List<Driver> listedDrivers) { this.listedDrivers = listedDrivers; }
}
