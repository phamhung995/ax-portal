-- Flyway migration V30: link notifications to feedback
-- PostgreSQL 17 compatible
--
-- When a feedback is resolved/ignored, the creator receives a notification.
-- The bell needs the feedback id to deep-link to the feedback detail popup.

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS feedback_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_notifications_feedback ON notifications (feedback_id);
