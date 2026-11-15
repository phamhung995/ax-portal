-- Flyway migration V65: organization_master restructure for the new
-- GDD member list format (1 person = 1 row, hierarchy Division > Center
-- > Group > Part).
--
-- Changes vs V14:
--   * PK: composite (knox_id, employee_number, part, group_name) -> knox_id
--     only. One person has exactly one org row.
--   * part / group_name become NULLABLE (managers stop at a higher level:
--     a row with Group but no Part is the manager of that Group's parts).
--   * New columns: center, division, phone, email, position (job title).
--
-- Decision (user-confirmed 2026-10-31): existing rows are DROPPED — the
-- org file will be re-imported with the new columns.

CREATE TABLE organization_master_new (
    knox_id           VARCHAR(100)  NOT NULL,
    user_name         VARCHAR(255)  NOT NULL,
    employed          VARCHAR(1)    NOT NULL,
    regular_partner   VARCHAR(20)   NOT NULL,
    company           VARCHAR(255),
    division          VARCHAR(255),
    center            VARCHAR(255),
    group_name        VARCHAR(255),
    part              VARCHAR(255),
    employee_number   VARCHAR(50),
    phone             VARCHAR(50),
    email             VARCHAR(255),
    position          VARCHAR(255),
    imported_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (knox_id)
);

DROP TABLE organization_master;
ALTER TABLE organization_master_new RENAME TO organization_master;

CREATE INDEX idx_org_user_name ON organization_master (LOWER(user_name));
CREATE INDEX idx_org_knox_ci ON organization_master (LOWER(knox_id));
CREATE INDEX idx_org_division ON organization_master (division);
CREATE INDEX idx_org_center ON organization_master (center);
CREATE INDEX idx_org_group ON organization_master (group_name);
CREATE INDEX idx_org_part ON organization_master (part);

-- Admin org row (Employed=Y) so the seeded admin can still log in.
-- Division-level row: the admin sees everything anyway (admin bypasses
-- the scope rule); the row only feeds the login gate + profile display.
INSERT INTO organization_master
    (knox_id, user_name, employed, regular_partner, company, division,
     center, group_name, part, employee_number, position)
VALUES
    ('admin', 'System Admin', 'Y', 'Regular', 'SDSV', 'Global Development Division',
     NULL, NULL, NULL, '0000000', 'Administrator')
ON CONFLICT (knox_id) DO NOTHING;
