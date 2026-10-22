package com.skillmanagement.user.dto;

import java.util.List;

public record BulkUploadResponse(
        int totalRows,
        int successCount,
        int errorCount,
        List<BulkRowResult> rows
) {
}
