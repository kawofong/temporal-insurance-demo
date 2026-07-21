// Response body for the list-all-policies endpoint.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.policy.auto.AutoPolicyState;
import com.ziggy.insurance.domains.policy.commercial.CommercialPolicyState;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyState;
import java.util.List;

public record PolicyListResponse(
    List<AutoPolicyState> auto,
    List<PropertyPolicyState> property,
    List<CommercialPolicyState> commercial
) {}
