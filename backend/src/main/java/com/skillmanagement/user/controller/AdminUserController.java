package com.skillmanagement.user.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.user.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.user.dto.*;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * User Management endpoints (admin only, SRS §27).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<UserSearchResult>> search(@RequestBody UserSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.search(request)));
    }

    /** User list for filter dropdowns (Feedback Requester/Handler). */
    @GetMapping("/search-users")
    public ResponseEntity<ApiResponse<List<UserListItem>>> searchUsers(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role) {
        UserSearchRequest request = new UserSearchRequest(
                userName, email, null, null, null, null, null, null, null, null, 1, 50, null, null);
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.searchUsers(request, role)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> detail(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getDetail(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDetailResponse>> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal User admin, HttpServletRequest http) {
        return ResponseEntity.status(201).body(ApiResponse.ok(
                adminUserService.createUser(request, admin.getEmail(), clientIp(http))));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> update(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User admin, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.updateUser(userId, request, admin.getEmail(), clientIp(http))));
    }

    @PostMapping(value = "/bulk/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkUploadResponse>> bulkUpload(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.validateBulkUpload(file)));
    }

    @PostMapping("/bulk/save")
    public ResponseEntity<ApiResponse<BulkSaveResult>> bulkSave(
            @RequestBody BulkSaveRequest request,
            @AuthenticationPrincipal User admin, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.saveBulkUpload(request.rows(), admin.getEmail(), clientIp(http))));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody UserSearchRequest request) throws IOException {
        byte[] xlsx = adminUserService.exportUsers(request);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"users.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @GetMapping("/organization-options")
    public ResponseEntity<ApiResponse<List<String>>> organizationOptions() {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.organizationOptions()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
