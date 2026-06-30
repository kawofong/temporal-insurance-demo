// REST controller for property policy operations.
// Maps HTTP endpoints to Temporal workflow queries, signals, and updates.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.policy.models.LossPayee;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyInput;
import com.ziggy.insurance.domains.policy.property.PropertyPolicyState;
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
@RequestMapping("/api/v1/policies/property")
public class PropertyPolicyController {

    private final PolicyService policyService;

    public PropertyPolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<CreatePolicyResponse> create(@RequestBody PropertyPolicyInput input) {
        String workflowId = policyService.createPropertyPolicy(input);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreatePolicyResponse(input.policyId(), workflowId));
    }

    @GetMapping("/{policyId}")
    public PropertyPolicyState get(@PathVariable String policyId) {
        return policyService.getPropertyPolicy(policyId);
    }

    // --- Loss payee updates ---

    @PostMapping("/{policyId}/loss-payees")
    public CountResponse addLossPayee(
            @PathVariable String policyId, @RequestBody LossPayee lossPayee) {
        int count = policyService.addLossPayee(policyId, lossPayee);
        return new CountResponse(count);
    }

    @DeleteMapping("/{policyId}/loss-payees/{lossPayeeId}")
    public ResponseEntity<Void> removeLossPayee(
            @PathVariable String policyId, @PathVariable String lossPayeeId) {
        policyService.removeLossPayee(policyId, lossPayeeId);
        return ResponseEntity.ok().build();
    }

    // --- Lifecycle signals ---

    @PostMapping("/{policyId}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable String policyId, @RequestBody ReasonRequest request) {
        policyService.suspendPropertyPolicy(policyId, request.reason());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable String policyId) {
        policyService.reactivatePropertyPolicy(policyId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable String policyId, @RequestBody ReasonRequest request) {
        policyService.cancelPropertyPolicy(policyId, request.reason());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/initiate-renewal")
    public ResponseEntity<Void> initiateRenewal(@PathVariable String policyId) {
        policyService.initiatePropertyRenewal(policyId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/complete-renewal")
    public ResponseEntity<Void> completeRenewal(@PathVariable String policyId) {
        policyService.completePropertyRenewal(policyId);
        return ResponseEntity.accepted().build();
    }
}
