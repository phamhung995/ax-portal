-- i18n keys for the "MCP deleted" badges (2026-11-05):
--  - Skill Detail MCPs quick view: badge next to a soft-deleted MCP (still downloadable)
--  - MCP dependency chip in Add/Edit/Update Version: badge when the linked MCP was deleted
--
-- NOTE (Flyway): V101 — checked flyway_schema_history: dev DB max version = 100.

INSERT INTO translation_keys (key, description) VALUES
    ('app.mcp.depDeleted', NULL),
    ('app.mcp.depDeletedTip', NULL),
    ('app.skills.mcpDeleted', NULL),
    ('app.skills.mcpDeletedTip', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.mcp.depDeleted', 'Deleted'),
    ('app.mcp.depDeletedTip', 'This MCP has been deleted. The skill keeps working — you can remove this dependency.'),
    ('app.skills.mcpDeleted', 'Deleted'),
    ('app.skills.mcpDeletedTip', 'This MCP has been deleted. Its files remain downloadable for this skill.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.mcp.depDeleted', 'Đã bị xóa'),
    ('app.mcp.depDeletedTip', 'MCP này đã bị xóa. Skill vẫn hoạt động — bạn có thể gỡ liên kết này.'),
    ('app.skills.mcpDeleted', 'Đã bị xóa'),
    ('app.skills.mcpDeletedTip', 'MCP này đã bị xóa. File vẫn tải được cho skill này.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.mcp.depDeleted', '삭제됨'),
    ('app.mcp.depDeletedTip', '이 MCP는 삭제되었습니다. Skill은 계속 작동하며 이 의존성을 제거할 수 있습니다.'),
    ('app.skills.mcpDeleted', '삭제됨'),
    ('app.skills.mcpDeletedTip', '이 MCP는 삭제되었습니다. 이 Skill을 위한 파일은 여전히 다운로드 가능합니다.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
