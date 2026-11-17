package com.furelise.skillmanagement.ainews.controller;

import com.furelise.skillmanagement.ainews.dto.AiNewsCommentItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsCommentRequest;
import com.furelise.skillmanagement.ainews.dto.AiNewsDetail;
import com.furelise.skillmanagement.ainews.dto.AiNewsItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsSearchCriteria;
import com.furelise.skillmanagement.ainews.dto.MentionUserItem;
import com.furelise.skillmanagement.ainews.dto.RecordViewResponse;
import com.furelise.skillmanagement.ainews.dto.ToggleHelpfulResponse;
import com.furelise.skillmanagement.ainews.dto.ToggleLikeResponse;
import com.furelise.skillmanagement.ainews.service.AiNewsCommentService;
import com.furelise.skillmanagement.ainews.service.AiNewsService;
import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * AI News endpoints (AI_News_Screen_SRS.md §5, Home SRS §7.4).
 * Path follows the SRS contract under the /api/v1 prefix:
 *   GET    /api/v1/home/ai-news                     — list (no body)
 *        optional search params: title, source, sender, from, to (ISO dates)
 *   GET    /api/v1/home/ai-news/{newsId}            — detail (with body)
 *   GET    /api/v1/home/ai-news/mention-users?q=    — @mention autocomplete
 *   GET    /api/v1/home/ai-news/{newsId}/comments
 *   POST   /api/v1/home/ai-news/{newsId}/comments
 *   DELETE /api/v1/home/ai-news/{newsId}/comments/{commentId}
 *   POST   /api/v1/home/ai-news/{newsId}/comments/{commentId}/helpful
 *   POST   /api/v1/home/ai-news/{newsId}/like
 *   POST   /api/v1/home/ai-news/{newsId}/view
 */
@RestController
@RequestMapping("/api/v1/home/ai-news")
@RequiredArgsConstructor
public class AiNewsController {

    private final AiNewsService aiNewsService;
    private final AiNewsCommentService aiNewsCommentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiNewsItem>>> list(
            @RequestParam(defaultValue = "8") int limit,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String sortBy) {
        // Clamp: limit <= 0 is invalid in PostgreSQL (LIMIT -1 = no limit) and
        // huge values are a DoS/over-fetch vector.
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        // No filter params → identical behavior to the plain limit-only list.
        if (title == null && source == null && sender == null && from == null && to == null
                && sortBy == null) {
            return ResponseEntity.ok(ApiResponse.ok(aiNewsService.list(safeLimit)));
        }
        return ResponseEntity.ok(ApiResponse.ok(aiNewsService.search(
                new AiNewsSearchCriteria(title, source, sender, from, to, sortBy), safeLimit)));
    }

    /** @mention autocomplete (authenticated, non-admin). */
    @GetMapping("/mention-users")
    public ResponseEntity<ApiResponse<List<MentionUserItem>>> mentionUsers(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(aiNewsService.mentionUsers(q)));
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
        return ResponseEntity.ok(ApiResponse.ok(aiNewsCommentService.comments(newsId, user)));
    }

    @PostMapping("/{newsId}/comments")
    public ResponseEntity<ApiResponse<AiNewsCommentItem>> addComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId,
            @RequestBody AiNewsCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(aiNewsCommentService.addComment(newsId, user, request)));
    }

    @DeleteMapping("/{newsId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId,
            @PathVariable Long commentId) {
        aiNewsCommentService.deleteComment(newsId, commentId, user);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{newsId}/comments/{commentId}/helpful")
    public ResponseEntity<ApiResponse<ToggleHelpfulResponse>> toggleHelpful(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId,
            @PathVariable Long commentId) {
        int count = aiNewsCommentService.toggleHelpful(newsId, commentId, user);
        return ResponseEntity.ok(ApiResponse.ok(new ToggleHelpfulResponse(count)));
    }

    @PostMapping("/{newsId}/like")
    public ResponseEntity<ApiResponse<ToggleLikeResponse>> toggleLike(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId) {
        int count = aiNewsService.toggleLike(newsId, user);
        return ResponseEntity.ok(ApiResponse.ok(new ToggleLikeResponse(count)));
    }

    /**
     * Record a news view (idempotent per user — the frontend fires this
     * after 30s of reading the detail). Returns the distinct-viewer count.
     */
    @PostMapping("/{newsId}/view")
    public ResponseEntity<ApiResponse<RecordViewResponse>> recordView(
            @AuthenticationPrincipal User user,
            @PathVariable Long newsId) {
        int count = aiNewsService.recordView(newsId, user);
        return ResponseEntity.ok(ApiResponse.ok(new RecordViewResponse(count)));
    }
}
