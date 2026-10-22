package com.skillmanagement.user.dto;

import java.util.List;

public record UserSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<UserListItem> items
) {
}
