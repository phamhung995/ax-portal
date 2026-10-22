-- Flyway migration V19: rename skill status PUBLISHED -> APPROVED.
--
-- The value is renamed in skills_master and in the audit table
-- skill_status_history; the CHECK constraint on approval_status is
-- replaced with one that allows 'APPROVED' instead of 'PUBLISHED'.

-- Drop the existing CHECK constraint(s) that still reference 'PUBLISHED'.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'skills_master'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%PUBLISHED%'
    LOOP
        EXECUTE format('ALTER TABLE skills_master DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

UPDATE skills_master
SET approval_status = 'APPROVED'
WHERE approval_status = 'PUBLISHED';

UPDATE skill_status_history
SET from_status = 'APPROVED'
WHERE from_status = 'PUBLISHED';

UPDATE skill_status_history
SET to_status = 'APPROVED'
WHERE to_status = 'PUBLISHED';

ALTER TABLE skills_master
    ADD CONSTRAINT chk_skill_approval_status
    CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'));
