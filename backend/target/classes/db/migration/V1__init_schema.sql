-- Flyway migration V1: Initial schema for skill-management
-- PostgreSQL 17 compatible

CREATE TABLE IF NOT EXISTS "user" (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255),
    email       VARCHAR(255) NOT NULL,
    gender      VARCHAR(50),
    mobile      VARCHAR(20),
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_user_email ON "user"(email);
CREATE INDEX IF NOT EXISTS idx_user_role ON "user"(role);
