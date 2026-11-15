package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.user.model.User;

/**
 * MCP workflow state machine (MCP Management SRS — rebuilt on the Skill
 * model, PUBLIC only).
 *
 * <pre>
 * DRAFT            → PENDING_APPROVAL   (owner or admin)
 * REJECTED         → PENDING_APPROVAL   (owner or admin; clears rejection_reason)
 * PENDING_APPROVAL → APPROVED           (admin only)
 * PENDING_APPROVAL → REJECTED           (admin only; rejectionReason required)
 * PENDING_APPROVAL → DRAFT              (owner only — Withdraw)
 * APPROVED         → PENDING_APPROVAL   (owner or admin — version update)
 * </pre>
 *
 * A pending MCP with staged metadata is a VERSION UPDATE: reject/withdraw
 * returns it to APPROVED (the old version stays live).
 */
public interface McpWorkflowService {

    /**
     * Transitions an MCP to a new status, enforcing the state machine and
     * role rules. Records mcp_status_history and, for approve/reject,
     * notifies the owner.
     */
    void transition(Long mcpId, User actor, String toStatus, String rejectionReason);
}
