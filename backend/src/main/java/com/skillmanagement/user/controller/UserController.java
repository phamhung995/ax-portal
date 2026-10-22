package com.skillmanagement.user.controller;

import com.skillmanagement.auth.dto.ChangePasswordRequest;
import com.skillmanagement.auth.dto.MeResponse;
import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.notification.dto.NotificationsRequest;
import com.skillmanagement.user.dto.*;
import com.skillmanagement.auth.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.user.dto.PreferencesRequest;
import com.skillmanagement.user.dto.ProfileUpdateRequest;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * User Settings endpoints (Settings SRS §5).
 * Email is immutable — not part of the profile payload.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(user.getId())));
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MeResponse>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestPart("profile") ProfileUpdateRequest profile,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
            @RequestParam(value = "removeAvatar", defaultValue = "false") boolean removeAvatar) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateProfile(user, profile, avatar, removeAvatar)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<MeResponse>> updatePreferences(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updatePreferences(user.getId(), request)));
    }

    @PutMapping("/notifications")
    public ResponseEntity<ApiResponse<MeResponse>> updateNotifications(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NotificationsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateNotifications(user.getId(), request)));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
