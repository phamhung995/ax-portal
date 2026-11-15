package com.furelise.skillmanagement.home.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.home.dto.ChartDimensions;
import com.furelise.skillmanagement.home.dto.ChartStats;
import com.furelise.skillmanagement.home.dto.CommunitySkills;
import com.furelise.skillmanagement.home.dto.HomeSkillItem;
import com.furelise.skillmanagement.home.dto.HomeSummary;
import com.furelise.skillmanagement.home.dto.NewRequestItem;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /**
     * Chart tab selector data (Home_Chart_SRS §6.1) — admin only.
     */
    @GetMapping("/chart-dimensions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USER')")
    public ResponseEntity<ApiResponse<ChartDimensions>> chartDimensions() {
        return ResponseEntity.ok(ApiResponse.ok(homeService.chartDimensions()));
    }

    /**
     * Chart tab metrics per Group/Part (Home_Chart_SRS §6.2) — admin only.
     */
    @GetMapping("/chart-stats")
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USER')")
    public ResponseEntity<ApiResponse<ChartStats>> chartStats(
            @RequestParam(defaultValue = "1y") String period,
            @RequestParam(defaultValue = "group") String dimension,
            @RequestParam(required = false) String group) {
        return ResponseEntity.ok(ApiResponse.ok(homeService.chartStats(period, dimension, group)));
    }
}
