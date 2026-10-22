-- Re-add the feedback.page_url column.
--
-- The column was dropped directly on the dev database (manual migration
-- V36 "drop feedback page url" — not present in this repo), which broke
-- schema validation at startup: the Feedback entity still maps page_url
-- and the feedback feature (create/detail/email) uses it throughout.
--
-- Restoring the column as defined in the original V29__feedback_table.sql
-- (VARCHAR(500), nullable).

ALTER TABLE feedback ADD COLUMN page_url VARCHAR(500);
