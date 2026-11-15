package com.furelise.skillmanagement.org.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One invalid row of an Organization List import, persisted as JSONB on
 * {@link OrgImportJob#errorRows}.
 */
public record OrgImportJobErrorRow(
        int rowNumber,
        String knoxId,
        String userName,
        java.util.List<String> errors
) {
    @JsonCreator
    public OrgImportJobErrorRow(
            @JsonProperty("rowNumber") int rowNumber,
            @JsonProperty("knoxId") String knoxId,
            @JsonProperty("userName") String userName,
            @JsonProperty("errors") java.util.List<String> errors) {
        this.rowNumber = rowNumber;
        this.knoxId = knoxId;
        this.userName = userName;
        this.errors = errors;
    }
}
