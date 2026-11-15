package com.furelise.skillmanagement.org.dto;

import java.util.List;

/**
 * Result of a System List Excel import (full replace).
 * Only error rows are returned — the file can hold tens of thousands of
 * rows, so the payload must stay small.
 */
public record SystemImportResponse(
        int systemsCount,
        int subSystemsCount,
        int errorCount,
        List<SystemImportRowResult> errorRows
) {
}
