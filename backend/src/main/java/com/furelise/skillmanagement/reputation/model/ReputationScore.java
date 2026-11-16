package com.furelise.skillmanagement.reputation.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Cached reputation score for one object (skill, MCP, AI tool, news item,
 * skill request). The score is DERIVED from the module's signal tables and
 * recomputed by {@code ReputationService}; never written by hand.
 * score NULL means "not enough evidence" (tier NEW).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "reputation_scores")
public class ReputationScore {

    @EmbeddedId
    private ReputationScoreId id;

    /** 0-100, NULL when there is not enough evidence. */
    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    /** NEW | TRUSTED | POPULAR (shared tier labels for content). */
    @Column(length = 20, nullable = false)
    @Builder.Default
    private String tier = "NEW";

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private int ratingCount = 0;

    /** Bayesian average (0-5) when the object has star ratings. */
    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private int downloadCount = 0;

    /** Wilson success rate, when install events exist. */
    @Column(name = "success_rate", precision = 5, scale = 4)
    private BigDecimal successRate;

    /** Raw breakdown for the "why this rank?" explainer. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> signals;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String targetType() {
        return id != null ? id.getTargetType() : null;
    }

    public Long targetId() {
        return id != null ? id.getTargetId() : null;
    }
}
