// REST controller for listing all policies across all types.
// Aggregates running policy workflows from the Temporal server.
package com.example.insurance.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyListController {

    private final PolicyService policyService;

    public PolicyListController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public PolicyListResponse list() {
        return policyService.listAllPolicies();
    }
}
