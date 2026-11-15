-- pg_trgm GIN indexes for the AI News search (title/sender ILIKE '%...%').
-- The search query (AiNewsRepository.findActiveSearch) uses
-- `title ILIKE '%' || :title || '%'` / `sender ILIKE '%' || :sender || '%'`,
-- which is a sequential scan without trigram indexes. With pg_trgm GIN
-- indexes the same query uses the index (leading-wildcard ILIKE is
-- supported by the trigram operator class).
--
-- The extension is created in the public schema; it is idempotent.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_ai_news_title_trgm
    ON ai_news USING gin (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_ai_news_sender_trgm
    ON ai_news USING gin (sender gin_trgm_ops);
