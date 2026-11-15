-- Skill Detail redesign (2026-11-07): Option A + hero V1 (stat chips).
-- New keys:
--   app.skills.tabOverview   — tab label (was app.skills.tabInfo "Information")
--   app.skills.heroSlogan    — download hero headline
--   app.skills.heroSub       — download hero sub-line
--   app.skills.downloadSkill — hero CTA button
--   app.skills.chipFiles     — hero stat chip (files)
--   app.skills.chipMcp       — hero stat chip (MCPs)
--
-- NOTE (Flyway): V115 — dev DB đã ở V114 (verify 2026-11-07).

INSERT INTO translation_keys (key, description) VALUES
    ('app.skills.tabOverview', NULL),
    ('app.skills.heroSlogan', NULL),
    ('app.skills.heroSub', NULL),
    ('app.skills.downloadSkill', NULL),
    ('app.skills.chipFiles', NULL),
    ('app.skills.chipMcp', NULL)
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skills.tabOverview', 'Overview'),
    ('app.skills.heroSlogan', 'Turn AI into your most understanding teammate!'),
    ('app.skills.heroSub', 'Use it right away with AI Pro — one click and it runs'),
    ('app.skills.downloadSkill', 'Download skill'),
    ('app.skills.chipFiles', 'files'),
    ('app.skills.chipMcp', 'MCPs')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skills.tabOverview', 'Tổng quan'),
    ('app.skills.heroSlogan', 'Biến AI thành đồng nghiệp thấu hiểu bạn nhất!'),
    ('app.skills.heroSub', 'Dùng ngay với AI Pro — 1 click là chạy'),
    ('app.skills.downloadSkill', 'Tải skill'),
    ('app.skills.chipFiles', 'files'),
    ('app.skills.chipMcp', 'MCP')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skills.tabOverview', '개요'),
    ('app.skills.heroSlogan', 'AI를 당신을 가장 잘 이해하는 동료로 만드세요!'),
    ('app.skills.heroSub', 'AI Pro에서 바로 사용 — 클릭 한 번이면 실행'),
    ('app.skills.downloadSkill', '스킬 다운로드'),
    ('app.skills.chipFiles', '파일'),
    ('app.skills.chipMcp', 'MCP')
) AS t(key, v) WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
