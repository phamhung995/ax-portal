package com.furelise.skillmanagement.feedback.dto;

import java.util.List;

public record FeedbackSearchResult(
        long totalCount,
        int page,
        int pageSize,
        List<FeedbackItem> items
) {}
