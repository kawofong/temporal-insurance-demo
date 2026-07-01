// REST controller for listing policies across all types.
// Aggregates running policy workflows from the Temporal server, optionally
// scoped to a single policyholder.
package com.ziggy.insurance.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyListController {

    private final PolicyService policyService;

    public PolicyListController(PolicyService policyService) {
        this.policyService = policyService;
    }

    // Lists running policies. When policyHolderId is supplied, results are limited to
    // that policyholder; otherwise every running policy is returned.
    @GetMapping
    public PolicyListResponse list(
            @RequestParam(name = "policyHolderId", required = false) String policyHolderId) {
        if (policyHolderId != null && !policyHolderId.isBlank()) {
            return policyService.listPoliciesByPolicyHolder(policyHolderId);
        }
        return policyService.listAllPolicies();
    }
}
