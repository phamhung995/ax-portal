package com.furelise.skillmanagement.org.dto;

import java.util.List;

/**
 * One invalid row of a System List Excel import.
 * sheet: "Systems List" or "Sub Systems List" (so the user knows where to fix).
 */
public record SystemImportRowResult(
        int rowNumber,
        String sheet,
        String systemKey,
        List<String> errors
) {
    public static SystemImportRowResult error(int rowNumber, String sheet, String systemKey, List<String> errors) {
        return new SystemImportRowResult(rowNumber, sheet, systemKey, errors);
    }
}
