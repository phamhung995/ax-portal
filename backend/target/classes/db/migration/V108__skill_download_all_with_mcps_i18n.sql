-- i18n key for the "Download All (Skill + MCPs)" button on the Skill Detail
-- creator row (2026-11-06): one zip = skill files + every available MCP
-- dependency (each MCP in its own subfolder).
--
-- NOTE (Flyway): V108 — checked flyway_schema_history: repo max = 107, dev DB max = 107.

INSERT INTO translation_keys (key, description) VALUES
    ('app.skills.downloadAllWithMcps', NULL),
    ('app.skills.downloadAllWithMcpsTip', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skills.downloadAllWithMcps', 'Download All (Skill + MCPs)'),
    ('app.skills.downloadAllWithMcpsTip', 'One zip: all skill files + every available MCP (each MCP in its own folder)')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skills.downloadAllWithMcps', 'Tải tất cả (Skill + MCP)'),
    ('app.skills.downloadAllWithMcpsTip', 'Một file zip: toàn bộ file của skill + mọi MCP có thể tải (mỗi MCP một thư mục riêng)')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skills.downloadAllWithMcps', '전체 다운로드 (Skill + MCP)'),
    ('app.skills.downloadAllWithMcpsTip', 'zip 1개: 스킬 파일 전체 + 다운로드 가능한 모든 MCP (MCP별 개별 폴더)')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
