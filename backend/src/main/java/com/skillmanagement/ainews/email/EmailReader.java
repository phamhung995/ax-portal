package com.skillmanagement.ainews.email;

import java.time.Instant;
import java.util.List;

/**
 * Email-reading skill interface (AI_News_Batch.md §3.1).
 *
 * <p>Contract:
 * <ul>
 *   <li>IMAP READONLY — never sends, deletes, or flags messages (NFR-NEWS-04).</li>
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
     * @param host     IMAP server host
     * @param port     IMAP port (993, SSL)
     * @param username mailbox user
     * @param password mailbox password (from secret store — never logged, BR-NEWS-011)
     * @param folder   IMAP folder (default {@code INBOX})
     * @param since    only emails with {@code date > since} are returned
     * @param limit    max emails per call
     * @return emails sorted by date ascending (oldest first)
     * @throws EmailReaderException on timeout, auth, or network failure
     */
    List<EmailRecord> read(String host, int port, String username, String password,
                           String folder, Instant since, int limit);
}
