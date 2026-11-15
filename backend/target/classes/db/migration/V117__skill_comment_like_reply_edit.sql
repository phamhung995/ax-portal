-- Skill comment v2 (2026-11-08): like + reply (depth 2) + edit.
-- Mirrors the AI News comment pattern (ai_news_comment / ai_news_comment_vote).
--
-- skill_comments:
--   parent_id  — NULL = top-level, else id of the top-level comment (depth 2 only)
--   like_count — denormalized "Helpful" counter
--   updated_at — set when the content is edited
--
-- skill_comment_likes — one like per user per comment (composite PK).
--
-- NOTE (Flyway): V117 — dev DB đang ở V116 (verify 2026-11-08).

ALTER TABLE skill_comments
    ADD COLUMN parent_id BIGINT,
    ADD COLUMN like_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMP;

CREATE INDEX idx_skill_comments_parent ON skill_comments (parent_id);

CREATE TABLE skill_comment_likes (
    comment_id BIGINT NOT NULL REFERENCES skill_comments (id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);

INSERT INTO translation_keys (key, description) VALUES
    ('app.skills.commentHelpful', NULL),
    ('app.skills.commentReply', NULL),
    ('app.skills.commentEdited', NULL),
    ('app.skills.commentReplies', NULL),
    ('app.skills.commentEmptyHint', NULL),
    ('app.skills.replyTo', NULL),
    ('app.skills.timeJustNow', NULL),
    ('app.skills.timeMinutesAgo', NULL),
    ('app.skills.timeHoursAgo', NULL),
    ('app.skills.timeDaysAgo', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skills.commentHelpful', 'Helpful'),
    ('app.skills.commentReply', 'Reply'),
    ('app.skills.commentEdited', 'edited'),
    ('app.skills.commentReplies', 'replies'),
    ('app.skills.commentEmptyHint', 'No comments yet — be the first to share your thoughts!'),
    ('app.skills.replyTo', 'Replying to {name}'),
    ('app.skills.timeJustNow', 'just now'),
    ('app.skills.timeMinutesAgo', '{n}m ago'),
    ('app.skills.timeHoursAgo', '{n}h ago'),
    ('app.skills.timeDaysAgo', '{n}d ago')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skills.commentHelpful', 'Hữu ích'),
    ('app.skills.commentReply', 'Trả lời'),
    ('app.skills.commentEdited', 'đã sửa'),
    ('app.skills.commentReplies', 'trả lời'),
    ('app.skills.commentEmptyHint', 'Chưa có comment nào — hãy là người đầu tiên chia sẻ!'),
    ('app.skills.replyTo', 'Đang trả lời {name}'),
    ('app.skills.timeJustNow', 'vừa xong'),
    ('app.skills.timeMinutesAgo', '{n} phút trước'),
    ('app.skills.timeHoursAgo', '{n} giờ trước'),
    ('app.skills.timeDaysAgo', '{n} ngày trước')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skills.commentHelpful', '도움됨'),
    ('app.skills.commentReply', '답장'),
    ('app.skills.commentEdited', '수정됨'),
    ('app.skills.commentReplies', '답장'),
    ('app.skills.commentEmptyHint', '아직 댓글이 없습니다 — 첫 댓글을 남겨보세요!'),
    ('app.skills.replyTo', '{name}님에게 답장 중'),
    ('app.skills.timeJustNow', '방금'),
    ('app.skills.timeMinutesAgo', '{n}분 전'),
    ('app.skills.timeHoursAgo', '{n}시간 전'),
    ('app.skills.timeDaysAgo', '{n}일 전')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
