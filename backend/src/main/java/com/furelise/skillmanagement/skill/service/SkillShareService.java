package com.furelise.skillmanagement.skill.service;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.skill.dto.SkillShareItem;
import com.furelise.skillmanagement.skill.dto.SkillShareResult;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillShare;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillShareRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Share management for PROJECT-scope skills.
 *
 * <p>Only the creator or an admin may share. Two share types:
 * <ul>
 *   <li>USER — a specific user (the shared user sees the skill)</li>
 *   <li>PROJECT — a project key; everyone in that project sees the skill.
 *       Allowed projects: the projects of non-End-phase systems except the
 *       creator's own projects.</li>
 * </ul>
 *
 * <p>External shares need admin approval: a USER share is external when the
 * user does not share any project with the creator; a PROJECT share is
 * always external (the creator's own projects are excluded from the options).
 * External shares are created with approval_status='PENDING' and, when the
 * skill is APPROVED, the skill moves to PENDING_APPROVAL with
 * pending_reason='SHARE' until an admin approves or rejects it. Internal
 * shares take effect immediately (approval_status='APPROVED').
 *
 * <p>Once an admin has approved the skill at least once
 * (admin_reviewed_yn='Y'), external shares also take effect immediately —
 * the skill was already reviewed (V91).
 */
@Service
@RequiredArgsConstructor
public class SkillShareService {

    private final SkillRepository skillRepository;
    private final SkillShareRepository shareRepository;
    private final UserRepository userRepository;
    private final SkillAccessHelper skillAccessHelper;
    private final SkillWorkflowService skillWorkflowService;

    /** The skill's current shares, with the shared users' names resolved. */
    @Transactional(readOnly = true)
    public List<SkillShareItem> listShares(Long skillId, User viewer) {
        Skill skill = requireShareable(skillId, viewer);
        List<SkillShare> shares = shareRepository.findBySkillIdOrderByCreatedAtAsc(skillId);
        java.util.Map<Long, String> names = loadNames(shares);
        return shares.stream()
                .map(s -> new SkillShareItem(s.getId(), s.getShareType(),
                        s.getUserId(),
                        s.getUserId() == null ? null : names.getOrDefault(s.getUserId(), null),
                        s.getProjectKey(),
                        s.getApprovalStatus()))
                .toList();
    }

    /**
     * Share the skill with the given users (skips already-shared ones).
     * Users outside the creator's projects get PENDING shares; when at least
     * one is created and the skill is APPROVED, the skill moves to
     * PENDING_APPROVAL (pending_reason='SHARE').
     */
    @Transactional
    public SkillShareResult shareUsers(Long skillId, List<Long> userIds, User actor) {
        Skill skill = requireShareable(skillId, actor);
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_INVALID,
                    "At least one user is required.");
        }
        List<Long> ids = userIds.stream().distinct().toList();
        if (ids.stream().anyMatch(id -> !userRepository.existsById(id))) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_INVALID,
                    "One of the selected users does not exist.");
        }
        List<SkillShare> existing = shareRepository.findBySkillIdOrderByCreatedAtAsc(skillId);
        Set<Long> alreadyShared = existing.stream()
                .filter(s -> SkillShare.TYPE_USER.equals(s.getShareType()))
                .map(SkillShare::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> newIds = ids.stream().filter(id -> !alreadyShared.contains(id)).toList();
        if (newIds.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_DUPLICATE,
                    "The selected users are already shared.");
        }
        Set<String> creatorProjects = skillAccessHelper.creatorProjects(skill.getCreatedBy());
        // A reviewed skill (admin approved it at least once) grants external
        // shares immediately — no further admin approval needed (V91).
        boolean reviewed = "Y".equals(skill.getAdminReviewedYn());
        List<SkillShare> saved = newIds.stream()
                .map(id -> {
                    boolean external = isExternalUser(id, creatorProjects);
                    return shareRepository.save(SkillShare.builder()
                            .skillId(skillId)
                            .shareType(SkillShare.TYPE_USER)
                            .userId(id)
                            .approvalStatus((external && !reviewed) ? SkillShare.STATUS_PENDING : SkillShare.STATUS_APPROVED)
                            .createdBy(actor.getId())
                            .build());
                })
                .toList();
        int pendingCount = (int) saved.stream()
                .filter(s -> SkillShare.STATUS_PENDING.equals(s.getApprovalStatus()))
                .count();
        maybeMakeSharePending(skill, pendingCount, actor);
        java.util.Map<Long, String> names = userRepository.findAllById(newIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        return new SkillShareResult(saved.stream()
                .map(s -> new SkillShareItem(s.getId(), s.getShareType(), s.getUserId(),
                        names.get(s.getUserId()), null, s.getApprovalStatus()))
                .toList(), pendingCount);
    }

    /**
     * Share the skill with everyone in the given projects (skips
     * already-shared ones). The shareable options exclude the creator's own
     * projects, so every new PROJECT share is external — PENDING for an
     * unreviewed skill, APPROVED for a reviewed one (V91).
     */
    @Transactional
    public SkillShareResult shareProjects(Long skillId, List<String> projectKeys, User actor) {
        Skill skill = requireShareable(skillId, actor);
        if (projectKeys == null || projectKeys.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_INVALID,
                    "At least one project is required.");
        }
        List<String> keys = projectKeys.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.trim())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_INVALID,
                    "At least one project is required.");
        }
        Set<String> allowed = skillAccessHelper.shareableProjectKeys(skill).stream()
                .map(k -> k.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<String> invalid = keys.stream()
                .filter(k -> !allowed.contains(k.toLowerCase(Locale.ROOT)))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_INVALID,
                    "One of the selected projects is not in the creator's group.");
        }
        List<SkillShare> existing = shareRepository.findBySkillIdOrderByCreatedAtAsc(skillId);
        Set<String> alreadyShared = existing.stream()
                .filter(s -> SkillShare.TYPE_PROJECT.equals(s.getShareType()))
                .map(s -> s.getProjectKey().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<String> newKeys = keys.stream()
                .filter(k -> !alreadyShared.contains(k.toLowerCase(Locale.ROOT)))
                .toList();
        if (newKeys.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_DUPLICATE,
                    "The selected projects are already shared.");
        }
        List<SkillShare> saved = newKeys.stream()
                .map(key -> shareRepository.save(SkillShare.builder()
                        .skillId(skillId)
                        .shareType(SkillShare.TYPE_PROJECT)
                        .projectKey(key)
                        // All shareable projects are outside the creator's own
                        // projects → external → PENDING (unless the skill was
                        // already reviewed by an admin — V91).
                        .approvalStatus("Y".equals(skill.getAdminReviewedYn())
                                ? SkillShare.STATUS_APPROVED : SkillShare.STATUS_PENDING)
                        .createdBy(actor.getId())
                        .build()))
                .toList();
        int pendingCount = (int) saved.stream()
                .filter(s -> SkillShare.STATUS_PENDING.equals(s.getApprovalStatus()))
                .count();
        maybeMakeSharePending(skill, pendingCount, actor);
        return new SkillShareResult(saved.stream()
                .map(s -> new SkillShareItem(s.getId(), s.getShareType(),
                        s.getUserId(), null, s.getProjectKey(), s.getApprovalStatus()))
                .toList(), pendingCount);
    }

    /** Remove a share (by its id). */
    @Transactional
    public void removeShare(Long skillId, Long shareId, User actor) {
        Skill skill = requireShareable(skillId, actor);
        SkillShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_SHARE_NOT_FOUND,
                        "Share not found."));
        if (!share.getSkillId().equals(skillId)) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_NOT_FOUND,
                    "Share not found.");
        }
        shareRepository.delete(share);
    }

    /** Remove several shares of the skill at once (unknown ids are ignored). */
    @Transactional
    public int removeShares(Long skillId, List<Long> shareIds, User actor) {
        Skill skill = requireShareable(skillId, actor);
        if (shareIds == null || shareIds.isEmpty()) {
            return 0;
        }
        List<SkillShare> shares = shareRepository.findAllById(shareIds).stream()
                .filter(s -> s.getSkillId().equals(skillId))
                .toList();
        shareRepository.deleteAll(shares);
        return shares.size();
    }

    /**
     * The project keys the skill may be shared with: all projects of
     * non-End-phase systems except the creator's own projects.
     */
    @Transactional(readOnly = true)
    public List<String> projectOptions(Long skillId, User viewer) {
        Skill skill = requireShareable(skillId, viewer);
        return skillAccessHelper.shareableProjectKeys(skill);
    }

    /**
     * When new external shares were created and the skill is APPROVED, move
     * it to PENDING_APPROVAL (pending_reason='SHARE') for admin review.
     * Skills in another state (DRAFT/REJECTED/already pending) keep their
     * status — visibility is still gated by the status rule.
     */
    private void maybeMakeSharePending(Skill skill, int pendingCount, User actor) {
        if (pendingCount == 0 || !Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())) {
            return;
        }
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        skillWorkflowService.transition(skill.getId(), actor, Skill.STATUS_PENDING_APPROVAL, null);
    }

    /** A user is external when they do not share any project with the creator. */
    private boolean isExternalUser(Long userId, Set<String> creatorProjects) {
        if (creatorProjects.isEmpty()) {
            return true;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return true;
        }
        return skillAccessHelper.projectsOf(user).stream().noneMatch(creatorProjects::contains);
    }

    /** The skill must exist, be PROJECT scope, and the actor must be creator or admin. */
    private Skill requireShareable(Long skillId, User actor) {
        Skill skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null || "Y".equals(skill.getDeletedYn())) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_FOUND, "Skill not found.");
        }
        if (!Skill.SCOPE_PROJECT.equals(skill.getPublicScope())) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_INVALID,
                    "Only PROJECT-scope skills can be shared.");
        }
        boolean isCreator = skill.getCreatedBy().equals(actor.getId());
        boolean isAdmin = actor.getRole() != null
                && "ADMIN_USER".equals(actor.getRole().getName());
        if (!isCreator && !isAdmin) {
            throw new BusinessException(BusinessErrorCode.SKILL_SHARE_FORBIDDEN,
                    "Only the creator or an admin can share this skill.");
        }
        return skill;
    }

    private java.util.Map<Long, String> loadNames(List<SkillShare> shares) {
        List<Long> userIds = shares.stream()
                .map(SkillShare::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return java.util.Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getName, (a, b) -> a));
    }
}
