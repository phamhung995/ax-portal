package com.skillmanagement.ainews.email;

/**
 * Thrown by the email-reading skill on timeout, auth, or network failure.
 * The batch job catches it per source, logs the source error, and continues
 * with the remaining sources (AI_News_Batch.md §4.2 step 3b, BR-NEWS-006).
 * The message never contains credentials (BR-NEWS-011).
 */
public class EmailReaderException extends RuntimeException {

    public EmailReaderException(String message) {
        super(message);
    }

    public EmailReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
