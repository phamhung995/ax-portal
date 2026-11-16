package com.furelise.skillmanagement.skillrequest.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.skillrequest.dto.BountyClaimResponse;
import com.furelise.skillmanagement.skillrequest.dto.BountyResponse;
import com.furelise.skillmanagement.skillrequest.service.BountyService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Bounty (treo thưởng) endpoints on skill requests. */
@RestController
@RequestMapping("/api/v1/skill-requests")
@RequiredArgsConstructor
public class BountyController {

    private final BountyService bountyService;

    @PostMapping("/{id}/bounty")
    public ResponseEntity<ApiResponse<BountyResponse>> postBounty(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        int points = body.getOrDefault("points", 0);
        return ResponseEntity.ok(ApiResponse.ok(bountyService.postBounty(id, user.getId(), points)));
    }

    @PostMapping("/{id}/claims")
    public ResponseEntity<ApiResponse<BountyClaimResponse>> claim(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                bountyService.claim(id, body.get("skillId"), user.getId())));
    }

    @GetMapping("/{id}/claims")
    public ResponseEntity<ApiResponse<List<BountyClaimResponse>>> claims(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(bountyService.claims(id)));
    }

    @PostMapping("/claims/{claimId}/accept")
    public ResponseEntity<ApiResponse<BountyClaimResponse>> accept(
            @AuthenticationPrincipal User user, @PathVariable Long claimId) {
        return ResponseEntity.ok(ApiResponse.ok(bountyService.acceptClaim(claimId, user.getId())));
    }

    @PostMapping("/claims/{claimId}/reject")
    public ResponseEntity<ApiResponse<BountyClaimResponse>> reject(
            @AuthenticationPrincipal User user, @PathVariable Long claimId) {
        return ResponseEntity.ok(ApiResponse.ok(bountyService.rejectClaim(claimId, user.getId())));
    }

    @PostMapping("/claims/{claimId}/dispute")
    public ResponseEntity<ApiResponse<BountyClaimResponse>> dispute(
            @AuthenticationPrincipal User user, @PathVariable Long claimId) {
        return ResponseEntity.ok(ApiResponse.ok(bountyService.disputeClaim(claimId, user.getId())));
    }
}
