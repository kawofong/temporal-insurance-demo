// Java-side mirror of the Python agents' PropertyClaimInput Pydantic model.
// The agents run on a different SDK (temporalio Python) and task queue, so the claim workflow
// invokes them as untyped child workflows and must speak their exact snake_case wire format.
// The @JsonProperty annotations pin each field name so Jackson emits/consumes the same keys the
// Pydantic models use; a serialization contract test (§9) guards against drift.
package com.ziggy.insurance.domains.claim.property.agents;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentPropertyClaim(
    @JsonProperty("claim_id") String claimId,
    @JsonProperty("policy_id") String policyId,
    @JsonProperty("policy_holder_id") String policyHolderId,
    @JsonProperty("cat_event_id") String catEventId,
    @JsonProperty("damage_tier") String damageTier,
    @JsonProperty("incident_description") String incidentDescription,
    @JsonProperty("incident_date") long incidentDate,
    @JsonProperty("property_address") String propertyAddress,
    @JsonProperty("property_type") String propertyType) {}
