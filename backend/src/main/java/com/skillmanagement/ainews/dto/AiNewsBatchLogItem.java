package com.skillmanagement.ainews.dto;

import java.time.Instant;

/**
 * One batch run log row (AI_News_Batch.md §7.2).
 */
public record AiNewsBatchLogItem(
        Long runId,
        Instant startedAt,
        Instant finishedAt,
        String status,
        int totalFetched,
        int totalInserted,
        int totalSkipped,
        int totalFailed,
        int totalArchived,
        String errorMessage,
        String triggerType
) {
}
