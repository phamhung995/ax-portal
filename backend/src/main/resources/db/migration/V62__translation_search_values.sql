-- Search now also matches translation values (en/vi/ko): update the search placeholder (en/vi/ko).
-- V61 is already applied on shared environments.

UPDATE translations
SET value = 'Search by key, description or value…'
WHERE language = 'en'
  AND key_id = (SELECT id FROM translation_keys WHERE key = 'app.translations.searchPh');

UPDATE translations
SET value = 'Tìm theo key, mô tả hoặc giá trị…'
WHERE language = 'vi'
  AND key_id = (SELECT id FROM translation_keys WHERE key = 'app.translations.searchPh');

UPDATE translations
SET value = '키, 설명 또는 값으로 검색…'
WHERE language = 'ko'
  AND key_id = (SELECT id FROM translation_keys WHERE key = 'app.translations.searchPh');
