-- AX Desktop promo popup (2026-11-06): the installer (v1.0.22) is now
-- available — the CTA changes from "Coming Soon" to "Download" and the
-- meta line shows the real installer size (~15 MB, was ~50 MB).
--
-- NOTE (Flyway): V114 — dev DB đã ở V112 (V110-V112 thuộc migrations của
-- developer khác, cài 2026-11-06 13:28). Đổi tên từ V111 để tránh trùng.

UPDATE translations
SET value = 'Download'
FROM translation_keys
WHERE translation_keys.id = translations.key_id
  AND translation_keys.key = 'app.promo.cta'
  AND translations.language = 'en';

UPDATE translations
SET value = 'Tải xuống'
FROM translation_keys
WHERE translation_keys.id = translations.key_id
  AND translation_keys.key = 'app.promo.cta'
  AND translations.language = 'vi';

UPDATE translations
SET value = '다운로드'
FROM translation_keys
WHERE translation_keys.id = translations.key_id
  AND translation_keys.key = 'app.promo.cta'
  AND translations.language = 'ko';

UPDATE translations
SET value = 'Windows 10+ · ~15 MB · Sign in with your Knox account'
FROM translation_keys
WHERE translation_keys.id = translations.key_id
  AND translation_keys.key = 'app.promo.meta'
  AND translations.language = 'en';

UPDATE translations
SET value = 'Windows 10+ · ~15 MB · Đăng nhập bằng tài khoản Knox'
FROM translation_keys
WHERE translation_keys.id = translations.key_id
  AND translation_keys.key = 'app.promo.meta'
  AND translations.language = 'vi';

UPDATE translations
SET value = 'Windows 10+ · ~15 MB · Knox 계정으로 로그인'
FROM translation_keys
WHERE translation_keys.id = translations.key_id
  AND translation_keys.key = 'app.promo.meta'
  AND translations.language = 'ko';
