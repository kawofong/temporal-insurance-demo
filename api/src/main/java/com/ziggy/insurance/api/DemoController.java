// REST controller for demo environment setup operations.
// Maps HTTP endpoint to the SetupDemoEnvironmentWorkflow.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.demo.DemoSetupResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final PolicyService policyService;

    public DemoController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping("/setup")
    public ResponseEntity<DemoSetupResult> setup() {
        DemoSetupResult result = policyService.setupDemoEnvironment();
        return ResponseEntity.ok(result);
    }
}
