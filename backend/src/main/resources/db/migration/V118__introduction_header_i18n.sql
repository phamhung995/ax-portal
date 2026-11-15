-- Introduction page button (2026-11-08): header link to /introduction.html
-- (local + dev only, decided at runtime by hostname — see
-- frontend/src/config/introduction.ts).
--
-- NOTE (Flyway): V118 — dev DB đang ở V117 (verify 2026-11-08).

INSERT INTO translation_keys (key, description) VALUES
    ('app.header.introduction', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.header.introduction', 'Introduction')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.header.introduction', 'Giới thiệu')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.header.introduction', '소개')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
