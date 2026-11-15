-- Fix app.mcp.* i18n values left NULL by V103 (2026-11-06): V103 cloned the
-- keys from app.skills.* but the Skill module has no equivalent keys → the
-- values were left NULL for all 3 languages → the UI rendered the raw key.
--  - app.mcp.dependentSkills: "Dependent Skills" tab in the MCP Detail Popup
--  - app.mcp.mcpCode / app.mcp.serverKey: tooltips in the MCP Detail Popup
--
-- NOTE (Flyway): V107 — checked flyway_schema_history: repo max = 106, dev DB max = 106.

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.mcp.dependentSkills', 'Dependent Skills'),
    ('app.mcp.mcpCode', 'MCP Code'),
    ('app.mcp.serverKey', 'Server Key')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.mcp.dependentSkills', 'Skill phụ thuộc'),
    ('app.mcp.mcpCode', 'Mã MCP'),
    ('app.mcp.serverKey', 'Server Key')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.mcp.dependentSkills', '의존 스킬'),
    ('app.mcp.mcpCode', 'MCP 코드'),
    ('app.mcp.serverKey', '서버 키')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;
