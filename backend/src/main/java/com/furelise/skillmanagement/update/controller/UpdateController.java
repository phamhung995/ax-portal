package com.furelise.skillmanagement.update.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.update.dto.UpdateManifest;
import com.furelise.skillmanagement.update.model.AppRelease;
import com.furelise.skillmanagement.update.service.UpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * App update API for AIPRO STUDIO silent auto-update (tauri-plugin-updater).
 * All endpoints PUBLIC (no auth) — air-gapped internal use.
 *
 * Manifest format matches tauri-plugin-updater expectations:
 * plugin GETs /latest, parses version + platforms.windows-x86_64,
 * downloads .nsis.zip from url, verifies signature, installs passive.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/update")
@RequiredArgsConstructor
public class UpdateController {

    private final UpdateService updateService;

    /**
     * Latest release manifest (tauri-plugin-updater format).
     * Returns 404 if no active release.
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatest() {
        UpdateManifest manifest = updateService.getLatestManifest();
        if (manifest == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NO_RELEASE", "No active release available"));
        }
        // Return raw manifest (NOT wrapped in ApiResponse) — plugin expects
        // {version, notes, pub_date, platforms} at top level.
        return ResponseEntity.ok(manifest);
    }

    /**
     * Download .nsis.zip binary for a specific version.
     * Streams with Content-Disposition attachment + octet-stream.
     */
    @GetMapping("/download/{version}")
    public ResponseEntity<Resource> download(@PathVariable String version) {
        AppRelease release = updateService.getByVersion(version);
        if (release == null) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = updateService.getReleaseResource(release);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String encodedName = URLEncoder.encode(release.getNsisZipName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(release.getNsisZipSize())
                .body(resource);
    }

    /**
     * List all active releases (newest first).
     */
    @GetMapping("/releases")
    public ResponseEntity<ApiResponse<List<AppRelease>>> listReleases() {
        return ResponseEntity.ok(ApiResponse.ok(updateService.listAll()));
    }

    /**
     * Upload new release (public, no auth).
     * Multipart: nsisZip (file) + signature (String, .sig content) + version + notes (optional)
     */
    @PostMapping(value = "/releases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USER')")
    public ResponseEntity<ApiResponse<AppRelease>> uploadRelease(
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "signature", required = false) String signature,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam("nsisZip") MultipartFile nsisZip
    ) throws IOException {
        if (nsisZip.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("EMPTY_FILE", "nsisZip file is required"));
        }
        if (version == null || version.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("MISSING_VERSION", "version is required"));
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("MISSING_SIGNATURE", "signature is required"));
        }
        AppRelease saved = updateService.uploadRelease(version, signature, notes, nsisZip);
        log.info("Uploaded release v{} ({} bytes)", version, saved.getNsisZipSize());
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    /**
     * Deactivate (soft-delete) a release by version.
     */
    @DeleteMapping("/releases/{version}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USER')")
    public ResponseEntity<ApiResponse<Boolean>> deleteRelease(@PathVariable String version) {
        boolean deactivated = updateService.deactivateRelease(version);
        if (!deactivated) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", "Release " + version + " not found"));
        }
        return ResponseEntity.ok(ApiResponse.ok(true));
    }
}
