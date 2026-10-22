package com.skillmanagement.ainews.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mailbox source configuration (AI_News_Batch.md §6.2).
 * Adding/removing mailboxes does not require a code change.
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

    /** IMAP user. */
    @Column(nullable = false, unique = true)
    private String mailbox;

    @Column(name = "imap_host", nullable = false)
    private String imapHost;

    @Column(name = "imap_port", nullable = false)
    @Builder.Default
    private Short imapPort = 993;

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
        if (imapPort == null) {
            imapPort = 993;
        }
    }
}
