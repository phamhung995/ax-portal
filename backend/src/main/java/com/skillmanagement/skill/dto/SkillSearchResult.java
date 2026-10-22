package com.skillmanagement.skill.dto;

import java.util.List;

public record SkillSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<SkillListItem> items
) {
}
