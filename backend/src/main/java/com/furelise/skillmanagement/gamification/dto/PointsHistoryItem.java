package com.furelise.skillmanagement.gamification.dto;

import java.time.Instant;

/** One ledger row, as shown in the points history. */
public record PointsHistoryItem(
        Long id,
        int amount,
        String type,
        String reason,
        Instant createdAt
) {
}
