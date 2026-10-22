package com.skillmanagement.user.dto;

public record BulkSaveResult(
        int savedCount,
        int skippedCount
) {
}
