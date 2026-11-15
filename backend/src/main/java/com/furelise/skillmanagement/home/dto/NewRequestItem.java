package com.furelise.skillmanagement.home.dto;

import java.time.Instant;

/**
 * A Feedback (VOC) row in the New Request card (Home SRS v1.25 §7.3).
 * The screenshot field is NOT included here (it can be a multi-MB
 * base64 string) — it is fetched only in the detail call.
 */
public record NewRequestItem(
        Long feedbackId,
        String content,
        String type,
        String status,
        String userName,
        Instant createdAt
) {
}
