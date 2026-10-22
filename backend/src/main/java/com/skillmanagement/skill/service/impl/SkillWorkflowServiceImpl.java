package com.skillmanagement.skill.service.impl;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.notification.model.Notification;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.model.SkillStatusHistory;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.model.UserSettings;
import com.skillmanagement.notification.repository.NotificationRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.repository.SkillStatusHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.repository.UserSettingsRepository;
import com.skillmanagement.mail.EmailService;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.skill.service.SkillVersionUpdateService;
import com.skillmanagement.skill.service.SkillWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill workflow state machine (Detail SRS §5.8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillWorkflowServiceImpl implements SkillWorkflowService {

    private static final int MAX_REJECTION_REASON = 500;

    private final SkillRepository skillRepository;
    private final SkillStatusHistoryRepository skillStatusHistoryRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final SkillAccessHelper skillAccessHelper;
    private final SkillVersionUpdateService versionUpdateService;

    @Override
    @Transactional
    public void transition(Long skillId, User actor, String toStatus, String rejectionReason) {
        Skill skill = skillAccessHelper.getVisible(skillId, actor);
        String from = skill.getApprovalStatus();
        boolean isAdmin = isAdmin(actor);
        boolean isCreator = skill.getCreatedBy().equals(actor.getId());
        // A pending skill with staged metadata is a version update (not a new skill).
        boolean isVersionUpdate = Skill.STATUS_PENDING_APPROVAL.equals(from)
                && skill.getPendingMetadata() != null;
        boolean versionRejected = isVersionUpdate && Skill.STATUS_REJECTED.equals(toStatus);
        boolean versionWithdrawn = isVersionUpdate && Skill.STATUS_DRAFT.equals(toStatus) && isCreator;
        // A rejected or withdrawn version update keeps the skill APPROVED (the
        // old version stays live) — the effective target status differs from
        // the request.
        String effectiveTo = (versionRejected || versionWithdrawn) ? Skill.STATUS_APPROVED : toStatus;

        validateTransition(from, effectiveTo, isAdmin, isCreator, rejectionReason, versionWithdrawn);

        skill.setApprovalStatus(effectiveTo);
        if (Skill.STATUS_PENDING_APPROVAL.equals(effectiveTo)) {
            skill.setRejectionReason(null);
        } else if (Skill.STATUS_REJECTED.equals(effectiveTo)) {
            skill.setRejectionReason(rejectionReason);
        } else if (Skill.STATUS_APPROVED.equals(effectiveTo) && !versionRejected) {
            skill.setRejectionReason(null);
        } else if (versionRejected) {
            // Rejected version update: the skill stays APPROVED but the reason
            // is kept so the creator can see why the update was rejected
            // (cleared on the next update submission).
            skill.setRejectionReason(rejectionReason);
        }
        skillRepository.save(skill);

        if (isVersionUpdate) {
            if (Skill.STATUS_APPROVED.equals(effectiveTo) && !versionRejected && !versionWithdrawn) {
                versionUpdateService.completeVersionUpdate(skill);
            } else if (versionRejected || versionWithdrawn) {
                versionUpdateService.discardPendingVersion(skill);
            }
        }

        skillStatusHistoryRepository.save(SkillStatusHistory.builder()
                .skillId(skillId)
                .fromStatus(from)
                .toStatus(effectiveTo)
                .changedBy(actor.getId())
                .rejectionReason(versionRejected ? rejectionReason : null)
                .build());

        // Notify the creator on approve/reject (not when the actor is the creator).
        if ((Skill.STATUS_APPROVED.equals(effectiveTo) || Skill.STATUS_REJECTED.equals(effectiveTo))
                && !isCreator) {
            notifyCreator(skill, toStatus, rejectionReason);
        }
    }

    private void validateTransition(String from, String to, boolean isAdmin, boolean isCreator,
                                    String rejectionReason, boolean versionWithdrawn) {
        boolean valid = switch (from) {
            case Skill.STATUS_DRAFT ->
                    Skill.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isCreator);
            case Skill.STATUS_REJECTED ->
                    Skill.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isCreator);
            case Skill.STATUS_APPROVED ->
                    // Version update submission (staged metadata + pending files).
                    Skill.STATUS_PENDING_APPROVAL.equals(to) && (isAdmin || isCreator);
            case Skill.STATUS_PENDING_APPROVAL ->
                    (Skill.STATUS_APPROVED.equals(to) && (isAdmin || versionWithdrawn))
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

    private void notifyCreator(Skill skill, String toStatus, String rejectionReason) {
        User creator = userRepository.findById(skill.getCreatedBy()).orElse(null);
        if (creator == null) {
            return;
        }
        boolean approved = Skill.STATUS_APPROVED.equals(toStatus);
        String title = approved
                ? "Your skill \"" + skill.getName() + "\" has been approved."
                : "Your skill \"" + skill.getName() + "\" was rejected.";
        String body = approved
                ? "Your skill \"" + skill.getName() + "\" has been approved and is now published."
                : "Your skill \"" + skill.getName() + "\" was rejected. Reason: "
                        + (rejectionReason != null ? rejectionReason : "");

        UserSettings settings = userSettingsRepository.findById(creator.getId())
                .orElse(UserSettings.builder().userId(creator.getId())
                        .inappNotify(true).emailNotify(true).build());

        if (settings.isInappNotify()) {
            notificationRepository.save(Notification.builder()
                    .userId(creator.getId())
                    .title(title)
                    .skillId(String.valueOf(skill.getId()))
                    .read(false)
                    .build());
        }
        if (settings.isEmailNotify()) {
            try {
                emailService.sendNotificationEmail(creator.getEmail(), title, body);
            } catch (Exception e) {
                log.warn("Không thể gửi email thông báo cho creator {}: {}", creator.getEmail(), e.getMessage());
            }
        }
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }
}
