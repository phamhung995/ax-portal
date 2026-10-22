-- Flyway migration V32: drop the feedback.content 1000-char CHECK constraint.
--
-- V29 created the feedback table with CHECK (char_length(content) <= 1000).
-- Content is now rich text (HTML) stored in a TEXT column with no length
-- limit (the 1000-char limit is enforced client-side on stripped text only).
-- The constraint name is derived from the table + column name by PostgreSQL.

ALTER TABLE feedback
    DROP CONSTRAINT IF EXISTS feedback_content_check;
