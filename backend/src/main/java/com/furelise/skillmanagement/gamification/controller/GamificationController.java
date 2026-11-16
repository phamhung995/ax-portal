package com.furelise.skillmanagement.gamification.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.gamification.dto.PointsHistoryItem;
import com.furelise.skillmanagement.gamification.model.Perk;
import com.furelise.skillmanagement.gamification.model.PerkRedemption;
import com.furelise.skillmanagement.gamification.service.GamificationService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Points wallet: balance, daily check-in, history, perk store. */
@RestController
@RequestMapping("/api/v1/gamification")
@RequiredArgsConstructor
public class GamificationController {

    private final GamificationService gamificationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GamificationService.MeResponse>> me(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(gamificationService.me(user.getId())));
    }

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<GamificationService.CheckInResult>> checkIn(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(gamificationService.checkIn(user.getId())));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PointsHistoryItem>>> history(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(gamificationService.history(user.getId(), page, pageSize)));
    }

    @GetMapping("/perks")
    public ResponseEntity<ApiResponse<List<Perk>>> perks(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(gamificationService.perks()));
    }

    @PostMapping("/perks/{perkId}/redeem")
    public ResponseEntity<ApiResponse<PerkRedemption>> redeem(
            @AuthenticationPrincipal User user, @PathVariable Long perkId) {
        return ResponseEntity.ok(ApiResponse.ok(gamificationService.redeemPerk(user.getId(), perkId)));
    }
}
