-- AI News @mention notifications: deep-link column for the GNB bell.
-- news_id: the AI News item the mention happened on (null for other event types).
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS news_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_notifications_news ON notifications (news_id);
-- Bell panel: top-10 latest per user — supports ORDER BY created_at DESC.
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC);
