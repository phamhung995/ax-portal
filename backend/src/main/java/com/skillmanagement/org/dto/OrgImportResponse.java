package com.skillmanagement.org.dto;

import java.util.List;

/**
 * Result of an organization Excel import (validate + save in one step).
 * matchedCount: SUCCESS rows whose Knox ID matched an existing user.
 * deactivatedCount: matched users set to DEACTIVE (Employed = N).
 */
public record OrgImportResponse(
        int totalRows,
        int successCount,
        int errorCount,
        int matchedCount,
        int unmatchedCount,
        int deactivatedCount,
        List<OrgImportRowResult> rows
) {
}
