package com.furelise.skillmanagement.org.dto;

import java.util.List;

/**
 * Result of a Human Resource List import: how many rows were imported and
 * which rows were invalid.
 */
public record HrImportResponse(
        int rowsCount,
        int errorCount,
        List<SystemImportRowResult> errorRows
) {
}
