package com.furelise.skillmanagement.reputation.dto;

import java.math.BigDecimal;

/**
 * Result of reporting one install event. duplicate is true when the exact
 * same event (target, user, version) was already recorded — the companion
 * may safely retry a delivery and get this instead of a double count.
 * score/tier are the item's reputation AFTER this event (unchanged when
 * the event was a duplicate).
 */
public record InstallEventResponse(
        String targetType,
        Long targetId,
        boolean duplicate,
        BigDecimal score,
        String tier
) {
}
