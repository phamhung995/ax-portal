-- Flyway migration V86: share popup "Type" column label i18n.
--
-- The Current shares table in the share popup has a "Type" column that
-- was hard-coded; this adds the i18n key (en/vi/ko).

INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.shareTypeCol', 'Share popup: table column "Type"')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.shareTypeCol', 'Type')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.shareTypeCol', 'Loại')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.shareTypeCol', '유형')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
