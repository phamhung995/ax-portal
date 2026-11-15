package com.furelise.skillmanagement.org.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.org.dto.OrgImportResponse;
import com.furelise.skillmanagement.org.dto.OrgImportStatus;
import com.furelise.skillmanagement.org.dto.OrgSearchRequest;
import com.furelise.skillmanagement.org.dto.OrgSearchResult;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.org.service.OrganizationService;
import com.furelise.skillmanagement.org.service.impl.OrgImportJobService;
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
    private final OrgImportJobService orgImportJobService;

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

    /** Uploads an Organization List file for the on-demand import (no import yet). */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OrgImportStatus>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User admin) {
        orgImportJobService.uploadFile(file, admin.getEmail());
        return ResponseEntity.status(201).body(ApiResponse.ok(orgImportJobService.status()));
    }

    /** Runs the newest pending upload right now (admin "Import Now"). */
    @PostMapping("/import-now")
    public ResponseEntity<ApiResponse<OrgImportStatus>> importNow(@AuthenticationPrincipal User admin) {
        orgImportJobService.runPendingImport("MANUAL:" + admin.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(orgImportJobService.status()));
    }

    /** Import job state (last run + pending file + running flag). */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<OrgImportStatus>> status() {
        return ResponseEntity.ok(ApiResponse.ok(orgImportJobService.status()));
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

    @GetMapping("/division-options")
    public ResponseEntity<ApiResponse<java.util.List<String>>> divisionOptions() {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.divisionOptions()));
    }

    @GetMapping("/center-options")
    public ResponseEntity<ApiResponse<java.util.List<String>>> centerOptions() {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.centerOptions()));
    }

    @GetMapping("/group-options")
    public ResponseEntity<ApiResponse<java.util.List<String>>> groupOptions() {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.groupOptions()));
    }

    @GetMapping("/part-options")
    public ResponseEntity<ApiResponse<java.util.List<String>>> partOptions() {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.partOptions()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
