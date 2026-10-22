package com.skillmanagement.home.dto;

import java.time.Instant;

/**
 * A Feature Request row in the New Request card (Home SRS §7.3).
 * NOTE: Feature Request module is not yet specified (no SRS in repo) —
 * the endpoint currently returns an empty list.
 */
public record NewRequestItem(
        String requestId,
        String title,
        String category,
        String status,
        String requesterName,
        Instant createdAt
) {
}
