package com.furelise.skillmanagement.mcp.dto;

import java.util.List;
import java.util.Map;

public record McpSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<McpListItem> items,
        /**
         * Total MCPs per process stage (stage id → count) under the current
         * search criteria + visibility, independent of paging.
         */
        Map<Long, Long> stageCounts
) {
}
