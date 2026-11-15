package com.furelise.skillmanagement.mcp.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.McpCommentPage;
import com.furelise.skillmanagement.mcp.dto.McpCommentRequest;
import com.furelise.skillmanagement.mcp.dto.McpDeleteCheckResponse;
import com.furelise.skillmanagement.mcp.dto.McpDeleteRequest;
import com.furelise.skillmanagement.mcp.dto.McpDetailResponse;
import com.furelise.skillmanagement.mcp.dto.McpRatingRequest;
import com.furelise.skillmanagement.mcp.dto.McpRatingResponse;
import com.furelise.skillmanagement.mcp.dto.McpSearchRequest;
import com.furelise.skillmanagement.mcp.dto.McpSearchResult;
import com.furelise.skillmanagement.mcp.dto.McpStatusRequest;
import com.furelise.skillmanagement.mcp.dto.McpSuggestionResponse;
import com.furelise.skillmanagement.mcp.dto.McpTaxonomyItem;
import com.furelise.skillmanagement.mcp.dto.McpVersionFileDownload;
import com.furelise.skillmanagement.mcp.dto.McpVersionItem;
import com.furelise.skillmanagement.mcp.model.McpFile;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpVersionFile;
import com.furelise.skillmanagement.mcp.repository.McpFileRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.McpCommentService;
import com.furelise.skillmanagement.mcp.service.McpFileStorageService;
import com.furelise.skillmanagement.mcp.service.McpRatingService;
import com.furelise.skillmanagement.mcp.service.McpService;
import com.furelise.skillmanagement.mcp.service.McpSuggestionService;
import com.furelise.skillmanagement.mcp.service.McpTaxonomyService;
import com.furelise.skillmanagement.mcp.service.McpWorkflowService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * MCP Management endpoints (MCP Management SRS — rebuilt on the Skill model,
 * PUBLIC only). All reads are visibility-aware; not-visible MCPs return 404.
 */
@RestController
@RequestMapping("/api/v1/mcps")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;
    private final McpTaxonomyService mcpTaxonomyService;
    private final McpRatingService mcpRatingService;
    private final McpCommentService mcpCommentService;
    private final McpWorkflowService mcpWorkflowService;
    private final McpFileStorageService mcpFileStorageService;
    private final McpFileRepository mcpFileRepository;
    private final McpAccessHelper mcpAccessHelper;
    private final McpSuggestionService mcpSuggestionService;

    /** Create an MCP (multipart: name, description, hashtags, categoryIds, stageIds, file[]). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal User user,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "file", required = false) MultipartFile[] files) {
        Long id = mcpService.create(name, description, hashtags, categoryIds, stageIds, files, user);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    /** Public list of MCP categories and process stages (form pickers). */
    @GetMapping("/taxonomy")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> taxonomy() {
        List<Map<String, Object>> categories = mcpTaxonomyService.listCategories().stream()
                .map(i -> Map.<String, Object>of("id", i.id(), "name", i.name()))
                .toList();
        List<Map<String, Object>> stages = mcpTaxonomyService.listStages().stream()
                .map(i -> Map.<String, Object>of("id", i.id(), "name", i.name(),
                        "description", i.description() == null ? "" : i.description()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("categories", categories, "stages", stages)));
    }

    /**
     * Fuzzy MCP suggestions for the Add/Edit Skill MCP picker
     * (Skill ↔ MCP integration).
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<McpSuggestionResponse>> suggest(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "skillName", required = false) String skillName,
            @RequestParam(value = "skillDesc", required = false) String skillDesc,
            @RequestParam(value = "skillHashtags", required = false) String skillHashtags) {
        McpSuggestionResponse response = mcpSuggestionService.suggest(
                skillName, skillDesc, splitCsv(skillHashtags), user);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** Search the MCP grid (visibility-aware, paged). */
    @GetMapping
    public ResponseEntity<ApiResponse<McpSearchResult>> search(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "mcpName", required = false) String mcpName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestParam(value = "createdDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateFrom,
            @RequestParam(value = "createdDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateTo,
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageId", required = false) Long stageId,
            @RequestParam(value = "subscribed", required = false) String subscribed,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "createdDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection) {
        McpSearchRequest request = new McpSearchRequest(
                mcpName, description, splitCsv(tags), creatorId,
                createdDateFrom, createdDateTo,
                splitCsv(statuses),
                categoryIds, stageId,
                subscribed,
                page, pageSize, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.ok(mcpService.search(request, user)));
    }

    /** MCP detail (404 when not visible to the viewer). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<McpDetailResponse>> detail(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(mcpService.getDetail(id, user)));
    }

    /** Skills that pin this MCP (reverse reference — Detail tab). */
    @GetMapping("/{id}/dependent-skills")
    public ResponseEntity<ApiResponse<List<McpDetailResponse.DependentSkillItem>>> dependentSkills(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                mcpService.getDetail(id, user).dependentSkills()));
    }

    /** Pre-delete validation for the bulk delete confirm dialog. */
    @GetMapping("/check-delete")
    public ResponseEntity<ApiResponse<McpDeleteCheckResponse>> checkDelete(
            @AuthenticationPrincipal User user,
            @RequestParam("ids") List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.ok(mcpService.checkDelete(ids, user)));
    }

    /** Bulk soft delete (all-or-nothing). */
    @DeleteMapping
    public ResponseEntity<Void> deleteBatch(
            @AuthenticationPrincipal User user,
            @RequestBody McpDeleteRequest request) {
        mcpService.deleteBatch(request.ids(), user);
        return ResponseEntity.noContent().build();
    }

    /** Idempotent view count (1 user / MCP / view window). */
    @PostMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> view(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        int count = mcpService.incrementView(id, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("viewCount", count)));
    }

    /** Export the search result (or the selected ids) to .xlsx. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "mcpName", required = false) String mcpName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestParam(value = "createdDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateFrom,
            @RequestParam(value = "createdDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateTo,
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageId", required = false) Long stageId,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        McpSearchRequest request = new McpSearchRequest(
                mcpName, description, splitCsv(tags), creatorId,
                createdDateFrom, createdDateTo,
                splitCsv(statuses),
                categoryIds, stageId,
                null,
                1, Integer.MAX_VALUE, "createdDate", "DESC");
        byte[] xlsx = mcpService.exportExcel(request, ids, user);
        String filename = "MCP_Management_"
                + java.time.LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".xlsx";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    // ── Edit (optimistic lock + FIELD_READ_ONLY) ─────────────────────────────
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Integer>>> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "file", required = false) MultipartFile[] files,
            @RequestParam(value = "removeFileIds", required = false) List<Long> removeFileIds,
            @RequestParam(value = "updatedAt", required = false) java.time.Instant updatedAt) {
        int version = mcpService.update(id, name, description, hashtags,
                categoryIds, stageIds, files, removeFileIds, updatedAt, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("version", version)));
    }

    /** Delete one attached file of an MCP. */
    @DeleteMapping("/{id}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fileId) {
        mcpService.deleteFile(id, fileId, user);
        return ResponseEntity.noContent().build();
    }

    // ── Subscription ──────────────────────────────────────────────────────────
    @PostMapping("/{id}/subscription")
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        mcpService.subscribe(id, user);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{id}/subscription")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        mcpService.unsubscribe(id, user);
        return ResponseEntity.noContent().build();
    }

    // ── Rating ────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<McpRatingResponse>> rating(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(mcpRatingService.getRating(id)));
    }

    @GetMapping("/{id}/rating/me")
    public ResponseEntity<ApiResponse<Double>> myRating(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(mcpRatingService.getMyRating(id, user.getId())));
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<McpRatingResponse>> upsertRating(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody McpRatingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                mcpRatingService.upsertRating(id, user, request.rating())));
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<McpCommentPage>> comments(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(mcpCommentService.list(id, user, page, size)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Void> addComment(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody McpCommentRequest request) {
        mcpCommentService.add(id, user, request.content());
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @PathVariable Long commentId) {
        mcpCommentService.delete(id, commentId, user);
        return ResponseEntity.noContent().build();
    }

    // ── Workflow ──────────────────────────────────────────────────────────────
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody McpStatusRequest request) {
        mcpWorkflowService.transition(id, user, request.approvalStatus(), request.rejectionReason());
        return ResponseEntity.noContent().build();
    }

    // ── Version update (APPROVED MCPs) ────────────────────────────────────────
    /**
     * Submits a version update (multipart: name, description, hashtags,
     * categoryIds, stageIds, file[], removeFileIds[]). Stages the update and
     * moves the MCP to PENDING_APPROVAL.
     */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "file", required = false) MultipartFile[] files,
            @RequestParam(value = "removeFileIds", required = false) List<Long> removeFileIds) {
        mcpService.updateVersion(id, name, description, hashtags,
                categoryIds, stageIds, files, removeFileIds, user);
        return ResponseEntity.status(201).build();
    }

    /** Cancels a pending version update (creator/admin). */
    @PostMapping("/{id}/versions/cancel")
    public ResponseEntity<Void> cancelVersionUpdate(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        mcpService.cancelVersionUpdate(id, user);
        return ResponseEntity.noContent().build();
    }

    /** List of superseded (snapshot) versions, newest first. */
    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<McpVersionItem>>> versions(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(mcpService.listVersions(id, user)));
    }

    /** Downloads a file of a snapshotted version. */
    @GetMapping("/{id}/versions/{version}/files/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadVersionFile(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable int version,
            @PathVariable Long fileId) {
        McpMaster mcp = mcpAccessHelper.getVisibleIncludingDeleted(id, user);
        checkDownloadAllowed(mcp, user);
        mcpService.recordDownload(id, user, false);
        McpVersionFileDownload dl = mcpService.downloadVersionFile(id, version, fileId, user);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + dl.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new org.springframework.core.io.InputStreamResource(dl.stream()));
    }

    /** Downloads ALL files of a snapshotted version as a zip. */
    @GetMapping("/{id}/versions/{version}/files/download-all")
    public ResponseEntity<byte[]> downloadVersionAll(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable int version) {
        McpMaster mcp = mcpAccessHelper.getVisibleIncludingDeleted(id, user);
        checkDownloadAllowed(mcp, user);
        mcpService.recordDownload(id, user, true);
        List<McpVersionFile> files = mcpService.listVersionFiles(id, version, user);
        String base = mcpZipName(mcp).replaceAll("\\.zip$", "");
        return zipFiles(files.stream()
                .map(f -> new FileEntry(f.getFileName(), f.getFilePath()))
                .toList(), base + ".v" + version + ".zip");
    }

    /** Downloads one staged (pending) file of a version update — creator/admin only. */
    @GetMapping("/{id}/pending-versions/files/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadPendingFile(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fileId) {
        McpMaster mcp = mcpAccessHelper.getVisible(id, user);
        checkPendingDownloadAllowed(mcp, user);
        McpFile file = mcpFileRepository.findById(fileId)
                .filter(f -> f.getMcpId().equals(id))
                .filter(f -> "Y".equals(f.getPendingYn()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                        "MCP file does not exist."));
        InputStream in = mcpFileStorageService.load(file.getFilePath());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new org.springframework.core.io.InputStreamResource(in));
    }

    /** Downloads all staged (pending) files of a version update as a zip — creator/admin only. */
    @GetMapping("/{id}/pending-versions/files/download-all")
    public ResponseEntity<byte[]> downloadPendingAll(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        McpMaster mcp = mcpAccessHelper.getVisible(id, user);
        checkPendingDownloadAllowed(mcp, user);
        List<McpFile> files = mcpFileRepository.findByMcpIdAndPendingYn(id, "Y");
        if (files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                    "MCP file does not exist.");
        }
        return zipFiles(files.stream()
                .map(f -> new FileEntry(f.getFileName(), f.getFilePath()))
                .toList(), mcpZipName(mcp).replaceAll("\\.zip$", "") + ".pending.zip");
    }

    // ── Download (per file) ───────────────────────────────────────────────────
    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> download(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fileId) {
        McpMaster mcp = mcpAccessHelper.getVisibleIncludingDeleted(id, user);
        checkDownloadAllowed(mcp, user);
        mcpService.recordDownload(id, user, false);
        McpFile file = mcpFileRepository.findById(fileId)
                .filter(f -> f.getMcpId().equals(id))
                .filter(f -> "N".equals(f.getPendingYn()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                        "MCP file does not exist."));
        InputStream in = mcpFileStorageService.load(file.getFilePath());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new org.springframework.core.io.InputStreamResource(in));
    }

    // ── Download all files (zip) ─────────────────────────────────────────────
    @GetMapping("/{id}/files/download-all")
    public ResponseEntity<byte[]> downloadAll(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        McpMaster mcp = mcpAccessHelper.getVisibleIncludingDeleted(id, user);
        checkDownloadAllowed(mcp, user);
        mcpService.recordDownload(id, user, true);
        List<McpFile> files = mcpFileRepository.findByMcpIdAndPendingYn(id, "N");
        if (files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                    "MCP file does not exist.");
        }
        return zipFiles(files.stream()
                .map(f -> new FileEntry(f.getFileName(), f.getFilePath()))
                .toList(), mcpZipName(mcp));
    }

    /** Packages the given files into an in-memory zip (NASC-safe). */
    private ResponseEntity<byte[]> zipFiles(List<FileEntry> files, String zipName) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            java.util.Set<String> usedNames = new java.util.HashSet<>();
            for (FileEntry f : files) {
                String entryName = uniqueEntryName(f.fileName(), usedNames);
                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                try (InputStream in = mcpFileStorageService.load(f.filePath())) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to package the MCP files.");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + zipName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }

    /** A file to include in a zip: display name + storage path. */
    private record FileEntry(String fileName, String filePath) {}

    /** Zip file name for Download All: the MCP name + ".zip" (invalid filename chars removed). */
    private String mcpZipName(McpMaster mcp) {
        String base = mcp.getMcpName()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
        if (base.isEmpty()) {
            base = "mcp_" + mcp.getId();
        }
        return base + ".zip";
    }

    /**
     * Download permission rule:
     * - creator: any status
     * - admin: PENDING_APPROVAL or APPROVED
     * - other user: APPROVED only (an MCP with a pending version update
     *   still serves its approved version, so it counts as live)
     */
    private void checkDownloadAllowed(McpMaster mcp, User user) {
        boolean isCreator = mcp.getCreatedBy().equals(user.getId());
        boolean isAdmin = user.getRole() != null
                && "ADMIN_USER".equals(user.getRole().getName());
        boolean allowed;
        if (isCreator) {
            allowed = true;
        } else if (isAdmin) {
            allowed = McpMaster.STATUS_PENDING_APPROVAL.equals(mcp.getStatus())
                    || McpMaster.STATUS_APPROVED.equals(mcp.getStatus());
        } else {
            allowed = McpMaster.STATUS_APPROVED.equals(mcp.getStatus())
                    || mcp.isVersionUpdatePending();
        }
        if (!allowed) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_APPROVED,
                    "This MCP cannot be downloaded in its current status.");
        }
    }

    /**
     * Staged (pending) files of a version update are only downloadable by
     * the creator or an admin, and only while the update is pending.
     */
    private void checkPendingDownloadAllowed(McpMaster mcp, User user) {
        if (!mcp.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                    "There is no pending version update.");
        }
        boolean isCreator = mcp.getCreatedBy().equals(user.getId());
        boolean isAdmin = user.getRole() != null
                && "ADMIN_USER".equals(user.getRole().getName());
        if (!isCreator && !isAdmin) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_APPROVED,
                    "Pending version files can only be downloaded by the creator or an admin.");
        }
    }

    private static String uniqueEntryName(String name, java.util.Set<String> used) {
        if (used.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 2;
        while (!used.add(base + " (" + i + ")" + ext)) {
            i++;
        }
        return base + " (" + i + ")" + ext;
    }

    private static java.util.List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
