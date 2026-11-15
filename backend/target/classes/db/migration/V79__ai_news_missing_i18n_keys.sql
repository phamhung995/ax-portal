-- i18n keys for the AI News screen that were missing from V56__translations.sql.
-- The ai-news feature migrated from its local i18n file to the shared
-- DB-driven store (stores/i18n.ts); these three keys are still used by
-- AiNewsView.vue / useAiNewsComments.ts but were not included in V56.

INSERT INTO translation_keys (key, description) VALUES
    ('aiNews.commentsLoadFail', 'AI News: comments load failure toast'),
    ('aiNews.likeFail', 'AI News: like update failure toast'),
    ('aiNews.viewers', 'AI News: viewer count label')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('aiNews.commentsLoadFail', 'Failed to load comments.'),
    ('aiNews.likeFail', 'Could not update like. Please try again.'),
    ('aiNews.viewers', '{n} viewers')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('aiNews.commentsLoadFail', 'Không tải được bình luận.'),
    ('aiNews.likeFail', 'Không thể cập nhật thích. Vui lòng thử lại.'),
    ('aiNews.viewers', '{n} người đã xem')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('aiNews.commentsLoadFail', '댓글을 불러오지 못했습니다.'),
    ('aiNews.likeFail', '좋아요를 업데이트하지 못했습니다. 다시 시도해 주세요.'),
    ('aiNews.viewers', '{n}명 열람')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
