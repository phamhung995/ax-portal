package com.skillmanagement.ainews.dto;

import java.time.Instant;

/**
 * AI news list row (Home SRS §7.4 / AI_News_Screen_SRS.md §5) — NO body
 * (body can be very large; the detail endpoint returns it).
 */
public record AiNewsItem(
        Long newsId,
        String title,
        String sender,
        String source,
        Instant createdAt
) {
}
