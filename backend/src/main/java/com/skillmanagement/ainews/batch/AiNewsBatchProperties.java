package com.skillmanagement.ainews.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * AI News batch job configuration (AI_News_Batch.md §4.1, §5).
 * Bound from the {@code ai-news.*} block in application.yml.
 *
 * <p>{@code mailbox-secrets} maps {@code ai_news_source.secret_ref} → password.
 * The DB only stores the reference (BR-NEWS-011); the values are supplied via
 * env vars on the server, never committed.
 */
@ConfigurationProperties(prefix = "ai-news")
public class AiNewsBatchProperties {

    /** Cron schedule — 00:00 and 12:00 daily (§4.1). Spring 6-field: sec min hour dom mon dow. */
    private String cron = "0 0 0,12 * * ?";

    /** Max emails per source per run (BR-NEWS-009). */
    private int fetchLimit = 50;

    /** Import window: emails older than this are skipped (BR-NEWS-003). */
    private int importWindowDays = 2;

    /** Archive cutoff: news older than this is archived (BR-NEWS-010). */
    private int archiveDays = 90;

    /**
     * Fallback IMAP host for sources whose {@code imap_host} is still a
     * placeholder (e.g. {@code imap.internal}). The real host is environment
     * specific and supplied via env var / application-local.yml.
     */
    private String defaultImapHost = "";

    /** secret_ref → mailbox password (supplied via env vars, never in DB). */
    private Map<String, String> mailboxSecrets = new HashMap<>();

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getFetchLimit() {
        return fetchLimit;
    }

    public void setFetchLimit(int fetchLimit) {
        this.fetchLimit = fetchLimit;
    }

    public int getImportWindowDays() {
        return importWindowDays;
    }

    public void setImportWindowDays(int importWindowDays) {
        this.importWindowDays = importWindowDays;
    }

    public int getArchiveDays() {
        return archiveDays;
    }

    public void setArchiveDays(int archiveDays) {
        this.archiveDays = archiveDays;
    }

    public String getDefaultImapHost() {
        return defaultImapHost;
    }

    public void setDefaultImapHost(String defaultImapHost) {
        this.defaultImapHost = defaultImapHost;
    }

    public Map<String, String> getMailboxSecrets() {
        return mailboxSecrets;
    }

    public void setMailboxSecrets(Map<String, String> mailboxSecrets) {
        this.mailboxSecrets = mailboxSecrets;
    }
}
