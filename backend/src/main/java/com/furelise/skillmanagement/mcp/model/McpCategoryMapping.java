package com.furelise.skillmanagement.mcp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table: MCP ↔ category (mcp_category_mapping).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "mcp_category_mapping")
@IdClass(McpCategoryMapping.McpCategoryMappingKey.class)
public class McpCategoryMapping {

    @Id
    @Column(name = "mcp_id")
    private Long mcpId;

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    /** Composite key for the join table. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpCategoryMappingKey {
        private Long mcpId;
        private Long categoryId;
    }
}
