package com.furelise.skillmanagement.aitools.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A file attached to an AI tool (one file per tool).
 * The physical file lives on disk under the AI tool upload directory;
 * only metadata is stored here.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_tool_files")
public class AiToolFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    /** Original file name (displayed in the UI, used as download name). */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** AI-tool-dir-relative path of the stored file (uuid-prefixed). */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
