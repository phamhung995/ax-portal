-- Flyway migration V121: i18n key for the personalized robot greeting.
-- The banner robot (public/home-banner/banner.js) greets the logged-in user
-- by name on its first wave: "Hello {name}, have a nice day!" — HomeView
-- substitutes {name} with the user's display name before posting the string
-- into the iframe.
--
-- NOTE (Flyway): V121 — next free version after V120 (local) / V118 on
-- origin/dev (checked 2026-11-10).

INSERT INTO translation_keys (key, description) VALUES
    ('home.banner.greeting', 'Robot personalized greeting ({name} = logged-in user name)')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('home.banner.greeting', 'Hello {name}, have a nice day! ✨')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('home.banner.greeting', 'Chào {name}, chúc bạn một ngày tốt lành! ✨')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('home.banner.greeting', '안녕하세요 {name}! 좋은 하루 보내세요! ✨')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
