-- V123__app_releases.sql — App update releases (AIPRO STUDIO silent auto-update)
-- Serves tauri-plugin-updater manifest + signed .nsis.zip download.
-- Public API (no auth) — air-gapped internal use.

CREATE TABLE IF NOT EXISTS app_releases (
    id              BIGSERIAL    PRIMARY KEY,
    version         VARCHAR(32)  NOT NULL UNIQUE,
    channel         VARCHAR(16)  NOT NULL DEFAULT 'stable',
    nsis_zip_name   VARCHAR(255) NOT NULL,
    nsis_zip_path   VARCHAR(512) NOT NULL,
    nsis_zip_size   BIGINT       NOT NULL,
    signature       TEXT         NOT NULL,
    release_notes   TEXT,
    published_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_app_releases_active_published ON app_releases (is_active, published_at DESC)
    WHERE is_active = TRUE;
