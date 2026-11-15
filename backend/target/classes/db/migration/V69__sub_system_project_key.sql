-- Flyway migration V69: project key on sub systems.
--
-- The "Sub Systems List" sheet carries a "Project Key" column per sub
-- system row. Storing it lets us list the sub systems of a project
-- directly (project_key join) without going through system_master.

ALTER TABLE sub_system_master
    ADD COLUMN project_key VARCHAR(100);

CREATE INDEX idx_sub_system_master_project_key ON sub_system_master (project_key);

-- i18n key for the sub system project column (en/vi/ko).
INSERT INTO translation_keys (key, description) VALUES
    ('app.org.colSubProjectKey', 'Systems screen: sub system Project Key column label')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.org.colSubProjectKey', 'Project Key (Sub)')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.org.colSubProjectKey', 'Project Key (Sub)')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.org.colSubProjectKey', '프로젝트 키 (서브)')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
