-- Flyway migration V22: Make skill name unique index partial (active skills only)
-- PostgreSQL 17 compatible
--
-- The original uk_skill_name_ci covered ALL rows, so soft-deleted skills
-- (deleted_yn = 'Y') kept occupying their names forever, blocking the
-- creation of a new skill with the same name.
-- A partial index enforces uniqueness only among active (not deleted) skills.

DROP INDEX IF EXISTS uk_skill_name_ci;
CREATE UNIQUE INDEX uk_skill_name_ci
    ON skills_master (LOWER(name))
    WHERE deleted_yn = 'N';
