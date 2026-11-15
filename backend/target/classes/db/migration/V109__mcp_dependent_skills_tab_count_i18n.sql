-- i18n key for the "Dependent Skills ({n})" tab label in the MCP Detail
-- popup (2026-11-06): shows the linked-skill count like the Comments tab.
--
-- NOTE (Flyway): V109 — checked flyway_schema_history: repo max = 108, dev DB Flyway max = 107 (V108 seeded manually).

INSERT INTO translation_keys (key, description) VALUES
    ('app.mcp.dependentSkillsTab', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.mcp.dependentSkillsTab', 'Dependent Skills ({n})')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.mcp.dependentSkillsTab', 'Skill phụ thuộc ({n})')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.mcp.dependentSkillsTab', '의존 스킬 ({n})')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
