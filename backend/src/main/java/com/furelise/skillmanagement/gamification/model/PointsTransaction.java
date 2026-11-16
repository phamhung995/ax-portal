package com.furelise.skillmanagement.gamification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One points movement. The ledger is the audit trail; balance is a cache. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "points_transactions")
public class PointsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** +earn / -spend. */
    @Column(nullable = false)
    private int amount;

    /** CHECK_IN | PUBLISH_SKILL | INSTALL | RATING | COMMENT | UPVOTE |
     *  READ_NEWS | BOUNTY_LOCK | BOUNTY_AWARD | BOUNTY_REFUND | PERK_REDEEM |
     *  STREAK_BONUS | PENALTY */
    @Column(length = 30, nullable = false)
    private String type;

    @Column(length = 300)
    private String reason;

    /** SKILL | SKILL_REQUEST | PERK | ... */
    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
