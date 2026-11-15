package com.furelise.skillmanagement.aitools.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * AI Tools grid search criteria:
 * all fields optional; AND between fields, OR within a multi-value field.
 *
 * <p>categoryIds: the tool must have at least one of the given categories.
 * stageId: the tool must be mapped to that process stage (Process Map view).
 * sortBy: "name", "uploader" or "downloadCount" (default "createdDate").
 */
public record AiToolSearchRequest(
        String name,
        List<String> tags,
        Long uploaderId,
        LocalDate createdDateFrom,
        LocalDate createdDateTo,
        List<Long> categoryIds,
        Long stageId,
        Integer page,
        Integer pageSize,
        String sortBy,
        String sortDirection
) {
}
