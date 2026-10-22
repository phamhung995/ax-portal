package com.skillmanagement.ainews.dto;

import java.time.Instant;

/**
 * AI news detail (AI_News_Screen_SRS.md §5.3) — includes the sanitized HTML body.
 */
public record AiNewsDetail(
        Long newsId,
        String title,
        String sender,
        String source,
        String body,
        Instant createdAt,
        int likeCount,
        boolean liked
) {
}
