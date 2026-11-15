-- AI News comments: rich text (HTML from Tiptap) instead of plain text.
--
-- content was VARCHAR(1000) (V24). Rich text HTML — and base64 data-URI
-- images embedded by the editor — can exceed 1000 characters, so the
-- column becomes TEXT. The 1000-character limit still applies to the
-- PLAIN TEXT of the comment (enforced in AiNewsServiceImpl.addComment).
-- Existing plain-text comments render unchanged through the same HTML
-- pipeline (no data migration needed).

ALTER TABLE ai_news_comment
    ALTER COLUMN content TYPE TEXT;
