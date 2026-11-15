package com.furelise.skillmanagement.org.dto;

import java.util.List;

public record HrSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<HrListItem> items
) {
}
