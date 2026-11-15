-- Flyway migration V4: Add refresh_token and blacklisted_token tables
-- PostgreSQL 17 compatible

CREATE TABLE IF NOT EXISTS refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_token ON refresh_token(token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expiry ON refresh_token(expiry_date);

CREATE TABLE IF NOT EXISTS blacklisted_token (
    id             BIGSERIAL PRIMARY KEY,
    jti            VARCHAR(36) NOT NULL UNIQUE,
    expiry_date    TIMESTAMPTZ NOT NULL,
    blacklisted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_blacklisted_token_jti ON blacklisted_token(jti);
CREATE INDEX IF NOT EXISTS idx_blacklisted_token_expiry ON blacklisted_token(expiry_date);
