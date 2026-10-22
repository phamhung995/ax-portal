-- Fix skill_files.file_path: the storage service resolves paths relative to
-- app.upload.skill-dir (uploads/skills/), but store() previously persisted
-- "skills/{skillId}/{file}" which resolved to uploads/skills/skills/{skillId}/.
-- Strip the redundant leading "skills/" so paths resolve correctly.
UPDATE skill_files
SET file_path = substring(file_path FROM position('/' IN file_path) + 1)
WHERE file_path LIKE 'skills/%';
