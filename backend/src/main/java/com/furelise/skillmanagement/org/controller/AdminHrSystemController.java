package com.furelise.skillmanagement.org.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.org.dto.HrImportStatus;
import com.furelise.skillmanagement.org.dto.HrSearchRequest;
import com.furelise.skillmanagement.org.dto.HrSearchResult;
import com.furelise.skillmanagement.org.service.HrSystemService;
import com.furelise.skillmanagement.org.service.impl.HrImportJobService;
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
 * Human Resource System List master data endpoints (admin only).
 */
@RestController
@RequestMapping("/api/v1/admin/hr-systems")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminHrSystemController {

    private final HrSystemService hrSystemService;
    private final HrImportJobService jobService;

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<HrSearchResult>> search(@RequestBody HrSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(hrSystemService.search(request)));
    }

    /** Uploads a Human Resource List file for the scheduled import (no import yet). */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HrImportStatus>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User admin) {
        jobService.uploadFile(file, admin.getEmail());
        return ResponseEntity.status(201).body(ApiResponse.ok(jobService.status()));
    }

    /** Runs the newest pending upload right now (admin "Import Now"). */
    @PostMapping("/import-now")
    public ResponseEntity<ApiResponse<HrImportStatus>> importNow(@AuthenticationPrincipal User admin) {
        jobService.runPendingImport("MANUAL:" + admin.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(jobService.status()));
    }

    /** Import job state (last run + pending file + running flag). */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<HrImportStatus>> status() {
        return ResponseEntity.ok(ApiResponse.ok(jobService.status()));
    }

    @GetMapping("/part-options")
    public ResponseEntity<ApiResponse<List<String>>> partOptions() {
        return ResponseEntity.ok(ApiResponse.ok(hrSystemService.partOptions()));
    }
}
