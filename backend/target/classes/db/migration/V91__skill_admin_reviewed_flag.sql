-- Flyway migration V91: one-time admin review flag.
--
-- Once an admin approves a skill (any pending type: new-skill submit,
-- version update, share pending, scope pending), the skill is considered
-- reviewed: from then on the creator can widen the scope and share
-- externally without further admin approval.
--
-- skills_master.admin_reviewed_yn:
--   'Y' — an admin has approved this skill at least once (permanent).
--   'N' — never reviewed (default).
--
-- Backfill: every skill that has a PENDING_APPROVAL -> APPROVED entry in
-- skill_status_history was approved by an admin (before V88 only admin
-- approval could create that transition) → mark as reviewed.

ALTER TABLE skills_master
    ADD COLUMN IF NOT EXISTS admin_reviewed_yn VARCHAR(1) NOT NULL DEFAULT 'N';

UPDATE skills_master m
SET admin_reviewed_yn = 'Y'
WHERE EXISTS (
    SELECT 1 FROM skill_status_history h
    WHERE h.skill_id = m.id
      AND h.from_status = 'PENDING_APPROVAL'
      AND h.to_status = 'APPROVED'
);
