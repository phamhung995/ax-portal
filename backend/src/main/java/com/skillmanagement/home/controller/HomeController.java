package com.skillmanagement.home.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.home.dto.CommunitySkills;
import com.skillmanagement.home.dto.HomeActivity;
import com.skillmanagement.home.dto.HomeSkillItem;
import com.skillmanagement.home.dto.HomeSummary;
import com.skillmanagement.home.dto.NewRequestItem;
import com.skillmanagement.user.model.User;
import com.skillmanagement.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Home screen endpoints (Home SRS §7).
 * AI News endpoints live in the AI News module (AiNewsController —
 * /api/v1/home/ai-news[/...]).
 */
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<HomeSummary>> summary(@AuthenticationPrincipal User user) {
        boolean isAdmin = user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
        return ResponseEntity.ok(ApiResponse.ok(homeService.summary(user, isAdmin)));
    }

    @GetMapping("/my-recent-skills")
    public ResponseEntity<ApiResponse<List<HomeSkillItem>>> myRecentSkills(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "5") int limit) {
        // Clamp: limit <= 0 is invalid in PostgreSQL (LIMIT -1 = no limit) and
        // huge values are a DoS/over-fetch vector.
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.ok(homeService.myRecentSkills(user, safeLimit)));
    }

    @GetMapping("/community-skills")
    public ResponseEntity<ApiResponse<CommunitySkills>> communitySkills(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(homeService.communitySkills(user)));
    }

    @GetMapping("/new-requests")
    public ResponseEntity<ApiResponse<List<NewRequestItem>>> newRequests(
            @RequestParam(defaultValue = "8") int limit) {
        // Clamp: limit <= 0 is invalid in PostgreSQL (LIMIT -1 = no limit) and
        // huge values are a DoS/over-fetch vector.
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.ok(homeService.newRequests(safeLimit)));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<HomeActivity>> activity(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "6") int weeks) {
        // Clamp: the activity window is bounded (6-week chart, 26-week heatmap).
        int safeWeeks = Math.min(Math.max(weeks, 1), 52);
        return ResponseEntity.ok(ApiResponse.ok(homeService.activity(user, safeWeeks)));
    }
}
