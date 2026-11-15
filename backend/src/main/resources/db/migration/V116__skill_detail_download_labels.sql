-- Skill Detail redesign (2026-11-07, follow-up): rename the per-section
-- "Download All" pills so each one says what it actually downloads:
--   📄 Skill section   → "Download Skill"  (app.skills.downloadSkillFiles)
--   🔗 MCP section     → "Download MCP"    (app.skills.downloadMcps)
-- The hero CTA now reuses the existing app.skills.downloadAll ("Download All").
-- app.skills.downloadSkill (V115) is no longer used — cleaned up here.
--
-- NOTE (Flyway): V116 — dev DB đã ở V115 (verify 2026-11-07).

INSERT INTO translation_keys (key, description) VALUES
    ('app.skills.downloadSkillFiles', NULL),
    ('app.skills.downloadMcps', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skills.downloadSkillFiles', 'Download Skill'),
    ('app.skills.downloadMcps', 'Download MCP')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skills.downloadSkillFiles', 'Tải Skill'),
    ('app.skills.downloadMcps', 'Tải MCP')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skills.downloadSkillFiles', '스킬 다운로드'),
    ('app.skills.downloadMcps', 'MCP 다운로드')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Cleanup: app.skills.downloadSkill (added in V115) is no longer used —
-- the hero CTA reuses app.skills.downloadAll instead.
DELETE FROM translations
WHERE key_id IN (SELECT id FROM translation_keys WHERE key = 'app.skills.downloadSkill');

DELETE FROM translation_keys WHERE key = 'app.skills.downloadSkill';
