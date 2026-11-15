-- Flyway migration V55: Human Resource System List import.
--
-- One row per person per system (sheet "Human Resource System List" of the
-- Human Resource List Excel). Monthly input columns (jan..dec) hold the
-- person's input in month/month (e.g. 0.5, 1). The table is fully replaced
-- on each import, like system_master / sub_system_master.

CREATE TABLE hr_system_assignment (
    id BIGSERIAL PRIMARY KEY,
    knox_id VARCHAR(100) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    employed VARCHAR(10),
    regular_partner VARCHAR(50),
    company VARCHAR(100),
    part VARCHAR(255),
    employee_number VARCHAR(50),
    system_key VARCHAR(100) NOT NULL,
    system_code VARCHAR(100),
    role VARCHAR(100),
    jan NUMERIC(5, 2),
    feb NUMERIC(5, 2),
    mar NUMERIC(5, 2),
    apr NUMERIC(5, 2),
    may NUMERIC(5, 2),
    jun NUMERIC(5, 2),
    jul NUMERIC(5, 2),
    aug NUMERIC(5, 2),
    sep NUMERIC(5, 2),
    oct NUMERIC(5, 2),
    nov NUMERIC(5, 2),
    dec NUMERIC(5, 2),
    imported_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_hr_system_assignment_knox ON hr_system_assignment(knox_id);
CREATE INDEX idx_hr_system_assignment_system ON hr_system_assignment(system_key);
CREATE INDEX idx_hr_system_assignment_part ON hr_system_assignment(part);

-- One uploaded Human Resource List file awaiting (or finished) import.
-- Same lifecycle as system_import_job: PENDING -> RUNNING -> SUCCESS / FAILED,
-- a newer upload supersedes an older PENDING job.

CREATE TABLE hr_import_job (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    rows_count INTEGER,
    error_count INTEGER,
    error_rows JSONB,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);
