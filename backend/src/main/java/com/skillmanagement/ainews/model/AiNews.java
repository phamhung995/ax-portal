package com.skillmanagement.ainews.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI news item (AI_News_Batch.md §6.1). The batch job is the only writer
 * (BR-NEWS-001); the public API is read-only.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_news")
public class AiNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long newsId;

    /** Email Message-ID (dedup, unique). */
    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(nullable = false)
    private String title;

    /** Sanitized HTML email body (nullable — email without body). */
    @Column(columnDefinition = "TEXT")
    private String body;

    /** Email `from` (name + address). */
    private String sender;

    /** Display name of the mailbox source. */
    @Column(nullable = false)
    private String source;

    /** Email date. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 'Y' = archived (older than 90 days, hidden from UI). */
    @Column(name = "archive_yn", nullable = false, length = 1)
    @Builder.Default
    private String archiveYn = "N";

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) {
            importedAt = Instant.now();
        }
        if (archiveYn == null) {
            archiveYn = "N";
        }
    }
}
