-- Flyway migration V77: update share project hint (en/vi/ko).
--
-- The shareable project list rule changed: it is now ALL projects of
-- non-End-phase systems except the creator's own projects (previously:
-- projects of the creator's GDC Group). The hint text is updated to
-- match.

UPDATE translations
SET value = 'Select one or more projects. Everyone in those projects will see this skill.'
WHERE language = 'en'
  AND key_id = (SELECT id FROM translation_keys WHERE key = 'app.skill.shareProjectHint');

UPDATE translations
SET value = 'Chọn một hoặc nhiều dự án. Mọi người trong các dự án đó sẽ thấy skill này.'
WHERE language = 'vi'
  AND key_id = (SELECT id FROM translation_keys WHERE key = 'app.skill.shareProjectHint');

UPDATE translations
SET value = '하나 이상의 프로젝트를 선택하세요. 해당 프로젝트의 모든 사람이 이 스킬을 볼 수 있습니다.'
WHERE language = 'ko'
  AND key_id = (SELECT id FROM translation_keys WHERE key = 'app.skill.shareProjectHint');
