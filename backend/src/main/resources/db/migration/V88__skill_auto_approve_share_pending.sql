-- Flyway migration V88: skill auto-approve (PRIVATE/PROJECT) + share/scope
-- pending approval.
--
-- skills_master:
--   pending_reason: 'SHARE' | 'SCOPE' | null — WHY the skill is in
--   PENDING_APPROVAL (a null reason + null pending_metadata is a legacy
--   submit-for-approval).
--   pending_scope: the new scope awaiting approval (only when
--   pending_reason = 'SCOPE').
-- skill_share:
--   approval_status: 'PENDING' | 'APPROVED' | 'REJECTED' — external shares
--   (user outside the creator's projects, or another project) are created
--   PENDING and only grant visibility once approved. Existing rows are
--   backfilled to APPROVED.

ALTER TABLE skills_master
    ADD COLUMN IF NOT EXISTS pending_reason VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pending_scope VARCHAR(20);

ALTER TABLE skill_share
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

-- ── i18n keys (en/vi/ko) ───────────────────────────────────────────────────
INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.sharePendingToast', 'Share popup: toast when a share puts the skill pending approval ({count} shares)'),
    ('app.skill.sharePendingBadge', 'Share popup: PENDING badge on a share row'),
    ('app.skill.shareRejectedBadge', 'Share popup: REJECTED badge on a share row'),
    ('app.skill.shareRejectedAlert', 'Skill detail: alert after a share was rejected by admin ({reason})'),
    ('app.skill.scopePendingAlert', 'Skill detail: alert while a scope change is pending ({scope})'),
    ('app.skill.sharePendingAlert', 'Skill detail: alert while external shares are pending approval')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.sharePendingToast', 'Shared. The skill is now pending admin approval because {count} share(s) are outside your projects.'),
    ('app.skill.sharePendingBadge', 'Pending'),
    ('app.skill.shareRejectedBadge', 'Rejected'),
    ('app.skill.shareRejectedAlert', 'Your share request was rejected. Reason: {reason}'),
    ('app.skill.scopePendingAlert', 'A scope change to {scope} is pending admin approval. The current scope still applies.'),
    ('app.skill.sharePendingAlert', 'This skill is pending admin approval because it was shared outside the creator''s projects.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.sharePendingToast', 'Đã chia sẻ. Skill đang chờ admin duyệt vì có {count} share ngoài project của bạn.'),
    ('app.skill.sharePendingBadge', 'Chờ duyệt'),
    ('app.skill.shareRejectedBadge', 'Bị từ chối'),
    ('app.skill.shareRejectedAlert', 'Yêu cầu chia sẻ của bạn đã bị từ chối. Lý do: {reason}'),
    ('app.skill.scopePendingAlert', 'Đang chờ admin duyệt đổi scope sang {scope}. Scope hiện tại vẫn được áp dụng.'),
    ('app.skill.sharePendingAlert', 'Skill này đang chờ admin duyệt vì được chia sẻ ra ngoài project của người tạo.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.sharePendingToast', '공유되었습니다. {count}개 공유가 프로젝트 밖이므로 관리자 승인이 필요합니다.'),
    ('app.skill.sharePendingBadge', '승인 대기'),
    ('app.skill.shareRejectedBadge', '거절됨'),
    ('app.skill.shareRejectedAlert', '공유 요청이 거절되었습니다. 사유: {reason}'),
    ('app.skill.scopePendingAlert', '스코프를 {scope}(으)로 변경하는 것이 관리자 승인 대기 중입니다. 현재 스코프가 계속 적용됩니다.'),
    ('app.skill.sharePendingAlert', '이 스킬은 생성자의 프로젝트 밖으로 공유되어 관리자 승인 대기 중입니다.')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
