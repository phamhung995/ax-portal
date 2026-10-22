package com.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A file attached to a skill (multi-file support).
 * The physical file lives on disk under the skill upload directory;
 * only metadata is stored here.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_files")
public class SkillFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    /** Original file name (displayed in the UI, used as download name). */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** Skill-dir-relative path of the stored file (uuid-prefixed). */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 'Y' = uploaded as part of a pending version update (hidden from the
     * live skill until the update is approved). 'N' = live file.
     */
    @Builder.Default
    @Column(name = "pending_yn", nullable = false, length = 1)
    private String pendingYn = "N";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
