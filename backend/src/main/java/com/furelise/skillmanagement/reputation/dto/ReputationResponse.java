package com.furelise.skillmanagement.reputation.dto;

import java.math.BigDecimal;

/** One cached reputation score, as shown to the user. */
public record ReputationResponse(
        String targetType,
        Long targetId,
        BigDecimal score,
        String tier,
        int ratingCount,
        BigDecimal ratingAvg,
        int downloadCount,
        BigDecimal successRate
) {
}
