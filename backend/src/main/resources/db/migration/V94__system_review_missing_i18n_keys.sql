-- i18n keys found by the full-system review (2026-11) — the last hardcoded
-- English/Vietnamese strings outside the MCP module (covered by V93).
-- Covers: AppShell nav error toast, Register Skill System/Sub System fields,
-- Skill Detail "MCP dependencies not available" download warning.
--
-- NOTE (Flyway): versions V92-V94 chosen after checking flyway_schema_history --
-- the shared DB was already at version 91, and V86-V88 were taken by other
-- developers' migrations (the original V86/V87/V88 files were renamed).

INSERT INTO translation_keys (key, description) VALUES
    ('app.common.navError', NULL),
    ('app.skills.downloadAnyway', NULL),
    ('app.skills.mcpDepsWarnDesc', NULL),
    ('app.skills.mcpDepsWarnNote', NULL),
    ('app.skills.mcpDepsWarnTitle', NULL),
    ('app.skills.mcpUnavailableArchived', NULL),
    ('app.skills.mcpUnavailableArchivedWarn', NULL),
    ('app.skills.mcpUnavailableGone', NULL),
    ('app.skills.mcpUnavailableGoneWarn', NULL),
    ('app.skills.mcpUnavailableNotApproved', NULL),
    ('app.skills.mcpUnavailableNotApprovedWarn', NULL),
    ('app.skills.mcpUnavailableScope', NULL),
    ('app.skills.mcpUnavailableScopeWarn', NULL),
    ('app.skills.subSystem', NULL),
    ('app.skills.subSystemPh', NULL),
    ('app.skills.system', NULL),
    ('app.skills.systemPh', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.common.navError', 'Unable to open the notification. Please try again.'),
    ('app.skills.downloadAnyway', 'Download anyway'),
    ('app.skills.mcpDepsWarnDesc', 'The following MCP dependencies of this skill are not available to you:'),
    ('app.skills.mcpDepsWarnNote', 'These MCPs are not included in this download. You can find an alternative MCP or contact the skill creator.'),
    ('app.skills.mcpDepsWarnTitle', 'Some MCP dependencies are not available'),
    ('app.skills.mcpUnavailableArchived', 'Archived'),
    ('app.skills.mcpUnavailableArchivedWarn', '⚠ Archived'),
    ('app.skills.mcpUnavailableGone', 'No longer exists'),
    ('app.skills.mcpUnavailableGoneWarn', '⚠ No longer exists'),
    ('app.skills.mcpUnavailableNotApproved', 'Not approved yet'),
    ('app.skills.mcpUnavailableNotApprovedWarn', '⚠ Not approved yet — available once approved'),
    ('app.skills.mcpUnavailableScope', 'Not available to you (scope restricted)'),
    ('app.skills.mcpUnavailableScopeWarn', '⚠ Not available to you — scope restricted'),
    ('app.skills.subSystem', 'Sub System'),
    ('app.skills.subSystemPh', 'Optional — select a sub system...'),
    ('app.skills.system', 'System'),
    ('app.skills.systemPh', 'Optional — select a system you are PIC of...')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.common.navError', 'Không thể mở thông báo. Vui lòng thử lại.'),
    ('app.skills.downloadAnyway', 'Vẫn tải xuống'),
    ('app.skills.mcpDepsWarnDesc', 'Các phụ thuộc MCP sau của skill này không khả dụng với bạn:'),
    ('app.skills.mcpDepsWarnNote', 'Các MCP này không được bao gồm trong lượt tải này. Bạn có thể tìm MCP thay thế hoặc liên hệ người tạo skill.'),
    ('app.skills.mcpDepsWarnTitle', 'Một số phụ thuộc MCP không khả dụng'),
    ('app.skills.mcpUnavailableArchived', 'Đã lưu trữ'),
    ('app.skills.mcpUnavailableArchivedWarn', '⚠ Đã lưu trữ'),
    ('app.skills.mcpUnavailableGone', 'Không còn tồn tại'),
    ('app.skills.mcpUnavailableGoneWarn', '⚠ Không còn tồn tại'),
    ('app.skills.mcpUnavailableNotApproved', 'Chưa được phê duyệt'),
    ('app.skills.mcpUnavailableNotApprovedWarn', '⚠ Chưa được phê duyệt — khả dụng sau khi phê duyệt'),
    ('app.skills.mcpUnavailableScope', 'Không khả dụng với bạn (giới hạn phạm vi)'),
    ('app.skills.mcpUnavailableScopeWarn', '⚠ Không khả dụng với bạn — giới hạn phạm vi'),
    ('app.skills.subSystem', 'Hệ con'),
    ('app.skills.subSystemPh', 'Tùy chọn — chọn hệ con...'),
    ('app.skills.system', 'Hệ thống'),
    ('app.skills.systemPh', 'Tùy chọn — chọn hệ thống bạn là PIC...')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.common.navError', '알림을 열 수 없습니다. 다시 시도해 주세요.'),
    ('app.skills.downloadAnyway', '그래도 다운로드'),
    ('app.skills.mcpDepsWarnDesc', '이 스킬의 다음 MCP 의존성은 귀하에게 사용할 수 없습니다:'),
    ('app.skills.mcpDepsWarnNote', '이러한 MCP는 이 다운로드에 포함되지 않습니다. 대체 MCP를 찾거나 스킬 생성자에게 문의하세요.'),
    ('app.skills.mcpDepsWarnTitle', '일부 MCP 의존성이 사용할 수 없음'),
    ('app.skills.mcpUnavailableArchived', '아카이브됨'),
    ('app.skills.mcpUnavailableArchivedWarn', '⚠ 아카이브됨'),
    ('app.skills.mcpUnavailableGone', '더 이상 존재하지 않음'),
    ('app.skills.mcpUnavailableGoneWarn', '⚠ 더 이상 존재하지 않음'),
    ('app.skills.mcpUnavailableNotApproved', '아직 승인되지 않음'),
    ('app.skills.mcpUnavailableNotApprovedWarn', '⚠ 아직 승인되지 않음 — 승인 후 사용 가능'),
    ('app.skills.mcpUnavailableScope', '귀하에게 사용할 수 없음(범위 제한)'),
    ('app.skills.mcpUnavailableScopeWarn', '⚠ 귀하에게 사용할 수 없음 — 범위 제한'),
    ('app.skills.subSystem', '서브 시스템'),
    ('app.skills.subSystemPh', '선택 — 서브 시스템을 선택하세요...'),
    ('app.skills.system', '시스템'),
    ('app.skills.systemPh', '선택 — PIC인 시스템을 선택하세요...')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
