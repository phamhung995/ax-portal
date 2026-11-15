package com.furelise.skillmanagement.skill.dto;

import java.util.List;
import java.util.Map;

public record SkillSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<SkillListItem> items,
        /**
         * Total skills per process stage (stage id → count) under the current
         * search criteria + visibility, independent of paging — the Process
         * Map pipeline shows these totals on every stage.
         */
        Map<Long, Long> stageCounts
) {
}
