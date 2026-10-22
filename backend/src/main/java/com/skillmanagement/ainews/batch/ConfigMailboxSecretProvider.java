package com.skillmanagement.ainews.batch;

import com.skillmanagement.ainews.email.MailboxSecretProvider;
import org.springframework.stereotype.Component;

/**
 * Resolves mailbox passwords from the {@code ai-news.mailbox-secrets} config
 * map (secret_ref → password). The DB only stores the {@code secret_ref}
 * (BR-NEWS-011); the values are supplied via env vars on the server and are
 * never logged.
 */
@Component
public class ConfigMailboxSecretProvider implements MailboxSecretProvider {

    private final AiNewsBatchProperties properties;

    public ConfigMailboxSecretProvider(AiNewsBatchProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        return properties.getMailboxSecrets().get(secretRef.trim());
    }
}
