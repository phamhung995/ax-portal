package com.furelise.skillmanagement.skill.service.impl;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.mcp.service.McpNotificationService;
import com.furelise.skillmanagement.mcp.service.McpPublishValidationService;
import com.furelise.skillmanagement.mcp.service.McpPublishValidationService.Blocker;
import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillShare;
import com.furelise.skillmanagement.skill.model.SkillStatusHistory;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.skill.repository.SkillCoworkUserRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillShareRepository;
import com.furelise.skillmanagement.skill.repository.SkillStatusHistoryRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.skill.service.SkillVersionUpdateService;
import com.furelise.skillmanagement.skill.service.SkillWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skill workflow state machine (Detail SRS §5.8).
 *
 * <p>Three kinds of PENDING_APPROVAL exist:
 * <ul>
 *   <li>legacy submit / version update (pending_metadata set or null
 *       reason) — the skill is not (yet) live;</li>
 *   <li>SHARE pending (pending_reason='SHARE') — external shares await
 *       admin approval; the skill stays live for its current audience;</li>
 *   <li>SCOPE pending (pending_reason='SCOPE') — a scope widening awaits
 *       approval; the old scope still applies.</li>
 * </ul>
 * For the last two, a reject or a creator withdraw does NOT move the skill
 * to REJECTED/DRAFT — it goes back to APPROVED (the pre-pending state).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillWorkflowServiceImpl implements SkillWorkflowService {

    private static final int MAX_REJECTION_REASON = 500;

    private final SkillRepository skillRepository;
    private final SkillStatusHistoryRepository skillStatusHistoryRepository;
    private final SkillShareRepository skillShareRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final SkillAccessHelper skillAccessHelper;
    private final SkillVersionUpdateService versionUpdateService;
    private final SkillCoworkUserRepository skillCoworkUserRepository;
    private final McpPublishValidationService mcpPublishValidationService;
    private final McpNotificationService mcpNotificationService;

    /** Public frontend base URL — appended to skill links in notification emails. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    @Override
    @Transactional
    public void transition(Long skillId, User actor, String toStatus, String rejectionReason) {
        Skill skill = skillAccessHelper.getVisible(skillId, actor);
        String from = skill.getApprovalStatus();
        boolean isAdmin = isAdmin(actor);
        boolean isCreator = skill.getCreatedBy().equals(actor.getId());
        boolean isCowork = !isCreator
                && skillCoworkUserRepository.existsBySkillIdAndUserId(skillId, actor.getId());
        // A pending skill with staged metadata is a version update (not a new skill).
        boolean isVersionUpdate = Skill.STATUS_PENDING_APPROVAL.equals(from)
                && skill.getPendingMetadata() != null;
        boolean versionRejected = isVersionUpdate && Skill.STATUS_REJECTED.equals(toStatus);
        boolean versionWithdrawn = isVersionUpdate && Skill.STATUS_DRAFT.equals(toStatus) && isCreator;
        // A pending skill with a pending_reason (no staged metadata) is a
        // SHARE or SCOPE pending — the skill stays live while pending.
        boolean shareScopePending = Skill.STATUS_PENDING_APPROVAL.equals(from)
                && skill.getPendingMetadata() == null
                && skill.getPendingReason() != null;
        boolean shareScopeRejected = shareScopePending && Skill.STATUS_REJECTED.equals(toStatus);
        boolean shareScopeWithdrawn = shareScopePending && Skill.STATUS_DRAFT.equals(toStatus) && isCreator;
        // A rejected or withdrawn version update keeps the skill APPROVED (the
        // old version stays live) — the effective target status differs from
        // the request. Same for a rejected/withdrawn share/scope pending:
        // the skill goes back to APPROVED (the pre-pending state).
        String effectiveTo = (versionRejected || versionWithdrawn
                || shareScopeRejected || shareScopeWithdrawn)
                ? Skill.STATUS_APPROVED : toStatus;

        validateTransition(from, effectiveTo, isAdmin, isCreator, isCowork, rejectionReason,
                versionWithdrawn, shareScopeWithdrawn);

        // FR-MCP-016 (v1.33): submit for approval requires ALL pinned MCPs to be
        // ready (APPROVED).
        // Fail → 400 + blockers[], skill stays DRAFT, owner notified (FR-MCP-017).
        boolean isSubmit = Skill.STATUS_PENDING_APPROVAL.equals(effectiveTo)
                && (Skill.STATUS_DRAFT.equals(from) || Skill.STATUS_REJECTED.equals(from));
        if (isSubmit) {
            List<Blocker> blockers = mcpPublishValidationService.validate(skillId);
            if (!blockers.isEmpty()) {
                mcpNotificationService.notifySkillBlocked(skillId, blockers);
                throw mcpBlockedException(blockers);
            }
        }
        // Q-25: re-validate when the admin approves (an MCP may have
        // changed between submit and approve).
        // Scope pending IS validated: widening from an auto-approved
        // scope (PRIVATE/PROJECT) to PART/GROUP/PUBLIC is the skill's
        // first real publication — the MCPs must be live for the new
        // audience. Share pending is NOT validated: the live scope and
        // the already-published version do not change.
        boolean isScopePending = shareScopePending
                && Skill.PENDING_REASON_SCOPE.equals(skill.getPendingReason());
        boolean isAdminApprove = isAdmin
                && Skill.STATUS_PENDING_APPROVAL.equals(from)
                && Skill.STATUS_APPROVED.equals(effectiveTo)
                && !shareScopeRejected && !shareScopeWithdrawn
                && (!shareScopePending || isScopePending);
        if (isAdminApprove) {
            List<Blocker> blockers = mcpPublishValidationService.validate(skillId);
            if (!blockers.isEmpty()) {
                throw mcpBlockedException(blockers);
            }
        }

        // One-time admin review (V91): any admin approval (new skill,
        // version update, share pending, scope pending) marks the skill as
        // reviewed — the creator can then widen scope and share externally
        // without further admin approval. Reject/withdraw do NOT count.
        boolean adminApproved = isAdmin
                && Skill.STATUS_PENDING_APPROVAL.equals(from)
                && Skill.STATUS_APPROVED.equals(effectiveTo)
                && !versionRejected && !shareScopeRejected;
        if (adminApproved) {
            skill.setAdminReviewedYn("Y");
        }

        skill.setApprovalStatus(effectiveTo);
        if (Skill.STATUS_PENDING_APPROVAL.equals(effectiveTo)) {
            skill.setRejectionReason(null);
        } else if (Skill.STATUS_REJECTED.equals(effectiveTo)) {
            skill.setRejectionReason(rejectionReason);
        } else if (Skill.STATUS_APPROVED.equals(effectiveTo)
                && !versionRejected && !shareScopeRejected) {
            skill.setRejectionReason(null);
        } else if (versionRejected || shareScopeRejected) {
            // Rejected version update / rejected share: the skill stays
            // APPROVED but the reason is kept so the creator can see why
            // (cleared on the next submission).
            skill.setRejectionReason(rejectionReason);
        }

        // Share/scope pending resolution:
        //  - approve: PENDING shares → APPROVED, or the new scope applies;
        //  - reject:  PENDING shares → REJECTED (kept so the creator can see
        //             and remove them), the old scope stays;
        //  - withdraw: PENDING shares are deleted, the old scope stays.
        if (shareScopePending) {
            boolean approved = Skill.STATUS_APPROVED.equals(effectiveTo)
                    && !shareScopeRejected && !shareScopeWithdrawn;
            if (Skill.PENDING_REASON_SHARE.equals(skill.getPendingReason())) {
                if (approved) {
                    setShareApprovalStatus(skillId, SkillShare.STATUS_PENDING, SkillShare.STATUS_APPROVED);
                } else if (shareScopeRejected) {
                    setShareApprovalStatus(skillId, SkillShare.STATUS_PENDING, SkillShare.STATUS_REJECTED);
                } else {
                    deleteShares(skillId, SkillShare.STATUS_PENDING);
                }
            } else if (Skill.PENDING_REASON_SCOPE.equals(skill.getPendingReason())) {
                if (approved && skill.getPendingScope() != null) {
                    skill.setPublicScope(skill.getPendingScope());
                }
            }
            skill.setPendingReason(null);
            skill.setPendingScope(null);
        }
        skillRepository.save(skill);

        if (isVersionUpdate) {
            if (Skill.STATUS_APPROVED.equals(effectiveTo) && !versionRejected && !versionWithdrawn) {
                versionUpdateService.completeVersionUpdate(skill, actor);
            } else if (versionRejected || versionWithdrawn) {
                versionUpdateService.discardPendingVersion(skill);
            }
        }

        skillStatusHistoryRepository.save(SkillStatusHistory.builder()
                .skillId(skillId)
                .fromStatus(from)
                .toStatus(effectiveTo)
                .changedBy(actor.getId())
                .rejectionReason(versionRejected || shareScopeRejected ? rejectionReason : null)
                .build());

        // Notify the creator on approve/reject (not when the actor is the creator).
        // For a rejected share/scope pending the effective target is APPROVED,
        // but toStatus is REJECTED, so the creator gets the "rejected" message
        // with the reason (same pattern as a rejected version update).
        if ((Skill.STATUS_APPROVED.equals(effectiveTo) || Skill.STATUS_REJECTED.equals(effectiveTo))
                && !isCreator) {
            notifyCreator(skill, actor, toStatus, rejectionReason);
        }
    }

    /** Moves all shares of the skill with one approval status to another. */
    private void setShareApprovalStatus(Long skillId, String fromStatus, String toStatus) {
        for (SkillShare share : skillShareRepository.findBySkillIdAndApprovalStatus(skillId, fromStatus)) {
            share.setApprovalStatus(toStatus);
            skillShareRepository.save(share);
        }
    }

    /** Deletes all shares of the skill with the given approval status. */
    private void deleteShares(Long skillId, String approvalStatus) {
        skillShareRepository.deleteAll(
                skillShareRepository.findBySkillIdAndApprovalStatus(skillId, approvalStatus));
    }

    private void validateTransition(String from, String to, boolean isAdmin, boolean isCreator,
                                    boolean isCowork, String rejectionReason, boolean versionWithdrawn,
                                    boolean shareScopeWithdrawn) {
        boolean valid = switch (from) {
            case Skill.STATUS_DRAFT ->
                    Skill.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isCreator || isCowork);
            case Skill.STATUS_REJECTED ->
                    Skill.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isCreator || isCowork);
            case Skill.STATUS_APPROVED ->
                    // Version update submission (staged metadata + pending files)
                    // or an external share (pending_reason='SHARE').
                    Skill.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isCreator || isCowork);
            case Skill.STATUS_PENDING_APPROVAL ->
                    (Skill.STATUS_APPROVED.equals(to) && (isAdmin || versionWithdrawn || shareScopeWithdrawn))
                            || (Skill.STATUS_REJECTED.equals(to) && isAdmin)
                            || (Skill.STATUS_DRAFT.equals(to) && isCreator);
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_TRANSITION,
                    "This status change is not allowed.");
        }
        if (Skill.STATUS_REJECTED.equals(to)) {
            if (rejectionReason == null || rejectionReason.isBlank()) {
                throw new BusinessException(BusinessErrorCode.SKILL_INVALID_TRANSITION,
                        "A rejection reason is required.");
            }
            if (rejectionReason.length() > MAX_REJECTION_REASON) {
                throw new BusinessException(BusinessErrorCode.SKILL_INVALID_TRANSITION,
                        "Rejection reason is at most 500 characters.");
            }
        }
    }

    /**
     * FR-MCP-016 (v1.33): maps the first blocker reason to its error code
     * (ERR-SKILL-MCP-001/002) and carries the full {@code blockers[]} in
     * the response's {@code data} field.
     */
    private BusinessException mcpBlockedException(List<Blocker> blockers) {
        String message = "Cannot proceed: " + blockers.size()
                + " MCP dependenc" + (blockers.size() > 1 ? "ies are" : "y is")
                + " not ready for approval.";
        return new BusinessException(BusinessErrorCode.MCP_DEPENDENCY_NOT_READY, message, blockers);
    }

    private void notifyCreator(Skill skill, User actor, String toStatus, String rejectionReason) {
        User creator = userRepository.findById(skill.getCreatedBy()).orElse(null);
        if (creator == null) {
            return;
        }
        UserSettings settings = userSettingsRepository.findById(creator.getId()).orElse(null);
        boolean approved = Skill.STATUS_APPROVED.equals(toStatus);
        String title = approved
                ? "Your skill \"" + skill.getName() + "\" has been approved."
                : "Your skill \"" + skill.getName() + "\" was rejected.";
        String skillUrl = skillDetailUrl(skill.getId());
        String body = (approved
                ? "Your skill \"" + skill.getName() + "\" has been approved and is now published."
                : "Your skill \"" + skill.getName() + "\" was rejected. Reason: "
                        + (rejectionReason != null ? rejectionReason : ""))
                + (skillUrl != null ? "\n\n" + skillUrl : "");
        eventNotificationService.notify(actor, creator, settings,
                new EventNotificationService.EventDetails(
                        title,
                        new EventNotificationService.EventContext(
                                String.valueOf(skill.getId()), null, null, null, null),
                        "[AX Portal] " + title, body));
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }

    /** Frontend URL that opens the skill detail popup (?detail={id}). */
    private String skillDetailUrl(Long skillId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/skills?detail=" + skillId;
    }
}
