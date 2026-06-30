// REST controller for commercial policy operations.
// Maps HTTP endpoints to Temporal workflow queries, signals, and updates.
package com.example.insurance.api;

import com.example.insurance.domains.policy.commercial.CommercialPolicyInput;
import com.example.insurance.domains.policy.commercial.CommercialPolicyState;
import com.example.insurance.domains.policy.models.AdditionalInsured;
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
@RequestMapping("/api/v1/policies/commercial")
public class CommercialPolicyController {

    private final PolicyService policyService;

    public CommercialPolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<CreatePolicyResponse> create(@RequestBody CommercialPolicyInput input) {
        String workflowId = policyService.createCommercialPolicy(input);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreatePolicyResponse(input.policyId(), workflowId));
    }

    @GetMapping("/{policyId}")
    public CommercialPolicyState get(@PathVariable String policyId) {
        return policyService.getCommercialPolicy(policyId);
    }

    // --- Additional insured updates ---

    @PostMapping("/{policyId}/additional-insureds")
    public CountResponse addAdditionalInsured(
            @PathVariable String policyId, @RequestBody AdditionalInsured additionalInsured) {
        int count = policyService.addAdditionalInsured(policyId, additionalInsured);
        return new CountResponse(count);
    }

    @DeleteMapping("/{policyId}/additional-insureds/{additionalInsuredId}")
    public ResponseEntity<Void> removeAdditionalInsured(
            @PathVariable String policyId, @PathVariable String additionalInsuredId) {
        policyService.removeAdditionalInsured(policyId, additionalInsuredId);
        return ResponseEntity.ok().build();
    }

    // --- Lifecycle signals ---

    @PostMapping("/{policyId}/suspend")
    public ResponseEntity<Void> suspend(
            @PathVariable String policyId, @RequestBody ReasonRequest request) {
        policyService.suspendCommercialPolicy(policyId, request.reason());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable String policyId) {
        policyService.reactivateCommercialPolicy(policyId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable String policyId, @RequestBody ReasonRequest request) {
        policyService.cancelCommercialPolicy(policyId, request.reason());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/initiate-renewal")
    public ResponseEntity<Void> initiateRenewal(@PathVariable String policyId) {
        policyService.initiateCommercialRenewal(policyId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{policyId}/complete-renewal")
    public ResponseEntity<Void> completeRenewal(@PathVariable String policyId) {
        policyService.completeCommercialRenewal(policyId);
        return ResponseEntity.accepted().build();
    }
}
