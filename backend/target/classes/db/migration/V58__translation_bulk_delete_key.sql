-- Bulk-delete label for the admin translations screen (en/vi/ko).
-- V57 is already applied on shared environments.

INSERT INTO translation_keys (key, description) VALUES
    ('app.translations.deleteSelected', 'Translations screen: bulk delete dialog title');

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.translations.deleteSelected', 'Delete {n} selected key(s)')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.translations.deleteSelected', 'Xóa {n} key đã chọn')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.translations.deleteSelected', '선택한 {n}개 키 삭제')
) AS t(key, v) WHERE translation_keys.key = t.key;
