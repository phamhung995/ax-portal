package com.skillmanagement.skill.dto;

import java.util.List;

public record SearchUserResult(
        long totalCount,
        int page,
        int pageSize,
        List<SearchUserItem> items
) {
}
