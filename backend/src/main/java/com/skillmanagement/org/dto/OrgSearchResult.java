package com.skillmanagement.org.dto;

import java.util.List;

public record OrgSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<OrgListItem> items
) {
}
