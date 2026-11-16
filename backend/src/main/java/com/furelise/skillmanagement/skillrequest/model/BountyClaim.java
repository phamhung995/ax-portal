package com.furelise.skillmanagement.skillrequest.model;

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

/**
 * A bounty claim: one answer to a request's bounty (guide §6, Phần 06).
 * Two ways to fulfil: BUILD (claimant created the skill) or MATCH (claimant
 * refers to an existing skill). The requester decides; silence past
 * decide_by auto-awards the earliest qualifying claim; DISPUTED goes to
 * the admin queue (the exception path, never the main flow).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "bounty_claims")
public class BountyClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "claimant_id", nullable = false)
    private Long claimantId;

    /** BUILD | MATCH */
    @Column(name = "claim_type", length = 10, nullable = false)
    private String claimType;

    /** PENDING | ACCEPTED | REJECTED | DISPUTED */
    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    /** Claim created_at + 7 days: requester must decide by then. */
    @Column(name = "decide_by", nullable = false)
    private Instant decideBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
