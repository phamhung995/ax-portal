package com.furelise.skillmanagement.ainews.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mailbox source configuration (AI_News_Batch.md §6.2).
 * Adding/removing mailboxes does not require a code change.
 *
 * <p>The legacy {@code imap_host}/{@code imap_port} columns were dropped by
 * V48, re-added out-of-band (V50/V51, not in this repo) while the
 * {@code ImapEmailReader} removal was still unmerged, and dropped again by
 * V112 once the Knox portal reader was fully in place. No code references
 * them (the reader is {@code KnoxPortalEmailReader}).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_news_source")
public class AiNewsSource {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sourceId;

    /** Knox portal username (the mailbox the batch job logs in with). */
    @Column(nullable = false, unique = true)
    private String mailbox;

    /** Reference to the secret store — never the password itself (BR-NEWS-011). */
    @Column(name = "secret_ref", nullable = false)
    private String secretRef;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    @Column(name = "last_success_run_at")
    private Instant lastSuccessRunAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (status == null) {
            status = STATUS_ACTIVE;
        }
    }
}
