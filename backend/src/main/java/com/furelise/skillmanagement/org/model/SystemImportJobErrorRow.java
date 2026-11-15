package com.furelise.skillmanagement.org.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One invalid row of a System List import, persisted as JSONB on
 * {@link SystemImportJob#errorRows}.
 */
public record SystemImportJobErrorRow(
        int rowNumber,
        String sheet,
        String systemKey,
        java.util.List<String> errors
) {
    @JsonCreator
    public SystemImportJobErrorRow(
            @JsonProperty("rowNumber") int rowNumber,
            @JsonProperty("sheet") String sheet,
            @JsonProperty("systemKey") String systemKey,
            @JsonProperty("errors") java.util.List<String> errors) {
        this.rowNumber = rowNumber;
        this.sheet = sheet;
        this.systemKey = systemKey;
        this.errors = errors;
    }
}
