package com.furelise.skillmanagement.org.dto;

import java.util.List;

public record SystemSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<SystemListItem> items
) {
}
