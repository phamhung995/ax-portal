-- Flyway migration V78: share batch delete i18n keys (en/vi/ko).
--
-- The Current shares table now uses checkbox selection + a Delete button
-- instead of a per-row remove button. Adds select-all/select-row labels
-- and the delete button label; the old per-row "Remove share" key is
-- dropped.

INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.shareSelectAll', 'Share popup: select-all checkbox aria label'),
    ('app.skill.shareSelectRow', 'Share popup: row checkbox aria label'),
    ('app.skill.shareDeleteBtn', 'Share popup: delete selected shares button ({count})')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.shareSelectAll', 'Select all shares'),
    ('app.skill.shareSelectRow', 'Select share'),
    ('app.skill.shareDeleteBtn', 'Delete ({count})')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.shareSelectAll', 'Chọn tất cả'),
    ('app.skill.shareSelectRow', 'Chọn'),
    ('app.skill.shareDeleteBtn', 'Xóa ({count})')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.shareSelectAll', '모두 선택'),
    ('app.skill.shareSelectRow', '선택'),
    ('app.skill.shareDeleteBtn', '삭제 ({count})')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

DELETE FROM translations
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.skill.shareRemove');
DELETE FROM translation_keys WHERE key = 'app.skill.shareRemove';
