package com.furelise.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table: skill ↔ cowork user (skill_cowork_users).
 * A cowork user gets creator-like management rights on the skill
 * (edit, submit/withdraw, version update, delete file) but CANNOT
 * delete the skill — only the creator or an admin can.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_cowork_users")
@IdClass(SkillCoworkUser.SkillCoworkUserKey.class)
public class SkillCoworkUser {

    @Id
    @Column(name = "skill_id")
    private Long skillId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Composite key for the join table. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillCoworkUserKey {
        private Long skillId;
        private Long userId;
    }
}
