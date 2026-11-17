package com.furelise.skillmanagement.skillrequest.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.skillrequest.dto.MentionUserItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCommentPage;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCommentRequest;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCreateRequest;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestFeedItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestFeedResult;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestSuggestions;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestStatsResult;
import com.furelise.skillmanagement.skillrequest.model.SkillRequestFile;
import com.furelise.skillmanagement.skillrequest.service.SkillRequestFileStorageService;
import com.furelise.skillmanagement.skillrequest.service.SkillRequestService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Skill Request Management (feed) endpoints.
 * All requests are visible to every authenticated user.
 */
@RestController
@RequestMapping("/api/v1/skill-requests")
@RequiredArgsConstructor
public class SkillRequestController {

    private final SkillRequestService skillRequestService;
    private final SkillRequestFileStorageService skillRequestFileStorageService;

    /**
     * Feed of requests ranked by (vote*2 + comment) * time decay.
     * Cursor-based paging: pass nextCursor from the previous page.
     * sortBy=reputation sorts best-first by cached reputation score.
     */
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<SkillRequestFeedResult>> feed(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "mine", defaultValue = "false") boolean mine,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sortBy", required = false) String sortBy) {
        return ResponseEntity.ok(ApiResponse.ok(
                skillRequestService.feed(cursor, limit, mine, title, sortBy, user)));
    }

    /**
     * The {@code limit} most recently created requests (newest first) —
     * used by the Home "New Request" card.
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<SkillRequestFeedItem>>> recent(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(skillRequestService.recent(limit, user)));
    }

    /**
     * Create a request (multipart: title, description, hashtags JSON, file[]).
     * Public immediately, posted to the feed.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal User user,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "file", required = false) MultipartFile[] files) {
        List<String> hashtagList = parseHashtags(hashtags);
        Long id = skillRequestService.create(new SkillRequestCreateRequest(title, description, hashtagList),
                files, user);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    /** Parses the hashtags JSON array form field (blank/absent = no hashtags). */
    private List<String> parseHashtags(String hashtags) {
        if (hashtags == null || hashtags.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(hashtags, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
            return list.stream().filter(java.util.Objects::nonNull).toList();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.furelise.skillmanagement.exception.BusinessException(
                    com.furelise.skillmanagement.exception.BusinessErrorCode.SKILL_REQUEST_HASHTAG_INVALID,
                    "Hashtags must be a JSON array of strings.");
        }
    }

    /** Detail of one request (same shape as a feed item). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillRequestFeedItem>> detail(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(skillRequestService.detail(id, user)));
    }

    /** Updates a request (creator only). Multipart: title, description, hashtags,
     *  optional new file[], optional removeFileIds[] (existing files to delete). */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "file", required = false) MultipartFile[] files,
            @RequestParam(value = "removeFileIds", required = false) List<Long> removeFileIds) {
        List<String> hashtagList = parseHashtags(hashtags);
        Long updatedId = skillRequestService.update(id,
                new SkillRequestCreateRequest(title, description, hashtagList), files, removeFileIds, user);
        return ResponseEntity.ok(ApiResponse.ok(updatedId));
    }

    /** Deletes a request and all related data (creator only). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        skillRequestService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    /** Vote for a request (409 when already voted). */
    @PostMapping("/{id}/vote")
    public ResponseEntity<Void> vote(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        skillRequestService.vote(id, user);
        return ResponseEntity.status(201).build();
    }

    /** Remove the viewer's vote (404 when not voted). */
    @DeleteMapping("/{id}/vote")
    public ResponseEntity<Void> unvote(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        skillRequestService.unvote(id, user);
        return ResponseEntity.noContent().build();
    }

    // ── Fulfillment (GĐ5, Q8) ──────────────────────────────────────────────
    /** Proposes an APPROVED skill as the answer; requester has 7 days to decide. */
    @PostMapping("/{id}/propose-fulfillment")
    public ResponseEntity<Void> proposeFulfillment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        skillRequestService.proposeFulfillment(id, body.get("skillId"), user);
        return ResponseEntity.status(201).build();
    }

    /** Requester decision on a pending fulfillment: { "accept": true/false }. */
    @PostMapping("/{id}/fulfillment-decision")
    public ResponseEntity<Void> fulfillmentDecision(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        skillRequestService.fulfillmentDecision(id, Boolean.TRUE.equals(body.get("accept")), user);
        return ResponseEntity.ok().build();
    }

    /**
     * @mention autocomplete: up to 10 enabled users matching the query
     * (case-insensitive name/email contains).
     */
    @GetMapping("/mention-users")
    public ResponseEntity<ApiResponse<List<MentionUserItem>>> mentionUsers(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "q", required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(skillRequestService.mentionUsers(q)));
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<SkillRequestCommentPage>> comments(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(skillRequestService.listComments(id, user, page, size)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Void> addComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody SkillRequestCommentRequest request) {
        skillRequestService.addComment(id, user, request.content(), request.linkedSkillId(),
                request.parentId());
        return ResponseEntity.status(201).build();
    }

    /** Likes a comment (409 when already liked). */
    @PostMapping("/{id}/comments/{commentId}/like")
    public ResponseEntity<Void> likeComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long commentId) {
        skillRequestService.likeComment(id, commentId, user);
        return ResponseEntity.status(201).build();
    }

    /** Removes the viewer's like (404 when not liked). */
    @DeleteMapping("/{id}/comments/{commentId}/like")
    public ResponseEntity<Void> unlikeComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long commentId) {
        skillRequestService.unlikeComment(id, commentId, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long commentId) {
        skillRequestService.deleteComment(id, commentId, user);
        return ResponseEntity.noContent().build();
    }

    /** Updates a comment's content (comment creator only). */
    @PutMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> updateComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long commentId,
            @RequestBody Map<String, String> body) {
        skillRequestService.updateComment(id, commentId, body.get("content"), user);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mark a skill-linked comment as resolved.
     * Only the request creator (or an admin) may resolve; idempotent.
     */
    @PutMapping("/{id}/comments/{commentId}/resolve")
    public ResponseEntity<Void> resolveComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long commentId) {
        skillRequestService.resolveComment(id, commentId, user);
        return ResponseEntity.noContent().build();
    }

    // ── Duplicate suggestions (option A) ─────────────────────────────────────
    /** Similar skills + requests for the keyword (hint box in the create popup). */
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<SkillRequestSuggestions>> suggestions(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.ok(skillRequestService.suggestions(keyword, user)));
    }

    /** Sidebar stats: top-10 voted requests, top-10 requesters, top-10 hashtags. */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<SkillRequestStatsResult>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(skillRequestService.stats()));
    }

    /** Downloads one attachment of a request (original file name). */
    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fileId) {
        SkillRequestFile file = skillRequestService.getFile(id, fileId);
        InputStream in = skillRequestFileStorageService.load(file.getFilePath());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(in));
    }
}
