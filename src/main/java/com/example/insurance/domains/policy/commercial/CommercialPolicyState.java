// Mutable workflow state for a commercial policy entity.
// Tracks lifecycle status, business name, and additional insureds.
package com.example.insurance.domains.policy.commercial;

import com.example.insurance.domains.policy.models.AdditionalInsured;
import com.example.insurance.domains.policy.models.PolicyStatus;
import java.util.ArrayList;
import java.util.List;

public class CommercialPolicyState {

    private String policyId;
    private PolicyStatus status;
    private long effectiveDate;
    private long expiryDate;
    private String businessName;
    private List<AdditionalInsured> additionalInsureds;

    public CommercialPolicyState() {}

    public static CommercialPolicyState fromInput(CommercialPolicyInput input) {
        CommercialPolicyState state = new CommercialPolicyState();
        state.policyId = input.policyId();
        state.status = PolicyStatus.ACTIVE;
        state.effectiveDate = input.effectiveDate();
        state.expiryDate = input.expiryDate();
        state.businessName = input.businessName();
        state.additionalInsureds = input.additionalInsureds() != null
            ? new ArrayList<>(input.additionalInsureds())
            : new ArrayList<>();
        return state;
    }

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }

    public PolicyStatus getStatus() { return status; }
    public void setStatus(PolicyStatus status) { this.status = status; }

    public long getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(long effectiveDate) { this.effectiveDate = effectiveDate; }

    public long getExpiryDate() { return expiryDate; }
    public void setExpiryDate(long expiryDate) { this.expiryDate = expiryDate; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public List<AdditionalInsured> getAdditionalInsureds() { return additionalInsureds; }
    public void setAdditionalInsureds(List<AdditionalInsured> additionalInsureds) { this.additionalInsureds = additionalInsureds; }
}
