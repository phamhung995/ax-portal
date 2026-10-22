-- Flyway migration V14: organization_master column restructure
-- Drop division/team, add part. New composite PK:
-- (knox_id, employee_number, part, group_name).
-- Decision (user-confirmed 2026-10-05): existing rows are DROPPED (no part
-- data to migrate); the org file will be re-imported with the new columns.

CREATE TABLE organization_master_new (
    knox_id           VARCHAR(100)  NOT NULL,
    user_name         VARCHAR(255)  NOT NULL,
    employed          VARCHAR(1)    NOT NULL,
    regular_partner   VARCHAR(20)   NOT NULL,
    company           VARCHAR(255),
    part              VARCHAR(255)  NOT NULL,
    group_name        VARCHAR(255)  NOT NULL,
    employee_number   VARCHAR(50)   NOT NULL,
    imported_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (knox_id, employee_number, part, group_name)
);

DROP TABLE organization_master;
ALTER TABLE organization_master_new RENAME TO organization_master;

CREATE INDEX idx_org_user_name ON organization_master (LOWER(user_name));
CREATE INDEX idx_org_knox ON organization_master (LOWER(knox_id));
CREATE INDEX idx_org_part ON organization_master (part);
CREATE INDEX idx_org_group ON organization_master (group_name);

-- Admin org row (Employed=Y) so the seeded admin can still log in.
INSERT INTO organization_master
    (knox_id, user_name, employed, regular_partner, company, part, group_name, employee_number)
VALUES
    ('admin', 'System Admin', 'Y', 'Regular', 'SDSV', 'System Admin Part', 'System Admin Group', '0000000')
ON CONFLICT (knox_id, employee_number, part, group_name) DO NOTHING;
