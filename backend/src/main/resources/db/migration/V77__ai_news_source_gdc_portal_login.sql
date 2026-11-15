-- ============================================================
-- V53: AI News source — guarantee the GDC source is ACTIVE.
--
-- V48 already set mailbox = 'gdcv.cag.ax' (the portal knox id).
-- A live manual run (2026-10-29) confirmed the portal sign-in
-- API (POST /portal/signin/rest/login, loginID field) accepts
-- exactly that knox id — 'gdcv.cag.ax' logs in with
-- loginStatus=SUCCESS. The mailbox value is therefore already
-- correct; this migration only guarantees the source is ACTIVE
-- so the batch job reads it.
-- ============================================================

UPDATE ai_news_source
   SET status = 'ACTIVE', updated_at = now()
 WHERE mailbox = 'gdcv.cag.ax';
