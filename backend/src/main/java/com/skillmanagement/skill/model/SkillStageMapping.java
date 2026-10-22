package com.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table: skill ↔ process stage (skill_stage_mapping).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_stage_mapping")
@IdClass(SkillStageMapping.SkillStageMappingKey.class)
public class SkillStageMapping {

    @Id
    @Column(name = "skill_id")
    private Long skillId;

    @Id
    @Column(name = "stage_id")
    private Long stageId;

    /** Composite key for the join table. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillStageMappingKey {
        private Long skillId;
        private Long stageId;
    }
}
