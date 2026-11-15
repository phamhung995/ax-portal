package com.furelise.skillmanagement.audit.dto;

import java.util.List;

/**
 * Paged audit log result for the admin screen.
 */
public record AuditLogPage(
        long total,
        int page,
        int pageSize,
        List<AuditLogItem> items
) {
}
