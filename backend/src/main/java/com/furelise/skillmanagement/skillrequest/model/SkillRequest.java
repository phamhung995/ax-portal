package com.furelise.skillmanagement.skillrequest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.List;

/**
 * A skill request: a public post in the skill request feed.
 * There is no status/approval workflow — a request is visible to everyone
 * immediately after creation. Nobody can delete a request, not even an admin.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_requests")
public class SkillRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> hashtags;

    @Column(name = "vote_count", nullable = false)
    @Builder.Default
    private Integer voteCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    // ---- Bounty (treo thưởng, V126) ----
    /** Staked points (escrowed at posting). 0 = no bounty. */
    @Column(name = "bounty_points", nullable = false)
    @Builder.Default
    private Integer bountyPoints = 0;

    /** NONE | ACTIVE | FULFILLED | EXPIRED | REFUNDED */
    @Column(name = "bounty_status", length = 20, nullable = false)
    @Builder.Default
    private String bountyStatus = "NONE";

    @Column(name = "bounty_expires_at")
    private Instant bountyExpiresAt;

    @Column(name = "fulfilled_skill_id")
    private Long fulfilledSkillId;

    @Column(name = "fulfilled_by")
    private Long fulfilledBy;

    @Column(name = "bounty_awarded_at")
    private Instant bountyAwardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
