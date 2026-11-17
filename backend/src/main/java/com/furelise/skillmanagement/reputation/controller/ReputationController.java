package com.furelise.skillmanagement.reputation.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.reputation.dto.ContributorResponse;
import com.furelise.skillmanagement.reputation.dto.ReputationExplainResponse;
import com.furelise.skillmanagement.reputation.dto.ReputationResponse;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Read-only reputation API. Scores are recomputed by the services that own
 * the signals (rating, download, bounty); this controller only reads the cache.
 */
@RestController
@RequestMapping("/api/v1/reputation")
@RequiredArgsConstructor
public class ReputationController {

    private static final Set<String> TARGET_TYPES = Set.of(
            "SKILL", "MCP", "AI_TOOL", "AI_NEWS", "SKILL_REQUEST");

    private final ReputationService reputationService;

    @GetMapping("/{targetType}/{targetId}")
    public ResponseEntity<ApiResponse<ReputationResponse>> get(
            @PathVariable String targetType, @PathVariable Long targetId) {
        return ResponseEntity.ok(ApiResponse.ok(
                reputationService.getReputation(validated(targetType), targetId)));
    }

    @GetMapping("/{targetType}/{targetId}/explain")
    public ResponseEntity<ApiResponse<ReputationExplainResponse>> explain(
            @PathVariable String targetType, @PathVariable Long targetId) {
        return ResponseEntity.ok(ApiResponse.ok(
                reputationService.explain(validated(targetType), targetId)));
    }

    /**
     * Contributor (author) reputation (GĐ5, BA §8.5). Dedicated endpoint
     * because contributors live in contributor_reputation, not in the
     * (target_type, target_id) score cache — the generic handler cannot
     * serve them.
     */
    @GetMapping("/CONTRIBUTOR/{userId}")
    public ResponseEntity<ApiResponse<ContributorResponse>> contributor(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(reputationService.getContributor(userId)));
    }

    private String validated(String targetType) {
        String upper = targetType.toUpperCase();
        if (!TARGET_TYPES.contains(upper)) {
            throw new IllegalArgumentException("Unknown target type: " + targetType);
        }
        return upper;
    }
}
