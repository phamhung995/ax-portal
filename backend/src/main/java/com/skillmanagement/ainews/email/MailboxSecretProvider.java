package com.skillmanagement.ainews.email;

/**
 * Resolves mailbox passwords from the secret store (BR-NEWS-011).
 * The DB only holds a {@code secret_ref} — the password itself is never
 * persisted and never logged.
 */
public interface MailboxSecretProvider {

    /**
     * Resolves the password for a secret reference.
     *
     * @param secretRef the {@code ai_news_source.secret_ref} value
     * @return the password, or null when the secret is unknown
     */
    String resolve(String secretRef);
}
