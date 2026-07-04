// Response body for the list-property-claims endpoint. Mirrors AutoClaimListResponse.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import java.util.List;

public record PropertyClaimListResponse(List<PropertyClaimState> claims) {}
