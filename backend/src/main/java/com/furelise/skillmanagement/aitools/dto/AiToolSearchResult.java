package com.furelise.skillmanagement.aitools.dto;

import java.util.List;
import java.util.Map;

/**
 * AI Tools grid search result (paged).
 */
public record AiToolSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<AiToolListItem> items,
        /**
         * Total tools per process stage (stage id → count) under the current
         * search criteria, independent of paging and of the stageId filter —
         * the Process Map pipeline shows these totals on every stage.
         */
        Map<Long, Long> stageCounts
) {
}
