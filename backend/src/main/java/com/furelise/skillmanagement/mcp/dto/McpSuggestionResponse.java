package com.furelise.skillmanagement.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for MCP suggestion (Add/Edit Skill — fuzzy matching).
 *
 * <p>{@code suggestions} — top-3 fuzzy match (weighted overlap) over
 * APPROVED MCPs; the owner's own unapproved MCPs are found via the
 * Browse All MCPs popup.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpSuggestionResponse {

    private List<FuzzySuggestion> suggestions;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FuzzySuggestion {
        private Long mcpId;
        private String mcpCode;
        private String mcpName;
        private String serverKey;
        private Integer currentVersion;
        private double score;
    }
}
