package com.furelise.skillmanagement.mcp.service.impl;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpStatusHistory;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpStatusHistoryRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.McpNotificationService;
import com.furelise.skillmanagement.mcp.service.McpVersionUpdateService;
import com.furelise.skillmanagement.mcp.service.McpWorkflowService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MCP workflow state machine (MCP Management SRS — rebuilt on the Skill
 * model, PUBLIC only). No cowork, no share/scope pending — only the two
 * kinds of PENDING_APPROVAL:
 * <ul>
 *   <li>submit (DRAFT/REJECTED → PENDING) — the MCP is not (yet) live;</li>
 *   <li>version update (APPROVED → PENDING, staged metadata) — the live
 *       version stays available until the admin approves the update.</li>
 * </ul>
 * A rejected or withdrawn version update keeps the MCP APPROVED (the old
 * version stays live) — the effective target status differs from the
 * request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpWorkflowServiceImpl implements McpWorkflowService {

    private static final int MAX_REJECTION_REASON = 500;

    private final McpMasterRepository mcpMasterRepository;
    private final McpStatusHistoryRepository mcpStatusHistoryRepository;
    private final McpAccessHelper mcpAccessHelper;
    private final McpVersionUpdateService versionUpdateService;
    private final McpNotificationService mcpNotificationService;

    @Override
    @Transactional
    public void transition(Long mcpId, User actor, String toStatus, String rejectionReason) {
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, actor);
        String from = mcp.getStatus();
        boolean isAdmin = isAdmin(actor);
        boolean isOwner = mcp.getCreatedBy().equals(actor.getId());

        // A pending MCP with staged metadata is a version update (not a new MCP).
        boolean isVersionUpdate = McpMaster.STATUS_PENDING_APPROVAL.equals(from)
                && mcp.getPendingMetadata() != null;
        boolean versionRejected = isVersionUpdate && McpMaster.STATUS_REJECTED.equals(toStatus);
        boolean versionWithdrawn = isVersionUpdate && McpMaster.STATUS_DRAFT.equals(toStatus) && isOwner;
        // A rejected or withdrawn version update keeps the MCP APPROVED (the
        // old version stays live) — the effective target status differs from
        // the request.
        String effectiveTo = (versionRejected || versionWithdrawn)
                ? McpMaster.STATUS_APPROVED : toStatus;

        validateTransition(from, effectiveTo, isAdmin, isOwner, rejectionReason,
                versionWithdrawn);

        mcp.setStatus(effectiveTo);
        if (McpMaster.STATUS_PENDING_APPROVAL.equals(effectiveTo)) {
            mcp.setRejectionReason(null);
        } else if (McpMaster.STATUS_REJECTED.equals(effectiveTo)) {
            mcp.setRejectionReason(rejectionReason);
        } else if (McpMaster.STATUS_APPROVED.equals(effectiveTo)
                && !versionRejected) {
            mcp.setRejectionReason(null);
        } else if (versionRejected) {
            // Rejected version update: the MCP stays APPROVED but the reason
            // is kept so the owner can see why (cleared on the next submission).
            mcp.setRejectionReason(rejectionReason);
        }
        mcpMasterRepository.save(mcp);

        if (isVersionUpdate) {
            if (McpMaster.STATUS_APPROVED.equals(effectiveTo) && !versionRejected && !versionWithdrawn) {
                versionUpdateService.completeVersionUpdate(mcp, actor);
            } else if (versionRejected || versionWithdrawn) {
                versionUpdateService.discardPendingVersion(mcp);
            }
        }

        mcpStatusHistoryRepository.save(McpStatusHistory.builder()
                .mcpId(mcpId)
                .fromStatus(from)
                .toStatus(effectiveTo)
                .changedBy(actor.getId())
                .rejectionReason(versionRejected ? rejectionReason : null)
                .build());

        // Notify the owner on approve/reject (not when the actor is the owner).
        // For a rejected version update the effective target is APPROVED,
        // but toStatus is REJECTED, so the owner gets the "rejected" message
        // with the reason.
        if ((McpMaster.STATUS_APPROVED.equals(effectiveTo) || McpMaster.STATUS_REJECTED.equals(effectiveTo))
                && !isOwner) {
            mcpNotificationService.notifyOwnerDecision(mcpId, mcp.getMcpName(),
                    mcp.getCreatedBy(), McpMaster.STATUS_APPROVED.equals(toStatus), rejectionReason);
        }
        // Notify all admins when a new MCP (not a version update) is submitted.
        boolean isSubmit = McpMaster.STATUS_PENDING_APPROVAL.equals(effectiveTo)
                && (McpMaster.STATUS_DRAFT.equals(from) || McpMaster.STATUS_REJECTED.equals(from));
        if (isSubmit) {
            mcpNotificationService.notifySubmitForApproval(mcpId, mcp.getMcpName(),
                    actor.getName());
        }
    }

    private void validateTransition(String from, String to, boolean isAdmin, boolean isOwner,
                                    String rejectionReason, boolean versionWithdrawn) {
        boolean valid = switch (from) {
            case McpMaster.STATUS_DRAFT ->
                    McpMaster.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isOwner);
            case McpMaster.STATUS_REJECTED ->
                    McpMaster.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isOwner);
            case McpMaster.STATUS_APPROVED ->
                    // Version update submission (staged metadata + pending files).
                    McpMaster.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isOwner);
            case McpMaster.STATUS_PENDING_APPROVAL ->
                    (McpMaster.STATUS_APPROVED.equals(to) && (isAdmin || versionWithdrawn))
                            || (McpMaster.STATUS_REJECTED.equals(to) && isAdmin)
                            || (McpMaster.STATUS_DRAFT.equals(to) && isOwner);
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(BusinessErrorCode.MCP_INVALID_TRANSITION,
                    "This status change is not allowed.");
        }
        if (McpMaster.STATUS_REJECTED.equals(to)) {
            if (rejectionReason == null || rejectionReason.isBlank()) {
                throw new BusinessException(BusinessErrorCode.MCP_INVALID_TRANSITION,
                        "A rejection reason is required.");
            }
            if (rejectionReason.length() > MAX_REJECTION_REASON) {
                throw new BusinessException(BusinessErrorCode.MCP_INVALID_TRANSITION,
                        "Rejection reason is at most 500 characters.");
            }
        }
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }
}
