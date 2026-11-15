-- Flyway migration V75: skill not-found toast message (en/vi/ko).
--
-- The skill detail popup shows a toast when a skill cannot be opened
-- (deleted, or not visible to the viewer — 404 SKILL_NOT_FOUND). The
-- message is neutral so it covers both cases without revealing whether
-- the skill exists.

INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.notFoundOrNoAccess', 'Skill detail: toast when a skill is deleted or not visible to the viewer')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.notFoundOrNoAccess', 'Skill not found or you do not have permission to view it.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.notFoundOrNoAccess', 'Không tìm thấy skill hoặc bạn không có quyền xem.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.notFoundOrNoAccess', '스킬을 찾을 수 없거나 볼 권한이 없습니다.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
