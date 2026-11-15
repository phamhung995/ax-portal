package com.furelise.skillmanagement.aitools.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Download log: one row per AI tool download event.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_tool_download_log")
public class AiToolDownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private Instant downloadedAt;

    @PrePersist
    protected void onCreate() {
        if (downloadedAt == null) {
            downloadedAt = Instant.now();
        }
    }
}
