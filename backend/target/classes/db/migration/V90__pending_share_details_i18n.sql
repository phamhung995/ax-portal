-- Flyway migration V90: pending share details for admin review.
--
-- When a skill is PENDING_APPROVAL with pending_reason='SHARE', the admin
-- needs to see WHICH shares await approval before deciding. The detail
-- popup shows the pending share list; the grid shows the pending count
-- next to the Pending badge.

-- ── i18n keys (en/vi/ko) ───────────────────────────────────────────────────
INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.pendingSharesTitle', 'Skill detail: heading of the pending share list for admin review'),
    ('app.skill.pendingSharesCount', 'Skill detail / grid: how many shares are awaiting approval ({count})'),
    ('app.skill.shareTypeProjectKey', 'Skill detail: prefix for a PROJECT share row in the pending list')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.pendingSharesTitle', 'Shares awaiting approval'),
    ('app.skill.pendingSharesCount', '{count} share(s) awaiting approval'),
    ('app.skill.shareTypeProjectKey', 'Project')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.pendingSharesTitle', 'Share đang chờ duyệt'),
    ('app.skill.pendingSharesCount', '{count} share đang chờ duyệt'),
    ('app.skill.shareTypeProjectKey', 'Project')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.pendingSharesTitle', '승인 대기 중인 공유'),
    ('app.skill.pendingSharesCount', '승인 대기 공유 {count}개'),
    ('app.skill.shareTypeProjectKey', '프로젝트')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
