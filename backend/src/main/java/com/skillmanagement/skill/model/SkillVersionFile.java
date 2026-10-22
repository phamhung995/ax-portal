package com.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A file belonging to a snapshotted skill version. Metadata is COPIED from
 * the skill_file row (no FK) because the live row may be deleted later;
 * the physical file on disk is kept for downloads.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_version_file")
public class SkillVersionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;
}
