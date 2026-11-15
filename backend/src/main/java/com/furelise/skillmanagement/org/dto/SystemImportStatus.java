package com.furelise.skillmanagement.org.dto;

import java.time.Instant;

/**
 * Import job state for the Systems tab UI: the newest finished run, the
 * pending file (if any), and whether a run is in progress.
 */
public record SystemImportStatus(
        boolean running,
        SystemImportJobInfo lastImport,
        SystemImportJobInfo pendingFile
) {
    public record SystemImportJobInfo(
            Long id,
            String fileName,
            String uploadedBy,
            Instant uploadedAt,
            String status,
            Integer systemsCount,
            Integer subSystemsCount,
            Integer errorCount,
            java.util.List<SystemImportRowResult> errorRows,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
