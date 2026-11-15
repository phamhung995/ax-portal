-- Flyway migration V120: rename the home banner slogan + caption i18n keys
-- from `home.banner.slogan.N` / `home.banner.caption` to `banner.slogan.N` /
-- `banner.caption` (all banner text now lives under the `banner.` prefix in
-- the translation_keys "Message term" table).
--
-- The teacher-board writing animation is removed in this change: the robot
-- now says the slogan in its speech bubble, so only the slogan + caption
-- keys are read by the banner (HomeView buildBannerI18n).
--
-- Values are copied from the old keys (idempotent: ON CONFLICT DO NOTHING),
-- then the old keys are deleted.

-- ── New keys ────────────────────────────────────────────────────────────────
INSERT INTO translation_keys (key, description) VALUES
    ('banner.caption', 'Banner slogan caption (speech bubble context)'),
    ('banner.slogan.0', 'Banner speech-bubble slogan 1'),
    ('banner.slogan.1', 'Banner speech-bubble slogan 2'),
    ('banner.slogan.2', 'Banner speech-bubble slogan 3'),
    ('banner.slogan.3', 'Banner speech-bubble slogan 4'),
    ('banner.slogan.4', 'Banner speech-bubble slogan 5')
ON CONFLICT (key) DO NOTHING;

-- Copy the existing translations over to the new keys.
INSERT INTO translations (key_id, language, value, updated_by, updated_at)
SELECT nk.id, t.language, t.value, t.updated_by, t.updated_at
FROM translation_keys nk
JOIN translation_keys ok ON ok.key = 'home.banner.' || substring(nk.key from 8)
JOIN translations t ON t.key_id = ok.id
WHERE nk.key LIKE 'banner.%'
ON CONFLICT (key_id, language) DO NOTHING;

-- ── Drop the old keys (and their translations via FK cascade) ──────────────
DELETE FROM translation_keys WHERE key LIKE 'home.banner.slogan.%';
DELETE FROM translation_keys WHERE key = 'home.banner.caption';
