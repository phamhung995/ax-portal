-- i18n key for the Edit Skill popup APPROVED state (2026-11-06):
-- MCP Dependencies section is read-only for APPROVED skills (same rule as
-- files) — the dependency set is managed through Update Version.
-- Mirrors app.skills.approvedFilesHint.
--
-- NOTE (Flyway): V113 — dev DB đã ở V112 (V110-V112 thuộc migrations của
-- developer khác, cài 2026-11-06 13:28). Đổi tên từ V110 để tránh trùng.

INSERT INTO translation_keys (key, description) VALUES
    ('app.skills.approvedMcpDepsHint', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skills.approvedMcpDepsHint', 'MCP dependencies of an approved skill can only be managed through Update Version.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skills.approvedMcpDepsHint', 'MCP dependencies của skill đã duyệt chỉ có thể quản lý qua Cập nhật phiên bản.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skills.approvedMcpDepsHint', '승인된 스킬의 MCP 의존성은 버전 업데이트에서만 관리할 수 있습니다.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
