// Mutable workflow state for a property policy entity.
// Tracks lifecycle status, insured property, and loss payees.
package com.ziggy.insurance.domains.policy.property;

import com.ziggy.insurance.domains.policy.models.InsuredProperty;
import com.ziggy.insurance.domains.policy.models.LossPayee;
import com.ziggy.insurance.domains.policy.models.PolicyStatus;
import java.util.ArrayList;
import java.util.List;

public class PropertyPolicyState {

    private String policyId;
    private PolicyStatus status;
    private long effectiveDate;
    private long expiryDate;
    private InsuredProperty property;
    private List<LossPayee> lossPayees;

    public PropertyPolicyState() {}

    public static PropertyPolicyState fromInput(PropertyPolicyInput input) {
        PropertyPolicyState state = new PropertyPolicyState();
        state.policyId = input.policyId();
        state.status = PolicyStatus.ACTIVE;
        state.effectiveDate = input.effectiveDate();
        state.expiryDate = input.expiryDate();
        state.property = input.property();
        state.lossPayees = input.lossPayees() != null
            ? new ArrayList<>(input.lossPayees())
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

    public InsuredProperty getProperty() { return property; }
    public void setProperty(InsuredProperty property) { this.property = property; }

    public List<LossPayee> getLossPayees() { return lossPayees; }
    public void setLossPayees(List<LossPayee> lossPayees) { this.lossPayees = lossPayees; }
}
