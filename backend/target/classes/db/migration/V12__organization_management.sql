-- Flyway migration V12: Organization Management module
-- New standalone master-data table. Additive only (no changes to existing tables).

CREATE TABLE IF NOT EXISTS organization_master (
    org_id            BIGSERIAL PRIMARY KEY,
    knox_id           VARCHAR(100) NOT NULL,
    user_name         VARCHAR(255) NOT NULL,
    employed          VARCHAR(1)   NOT NULL,          -- 'Y' / 'N'
    regular_partner   VARCHAR(20)  NOT NULL,          -- 'Regular' / 'Partner'
    company           VARCHAR(255),
    group_name        VARCHAR(255),                   -- "Group" column in the Excel template
    employee_number   VARCHAR(50),
    division          VARCHAR(255),
    team              VARCHAR(255),
    imported_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Knox ID is the link to "user" (local part of email): unique case-insensitive.
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_knox_ci ON organization_master (LOWER(knox_id));
CREATE INDEX IF NOT EXISTS idx_org_user_name ON organization_master (LOWER(user_name));
CREATE INDEX IF NOT EXISTS idx_org_group ON organization_master (group_name);
CREATE INDEX IF NOT EXISTS idx_org_company ON organization_master (company);
