package com.furelise.skillmanagement.reputation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One install outcome reported by the companion app (GĐ6, BA FR-6).
 * The success rate over these rows (Wilson lower bound) enters the
 * Skill/MCP score once >= 5 installs exist for an item (BA §9.1).
 * user_id is nullable: the companion may report an install without a
 * logged-in user. version is the skill/MCP version that was installed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "install_events", uniqueConstraints = @UniqueConstraint(
        name = "uk_install_event",
        columnNames = {"target_type", "target_id", "user_id", "version"}))
public class InstallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 50)
    private String version;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (reportedAt == null) {
            reportedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
