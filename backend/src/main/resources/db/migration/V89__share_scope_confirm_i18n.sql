-- Flyway migration V89: confirm dialogs for share/scope pending actions.
--
-- When a share (external) or a scope widening moves an APPROVED skill to
-- PENDING_APPROVAL, the frontend shows a confirm dialog first so the user
-- knows the skill will wait for admin approval.

-- ── i18n keys (en/vi/ko) ───────────────────────────────────────────────────
INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.shareConfirmTitle', 'Share confirm dialog: title'),
    ('app.skill.shareConfirmDesc', 'Share confirm dialog: message (external shares put the skill pending)'),
    ('app.skill.scopeConfirmTitle', 'Scope change confirm dialog: title'),
    ('app.skill.scopeConfirmDesc', 'Scope change confirm dialog: message ({scope} = new scope)'),
    ('app.skill.scopePendingToast', 'Edit form: toast after saving a scope change that is pending approval')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.shareConfirmTitle', 'Confirm share'),
    ('app.skill.shareConfirmDesc', 'Some of the selected shares are outside the creator''s projects. The skill will move to PENDING_APPROVAL until an admin approves the external shares. Do you want to continue?'),
    ('app.skill.scopeConfirmTitle', 'Confirm scope change'),
    ('app.skill.scopeConfirmDesc', 'Changing the scope to {scope} requires admin approval. The current scope still applies until it is approved, and the skill will move to PENDING_APPROVAL. Do you want to continue?'),
    ('app.skill.scopePendingToast', 'Saved. The scope change is pending admin approval.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.shareConfirmTitle', 'Xác nhận chia sẻ'),
    ('app.skill.shareConfirmDesc', 'Một số share được chọn nằm ngoài project của người tạo. Skill sẽ chuyển sang PENDING_APPROVAL cho đến khi admin duyệt các share ngoài. Bạn có muốn tiếp tục?'),
    ('app.skill.scopeConfirmTitle', 'Xác nhận đổi scope'),
    ('app.skill.scopeConfirmDesc', 'Đổi scope sang {scope} cần admin duyệt. Scope hiện tại vẫn được áp dụng cho đến khi duyệt và skill sẽ chuyển sang PENDING_APPROVAL. Bạn có muốn tiếp tục?'),
    ('app.skill.scopePendingToast', 'Đã lưu. Đổi scope đang chờ admin duyệt.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.shareConfirmTitle', '공유 확인'),
    ('app.skill.shareConfirmDesc', '선택한 공유 중 일부가 생성자의 프로젝트 밖입니다. 관리자가 외부 공유를 승인할 때까지 스킬이 승인 대기 상태로 전환됩니다. 계속하시겠습니까?'),
    ('app.skill.scopeConfirmTitle', '스코프 변경 확인'),
    ('app.skill.scopeConfirmDesc', '{scope}(으)로 스코프를 변경하려면 관리자 승인이 필요합니다. 승인될 때까지 현재 스코프가 계속 적용되며 스킬은 승인 대기 상태로 전환됩니다. 계속하시겠습니까?'),
    ('app.skill.scopePendingToast', '저장되었습니다. 스코프 변경이 관리자 승인 대기 중입니다.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
