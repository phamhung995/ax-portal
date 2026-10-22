-- Flyway migration V13: link user <-> organization via knox_id
-- 1) "user": add knox_id (derived from email local part), drop part/group_name
-- 2) user_history: drop part/group_name
-- 3) organization_master: rebuild with composite PK
--    (knox_id, employee_number, group_name, division, team)
--    so one user can have multiple organization rows.
-- 4) Seed an Employed=Y org row for the default admin so it can still log in.

-- 1) user.knox_id
-- NOTE: NOT unique — the live DB has two users sharing the same email local part
-- (admin@skillmgmt.local and admin@gmail.com both -> 'admin'). Lookups return a list.
ALTER TABLE "user" ADD COLUMN knox_id VARCHAR(100);
UPDATE "user" SET knox_id = SPLIT_PART(email, '@', 1) WHERE knox_id IS NULL;
ALTER TABLE "user" ALTER COLUMN knox_id SET NOT NULL;
CREATE INDEX idx_user_knox_ci ON "user" (LOWER(knox_id));

ALTER TABLE "user" DROP COLUMN part;
ALTER TABLE "user" DROP COLUMN group_name;

-- 2) user_history
ALTER TABLE user_history DROP COLUMN IF EXISTS part;
ALTER TABLE user_history DROP COLUMN IF EXISTS group_name;

-- 3) organization_master rebuild
CREATE TABLE organization_master_new (
    knox_id           VARCHAR(100)  NOT NULL,
    user_name         VARCHAR(255)  NOT NULL,
    employed          VARCHAR(1)    NOT NULL,
    regular_partner   VARCHAR(20)   NOT NULL,
    company           VARCHAR(255),
    group_name        VARCHAR(255)  NOT NULL,
    employee_number   VARCHAR(50)   NOT NULL,
    division          VARCHAR(255)  NOT NULL,
    team              VARCHAR(255)  NOT NULL,
    imported_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (knox_id, employee_number, group_name, division, team)
);

INSERT INTO organization_master_new
    (knox_id, user_name, employed, regular_partner, company, group_name, employee_number, division, team, imported_at)
SELECT knox_id, user_name, employed, regular_partner, company, group_name, employee_number, division, team, imported_at
FROM organization_master
WHERE group_name IS NOT NULL AND group_name <> ''
  AND employee_number IS NOT NULL AND employee_number <> ''
  AND division IS NOT NULL AND division <> ''
  AND team IS NOT NULL AND team <> ''
ON CONFLICT (knox_id, employee_number, group_name, division, team) DO NOTHING;

DROP TABLE organization_master;
ALTER TABLE organization_master_new RENAME TO organization_master;

CREATE INDEX idx_org_user_name ON organization_master (LOWER(user_name));
CREATE INDEX idx_org_knox ON organization_master (LOWER(knox_id));
CREATE INDEX idx_org_group ON organization_master (group_name);

-- 4) Admin org row (Employed=Y) so the seeded admin can log in after part/group removal
INSERT INTO organization_master
    (knox_id, user_name, employed, regular_partner, company, group_name, employee_number, division, team)
VALUES
    ('admin', 'System Admin', 'Y', 'Regular', 'SDSV', 'System Admin Part', '0000000', 'System Division', 'System Team')
ON CONFLICT (knox_id, employee_number, group_name, division, team) DO NOTHING;
