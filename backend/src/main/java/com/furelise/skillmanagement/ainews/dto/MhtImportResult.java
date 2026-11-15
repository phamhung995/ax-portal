package com.furelise.skillmanagement.ainews.dto;

/**
 * Per-file result of a manual .mht import (AI_News_Screen_SRS.md v1.14 FR-21).
 *
 * @param index    0-based position of the file in the uploaded batch
 * @param fileName original file name
 * @param status   INSERTED | SKIPPED_DUPLICATE | FAILED
 * @param newsId   ai_news.news_id when INSERTED, else null
 * @param error    human-readable reason when FAILED, else null
 */
public record MhtImportResult(
        int index,
        String fileName,
        String status,
        Long newsId,
        String error
) {
    public static final String STATUS_INSERTED = "INSERTED";
    public static final String STATUS_SKIPPED_DUPLICATE = "SKIPPED_DUPLICATE";
    public static final String STATUS_FAILED = "FAILED";
}
