package com.furelise.skillmanagement.aitools.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table: AI tool ↔ category (ai_tool_category_mapping).
 * Categories are shared with Skill Management (skill_category).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_tool_category_mapping")
@IdClass(AiToolCategoryMapping.AiToolCategoryMappingKey.class)
public class AiToolCategoryMapping {

    @Id
    @Column(name = "tool_id")
    private Long toolId;

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    /** Composite key for the join table. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiToolCategoryMappingKey {
        private Long toolId;
        private Long categoryId;
    }
}
