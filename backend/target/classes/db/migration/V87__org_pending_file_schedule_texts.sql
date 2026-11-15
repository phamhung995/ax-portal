-- Flyway migration V87: fix stale 15:55 schedule references in the
-- Organization Management pending-file / upload-hint texts.
--
-- The import crons changed on 2026-11-01: Organization 02:00, Systems
-- 02:00, HR 03:00 (was 15:55). The texts written in V64/V67 still say
-- 15:55 (or no time for Org). Systems and HR shared app.org.pendingFile
-- and app.org.uploadHint — HR now gets its own keys with the 03:00 time.

-- New HR-specific keys.
INSERT INTO translation_keys (key, description) VALUES
    ('app.org.pendingFileHr', 'HR screen: pending file status (03:00 scheduler)'),
    ('app.org.uploadHintHr', 'HR screen: upload dialog hint (03:00 scheduler)')
ON CONFLICT (key) DO NOTHING;

-- ── English ────────────────────────────────────────────────────────────────
UPDATE translations SET value = 'Pending: {file} (uploaded {time} by {by}) — will run at 02:00, or click Import Now.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.pendingFile') AND language = 'en';
UPDATE translations SET value = 'Pending: {file} (uploaded {time} by {by}) — will run at 02:00, or click Import Now.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.pendingFileOrg') AND language = 'en';
UPDATE translations SET value = 'The file will be imported at 02:00 daily (or click Import Now). Each import replaces ALL data; only the newest upload is used. If the file is NASC-protected, open it in Excel and Save As to a new file first.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.uploadHint') AND language = 'en';
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', 'Pending: {file} (uploaded {time} by {by}) — will run at 03:00, or click Import Now.'
FROM translation_keys WHERE key = 'app.org.pendingFileHr'
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', 'The file will be imported at 03:00 daily (or click Import Now). Each import replaces ALL data; only the newest upload is used. If the file is NASC-protected, open it in Excel and Save As to a new file first.'
FROM translation_keys WHERE key = 'app.org.uploadHintHr'
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;

-- ── Vietnamese ─────────────────────────────────────────────────────────────
UPDATE translations SET value = 'Chờ xử lý: {file} (tải lên lúc {time} bởi {by}) — sẽ chạy lúc 02:00, hoặc bấm Import ngay.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.pendingFile') AND language = 'vi';
UPDATE translations SET value = 'Chờ xử lý: {file} (tải lên lúc {time} bởi {by}) — sẽ chạy lúc 02:00, hoặc bấm Import ngay.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.pendingFileOrg') AND language = 'vi';
UPDATE translations SET value = 'File sẽ được import lúc 02:00 hằng ngày (hoặc bấm Import ngay). Mỗi lần import thay thế TOÀN BỘ dữ liệu; chỉ dùng file tải lên mới nhất. Nếu file bị bảo vệ NASC, mở trong Excel và Save As thành file mới.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.uploadHint') AND language = 'vi';
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', 'Chờ xử lý: {file} (tải lên lúc {time} bởi {by}) — sẽ chạy lúc 03:00, hoặc bấm Import ngay.'
FROM translation_keys WHERE key = 'app.org.pendingFileHr'
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', 'File sẽ được import lúc 03:00 hằng ngày (hoặc bấm Import ngay). Mỗi lần import thay thế TOÀN BỘ dữ liệu; chỉ dùng file tải lên mới nhất. Nếu file bị bảo vệ NASC, mở trong Excel và Save As thành file mới.'
FROM translation_keys WHERE key = 'app.org.uploadHintHr'
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;

-- ── Korean ─────────────────────────────────────────────────────────────────
UPDATE translations SET value = '대기: {file} ({time}에 {by} 업로드) — 02:00에 실행되거나 지금 import를 클릭하세요.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.pendingFile') AND language = 'ko';
UPDATE translations SET value = '대기 중: {file} ({time}에 {by} 업로드) — 02:00에 실행되거나 Import Now을(를) 클릭하세요.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.pendingFileOrg') AND language = 'ko';
UPDATE translations SET value = '파일은 매일 02:00에 import됩니다 (또는 지금 import 클릭). 각 import는 모든 데이터를 대체하며, 가장 최신 업로드만 사용됩니다. NASC 보호 파일은 Excel에서 열어 새 파일로 저장하세요.'
WHERE key_id = (SELECT id FROM translation_keys WHERE key = 'app.org.uploadHint') AND language = 'ko';
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', '대기: {file} ({time}에 {by} 업로드) — 03:00에 실행되거나 지금 import를 클릭하세요.'
FROM translation_keys WHERE key = 'app.org.pendingFileHr'
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', '파일은 매일 03:00에 import됩니다 (또는 지금 import 클릭). 각 import는 모든 데이터를 대체하며, 가장 최신 업로드만 사용됩니다. NASC 보호 파일은 Excel에서 열어 새 파일로 저장하세요.'
FROM translation_keys WHERE key = 'app.org.uploadHintHr'
ON CONFLICT (key_id, language) DO UPDATE SET value = EXCLUDED.value;
