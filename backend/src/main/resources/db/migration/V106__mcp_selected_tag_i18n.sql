-- i18n key for the "Selected" tag in the Browse All MCPs popup (2026-11-05):
-- the tag previously reused app.mcp.selected ("Selected ({n})") without
-- passing {n} → the literal "{n}" was rendered.
--
-- NOTE (Flyway): V106 — checked flyway_schema_history: repo max = 105, dev DB max = 105.

INSERT INTO translation_keys (key, description) VALUES
    ('app.mcp.selectedTag', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.mcp.selectedTag', 'Selected')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.mcp.selectedTag', 'Đã chọn')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.mcp.selectedTag', '선택됨')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
