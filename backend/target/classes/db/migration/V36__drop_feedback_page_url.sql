-- Feedback: remove page_url (page context no longer collected or displayed).
ALTER TABLE feedback DROP COLUMN IF EXISTS page_url;
