-- Flyway migration V23: Skill name unique per creator (not global)
-- PostgreSQL 17 compatible
--
-- Business rule change: a user cannot create two skills with the same name
-- (case-insensitive, active only), but different users MAY have skills with
-- the same name. The old global name index is replaced by a composite
-- (name, creator) partial index.

DROP INDEX IF EXISTS uk_skill_name_ci;
CREATE UNIQUE INDEX uk_skill_name_creator_ci
    ON skills_master (LOWER(name), created_by)
    WHERE deleted_yn = 'N';
