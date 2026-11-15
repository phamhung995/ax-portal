-- Flyway migration V35: align skill_files.pending_yn with Hibernate validation.
--
-- V34 created pending_yn as CHAR(1) (PostgreSQL bpchar). Hibernate maps the
-- String field to VARCHAR(1) and schema-validation (ddl-auto=validate) rejects
-- the type mismatch. Alter the column to VARCHAR(1) so the entity and DB agree.
-- (Same pattern as V16 for skills_master.deleted_yn.)

ALTER TABLE skill_files
    ALTER COLUMN pending_yn TYPE VARCHAR(1);
