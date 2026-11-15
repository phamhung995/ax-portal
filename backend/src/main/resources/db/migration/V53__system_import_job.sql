-- System import job tracking: uploaded files wait for the nightly batch
-- (or an admin "Import Now") and each run's result is stored for the UI.

CREATE TABLE system_import_job (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    systems_count INTEGER,
    sub_systems_count INTEGER,
    error_count INTEGER,
    error_rows JSONB,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);
CREATE INDEX idx_system_import_job_status ON system_import_job(status);
