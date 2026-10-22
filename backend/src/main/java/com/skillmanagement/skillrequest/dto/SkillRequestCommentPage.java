package com.skillmanagement.skillrequest.dto;

import java.util.List;

public record SkillRequestCommentPage(
        long totalCount,
        int page,
        int size,
        List<SkillRequestCommentItem> items
) {
}
