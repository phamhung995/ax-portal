-- Flyway migration V71: PROJECT skill scope + system/sub system markers.
--
-- Adds a 5th skill scope, PROJECT: a skill is visible to users who share a
-- project with the creator (project of a system = client_of_sds when set,
-- otherwise project_key) and to the creator's org managers. Level order:
-- PRIVATE < PROJECT < PART < GROUP < PUBLIC.
--
-- system_key / sub_system_code mark which system (and optional sub system)
-- the skill belongs to. They are markers only — they do not affect
-- visibility (the project comes from the creator's PIC systems).

ALTER TABLE skills_master
    ADD COLUMN system_key VARCHAR(100),
    ADD COLUMN sub_system_code VARCHAR(100);

ALTER TABLE skill_version
    ADD COLUMN system_key VARCHAR(100),
    ADD COLUMN sub_system_code VARCHAR(100);

ALTER TABLE skills_master DROP CONSTRAINT skills_master_public_scope_check;
ALTER TABLE skills_master ADD CONSTRAINT skills_master_public_scope_check
    CHECK (public_scope IN ('PRIVATE', 'PROJECT', 'PART', 'GROUP', 'PUBLIC'));

-- i18n keys for the new UI parts (en/vi/ko).
INSERT INTO translation_keys (key, description) VALUES
    ('app.skill.scopeProject', 'Skill form: PROJECT scope option label'),
    ('app.skill.formSystem', 'Skill form: System combobox label (PROJECT scope marker)'),
    ('app.skill.formSubSystem', 'Skill form: Sub System combobox label (PROJECT scope marker)'),
    ('app.skill.selectSystem', 'Skill form: System combobox placeholder'),
    ('app.skill.selectSubSystem', 'Skill form: Sub System combobox placeholder'),
    ('app.skill.searchSystem', 'Skill search: System filter label'),
    ('app.skill.searchSubSystem', 'Skill search: Sub System filter label'),
    ('app.skill.subSystemNeedSystem', 'Skill search: Sub System filter hint when no system selected'),
    ('app.skill.detailSystem', 'Skill detail: System marker label'),
    ('app.skill.detailSubSystem', 'Skill detail: Sub System marker label')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.skill.scopeProject', 'Project'),
    ('app.skill.formSystem', 'System'),
    ('app.skill.formSubSystem', 'Sub System'),
    ('app.skill.selectSystem', 'Select a system'),
    ('app.skill.selectSubSystem', 'Select a sub system'),
    ('app.skill.searchSystem', 'System'),
    ('app.skill.searchSubSystem', 'Sub System'),
    ('app.skill.subSystemNeedSystem', 'Select a system first'),
    ('app.skill.detailSystem', 'System'),
    ('app.skill.detailSubSystem', 'Sub System')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.skill.scopeProject', 'Dự án'),
    ('app.skill.formSystem', 'Hệ thống'),
    ('app.skill.formSubSystem', 'Hệ thống con'),
    ('app.skill.selectSystem', 'Chọn hệ thống'),
    ('app.skill.selectSubSystem', 'Chọn hệ thống con'),
    ('app.skill.searchSystem', 'Hệ thống'),
    ('app.skill.searchSubSystem', 'Hệ thống con'),
    ('app.skill.subSystemNeedSystem', 'Chọn hệ thống trước'),
    ('app.skill.detailSystem', 'Hệ thống'),
    ('app.skill.detailSubSystem', 'Hệ thống con')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.skill.scopeProject', '프로젝트'),
    ('app.skill.formSystem', '시스템'),
    ('app.skill.formSubSystem', '서브 시스템'),
    ('app.skill.selectSystem', '시스템을 선택하세요'),
    ('app.skill.selectSubSystem', '서브 시스템을 선택하세요'),
    ('app.skill.searchSystem', '시스템'),
    ('app.skill.searchSubSystem', '서브 시스템'),
    ('app.skill.subSystemNeedSystem', '먼저 시스템을 선택하세요'),
    ('app.skill.detailSystem', '시스템'),
    ('app.skill.detailSubSystem', '서브 시스템')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
