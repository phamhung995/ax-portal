-- Restore the app.translations.add key (add dialog submit button).
-- The key was seeded in V57 but got deleted from the shared DBs through the
-- admin UI; re-insert it idempotently so environments where it still exists
-- are unaffected.

INSERT INTO translation_keys (key, description)
VALUES ('app.translations.add', 'Translations screen: add button')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', 'Add'
FROM translation_keys
WHERE key = 'app.translations.add'
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', 'Thêm'
FROM translation_keys
WHERE key = 'app.translations.add'
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', '추가'
FROM translation_keys
WHERE key = 'app.translations.add'
ON CONFLICT (key_id, language) DO NOTHING;
