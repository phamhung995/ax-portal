package com.skillmanagement.ainews.dto;

/**
 * Manual-run result (AI_News_Batch.md §7.1 success response).
 */
public record AiNewsRunResult(
        Long runId,
        String status,
        int totalInserted,
        int totalSkipped,
        int totalFailed,
        int totalArchived
) {
}
