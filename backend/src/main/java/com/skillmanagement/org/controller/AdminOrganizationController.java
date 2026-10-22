package com.skillmanagement.org.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.org.dto.OrgImportResponse;
import com.skillmanagement.org.dto.OrgSearchRequest;
import com.skillmanagement.org.dto.OrgSearchResult;
import com.skillmanagement.user.model.User;
import com.skillmanagement.org.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Organization Management endpoints (admin only).
 */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminOrganizationController {

    private final OrganizationService organizationService;

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<OrgSearchResult>> search(@RequestBody OrgSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.search(request)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OrgImportResponse>> importExcel(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User admin, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.ok(
                organizationService.importExcel(file, admin.getEmail(), clientIp(http))));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws java.io.IOException {
        byte[] xlsx = organizationService.downloadTemplate();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"organization-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @GetMapping("/part-options")
    public ResponseEntity<ApiResponse<java.util.List<String>>> partOptions() {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.partOptions()));
    }

    @GetMapping("/group-options")
    public ResponseEntity<ApiResponse<java.util.List<String>>> groupOptions() {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.groupOptions()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
