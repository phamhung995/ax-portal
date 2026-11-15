package com.furelise.skillmanagement.audit.controller;

import com.furelise.skillmanagement.audit.dto.AuditLogPage;
import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit Log endpoints (admin only).
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<AuditLogPage>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String actorName,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditService.page(page, pageSize, actorName, entityType, action)));
    }
}
