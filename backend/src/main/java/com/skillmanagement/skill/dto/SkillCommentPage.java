package com.skillmanagement.skill.dto;

import java.util.List;

public record SkillCommentPage(
        long totalCount,
        int page,
        int size,
        List<SkillCommentItem> items
) {
}
