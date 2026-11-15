package com.furelise.skillmanagement.skill.dto;

/**
 * One row of the skill share list.
 *
 * @param id             the share row id (for removal)
 * @param shareType      USER or PROJECT
 * @param userId         set for USER shares
 * @param userName       resolved name for USER shares (null when unknown)
 * @param projectKey     set for PROJECT shares
 * @param approvalStatus PENDING | APPROVED | REJECTED
 */
public record SkillShareItem(
        Long id,
        String shareType,
        Long userId,
        String userName,
        String projectKey,
        String approvalStatus
) {
}
