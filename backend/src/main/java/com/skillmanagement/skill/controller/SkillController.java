package com.skillmanagement.skill.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.skill.dto.SkillCommentPage;
import com.skillmanagement.skill.dto.SkillCommentRequest;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.skill.dto.SkillDeleteRequest;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.model.SkillFile;
import com.skillmanagement.user.model.User;
import com.skillmanagement.skill.model.SkillVersionFile;
import com.skillmanagement.skill.repository.SkillFileRepository;
import com.skillmanagement.skill.service.SkillCommentService;
import com.skillmanagement.skill.service.SkillFileStorageService;
import com.skillmanagement.skill.service.SkillRatingService;
import com.skillmanagement.skill.service.SkillService;
import com.skillmanagement.skill.service.SkillTaxonomyService;
import com.skillmanagement.skill.service.SkillWorkflowService;
import com.skillmanagement.skill.service.SkillAccessHelper;
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
 * Skill Management endpoints (Skill Management SRS).
 * All reads are visibility-aware; not-visible skills return 404.
 */
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final SkillTaxonomyService skillTaxonomyService;
    private final SkillRatingService skillRatingService;
    private final SkillCommentService skillCommentService;
    private final SkillWorkflowService skillWorkflowService;
    private final SkillFileStorageService skillFileStorageService;
    private final SkillFileRepository skillFileRepository;
    private final SkillAccessHelper skillAccessHelper;

    /** Create a skill (multipart: name, description, publicScope, hashtags, categoryIds, stageIds, file[]). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal User user,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "publicScope", defaultValue = "PRIVATE") String publicScope,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "file", required = false) MultipartFile[] files) {
        Long id = skillService.create(name, description, publicScope, hashtags,
                categoryIds, stageIds, files, user);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    /** Public list of categories and process stages (form pickers). */
    @GetMapping("/taxonomy")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> taxonomy() {
        List<Map<String, Object>> categories = skillTaxonomyService.listCategories().stream()
                .map(i -> Map.<String, Object>of("id", i.id(), "name", i.name()))
                .toList();
        List<Map<String, Object>> stages = skillTaxonomyService.listStages().stream()
                .map(i -> Map.<String, Object>of("id", i.id(), "name", i.name(),
                        "description", i.description() == null ? "" : i.description()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("categories", categories, "stages", stages)));
    }

    /** Search the skill grid (visibility-aware, paged). */
    @GetMapping
    public ResponseEntity<ApiResponse<SkillSearchResult>> search(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "skillName", required = false) String skillName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestParam(value = "createdDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateFrom,
            @RequestParam(value = "createdDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateTo,
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "scopes", required = false) String scopes,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageId", required = false) Long stageId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "createdDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection) {
        SkillSearchRequest request = new SkillSearchRequest(
                skillName, description, splitCsv(tags), creatorId,
                createdDateFrom, createdDateTo,
                splitCsv(statuses), splitCsv(scopes),
                categoryIds, stageId,
                page, pageSize, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.ok(skillService.search(request, user)));
    }

    /** Skill detail (404 when not visible to the viewer). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillDetailResponse>> detail(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(skillService.getDetail(id, user)));
    }

    /** Pre-delete validation for the bulk delete confirm dialog. */
    @GetMapping("/check-delete")
    public ResponseEntity<ApiResponse<SkillDeleteCheckResponse>> checkDelete(
            @AuthenticationPrincipal User user,
            @RequestParam("ids") List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.ok(skillService.checkDelete(ids, user)));
    }

    /** Bulk soft delete (all-or-nothing). */
    @DeleteMapping
    public ResponseEntity<Void> deleteBatch(
            @AuthenticationPrincipal User user,
            @RequestBody SkillDeleteRequest request) {
        skillService.deleteBatch(request.ids(), user);
        return ResponseEntity.noContent().build();
    }

    /** Idempotent view count (1 user / skill / view window). */
    @PostMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> view(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        int count = skillService.incrementView(id, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("viewCount", count)));
    }

    /** Export the search result (or the selected ids) to .xlsx. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "skillName", required = false) String skillName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestParam(value = "createdDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateFrom,
            @RequestParam(value = "createdDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateTo,
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "scopes", required = false) String scopes,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageId", required = false) Long stageId,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        SkillSearchRequest request = new SkillSearchRequest(
                skillName, description, splitCsv(tags), creatorId,
                createdDateFrom, createdDateTo,
                splitCsv(statuses), splitCsv(scopes),
                categoryIds, stageId,
                1, Integer.MAX_VALUE, "createdDate", "DESC");
        byte[] xlsx = skillService.exportExcel(request, ids, user);
        String filename = "Skill_Management_"
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
            @RequestParam(value = "publicScope", defaultValue = "PRIVATE") String publicScope,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "file", required = false) MultipartFile[] files,
            @RequestParam(value = "updatedAt", required = false) java.time.Instant updatedAt) {
        int version = skillService.update(id, name, description, publicScope,
                hashtags, categoryIds, stageIds, files, updatedAt, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("version", version)));
    }

    /** Delete one attached file of a skill. */
    @DeleteMapping("/{id}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fileId) {
        skillService.deleteFile(id, fileId, user);
        return ResponseEntity.noContent().build();
    }

    // ── Subscription ──────────────────────────────────────────────────────────
    @PostMapping("/{id}/subscription")
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        skillService.subscribe(id, user);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{id}/subscription")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        skillService.unsubscribe(id, user);
        return ResponseEntity.noContent().build();
    }

    // ── Rating ────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<SkillRatingResponse>> rating(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(skillRatingService.getRating(id)));
    }

    @GetMapping("/{id}/rating/me")
    public ResponseEntity<ApiResponse<Double>> myRating(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(skillRatingService.getMyRating(id, user.getId())));
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<SkillRatingResponse>> upsertRating(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody SkillRatingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                skillRatingService.upsertRating(id, user, request.rating())));
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<SkillCommentPage>> comments(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(skillCommentService.list(id, user, page, size)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Void> addComment(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody SkillCommentRequest request) {
        skillCommentService.add(id, user, request.content());
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @PathVariable Long commentId) {
        skillCommentService.delete(id, commentId, user);
        return ResponseEntity.noContent().build();
    }

    // ── Workflow ──────────────────────────────────────────────────────────────
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody SkillStatusRequest request) {
        skillWorkflowService.transition(id, user, request.approvalStatus(), request.rejectionReason());
        return ResponseEntity.noContent().build();
    }

    // ── Version update (APPROVED skills) ──────────────────────────────────────
    /**
     * Submits a version update (multipart: name, description, publicScope,
     * hashtags, categoryIds, stageIds, file[], removeFileIds[]).
     * Stages the update and moves the skill to PENDING_APPROVAL.
     */
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateVersion(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "publicScope", defaultValue = "PRIVATE") String publicScope,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "file", required = false) MultipartFile[] files,
            @RequestParam(value = "removeFileIds", required = false) List<Long> removeFileIds) {
        skillService.updateVersion(id, name, description, publicScope, hashtags,
                categoryIds, stageIds, files, removeFileIds, user);
        return ResponseEntity.status(201).build();
    }

    /** Cancels a pending version update (creator/admin). */
    @PostMapping("/{id}/versions/cancel")
    public ResponseEntity<Void> cancelVersionUpdate(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        skillService.cancelVersionUpdate(id, user);
        return ResponseEntity.noContent().build();
    }

    /** List of superseded (snapshot) versions, newest first. */
    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<java.util.List<SkillVersionItem>>> versions(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(skillService.listVersions(id, user)));
    }

    /** Downloads a file of a snapshotted version. */
    @GetMapping("/{id}/versions/{version}/files/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadVersionFile(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable int version,
            @PathVariable Long fileId) {
        Skill skill = skillAccessHelper.getVisible(id, user);
        checkDownloadAllowed(skill, user);
        SkillVersionFileDownload dl =
                skillService.downloadVersionFile(id, version, fileId, user);
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
        Skill skill = skillAccessHelper.getVisible(id, user);
        checkDownloadAllowed(skill, user);
        List<SkillVersionFile> files =
                skillService.listVersionFiles(id, version, user);
        String base = skillZipName(skill).replaceAll("\\.zip$", "");
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
        Skill skill = skillAccessHelper.getVisible(id, user);
        checkPendingDownloadAllowed(skill, user);
        SkillFile file = skillFileRepository.findById(fileId)
                .filter(f -> f.getSkillId().equals(id))
                .filter(f -> "Y".equals(f.getPendingYn()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                        "Skill file does not exist."));
        InputStream in = skillFileStorageService.load(file.getFilePath());
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
        Skill skill = skillAccessHelper.getVisible(id, user);
        checkPendingDownloadAllowed(skill, user);
        List<SkillFile> files = skillFileRepository.findBySkillIdAndPendingYn(id, "Y");
        if (files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                    "Skill file does not exist.");
        }
        return zipFiles(files.stream()
                .map(f -> new FileEntry(f.getFileName(), f.getFilePath()))
                .toList(), skillZipName(skill).replaceAll("\\.zip$", "") + ".pending.zip");
    }

    // ── Download (per file) ───────────────────────────────────────────────────
    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> download(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fileId) {
        Skill skill = skillAccessHelper.getVisible(id, user);
        checkDownloadAllowed(skill, user);
        SkillFile file = skillFileRepository.findById(fileId)
                .filter(f -> f.getSkillId().equals(id))
                .filter(f -> "N".equals(f.getPendingYn()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                        "Skill file does not exist."));
        InputStream in = skillFileStorageService.load(file.getFilePath());
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
        Skill skill = skillAccessHelper.getVisible(id, user);
        checkDownloadAllowed(skill, user);
        List<SkillFile> files = skillFileRepository.findBySkillIdAndPendingYn(id, "N");
        if (files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                    "Skill file does not exist.");
        }
        return zipFiles(files.stream()
                .map(f -> new FileEntry(f.getFileName(), f.getFilePath()))
                .toList(), skillZipName(skill));
    }

    /** Packages the given files into an in-memory zip (NASC-safe). */
    private ResponseEntity<byte[]> zipFiles(List<FileEntry> files, String zipName) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            java.util.Set<String> usedNames = new java.util.HashSet<>();
            for (FileEntry f : files) {
                String entryName = uniqueEntryName(f.fileName(), usedNames);
                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                try (InputStream in = skillFileStorageService.load(f.filePath())) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to package the skill files.");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + zipName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }

    /** A file to include in a zip: display name + storage path. */
    private record FileEntry(String fileName, String filePath) {}

    /** Zip file name for Download All: the skill name + ".zip" (invalid filename chars removed). */
    private String skillZipName(Skill skill) {
        String base = skill.getName()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
        if (base.isEmpty()) {
            base = "skill_" + skill.getId();
        }
        return base + ".zip";
    }

    /**
     * Download permission rule:
     * - creator: any status
     * - admin: PENDING_APPROVAL or APPROVED
     * - other user: APPROVED only (a skill with a pending version update
     *   still serves its approved version, so it counts as live)
     */
    private void checkDownloadAllowed(Skill skill, User user) {
        boolean isCreator = skill.getCreatedBy().equals(user.getId());
        boolean isAdmin = user.getRole() != null
                && "ADMIN_USER".equals(user.getRole().getName());
        boolean allowed;
        if (isCreator) {
            allowed = true;
        } else if (isAdmin) {
            allowed = Skill.STATUS_PENDING_APPROVAL.equals(skill.getApprovalStatus())
                    || Skill.STATUS_APPROVED.equals(skill.getApprovalStatus());
        } else {
            allowed = Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())
                    || skill.isVersionUpdatePending();
        }
        if (!allowed) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_APPROVED,
                    "This skill cannot be downloaded in its current status.");
        }
    }

    /**
     * Staged (pending) files of a version update are only downloadable by
     * the creator or an admin, and only while the update is pending.
     */
    private void checkPendingDownloadAllowed(Skill skill, User user) {
        if (!skill.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                    "There is no pending version update.");
        }
        boolean isCreator = skill.getCreatedBy().equals(user.getId());
        boolean isAdmin = user.getRole() != null
                && "ADMIN_USER".equals(user.getRole().getName());
        if (!isCreator && !isAdmin) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_APPROVED,
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
