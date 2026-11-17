package com.furelise.skillmanagement.reputation.dto;

import java.math.BigDecimal;
import java.util.List;

/** Human-readable reasons behind a score ("why is this skill TRUSTED?"). */
public record ReputationExplainResponse(
        String targetType,
        Long targetId,
        BigDecimal score,
        String tier,
        List<String> reasons
) {
}
