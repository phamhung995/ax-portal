package com.furelise.skillmanagement.mcp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Download log: one row per public download event (single file or
 * "Download All" zip, current or snapshotted version). Pending-version
 * previews are NOT logged.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "mcp_download_log")
public class McpDownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mcp_id", nullable = false)
    private Long mcpId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** TRUE for "Download All" (zip) downloads, FALSE for single-file. */
    @Column(name = "download_all", nullable = false)
    @Builder.Default
    private boolean downloadAll = false;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private Instant downloadedAt;

    @PrePersist
    protected void onCreate() {
        if (downloadedAt == null) {
            downloadedAt = Instant.now();
        }
    }
}
