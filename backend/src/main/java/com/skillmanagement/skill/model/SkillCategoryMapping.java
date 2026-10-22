package com.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table: skill ↔ category (skill_category_mapping).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_category_mapping")
@IdClass(SkillCategoryMapping.SkillCategoryMappingKey.class)
public class SkillCategoryMapping {

    @Id
    @Column(name = "skill_id")
    private Long skillId;

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    /** Composite key for the join table. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillCategoryMappingKey {
        private Long skillId;
        private Long categoryId;
    }
}
