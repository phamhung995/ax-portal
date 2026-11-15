-- i18n keys for the Update Version popup relabel + required Description
-- (2026-11-05, PO):
--  - "Create Version" button -> "Update Version" (reuses app.mcp.updateVersion)
--  - "Changelog" field -> "Update Info" (app.mcp.updateInfo + required/tooLong/placeholder)
--  - Description becomes required on Add / Edit / Update Version (app.mcp.descRequired)
--
-- NOTE (Flyway): V102 — checked flyway_schema_history: dev DB max version = 101.

INSERT INTO translation_keys (key, description) VALUES
    ('app.mcp.updateInfo', NULL),
    ('app.mcp.updateInfoRequired', NULL),
    ('app.mcp.updateInfoTooLong', NULL),
    ('app.mcp.updateInfoPh', NULL),
    ('app.mcp.descRequired', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.mcp.updateInfo', 'Update Info'),
    ('app.mcp.updateInfoRequired', 'Update Info is required for a new version.'),
    ('app.mcp.updateInfoTooLong', 'Update Info is at most {n} characters.'),
    ('app.mcp.updateInfoPh', 'Describe what changed in this version...'),
    ('app.mcp.descRequired', 'Description is required.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.mcp.updateInfo', 'Thông tin cập nhật'),
    ('app.mcp.updateInfoRequired', 'Thông tin cập nhật là bắt buộc cho phiên bản mới.'),
    ('app.mcp.updateInfoTooLong', 'Thông tin cập nhật tối đa {n} ký tự.'),
    ('app.mcp.updateInfoPh', 'Mô tả những gì đã thay đổi trong phiên bản này...'),
    ('app.mcp.descRequired', 'Mô tả không được để trống.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.mcp.updateInfo', '업데이트 정보'),
    ('app.mcp.updateInfoRequired', '새 버전에는 업데이트 정보가 필요합니다.'),
    ('app.mcp.updateInfoTooLong', '업데이트 정보는 최대 {n}자입니다.'),
    ('app.mcp.updateInfoPh', '이 버전에서 변경된 내용을 설명하세요...'),
    ('app.mcp.descRequired', '설명은 필수입니다.')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
