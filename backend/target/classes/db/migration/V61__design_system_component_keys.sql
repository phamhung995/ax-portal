-- Shared design-system component strings: PrismTable, PrismFileUpload, PrismCodeBlock (en/vi/ko).
-- V60 is already applied on shared environments.

INSERT INTO translation_keys (key, description) VALUES
    ('app.table.loading', 'PrismTable: loading row text'),
    ('app.table.empty', 'PrismTable: default empty text'),
    ('app.fileUpload.dragDrop', 'PrismFileUpload: drag & drop label'),
    ('app.fileUpload.orSelect', 'PrismFileUpload: "or" separator'),
    ('app.fileUpload.chooseFromDevice', 'PrismFileUpload: choose from device link'),
    ('app.fileUpload.maxSize', 'PrismFileUpload: max size meta ({size} placeholder)'),
    ('app.fileUpload.removeFile', 'PrismFileUpload: remove file aria-label ({name} placeholder)'),
    ('app.fileUpload.filesCount', 'PrismFileUpload: files count unit'),
    ('app.fileUpload.clearAll', 'PrismFileUpload: clear all button'),
    ('app.fileUpload.invalidType', 'PrismFileUpload: invalid type error ({name} placeholder)'),
    ('app.fileUpload.tooLarge', 'PrismFileUpload: size exceeded error ({name} placeholder)'),
    ('app.codeBlock.copied', 'PrismCodeBlock: copied status'),
    ('app.codeBlock.copyFailed', 'PrismCodeBlock: copy failed status'),
    ('app.codeBlock.copyAria', 'PrismCodeBlock: copy button aria-label');

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.table.loading', 'Loading…'),
    ('app.table.empty', 'No data.'),
    ('app.fileUpload.dragDrop', 'Drag & drop'),
    ('app.fileUpload.orSelect', 'or'),
    ('app.fileUpload.chooseFromDevice', 'choose from device'),
    ('app.fileUpload.maxSize', 'max {size}'),
    ('app.fileUpload.removeFile', 'Remove {name}'),
    ('app.fileUpload.filesCount', 'files'),
    ('app.fileUpload.clearAll', 'Clear all'),
    ('app.fileUpload.invalidType', 'Invalid file type: {name}'),
    ('app.fileUpload.tooLarge', 'File exceeds the allowed size: {name}'),
    ('app.codeBlock.copied', 'Copied'),
    ('app.codeBlock.copyFailed', 'Could not copy. Select the code and copy manually.'),
    ('app.codeBlock.copyAria', 'Copy code')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.table.loading', 'Đang tải dữ liệu…'),
    ('app.table.empty', 'Không có dữ liệu.'),
    ('app.fileUpload.dragDrop', 'Kéo thả file'),
    ('app.fileUpload.orSelect', 'hoặc'),
    ('app.fileUpload.chooseFromDevice', 'chọn từ máy'),
    ('app.fileUpload.maxSize', 'tối đa {size}'),
    ('app.fileUpload.removeFile', 'Xóa file {name}'),
    ('app.fileUpload.filesCount', 'file'),
    ('app.fileUpload.clearAll', 'Xóa tất cả'),
    ('app.fileUpload.invalidType', 'Loại file không hợp lệ: {name}'),
    ('app.fileUpload.tooLarge', 'File vượt quá dung lượng cho phép: {name}'),
    ('app.codeBlock.copied', 'Đã sao chép'),
    ('app.codeBlock.copyFailed', 'Không thể sao chép. Hãy chọn mã và sao chép thủ công.'),
    ('app.codeBlock.copyAria', 'Sao chép mã')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.table.loading', '불러오는 중…'),
    ('app.table.empty', '데이터가 없습니다.'),
    ('app.fileUpload.dragDrop', '드래그 앤 드롭'),
    ('app.fileUpload.orSelect', '또는'),
    ('app.fileUpload.chooseFromDevice', '기기에서 선택'),
    ('app.fileUpload.maxSize', '최대 {size}'),
    ('app.fileUpload.removeFile', '{name} 삭제'),
    ('app.fileUpload.filesCount', '개 파일'),
    ('app.fileUpload.clearAll', '모두 지우기'),
    ('app.fileUpload.invalidType', '잘못된 파일 형식: {name}'),
    ('app.fileUpload.tooLarge', '허용 크기를 초과한 파일: {name}'),
    ('app.codeBlock.copied', '복사됨'),
    ('app.codeBlock.copyFailed', '복사할 수 없습니다. 코드를 선택하여 수동으로 복사하세요.'),
    ('app.codeBlock.copyAria', '코드 복사')
) AS t(key, v) WHERE translation_keys.key = t.key;
