package com.furelise.skillmanagement.audit.service;

import com.furelise.skillmanagement.audit.dto.AuditLogPage;
import com.furelise.skillmanagement.user.model.User;

/**
 * Audit log for write operations (admin traceability).
 * Fail-safe: a logging error must never break the business operation.
 */
public interface AuditService {

    /**
     * Records one audited action.
     *
     * @param actor      the acting user (null for system actions)
     * @param action     short verb, e.g. "CREATE", "UPDATE", "DELETE", "APPROVE"
     * @param entityType entity name, e.g. "SKILL", "USER", "ORGANIZATION"
     * @param entityId   entity id as string (null when unknown)
     * @param details    free-form summary (e.g. "name=Foo, status=APPROVED")
     */
    void log(User actor, String action, String entityType, String entityId, String details);

    /**
     * Records one audited action, resolving the actor from the current
     * Spring Security context (the authenticated user).
     */
    void log(String action, String entityType, String entityId, String details);

    /** Paged, newest-first query for the admin screen. */
    AuditLogPage page(int page, int pageSize, String actorName, String entityType, String action);
}
