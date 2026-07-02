// Response body for the list-claims endpoint.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.auto.AutoClaimState;
import java.util.List;

public record AutoClaimListResponse(List<AutoClaimState> claims) {}
