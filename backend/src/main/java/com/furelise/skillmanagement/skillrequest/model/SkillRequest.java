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

    /**
     * Fulfillment (GĐ5, BA §8.4 / Q8 chốt 18/11): a request is FULFILLED when
     * the requester accepts a linked APPROVED skill, or auto-confirmed after
     * 7 days of silence if the linked skill still meets the bar (APPROVED).
     * Status: OPEN | PENDING_CONFIRM | FULFILLED.
     */
    public static final String FULFILLMENT_OPEN = "OPEN";
    public static final String FULFILLMENT_PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String FULFILLMENT_FULFILLED = "FULFILLED";

    @Column(name = "fulfilled_skill_id")
    private Long fulfilledSkillId;

    @Column(name = "fulfillment_status", nullable = false)
    @Builder.Default
    private String fulfillmentStatus = FULFILLMENT_OPEN;

    /** Requester decision deadline (propose time + 7 days); null unless pending. */
    @Column(name = "fulfillment_deadline")
    private Instant fulfillmentDeadline;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
