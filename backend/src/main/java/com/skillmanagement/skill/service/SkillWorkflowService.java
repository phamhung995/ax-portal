package com.skillmanagement.skill.service;

import com.skillmanagement.user.model.User;

/**
 * Skill workflow state machine (Detail SRS §5.8).
 *
 * <pre>
 * DRAFT            → PENDING_APPROVAL   (creator or admin)
 * REJECTED         → PENDING_APPROVAL   (creator or admin; clears rejection_reason)
 * PENDING_APPROVAL → PUBLISHED          (admin only)
 * PENDING_APPROVAL → REJECTED           (admin only; rejectionReason required)
 * PENDING_APPROVAL → DRAFT              (creator only — Withdraw)
 * </pre>
 */
public interface SkillWorkflowService {

    /**
     * Transitions a skill to a new status, enforcing the state machine and
     * role rules. Records skill_status_history and, for approve/reject,
     * notifies the creator.
     */
    void transition(Long skillId, User actor, String toStatus, String rejectionReason);
}
