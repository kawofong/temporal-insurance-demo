// REST controller for auto policy operations.
// Maps HTTP endpoints to Temporal workflow queries, signals, and updates.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.policy.auto.AutoPolicyInput;
import com.ziggy.insurance.domains.policy.auto.AutoPolicyState;
import com.ziggy.insurance.domains.policy.models.Driver;
import com.ziggy.insurance.domains.policy.models.Vehicle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies/auto")
public class AutoPolicyController {

    private final PolicyService policyService;

    public AutoPolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<CreatePolicyResponse> create(@RequestBody AutoPolicyInput input) {
        String workflowId = policyService.createAutoPolicy(input);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreatePolicyResponse(input.policyId(), workflowId));
    }

    @GetMapping("/{policyId}")
    public AutoPolicyState get(@PathVariable String policyId) {
        return policyService.getAutoPolicy(policyId);
    }

    // --- Vehicle updates ---

    @PostMapping("/{policyId}/vehicles")
    public CountResponse addVehicle(@PathVariable String policyId, @RequestBody Vehicle vehicle) {
        int count = policyService.addVehicle(policyId, vehicle);
        return new CountResponse(count);
    }

    @DeleteMapping("/{policyId}/vehicles/{vehicleId}")
    public ResponseEntity<Void> removeVehicle(
            @PathVariable String policyId, @PathVariable String vehicleId) {
        policyService.removeVehicle(policyId, vehicleId);
        return ResponseEntity.ok().build();
    }

    // --- Driver signals ---

    @PostMapping("/{policyId}/drivers")
    public ResponseEntity<Void> addDriver(
            @PathVariable String policyId, @RequestBody Driver driver) {
        policyService.addDriver(policyId, driver);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{policyId}/drivers/{driverId}")
    public ResponseEntity<Void> removeDriver(
            @PathVariable String policyId, @PathVariable String driverId) {
        policyService.removeDriver(policyId, driverId);
        return ResponseEntity.accepted().build();
    }

    // --- Lifecycle signals ---

    @PostMapping("/{policyId}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable String policyId, @RequestBody ReasonRequest request) {
        policyService.suspendAutoPolicy(policyId, request.reason());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable String policyId) {
        policyService.reactivateAutoPolicy(policyId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable String policyId, @RequestBody ReasonRequest request) {
        policyService.cancelAutoPolicy(policyId, request.reason());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/initiate-renewal")
    public ResponseEntity<Void> initiateRenewal(@PathVariable String policyId) {
        policyService.initiateAutoRenewal(policyId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/complete-renewal")
    public ResponseEntity<Void> completeRenewal(@PathVariable String policyId) {
        policyService.completeAutoRenewal(policyId);
        return ResponseEntity.accepted().build();
    }
}
