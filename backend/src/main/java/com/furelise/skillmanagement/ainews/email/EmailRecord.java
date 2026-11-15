package com.furelise.skillmanagement.ainews.email;

import java.time.Instant;
import java.util.Map;

/**
 * One email returned by the email-reading skill (AI_News_Batch.md §3.1).
 * Output contract: {@code { messageId, subject, from, to, date, bodyText, bodyHtml }}
 * plus the {@code Auto-Submitted} header value needed for the auto-reply filter (§3.3 rule 3)
 * and the inline (multipart/related) images keyed by their {@code Content-ID}
 * (without the {@code <>} delimiters) for the AI News image store.
 */
public record EmailRecord(
        String messageId,
        String subject,
        String from,
        String to,
        Instant date,
        String bodyText,
        String bodyHtml,
        String autoSubmitted,
        Map<String, byte[]> cidImages
) {

    /**
     * True when the email is an auto-reply or a bounce/DSN notification
     * (header {@code Auto-Submitted: auto-replied} or {@code auto-generated},
     * AI_News_Batch.md §3.3 rule 3).
     */
    public boolean isAutoReply() {
        if (autoSubmitted == null) {
            return false;
        }
        String value = autoSubmitted.trim().toLowerCase(java.util.Locale.ROOT);
        return "auto-replied".equals(value) || "auto-generated".equals(value);
    }
}
