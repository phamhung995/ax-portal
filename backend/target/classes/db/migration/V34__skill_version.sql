-- Flyway migration V34: skill version updates + version history snapshots.
--
-- A version update on an APPROVED skill is staged as pending state:
--   * new files are stored with pending_yn = 'Y' (hidden from normal use)
--   * the proposed metadata + files-to-remove live on the skill as JSONB
-- On approval the CURRENT state is snapshotted into skill_version /
-- skill_version_file, the pending state is applied, and version is bumped.
-- Files removed from the live skill are NOT deleted from disk — old
-- versions keep them for download.

-- Pending flag on skill files (version-update uploads before approval).
ALTER TABLE skill_files ADD COLUMN pending_yn CHAR(1) NOT NULL DEFAULT 'N';

-- Staged metadata + files-to-remove for a pending version update.
ALTER TABLE skills_master ADD COLUMN pending_metadata JSONB;
ALTER TABLE skills_master ADD COLUMN pending_remove_file_ids JSONB;

-- Snapshot of a skill version (point-in-time metadata at review time).
CREATE TABLE skill_version (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skills_master(id),
    version INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    public_scope VARCHAR(20) NOT NULL,
    hashtags JSONB,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_skill_version UNIQUE (skill_id, version)
);

CREATE INDEX idx_skill_version_skill ON skill_version (skill_id, version DESC);

-- Files belonging to a snapshotted version. Metadata is COPIED (no FK to
-- skill_files) because the live skill_file row may be deleted later.
CREATE TABLE skill_version_file (
    id BIGSERIAL PRIMARY KEY,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT
);

CREATE INDEX idx_skill_version_file_version ON skill_version_file (skill_version_id);
