// Response body for the list-all-policies endpoint.
// Groups policies by type: auto, property, and commercial.
package com.example.insurance.api;

import com.example.insurance.domains.policy.auto.AutoPolicyState;
import com.example.insurance.domains.policy.commercial.CommercialPolicyState;
import com.example.insurance.domains.policy.property.PropertyPolicyState;
import java.util.List;

public record PolicyListResponse(
    List<AutoPolicyState> auto,
    List<PropertyPolicyState> property,
    List<CommercialPolicyState> commercial
) {}
