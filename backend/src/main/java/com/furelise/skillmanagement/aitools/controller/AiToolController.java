package com.furelise.skillmanagement.aitools.controller;

import com.furelise.skillmanagement.aitools.dto.AiToolDeleteRequest;
import com.furelise.skillmanagement.aitools.dto.AiToolDetailResponse;
import com.furelise.skillmanagement.aitools.dto.AiToolSearchRequest;
import com.furelise.skillmanagement.aitools.dto.AiToolSearchResult;
import com.furelise.skillmanagement.aitools.service.AiToolService;
import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.skill.service.SkillTaxonomyService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI Tools endpoints.
 * Reads (search/detail/download) are open to every authenticated user;
 * anyone can create; update/delete are limited to the uploader or an admin
 * (enforced in the service).
 */
@RestController
@RequestMapping("/api/v1/ai-tools")
@RequiredArgsConstructor
public class AiToolController {

    private final AiToolService aiToolService;
    private final SkillTaxonomyService skillTaxonomyService;

    /** Public list of categories and process stages (form pickers, shared with Skill Management). */
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

    /** Search the AI tools grid (paged). */
    @GetMapping
    public ResponseEntity<ApiResponse<AiToolSearchResult>> search(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "uploaderId", required = false) Long uploaderId,
            @RequestParam(value = "createdDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateFrom,
            @RequestParam(value = "createdDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDateTo,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageId", required = false) Long stageId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "createdDate") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection) {
        AiToolSearchRequest request = new AiToolSearchRequest(
                name, splitCsv(tags), uploaderId,
                createdDateFrom, createdDateTo,
                categoryIds, stageId,
                page, pageSize, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.ok(aiToolService.search(request, user)));
    }

    /** AI tool detail (404 when not found or deleted). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiToolDetailResponse>> detail(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(aiToolService.getDetail(id, user)));
    }

    /** Create an AI tool (multipart: name, description, tags, categoryIds, stageIds, file). Any authenticated user. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal User user,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        Long id = aiToolService.create(name, description, tags, categoryIds, stageIds, file, user);
        return ResponseEntity.status(201).body(ApiResponse.ok(id));
    }

    /** Update an AI tool (multipart; a new file replaces the old one,
     *  removeFile=true deletes the attached file). Uploader or admin only. */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "stageIds", required = false) List<Long> stageIds,
            @RequestParam(value = "removeFile", required = false, defaultValue = "false") boolean removeFile,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        aiToolService.update(id, name, description, tags, categoryIds, stageIds, removeFile, file, user);
        return ResponseEntity.noContent().build();
    }

    /** Bulk delete (soft delete + files removed from disk). Uploader or admin only, per tool;
     *  tools the user may not edit are skipped and reported. */
    @DeleteMapping
    public ResponseEntity<ApiResponse<AiToolService.AiToolDeleteResult>> deleteBatch(
            @AuthenticationPrincipal User user,
            @RequestBody AiToolDeleteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(aiToolService.deleteBatch(request.ids(), user)));
    }

    /** Downloads the attached file of a tool (records the download event). */
    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long fileId) {
        AiToolService.AiToolDownloadResult dl = aiToolService.download(id, fileId, user);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + dl.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(dl.stream()));
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
