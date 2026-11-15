package com.furelise.skillmanagement.org.dto;

import java.time.Instant;
import java.util.List;

/**
 * Import job state for the Organization tab UI: the newest finished run,
 * the pending file (if any), and whether a run is in progress.
 */
public record OrgImportStatus(
        boolean running,
        OrgImportJobInfo lastImport,
        OrgImportJobInfo pendingFile
) {
    public record OrgImportJobInfo(
            Long id,
            String fileName,
            String uploadedBy,
            Instant uploadedAt,
            String status,
            Integer rowsCount,
            Integer successCount,
            Integer errorCount,
            Integer matchedCount,
            Integer deactivatedCount,
            List<OrgImportRowResult> errorRows,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
