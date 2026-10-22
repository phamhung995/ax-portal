-- Flyway migration V16: align skills_master.deleted_yn with Hibernate validation.
--
-- V15 created deleted_yn as CHAR(1) (PostgreSQL bpchar). Hibernate maps the
-- String field to VARCHAR(1) and schema-validation (ddl-auto=validate) rejects
-- the type mismatch. Alter the column to VARCHAR(1) so the entity and DB agree.

ALTER TABLE skills_master
    ALTER COLUMN deleted_yn TYPE VARCHAR(1);
