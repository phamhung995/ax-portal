package com.skillmanagement.skill.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Skill grid search criteria (Skill Management SRS §3):
 * all fields optional; AND between fields, OR within a multi-value field.
 *
 * <p>categoryIds: the skill must have at least one of the given categories.
 * stageId: the skill must be mapped to that process stage (Process Map view).
 */
public record SkillSearchRequest(
        String skillName,
        String description,
        List<String> tags,
        Long creatorId,
        LocalDate createdDateFrom,
        LocalDate createdDateTo,
        List<String> statuses,
        List<String> scopes,
        List<Long> categoryIds,
        Long stageId,
        Integer page,
        Integer pageSize,
        String sortBy,
        String sortDirection
) {
}
