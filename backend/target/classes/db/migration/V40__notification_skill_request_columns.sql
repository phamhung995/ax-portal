-- Skill Forum notifications: deep-link columns for the GNB bell.
-- request_id: the skill request the notification refers to (open detail page).
-- comment_id: the specific comment (null for request-level events).
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS request_id BIGINT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS comment_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_notifications_request ON notifications (request_id);
