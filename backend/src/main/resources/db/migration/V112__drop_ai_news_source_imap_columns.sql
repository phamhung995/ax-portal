-- V112: drop the legacy IMAP columns from ai_news_source.
--
-- V48 already dropped them when the batch switched from IMAP to the Knox
-- portal web mail API, but V48 shipped before the ImapEmailReader removal
-- was merged, so the columns were re-added out-of-band (V50/V51, not in
-- this repo) to keep dev servers starting. The reader is now
-- KnoxPortalEmailReader and no code references imap_host/imap_port
-- (verified: no Java/frontend references), so the columns can finally go.
--
-- IF EXISTS: on databases where V48's drop was never reversed (fresh DBs
-- built from this repo) the columns are already absent.

ALTER TABLE ai_news_source DROP COLUMN IF EXISTS imap_host;
ALTER TABLE ai_news_source DROP COLUMN IF EXISTS imap_port;
