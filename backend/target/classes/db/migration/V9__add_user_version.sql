-- Flyway migration V9: Optimistic locking for "user" (User Management SRS §5.5)
-- PostgreSQL 17 compatible

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
