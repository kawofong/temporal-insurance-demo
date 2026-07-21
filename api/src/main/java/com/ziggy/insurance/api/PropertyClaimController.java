// REST controller for property claim operations.
// Maps HTTP endpoints to Temporal workflow start, query, and signal.
package com.ziggy.insurance.api;

import com.ziggy.insurance.domains.claim.models.AdjusterApprovalRequest;
import com.ziggy.insurance.domains.claim.models.AdjusterDenialRequest;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.property.PropertyClaimState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claims/property")
public class PropertyClaimController {

    private final PropertyClaimService claimService;

    public PropertyClaimController(PropertyClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<FnolResponse> submit(@RequestBody PropertyFnolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(claimService.submitPropertyClaim(request));
    }

    @GetMapping("/{claimId}")
    public PropertyClaimState get(@PathVariable String claimId) {
        return claimService.getPropertyClaim(claimId);
    }

    @PostMapping("/{claimId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable String claimId, @RequestBody AdjusterApprovalRequest request) {
        claimService.approvePropertyClaim(claimId, request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{claimId}/deny")
    public ResponseEntity<Void> deny(
            @PathVariable String claimId, @RequestBody AdjusterDenialRequest request) {
        claimService.denyPropertyClaim(claimId, request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{claimId}/damage-assessment")
    public ResponseEntity<Void> submitDamageAssessment(
            @PathVariable String claimId, @RequestBody DamageAssessmentResult assessment) {
        claimService.submitDamageAssessment(claimId, assessment);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public PropertyClaimListResponse list(
            @RequestParam(required = false) String policyHolderId,
            @RequestParam(required = false) String policyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String pageToken) {
        return claimService.listClaims(policyHolderId, policyId, status, pageSize, pageToken);
    }
}
