package com.skillmanagement.org.dto;

import java.util.List;

/**
 * Per-row result of an organization Excel import.
 * status: SUCCESS / ERROR. errors holds all row-level errors (may be multiple).
 */
public record OrgImportRowResult(
        int rowNumber,
        String knoxId,
        String userName,
        String status,
        List<String> errors
) {
    public static OrgImportRowResult success(int rowNumber, String knoxId, String userName) {
        return new OrgImportRowResult(rowNumber, knoxId, userName, "SUCCESS", List.of());
    }

    public static OrgImportRowResult error(int rowNumber, String knoxId, String userName, List<String> errors) {
        return new OrgImportRowResult(rowNumber, knoxId, userName, "ERROR", errors);
    }
}
