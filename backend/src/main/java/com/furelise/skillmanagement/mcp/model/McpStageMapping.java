package com.furelise.skillmanagement.mcp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table: MCP ↔ process stage (mcp_stage_mapping).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "mcp_stage_mapping")
@IdClass(McpStageMapping.McpStageMappingKey.class)
public class McpStageMapping {

    @Id
    @Column(name = "mcp_id")
    private Long mcpId;

    @Id
    @Column(name = "stage_id")
    private Long stageId;

    /** Composite key for the join table. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpStageMappingKey {
        private Long mcpId;
        private Long stageId;
    }
}
