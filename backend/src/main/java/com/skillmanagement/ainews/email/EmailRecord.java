package com.skillmanagement.ainews.email;

import java.time.Instant;

/**
 * One email returned by the email-reading skill (AI_News_Batch.md §3.1).
 * Output contract: {@code { messageId, subject, from, to, date, bodyText, bodyHtml }}
 * plus the {@code Auto-Submitted} header value needed for the auto-reply filter (§3.3 rule 3).
 */
public record EmailRecord(
        String messageId,
        String subject,
        String from,
        String to,
        Instant date,
        String bodyText,
        String bodyHtml,
        String autoSubmitted
) {

    /** True when the email is an auto-reply (header {@code Auto-Submitted: auto-replied}). */
    public boolean isAutoReply() {
        return "auto-replied".equalsIgnoreCase(autoSubmitted);
    }
}
