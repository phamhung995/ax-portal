-- ============================================================
-- V48: AI News source — switch read source from IMAP to the Knox
-- C&C Suite portal web mail API (AI_News_Batch.md v1.22).
--
-- (Was V44 — renamed to V48 because V44__skill_download_log.sql already
-- owns version 44; two files with the same Flyway version make the app
-- fail to start in any Flyway-enabled environment.)
--
-- Knox does not expose IMAP (web login via SSO, desktop client uses a
-- proprietary sync protocol), so the batch job reads mailboxes through
-- the portal web API instead:
--   1) drop the IMAP columns (no longer used by the reader)
--   2) the 5 placeholder '@internal' mailboxes have no real portal
--      account yet -> INACTIVE (the job only reads gdcv.cag.ax);
--      re-activate with UPDATE ... status='ACTIVE' + set the
--      AI_NEWS_SECRET_* env once a portal account exists (no deploy)
--   3) mailbox = portal username (no longer an IMAP address)
-- ============================================================

ALTER TABLE ai_news_source DROP COLUMN imap_host;
ALTER TABLE ai_news_source DROP COLUMN imap_port;

UPDATE ai_news_source
   SET status = 'INACTIVE', updated_at = now()
 WHERE mailbox LIKE '%@internal';

UPDATE ai_news_source
   SET mailbox = 'gdcv.cag.ax', updated_at = now()
 WHERE mailbox = 'gdcv.cag.ax@samsung.com';
