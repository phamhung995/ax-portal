package com.furelise.skillmanagement.reputation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contributor (author) reputation — derived from the reputation of the items
 * the user created, plus fulfilled requests and reports. Kept SEPARATE from
 * item scores: an author's tier never adds points to their items.
 * Tier names are the author's own (not the content tiers):
 * NEW_MEMBER | VERIFIED | TRUSTED_AUTHOR | EXPERT.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "contributor_reputation")
public class ContributorReputation {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String tier = "NEW_MEMBER";

    @Column(name = "item_count", nullable = false)
    @Builder.Default
    private int itemCount = 0;

    @Column(name = "avg_score", precision = 5, scale = 2)
    private BigDecimal avgScore;

    @Column(name = "fulfilled_requests", nullable = false)
    @Builder.Default
    private int fulfilledRequests = 0;

    @Column(name = "report_count", nullable = false)
    @Builder.Default
    private int reportCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
