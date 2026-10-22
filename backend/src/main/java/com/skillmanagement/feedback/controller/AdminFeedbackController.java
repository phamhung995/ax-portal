package com.skillmanagement.feedback.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.feedback.dto.*;
import com.skillmanagement.feedback.dto.FeedbackDetailResponse;
import com.skillmanagement.feedback.dto.FeedbackSearchRequest;
import com.skillmanagement.feedback.dto.FeedbackSearchResult;
import com.skillmanagement.feedback.dto.FeedbackStatusRequest;
import com.skillmanagement.feedback.service.FeedbackService;
import com.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Feedback (VOC) management — admin only.
 */
@RestController
@RequestMapping("/api/v1/admin/feedback")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<FeedbackSearchResult>> search(
            @RequestBody FeedbackSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.search(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FeedbackDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.detail(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin,
            @RequestBody FeedbackStatusRequest request) {
        feedbackService.changeStatus(id, admin, request);
        return ResponseEntity.ok().build();
    }

    /** Export the search result (all pages) to .xlsx. */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody FeedbackSearchRequest request) {
        byte[] xlsx = feedbackService.exportExcel(request);
        String filename = "Feedback_"
                + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".xlsx";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
