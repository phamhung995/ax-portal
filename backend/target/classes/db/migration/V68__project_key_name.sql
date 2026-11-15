-- Flyway migration V68: project key/name on HR assignments + systems.
--
-- Both source Excel files carry "Project Key" / "Project Name":
--   * human-resource-list, sheet "Human Resource System List" (per person-system row)
--   * system-list, sheet "Systems List" (per system)
-- Storing them lets us answer "which project is a person in" and "which
-- systems/sub systems belong to a project" (project_key join).

ALTER TABLE hr_system_assignment
    ADD COLUMN project_key  VARCHAR(100),
    ADD COLUMN project_name VARCHAR(255);

ALTER TABLE system_master
    ADD COLUMN project_key  VARCHAR(100),
    ADD COLUMN project_name VARCHAR(255);

CREATE INDEX idx_hr_system_assignment_project_key ON hr_system_assignment (project_key);
CREATE INDEX idx_system_master_project_key ON system_master (project_key);

-- i18n keys for the new grid columns (en/vi/ko).
INSERT INTO translation_keys (key, description) VALUES
    ('app.org.colProjectKey', 'Org screens: Project Key column label'),
    ('app.org.colProjectName', 'Org screens: Project Name column label')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.org.colProjectKey', 'Project Key'),
    ('app.org.colProjectName', 'Project Name')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.org.colProjectKey', 'Project Key'),
    ('app.org.colProjectName', 'Tên dự án')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.org.colProjectKey', '프로젝트 키'),
    ('app.org.colProjectName', '프로젝트 이름')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
