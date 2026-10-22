package com.skillmanagement.feedback.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.feedback.dto.FeedbackDetailResponse;
import com.skillmanagement.feedback.dto.FeedbackRequest;
import com.skillmanagement.feedback.dto.FeedbackSearchResult;
import com.skillmanagement.feedback.service.FeedbackService;
import com.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Feedback (VOC) submission — any authenticated user.
 */
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<Void> submit(
            @AuthenticationPrincipal User user,
            @RequestBody FeedbackRequest request) {
        feedbackService.submit(user, request);
        return ResponseEntity.status(201).build();
    }

    /** The user's own feedback list (VOC popup — "My VOCs" tab). */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<FeedbackSearchResult>> mine(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.listMine(user, page, pageSize)));
    }

    /** The user's own feedback detail (opened from the notification bell). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FeedbackDetailResponse>> detail(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.detailForOwner(id, user)));
    }
}
