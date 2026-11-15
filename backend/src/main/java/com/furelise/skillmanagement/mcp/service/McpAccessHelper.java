package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared visibility helper for MCP (PUBLIC only — no scope):
 * deleted_yn='N' + (owner: all statuses | admin: all statuses | others:
 * APPROVED, or a version-update-pending MCP which still serves its live
 * approved version). Not-visible MCPs return 404 MCP_NOT_FOUND so their
 * existence is not revealed (mirrors SkillAccessHelper).
 */
@Component
@RequiredArgsConstructor
public class McpAccessHelper {

    private final McpMasterRepository mcpMasterRepository;

    /**
     * Returns the MCP if it exists, is not soft-deleted, and is visible to
     * the viewer; otherwise throws 404 MCP_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public McpMaster getVisible(Long mcpId, User viewer) {
        McpMaster mcp = mcpMasterRepository.findById(mcpId).orElse(null);
        if (mcp == null || "Y".equals(mcp.getDeletedYn())) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_FOUND, "MCP not found.");
        }
        boolean visible = mcpMasterRepository.count(
                McpMasterRepository.visibleTo(viewer)
                        .and((root, query, cb) -> cb.equal(root.get("id"), mcpId))) > 0;
        if (!visible) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_FOUND, "MCP not found.");
        }
        return mcp;
    }

    /**
     * Like {@link #getVisible} but ALLOWS soft-deleted MCPs: used by the
     * download endpoints so skills that pin a deleted MCP keep working —
     * its files remain downloadable. Visibility rule still applies for
     * non-deleted MCPs; a deleted MCP is loadable by anyone (it no longer
     * appears in any list, so no information is leaked).
     */
    @Transactional(readOnly = true)
    public McpMaster getVisibleIncludingDeleted(Long mcpId, User viewer) {
        McpMaster mcp = mcpMasterRepository.findById(mcpId).orElse(null);
        if (mcp == null) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_FOUND, "MCP not found.");
        }
        if ("Y".equals(mcp.getDeletedYn())) {
            return mcp;
        }
        boolean visible = mcpMasterRepository.count(
                McpMasterRepository.visibleTo(viewer)
                        .and((root, query, cb) -> cb.equal(root.get("id"), mcpId))) > 0;
        if (!visible) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_FOUND, "MCP not found.");
        }
        return mcp;
    }
}
