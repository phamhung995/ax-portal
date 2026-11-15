-- Flyway migration V122: i18n key for the robot's sleep speech bubble.
-- While the robot sleeps (eyes closed) the speech bubble may only show
-- sleep-related lines — the banner cycles between this key and "z Z Z".
-- Any other phrase is suppressed while sleeping (banner.js).
--
-- NOTE (Flyway): V122 — next free version after V121 (local) / V118 on
-- origin/dev (checked 2026-11-10).

INSERT INTO translation_keys (key, description) VALUES
    ('home.banner.speech.sleep', 'Robot speech while sleeping (sleep/rest related only)')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('home.banner.speech.sleep', 'Recharging my energy... z Z Z')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('home.banner.speech.sleep', 'Đang nạp năng lượng... z Z Z')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('home.banner.speech.sleep', '에너지 충전 중... z Z Z')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
