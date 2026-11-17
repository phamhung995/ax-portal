-- V126: Install events (GĐ6, BA FR-6 / Q5 chốt 18/11).
-- The companion app reports 1-click install outcomes here; the success
-- rate (Wilson lower bound) enters the Skill/MCP score once >= 5 installs
-- exist for an item (BA §9.1: 0.6 quality + 0.3 wilson + 0.1 popularity).
-- Auth (Q5): dedicated companion token + rate-limit + HMAC-SHA256 signature
-- of the raw body — enforced in the service layer, not by this table.
-- Dedupe: one row per (target_type, target_id, user_id, version); a
-- companion retry of the same event is ignored, never double-counted.
-- NOTE: the unique key cannot dedupe anonymous rows (user_id NULL) or
-- version-less rows — Postgres treats NULLs as distinct. The service
-- checks for duplicates explicitly (existsDuplicate) before inserting;
-- the constraint below is the backstop for the non-null case.

CREATE TABLE install_events (
    id            BIGSERIAL PRIMARY KEY,
    target_type   VARCHAR(20) NOT NULL,  -- SKILL | MCP
    target_id     BIGINT NOT NULL,
    user_id       BIGINT REFERENCES "user"(id),
    success       BOOLEAN NOT NULL,
    version       VARCHAR(50),
    reported_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_install_event UNIQUE (target_type, target_id, user_id, version)
);

CREATE INDEX idx_install_events_target ON install_events (target_type, target_id);
