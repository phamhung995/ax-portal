package com.skillmanagement.ainews.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Batch run log (AI_News_Batch.md §6.3) — one row per AI-NEWS-IMPORT run.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_news_batch_log")
public class AiNewsBatchLog {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    public static final String TRIGGER_CRON = "CRON";
    public static final String TRIGGER_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long runId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "total_fetched", nullable = false)
    @Builder.Default
    private Integer totalFetched = 0;

    @Column(name = "total_inserted", nullable = false)
    @Builder.Default
    private Integer totalInserted = 0;

    @Column(name = "total_skipped", nullable = false)
    @Builder.Default
    private Integer totalSkipped = 0;

    @Column(name = "total_failed", nullable = false)
    @Builder.Default
    private Integer totalFailed = 0;

    @Column(name = "total_archived", nullable = false)
    @Builder.Default
    private Integer totalArchived = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "trigger_type", nullable = false, length = 10)
    @Builder.Default
    private String triggerType = TRIGGER_CRON;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
