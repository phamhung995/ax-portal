-- Flyway migration V92: confirm dialog for version updates that require
-- admin approval (V91 anti-cheat: a reviewed skill's version update always
-- goes through the pending flow, even at PRIVATE/PROJECT scope).

-- ── i18n keys (en/vi/ko) ───────────────────────────────────────────────────
INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.versionConfirmTitle', 'Version update confirm dialog: title'),
    ('app.skill.versionConfirmDesc', 'Version update confirm dialog: message (the update will wait for admin approval)')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.versionConfirmTitle', 'Confirm version update'),
    ('app.skill.versionConfirmDesc', 'This version update requires admin approval. The current version stays available to everyone until it is approved, and the skill will move to PENDING_APPROVAL. Do you want to continue?')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.versionConfirmTitle', 'Xác nhận cập nhật phiên bản'),
    ('app.skill.versionConfirmDesc', 'Cập nhật phiên bản này cần admin duyệt. Phiên bản hiện tại vẫn dùng được cho mọi người cho đến khi duyệt và skill sẽ chuyển sang PENDING_APPROVAL. Bạn có muốn tiếp tục?')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.versionConfirmTitle', '버전 업데이트 확인'),
    ('app.skill.versionConfirmDesc', '이 버전 업데이트에는 관리자 승인이 필요합니다. 승인될 때까지 현재 버전이 계속 사용 가능하며 스킬은 승인 대기 상태로 전환됩니다. 계속하시겠습니까?')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
