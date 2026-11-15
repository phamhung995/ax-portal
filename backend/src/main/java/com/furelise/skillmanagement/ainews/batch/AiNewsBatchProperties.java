package com.furelise.skillmanagement.ainews.batch;

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

    /**
     * Master switch for the scheduled batch. Defaults to false (batch
     * paused). Set to true (application.yml or AI_NEWS_ENABLED=true) to
     * re-enable the scheduled import.
     */
    private boolean enabled = false;

    /** Cron schedule — 00:00 and 12:00 daily (§4.1). Spring 6-field: sec min hour dom mon dow. */
    private String cron = "0 0 0,12 * * ?";

    /** Max emails per source per run (BR-NEWS-009). */
    private int fetchLimit = 50;

    /** Import window: emails older than this are skipped (BR-NEWS-003). */
    private int importWindowDays = 2;

    /** Archive cutoff: news older than this is archived (BR-NEWS-010). */
    private int archiveDays = 90;

    /**
     * Knox portal configuration — base URL + API paths/field names
     * (AI_News_Batch.md §3.1). Defaults are the values probed from the
     * portal SPA bundles; the probe (scripts/probe_knox_portal.py) may
     * adjust field names via env vars without a code change.
     */
    private Knox knox = new Knox();

    /** secret_ref → mailbox password (supplied via env vars, never in DB). */
    private Map<String, String> mailboxSecrets = new HashMap<>();

    /**
     * Ops recipient for the BR-NEWS-012 alert (FAILED/PARTIAL 3 consecutive
     * runs). Empty by default — when unset, the alert is log-only. Supplied
     * via env var {@code AI_NEWS_ALERT_EMAIL}, never committed.
     */
    private String alertEmail = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public Knox getKnox() {
        return knox;
    }

    public void setKnox(Knox knox) {
        this.knox = knox;
    }

    public Map<String, String> getMailboxSecrets() {
        return mailboxSecrets;
    }

    public void setMailboxSecrets(Map<String, String> mailboxSecrets) {
        this.mailboxSecrets = mailboxSecrets;
    }

    public String getAlertEmail() {
        return alertEmail;
    }

    public void setAlertEmail(String alertEmail) {
        this.alertEmail = alertEmail;
    }

    /**
     * Knox portal (Knox C&C Suite web mail) connection settings.
     *
     * <p>Auth: the reader logs into the portal sign-in API with the mailbox
     * user/password (secret store) and reuses the session cookie until it
     * expires. Paths and field names default to the values probed from the
     * portal SPA bundles (scripts/probe_knox_portal.py) and stay configurable
     * so a contract change only needs a config update.</p>
     */
    public static class Knox {

        /** Portal host (mail API base URL). */
        private String baseUrl = "http://kor5.samsung.net";

        /** Portal sign-in host (login API base URL). */
        private String loginBaseUrl = "https://www.samsung.net";

        /** Sign-in endpoint (POST, JSON body). */
        private String loginPath = "/portal/signin/rest/login";

        /** Login request field names (verified against the real loginapp
         *  SPA payload — note the capital ID and the userPassword name). */
        private String loginUserIdField = "loginID";
        private String loginPasswordField = "userPassword";

        /** Login response field holding the status (e.g. SUCCESS / NOT_EXIST_ID). */
        private String loginStatusField = "loginStatus";

        /** Mail list endpoint (POST, query params + JSON body). */
        private String mailListPath = "/mail/rest/v1/mails/list";

        /** Mail detail endpoint (POST, JSON body). */
        private String mailReadPath = "/mail/rest/v1/mails/read";

        /**
         * .mht download endpoint (POST, empty JSON body, query params
         * folderID + mailSeq). Returns the mail as a MIME message/rfc822
         * (.mht) with all inline image parts — the same format the manual
         * import accepts. Used by the batch reader so imported news carries
         * the real Message-ID (dedup matches manual imports), the inline
         * images (cidImages) and the Auto-Submitted header.
         */
        private String mhtmlDownloadPath = "/mail/rest/v1/mails/mhtml/download";

        /** Per-page size for the mail list. */
        private int pageSize = 50;

        /** List response: field holding the array of mail headers. */
        private String listItemsField = "headerVOs";

        /** Mail header field names (list + detail) — verified against the
         *  live portal mail API (2026-10-29). */
        private String mailIdField = "mailID";
        private String subjectField = "subject";
        private String senderField = "fromAddress";
        private String senderNameField = "fromName";
        private String dateField = "date";

        /** List-item fields required by the detail (read) call as query params. */
        private String mailSeqField = "mailSeq";
        private String folderIdField = "folderID";

        /** Detail response: field holding the HTML body. */
        private String bodyHtmlField = "contentHtml";

        /**
         * Time zone for zone-less mail dates ({@code yyyyMMddHHmmss},
         * {@code yyyy-MM-dd HH:mm:ss}, {@code yyyyMMdd}). The portal is a
         * Samsung Korea service — its zone-less timestamps are KST. The prod
         * JVM runs UTC, so interpreting them in {@code systemDefault()}
         * would shift every such date by 7 hours (wrong created_at + a
         * BR-NEWS-009 marker in the shifted instant space that can drop
         * boundary emails permanently).
         */
        private String dateZone = "Asia/Seoul";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getLoginBaseUrl() {
            return loginBaseUrl;
        }

        public void setLoginBaseUrl(String loginBaseUrl) {
            this.loginBaseUrl = loginBaseUrl;
        }

        public String getLoginPath() {
            return loginPath;
        }

        public void setLoginPath(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getLoginUserIdField() {
            return loginUserIdField;
        }

        public void setLoginUserIdField(String loginUserIdField) {
            this.loginUserIdField = loginUserIdField;
        }

        public String getLoginPasswordField() {
            return loginPasswordField;
        }

        public void setLoginPasswordField(String loginPasswordField) {
            this.loginPasswordField = loginPasswordField;
        }

        public String getLoginStatusField() {
            return loginStatusField;
        }

        public void setLoginStatusField(String loginStatusField) {
            this.loginStatusField = loginStatusField;
        }

        public String getMailListPath() {
            return mailListPath;
        }

        public void setMailListPath(String mailListPath) {
            this.mailListPath = mailListPath;
        }

        public String getMailReadPath() {
            return mailReadPath;
        }

        public void setMailReadPath(String mailReadPath) {
            this.mailReadPath = mailReadPath;
        }

        public String getMhtmlDownloadPath() {
            return mhtmlDownloadPath;
        }

        public void setMhtmlDownloadPath(String mhtmlDownloadPath) {
            this.mhtmlDownloadPath = mhtmlDownloadPath;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public String getListItemsField() {
            return listItemsField;
        }

        public void setListItemsField(String listItemsField) {
            this.listItemsField = listItemsField;
        }

        public String getMailIdField() {
            return mailIdField;
        }

        public void setMailIdField(String mailIdField) {
            this.mailIdField = mailIdField;
        }

        public String getMailSeqField() {
            return mailSeqField;
        }

        public void setMailSeqField(String mailSeqField) {
            this.mailSeqField = mailSeqField;
        }

        public String getFolderIdField() {
            return folderIdField;
        }

        public void setFolderIdField(String folderIdField) {
            this.folderIdField = folderIdField;
        }

        public String getSubjectField() {
            return subjectField;
        }

        public void setSubjectField(String subjectField) {
            this.subjectField = subjectField;
        }

        public String getSenderField() {
            return senderField;
        }

        public void setSenderField(String senderField) {
            this.senderField = senderField;
        }

        public String getSenderNameField() {
            return senderNameField;
        }

        public void setSenderNameField(String senderNameField) {
            this.senderNameField = senderNameField;
        }

        public String getDateField() {
            return dateField;
        }

        public void setDateField(String dateField) {
            this.dateField = dateField;
        }

        public String getBodyHtmlField() {
            return bodyHtmlField;
        }

        public void setBodyHtmlField(String bodyHtmlField) {
            this.bodyHtmlField = bodyHtmlField;
        }

        public String getDateZone() {
            return dateZone;
        }

        public void setDateZone(String dateZone) {
            this.dateZone = dateZone;
        }
    }
}
