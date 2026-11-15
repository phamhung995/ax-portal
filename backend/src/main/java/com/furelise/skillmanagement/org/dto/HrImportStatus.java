package com.furelise.skillmanagement.org.dto;

import java.time.Instant;

/**
 * Import job state for the Human Resource tab UI: the newest finished run,
 * the pending file (if any), and whether a run is in progress.
 */
public record HrImportStatus(
        boolean running,
        HrImportJobInfo lastImport,
        HrImportJobInfo pendingFile
) {
    public record HrImportJobInfo(
            Long id,
            String fileName,
            String uploadedBy,
            Instant uploadedAt,
            String status,
            Integer rowsCount,
            Integer errorCount,
            java.util.List<SystemImportRowResult> errorRows,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
