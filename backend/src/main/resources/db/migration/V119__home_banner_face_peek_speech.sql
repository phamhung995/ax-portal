-- Flyway migration V119: i18n key for the new "face-peek" robot action.
-- The banner robot (public/home-banner/banner.js) now occasionally flies to
-- the banner center and scales up toward the viewer, as if peering through
-- a window. Its speech bubble needs a translated line.
--
-- NOTE (Flyway): V119 — next free version after V118 on origin/dev
-- (checked 2026-11-09).

INSERT INTO translation_keys (key, description) VALUES
    ('home.banner.speech.facePeek', 'Robot speech: face peek (scales up toward the viewer)')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('home.banner.speech.facePeek', 'Psst... I''m right here! 👀')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('home.banner.speech.facePeek', 'Nè... tôi ở ngay đây! 👀')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('home.banner.speech.facePeek', '이봐요... 여기 있어요! 👀')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
