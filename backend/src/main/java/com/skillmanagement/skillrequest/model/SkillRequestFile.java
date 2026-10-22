package com.skillmanagement.skillrequest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A file attached to a skill request post (image or non-image).
 * The physical file lives on disk under the skill-request upload directory;
 * only metadata is stored here.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_request_files")
public class SkillRequestFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** Original file name (displayed in the UI, used as download name). */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** Skill-request-dir-relative path of the stored file (uuid-prefixed). */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    /** True for image attachments (rendered in the post's image grid). */
    @Column(name = "is_image", nullable = false)
    private boolean isImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
