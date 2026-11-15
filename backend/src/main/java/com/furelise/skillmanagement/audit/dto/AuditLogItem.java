package com.furelise.skillmanagement.audit.dto;

import java.time.Instant;

/**
 * One row of the audit log for the admin Audit Log screen.
 */
public record AuditLogItem(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String entityType,
        String entityId,
        String details,
        String ipAddress,
        Instant createdAt
) {
}
