-- ai_news.archived_at — when the row was flipped to archive_yn = 'Y'.
--
-- Purpose: the daily archive image sweep (AiNewsBatchService.runArchiveCleanup)
-- previously selected ALL archived ids (WHERE archive_yn = 'Y') on every run —
-- an unbounded, ever-growing scan. With archived_at the daily sweep only
-- selects ids archived in the last 24h (incremental); a weekly full sweep
-- (WHERE archive_yn = 'Y') keeps the self-healing property (orphan image
-- folders from a crash between the archive flip and the file delete are
-- still cleaned on the next weekly sweep).
--
-- Backfill: existing archived rows get now() (the moment the system starts
-- tracking archive time) — NOT created_at + 90 days. A row archived long
-- ago would get a FUTURE date (created_at + 90d can be days/weeks ahead),
-- which would keep it inside the 24h incremental window (archived_at >=
-- now() - 24h) for up to 90 days after deploy — re-sweeping the old rows
-- daily, exactly what the incremental sweep is meant to avoid. With now()
-- the old rows fall outside the window within 24h; the first weekly full
-- sweep covers them (their folders are already clean → cheap no-ops).

ALTER TABLE ai_news ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE;

UPDATE ai_news
SET archived_at = now()
WHERE archive_yn = 'Y' AND archived_at IS NULL;

-- Incremental sweep query (findArchivedIdsSince).
CREATE INDEX IF NOT EXISTS idx_ai_news_archived_at
    ON ai_news (archived_at) WHERE archive_yn = 'Y';
