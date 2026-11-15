-- Admin translation management screen labels (en/vi/ko).
-- V56 is already applied on shared environments; this migration adds the
-- app.translations.* keys used by TranslationManagementView and removes the
-- app.lang.* keys (the switcher now uses fixed native endonyms, not DB values).

DELETE FROM translation_keys WHERE key IN ('app.lang.en', 'app.lang.vi', 'app.lang.ko');

INSERT INTO translation_keys (key, description) VALUES
    ('app.translations.searchPh', 'Translations screen: search placeholder'),
    ('app.translations.addKey', 'Translations screen: add key button'),
    ('app.translations.colKey', 'Translations screen: Key column'),
    ('app.translations.colDescription', 'Translations screen: Description column'),
    ('app.translations.colUpdated', 'Translations screen: Updated column'),
    ('app.translations.addTitle', 'Translations screen: add dialog title'),
    ('app.translations.keyLabel', 'Translations screen: key field label'),
    ('app.translations.descLabel', 'Translations screen: description field label'),
    ('app.translations.keyPh', 'Translations screen: key field placeholder'),
    ('app.translations.descPh', 'Translations screen: description field placeholder'),
    ('app.translations.cancel', 'Translations screen: cancel button'),
    ('app.translations.add', 'Translations screen: add button'),
    ('app.translations.delete', 'Translations screen: delete button'),
    ('app.translations.deleteTitle', 'Translations screen: delete dialog title'),
    ('app.translations.deleteDesc', 'Translations screen: delete dialog description'),
    ('app.translations.noData', 'Translations screen: empty table'),
    ('app.translations.loadError', 'Translations screen: load error title'),
    ('app.translations.retry', 'Translations screen: retry button'),
    ('app.translations.saved', 'Translations screen: save toast'),
    ('app.translations.added', 'Translations screen: add toast'),
    ('app.translations.deleted', 'Translations screen: delete toast'),
    ('app.translations.keyInvalid', 'Translations screen: key validation error'),
    ('app.translations.keyExists', 'Translations screen: duplicate key error');

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.translations.searchPh', 'Search by key or description…'),
    ('app.translations.addKey', 'Add key'),
    ('app.translations.colKey', 'Key'),
    ('app.translations.colDescription', 'Description'),
    ('app.translations.colUpdated', 'Updated'),
    ('app.translations.addTitle', 'Add translation key'),
    ('app.translations.keyLabel', 'Key'),
    ('app.translations.descLabel', 'Description'),
    ('app.translations.keyPh', 'e.g. home.title'),
    ('app.translations.descPh', 'Optional'),
    ('app.translations.cancel', 'Cancel'),
    ('app.translations.add', 'Add'),
    ('app.translations.delete', 'Delete'),
    ('app.translations.deleteTitle', 'Delete translation key'),
    ('app.translations.deleteDesc', 'This removes the key and all its translations. This action cannot be undone.'),
    ('app.translations.noData', 'No translations found.'),
    ('app.translations.loadError', 'Unable to load translations.'),
    ('app.translations.retry', 'Retry'),
    ('app.translations.saved', 'Translation saved'),
    ('app.translations.added', 'Key added'),
    ('app.translations.deleted', 'Key deleted'),
    ('app.translations.keyInvalid', 'Key must start with a letter, use only letters, digits, ".", "_" or "-", max 120 chars.'),
    ('app.translations.keyExists', 'Key already exists.')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.translations.searchPh', 'Tìm theo key hoặc mô tả…'),
    ('app.translations.addKey', 'Thêm key'),
    ('app.translations.colKey', 'Key'),
    ('app.translations.colDescription', 'Mô tả'),
    ('app.translations.colUpdated', 'Cập nhật'),
    ('app.translations.addTitle', 'Thêm key bản dịch'),
    ('app.translations.keyLabel', 'Key'),
    ('app.translations.descLabel', 'Mô tả'),
    ('app.translations.keyPh', 'vd: home.title'),
    ('app.translations.descPh', 'Tùy chọn'),
    ('app.translations.cancel', 'Hủy'),
    ('app.translations.add', 'Thêm'),
    ('app.translations.delete', 'Xóa'),
    ('app.translations.deleteTitle', 'Xóa key bản dịch'),
    ('app.translations.deleteDesc', 'Hành động này xóa key và toàn bộ bản dịch. Không thể hoàn tác.'),
    ('app.translations.noData', 'Không tìm thấy bản dịch nào.'),
    ('app.translations.loadError', 'Không tải được danh sách bản dịch.'),
    ('app.translations.retry', 'Thử lại'),
    ('app.translations.saved', 'Đã lưu bản dịch'),
    ('app.translations.added', 'Đã thêm key'),
    ('app.translations.deleted', 'Đã xóa key'),
    ('app.translations.keyInvalid', 'Key phải bắt đầu bằng chữ, chỉ dùng chữ, số, ".", "_" hoặc "-", tối đa 120 ký tự.'),
    ('app.translations.keyExists', 'Key đã tồn tại.')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.translations.searchPh', '키 또는 설명으로 검색…'),
    ('app.translations.addKey', '키 추가'),
    ('app.translations.colKey', '키'),
    ('app.translations.colDescription', '설명'),
    ('app.translations.colUpdated', '수정됨'),
    ('app.translations.addTitle', '번역 키 추가'),
    ('app.translations.keyLabel', '키'),
    ('app.translations.descLabel', '설명'),
    ('app.translations.keyPh', '예: home.title'),
    ('app.translations.descPh', '선택 사항'),
    ('app.translations.cancel', '취소'),
    ('app.translations.add', '추가'),
    ('app.translations.delete', '삭제'),
    ('app.translations.deleteTitle', '번역 키 삭제'),
    ('app.translations.deleteDesc', '이 키와 모든 번역이 삭제됩니다. 되돌릴 수 없습니다.'),
    ('app.translations.noData', '번역이 없습니다.'),
    ('app.translations.loadError', '번역을 불러오지 못했습니다.'),
    ('app.translations.retry', '다시 시도'),
    ('app.translations.saved', '번역 저장됨'),
    ('app.translations.added', '키 추가됨'),
    ('app.translations.deleted', '키 삭제됨'),
    ('app.translations.keyInvalid', '키는 문자로 시작하고, 문자/숫자/"/"/"/"/"-"만 사용하며, 최대 120자입니다.'),
    ('app.translations.keyExists', '이미 존재하는 키입니다.')
) AS t(key, v) WHERE translation_keys.key = t.key;
