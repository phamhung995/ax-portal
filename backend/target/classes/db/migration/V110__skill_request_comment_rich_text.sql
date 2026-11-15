-- Skill Market comments move to rich text (Tiptap HTML, same as AI News
-- comments in V42): the 1000-char CHECK on the raw column is no longer
-- meaningful — the API now enforces a 1000-char PLAIN-TEXT limit and a
-- 20 KB sanitized-HTML cap in code (SkillRequestServiceImpl).

ALTER TABLE skill_request_comments
    DROP CONSTRAINT IF EXISTS skill_request_comments_content_check;
