-- i18n key for the Edit MCP popup APPROVED state (PO 2026-11):
-- the "approved, updated immediately" info alert was replaced by a read-only
-- Files section with this hint (mirrors app.skills.approvedFilesHint).
--
-- NOTE (Flyway): versions V92-V96 chosen after checking flyway_schema_history --
-- the shared DB was already at version 91, and V86-V88 were taken by other
-- developers' migrations (the original V86/V87/V88 files were renamed).

INSERT INTO translation_keys (key, description) VALUES
    ('app.mcp.approvedFilesHint', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.mcp.approvedFilesHint', 'Files of an approved MCP can only be managed through Update Version.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.mcp.approvedFilesHint', 'File của MCP đã duyệt chỉ có thể quản lý qua Cập nhật phiên bản.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.mcp.approvedFilesHint', '승인된 MCP의 파일은 버전 업데이트에서만 관리할 수 있습니다.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
