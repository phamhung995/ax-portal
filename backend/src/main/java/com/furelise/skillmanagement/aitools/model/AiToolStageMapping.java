package com.furelise.skillmanagement.aitools.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table: AI tool ↔ process stage (ai_tool_stage_mapping).
 * Stages are shared with Skill Management (process_stage).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_tool_stage_mapping")
@IdClass(AiToolStageMapping.AiToolStageMappingKey.class)
public class AiToolStageMapping {

    @Id
    @Column(name = "tool_id")
    private Long toolId;

    @Id
    @Column(name = "stage_id")
    private Long stageId;

    /** Composite key for the join table. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiToolStageMappingKey {
        private Long toolId;
        private Long stageId;
    }
}
