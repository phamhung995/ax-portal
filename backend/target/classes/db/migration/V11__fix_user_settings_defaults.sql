-- Flyway migration V11: Fix user_settings boolean defaults
-- PostgreSQL 17 compatible
--
-- Rows created before the Java-side defaults were enforced may have
-- email_notify / inapp_notify = FALSE; the SRS default is TRUE.

UPDATE user_settings SET email_notify = TRUE WHERE email_notify = FALSE;
UPDATE user_settings SET inapp_notify = TRUE WHERE inapp_notify = FALSE;
