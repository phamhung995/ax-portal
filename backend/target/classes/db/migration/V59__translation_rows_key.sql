-- "Rows" label for the pagination page-size selector on the translations screen (en/vi/ko).
-- V58 is already applied on shared environments.

INSERT INTO translation_keys (key, description) VALUES
    ('app.translations.rows', 'Translations screen: rows-per-page label');

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.translations.rows', 'Rows')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.translations.rows', 'Số dòng')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.translations.rows', '행')
) AS t(key, v) WHERE translation_keys.key = t.key;

-- Fix garbled Korean seed text in V57 (keyInvalid).
UPDATE translations
SET value = '키는 문자로 시작하고, 문자/숫자/"."/"_" 또는 "-"만 사용하며, 최대 120자입니다.'
WHERE language = 'ko'
  AND key_id = (SELECT id FROM translation_keys WHERE key = 'app.translations.keyInvalid');
