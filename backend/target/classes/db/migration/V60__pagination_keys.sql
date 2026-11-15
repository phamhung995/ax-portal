-- Pagination labels for the shared PrismPagination component (en/vi/ko).
-- V59 is already applied on shared environments.

INSERT INTO translation_keys (key, description) VALUES
    ('app.pagination.status', 'Pagination: page/total status text ({page}, {pages}, {total} placeholders)'),
    ('app.pagination.ariaLabel', 'Pagination: nav aria-label'),
    ('app.pagination.prev', 'Pagination: previous page aria-label'),
    ('app.pagination.next', 'Pagination: next page aria-label');

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.pagination.status', 'Page {page} / {pages} · {total} items'),
    ('app.pagination.ariaLabel', 'Pagination'),
    ('app.pagination.prev', 'Previous page'),
    ('app.pagination.next', 'Next page')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.pagination.status', 'Trang {page} / {pages} · {total} mục'),
    ('app.pagination.ariaLabel', 'Phân trang'),
    ('app.pagination.prev', 'Trang trước'),
    ('app.pagination.next', 'Trang sau')
) AS t(key, v) WHERE translation_keys.key = t.key;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.pagination.status', '{page} / {pages} 페이지 · {total}개 항목'),
    ('app.pagination.ariaLabel', '페이지 이동'),
    ('app.pagination.prev', '이전 페이지'),
    ('app.pagination.next', '다음 페이지')
) AS t(key, v) WHERE translation_keys.key = t.key;
