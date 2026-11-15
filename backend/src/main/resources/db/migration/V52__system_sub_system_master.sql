-- System / Sub System master data (imported from the System List Excel).
-- Full-replace on each import: the application deletes all rows and
-- re-inserts the file's data, so no unique constraint couples the two
-- tables beyond the FK.

CREATE TABLE system_master (
    id BIGSERIAL PRIMARY KEY,
    system_key VARCHAR(100) NOT NULL,
    system_name VARCHAR(255) NOT NULL,
    current_phase VARCHAR(50),
    client_of_sds VARCHAR(255),
    gdc_group VARCHAR(255),
    gdc_part VARCHAR(255),
    imported_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_system_master_key UNIQUE (system_key)
);
CREATE INDEX idx_system_master_part ON system_master(gdc_part);

CREATE TABLE sub_system_master (
    id BIGSERIAL PRIMARY KEY,
    system_key VARCHAR(100) NOT NULL REFERENCES system_master(system_key),
    sub_system_code VARCHAR(100) NOT NULL,
    sub_system_name VARCHAR(255) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_sub_system_key_code UNIQUE (system_key, sub_system_code)
);
CREATE INDEX idx_sub_system_master_system ON sub_system_master(system_key);
