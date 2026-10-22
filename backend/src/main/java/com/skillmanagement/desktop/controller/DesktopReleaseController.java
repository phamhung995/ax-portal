package com.skillmanagement.desktop.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.desktop.dto.DesktopDtos;
import com.skillmanagement.desktop.service.DesktopArtifactStorage;
import com.skillmanagement.desktop.service.DesktopReleaseService;
import com.skillmanagement.user.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/desktop/skills/{skillId}/releases/{releaseId}")
@Profile({"desktop-pilot","desktop-test"})
public class DesktopReleaseController {
    private final DesktopReleaseService releases;
    private final DesktopArtifactStorage storage;
    public DesktopReleaseController(DesktopReleaseService releases,DesktopArtifactStorage storage) { this.releases=releases;this.storage=storage; }
    @GetMapping public ApiResponse<DesktopDtos.Release> release(@AuthenticationPrincipal User user,@PathVariable("skillId") long skillId,@PathVariable("releaseId") String releaseId) { return ApiResponse.ok(releases.dto(releases.authorized(skillId,releaseId,user))); }
    @GetMapping("/package") public ResponseEntity<InputStreamResource> artifact(@AuthenticationPrincipal User user,@PathVariable("skillId") long skillId,@PathVariable("releaseId") String releaseId) {
        var artifact=releases.authorized(skillId,releaseId,user).artifact();
        var body=storage.open(artifact);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentLength(artifact.sizeBytes())
            .contentType(MediaType.parseMediaType(artifact.format().equals("zip")?"application/zip":"application/x-7z-compressed"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(artifact.fileName(),StandardCharsets.UTF_8).build().toString()).body(body);
    }
}
