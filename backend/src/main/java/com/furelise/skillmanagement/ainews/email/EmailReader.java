package com.furelise.skillmanagement.ainews.email;

import java.time.Instant;
import java.util.List;

/**
 * Email-reading skill interface (AI_News_Batch.md §3.1).
 *
 * <p>Contract:
 * <ul>
 *   <li>Read-only — never sends, deletes, or flags messages (NFR-NEWS-04).</li>
 *   <li>Timeout 60s per mailbox; any failure (timeout/auth/network) is thrown as
 *       {@link EmailReaderException} so the batch job can log the source error and
 *       continue with the remaining sources (§4.2 step 3b, BR-NEWS-006).</li>
 *   <li>Results are sorted by date ASCENDING (oldest first) — required by
 *       BR-NEWS-009 so the part that overflows the limit is always newer than
 *       {@code last_success_run_at}.</li>
 * </ul>
 */
public interface EmailReader {

    /**
     * Reads up to {@code limit} emails newer than {@code since} from the given mailbox.
     *
     * <p>The portal base URL is global (config {@code ai-news.knox.base-url}) —
     * not a per-call argument.</p>
     *
     * @param username portal mailbox user
     * @param password portal password (from secret store — never logged, BR-NEWS-011)
     * @param folder   mail folder (default {@code INBOX})
     * @param since    only emails with {@code date > since} are returned
     * @param limit    max emails per call
     * @param deadline wall-clock deadline the caller must not exceed (the
     *                 batch job's 10-minute cap, NFR-NEWS-01). The reader
     *                 checks it between pages and between detail fetches and
     *                 throws {@link EmailReaderException} when exceeded —
     *                 without this, a slow portal (20 pages + 50 details ×
     *                 60s timeout) could run one source's read phase for
     *                 ~70 minutes, past the documented cap.
     * @return emails sorted by date ascending (oldest first)
     * @throws EmailReaderException on timeout, auth, network failure, or
     *                              deadline exceeded
     */
    List<EmailRecord> read(String username, String password,
                           String folder, Instant since, int limit, Instant deadline);
}
