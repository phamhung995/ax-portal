-- Flyway migration V67: organization import job.
--
-- The Organization tab import is moved from a synchronous in-request import
-- (long-running request -> client aborts on large files) to the same
-- upload -> job -> "Import Now" pattern as system_import_job / hr_import_job:
-- an admin uploads a file (PENDING job row), then runs it via "Import Now"
-- (or a later scheduled run). A newer upload supersedes older PENDING jobs.

CREATE TABLE org_import_job (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    rows_count INTEGER,
    success_count INTEGER,
    error_count INTEGER,
    matched_count INTEGER,
    deactivated_count INTEGER,
    error_rows JSONB,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

-- i18n keys for the Organization import job flow (upload -> Import Now).
-- Reuses V64 shared keys (importNow, importing, lastImport, failed, viewErrors,
-- errors, uploadTitle, fileLabel, fileReady, noPendingFile) and V66 keys
-- (uploadDescOrg, uploadHintOrg, importedOrgToast, fileLabelOrg,
-- fileReadyOrg, uploadDialogTitle, errorDialogCaption).
-- Org-specific keys below because the org import has NO 15:55 scheduler
-- (unlike Systems/HR) — it only runs via "Import Now".

INSERT INTO translation_keys (key, description) VALUES
    ('app.org.lastImportOrgSuccess', 'Org screen: org import success status line'),
    ('app.org.pendingFileOrg', 'Org screen: org pending file status (no scheduler)'),
    ('app.org.uploadedToastOrg', 'Org screen: org upload success toast (no scheduler)')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.org.lastImportOrgSuccess', '{success} rows, {errors} errors, {matched} matched, {deactivated} deactivated'),
    ('app.org.pendingFileOrg', 'Pending: {file} (uploaded {time} by {by}) — click Import Now.'),
    ('app.org.uploadedToastOrg', 'File uploaded. Use "Import Now" to import it.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.org.lastImportOrgSuccess', '{success} dòng, {errors} lỗi, {matched} khớp, {deactivated} bị vô hiệu hóa'),
    ('app.org.pendingFileOrg', 'Chờ xử lý: {file} (tải lên lúc {time} bởi {by}) — bấm Import ngay.'),
    ('app.org.uploadedToastOrg', 'Đã tải file lên. Bấm "Import ngay" để import.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.org.lastImportOrgSuccess', '{success}행, {errors} 오류, {matched} 일치, {deactivated} 비활성화'),
    ('app.org.pendingFileOrg', '대기 중: {file} ({time}에 {by} 업로드) — Import Now을(를) 클릭하세요.'),
    ('app.org.uploadedToastOrg', '파일이 업로드되었습니다. "Import Now"을(를) 클릭하여 import하세요.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
