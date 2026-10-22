package com.skillmanagement.ainews.controller;

import com.skillmanagement.ainews.dto.AiNewsCommentItem;
import com.skillmanagement.ainews.dto.AiNewsCommentRequest;
import com.skillmanagement.ainews.dto.AiNewsDetail;
import com.skillmanagement.ainews.dto.AiNewsItem;
import com.skillmanagement.ainews.service.AiNewsService;
import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI News endpoints (AI_News_Screen_SRS.md §5, Home SRS §7.4).
 * Path follows the SRS contract under the /api/v1 prefix:
 *   GET    /api/v1/home/ai-news                     — list (no body)
 *   GET    /api/v1/home/ai-news/{newsId}            — detail (with body)
 *   GET    /api/v1/home/ai-news/{newsId}/comments
 *   POST   /api/v1/home/ai-news/{newsId}/comments
 *   DELETE /api/v1/home/ai-news/{newsId}/comments/{commentId}
 *   POST   /api/v1/home/ai-news/{newsId}/comments/{commentId}/helpful
 *   POST   /api/v1/home/ai-news/{newsId}/like
 */
@RestController
@RequestMapping("/api/v1/home/ai-news")
@RequiredArgsConstructor
public class AiNewsController {

    private final AiNewsService aiNewsService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiNewsItem>>> list(
            @RequestParam(defaultValue = "8") int limit) {
        // Clamp: limit <= 0 is invalid in PostgreSQL (LIMIT -1 = no limit) and
        // huge values are a DoS/over-fetch vector.
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.ok(aiNewsService.list(safeLimit)));
    }

    @GetMapping("/{newsId}")
    public ResponseEntity<ApiResponse<AiNewsDetail>> detail(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId) {
        return ResponseEntity.ok(ApiResponse.ok(aiNewsService.detail(newsId, user)));
    }

    @GetMapping("/{newsId}/comments")
    public ResponseEntity<ApiResponse<List<AiNewsCommentItem>>> comments(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId) {
        return ResponseEntity.ok(ApiResponse.ok(aiNewsService.comments(newsId, user)));
    }

    @PostMapping("/{newsId}/comments")
    public ResponseEntity<ApiResponse<AiNewsCommentItem>> addComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId,
            @RequestBody AiNewsCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(aiNewsService.addComment(newsId, user, request)));
    }

    @DeleteMapping("/{newsId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId,
            @PathVariable Long commentId) {
        aiNewsService.deleteComment(newsId, commentId, user);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{newsId}/comments/{commentId}/helpful")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> toggleHelpful(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId,
            @PathVariable Long commentId) {
        int count = aiNewsService.toggleHelpful(newsId, commentId, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("helpfulCount", count)));
    }

    @PostMapping("/{newsId}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> toggleLike(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId) {
        int count = aiNewsService.toggleLike(newsId, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("likeCount", count)));
    }
}
