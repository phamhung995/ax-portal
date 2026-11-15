package com.furelise.skillmanagement.mcp.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.mcp.dto.McpTaxonomyCreateRequest;
import com.furelise.skillmanagement.mcp.dto.McpTaxonomyItem;
import com.furelise.skillmanagement.mcp.dto.McpTaxonomyUpdateRequest;
import com.furelise.skillmanagement.mcp.service.McpTaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP categories & process stages management — admin only.
 */
@RestController
@RequestMapping("/api/v1/admin/mcp-taxonomy")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminMcpTaxonomyController {

    private final McpTaxonomyService taxonomyService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<McpTaxonomyItem>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.ok(taxonomyService.listCategories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<Long>> createCategory(
            @RequestBody McpTaxonomyCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(taxonomyService.createCategory(request.name())));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Void> updateCategory(
            @PathVariable Long id,
            @RequestBody McpTaxonomyUpdateRequest request) {
        taxonomyService.updateCategory(id, request.name());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        taxonomyService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<McpTaxonomyItem>>> listStages() {
        return ResponseEntity.ok(ApiResponse.ok(taxonomyService.listStages()));
    }

    @PostMapping("/stages")
    public ResponseEntity<ApiResponse<Long>> createStage(
            @RequestBody McpTaxonomyCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(
                taxonomyService.createStage(request.name(), request.description())));
    }

    @PutMapping("/stages/{id}")
    public ResponseEntity<Void> updateStage(
            @PathVariable Long id,
            @RequestBody McpTaxonomyUpdateRequest request) {
        taxonomyService.updateStage(id, request.name(), request.description());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/stages/{id}")
    public ResponseEntity<Void> deleteStage(@PathVariable Long id) {
        taxonomyService.deleteStage(id);
        return ResponseEntity.noContent().build();
    }
}
