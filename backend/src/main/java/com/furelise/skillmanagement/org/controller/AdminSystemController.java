package com.furelise.skillmanagement.org.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.org.dto.SystemImportStatus;
import com.furelise.skillmanagement.org.dto.SystemSearchRequest;
import com.furelise.skillmanagement.org.dto.SystemSearchResult;
import com.furelise.skillmanagement.org.service.SystemService;
import com.furelise.skillmanagement.org.service.impl.SystemImportJobService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * System / Sub System master data endpoints (admin only).
 */
@RestController
@RequestMapping("/api/v1/admin/systems")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminSystemController {

    private final SystemService systemService;
    private final SystemImportJobService jobService;

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<SystemSearchResult>> search(@RequestBody SystemSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(systemService.search(request)));
    }

    /** Uploads a System List file for the scheduled import (no import yet). */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SystemImportStatus>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User admin) {
        jobService.uploadFile(file, admin.getEmail());
        return ResponseEntity.status(201).body(ApiResponse.ok(jobService.status()));
    }

    /** Runs the newest pending upload right now (admin "Import Now"). */
    @PostMapping("/import-now")
    public ResponseEntity<ApiResponse<SystemImportStatus>> importNow(@AuthenticationPrincipal User admin) {
        jobService.runPendingImport("MANUAL:" + admin.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(jobService.status()));
    }

    /** Import job state (last run + pending file + running flag). */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SystemImportStatus>> status() {
        return ResponseEntity.ok(ApiResponse.ok(jobService.status()));
    }

    @GetMapping("/part-options")
    public ResponseEntity<ApiResponse<List<String>>> partOptions() {
        return ResponseEntity.ok(ApiResponse.ok(systemService.partOptions()));
    }
}
