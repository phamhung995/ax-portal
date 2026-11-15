-- Flyway migration V70: nullable sub system code.
--
-- The "Sub Systems List" sheet contains rows that carry only a sub system
-- name (e.g. "Customer System") and no code. Those rows are imported with
-- a NULL code instead of being reported as errors. The existing unique
-- constraint (system_key, sub_system_code) stays — PostgreSQL allows
-- multiple NULLs in a unique index, and the import keeps its
-- duplicate-row check for rows that do have a code.

ALTER TABLE sub_system_master
    ALTER COLUMN sub_system_code DROP NOT NULL;
