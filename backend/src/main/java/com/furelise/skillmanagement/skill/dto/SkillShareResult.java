package com.furelise.skillmanagement.skill.dto;

import java.util.List;

/**
 * Response of a share action (share users / share projects).
 *
 * @param items       the newly created share rows
 * @param pendingCount how many of the new shares are PENDING (external —
 *                     outside the creator's projects). When &gt; 0 the skill
 *                     moved to PENDING_APPROVAL and the frontend shows the
 *                     "pending admin approval" toast.
 */
public record SkillShareResult(
        List<SkillShareItem> items,
        int pendingCount
) {
}
