CREATE TABLE desktop_package (
    id uuid PRIMARY KEY,
    format varchar(8) NOT NULL CHECK (format IN ('zip','7z')),
    file_name varchar(255) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 104857600),
    sha256 varchar(64) NOT NULL CHECK (length(sha256) = 64),
    storage_key uuid NOT NULL UNIQUE
);
CREATE TABLE desktop_release (
    id uuid PRIMARY KEY,
    skill_id bigint NOT NULL CHECK (skill_id > 0),
    sequence bigint NOT NULL CHECK (sequence > 0),
    metadata_json text NOT NULL CHECK (octet_length(metadata_json) <= 65536),
    package_id uuid NOT NULL REFERENCES desktop_package(id),
    withdrawn boolean NOT NULL DEFAULT false,
    UNIQUE (skill_id, sequence)
);
CREATE INDEX desktop_release_latest_idx ON desktop_release(skill_id, withdrawn, sequence DESC);
