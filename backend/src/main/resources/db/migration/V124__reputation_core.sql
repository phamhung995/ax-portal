-- V124: Reputation core (shared cache for every module).
-- One cached score row per (target_type, target_id). The score is DERIVED from
-- the module's own signal tables (skill_ratings, *_download_log, ...) and
-- recomputed by ReputationService; this table is never written by hand.
-- target_type: SKILL | MCP | AI_TOOL | AI_NEWS | SKILL_REQUEST
-- (Contributor reputation lives in contributor_reputation, derived the same way.)
-- Decided 2026-11-15/16: one shared core, per-type signals; tier labels
-- NEW/TRUSTED/POPULAR are shared for content; author tiers use their own names.

CREATE TABLE reputation_scores (
    target_type     VARCHAR(20) NOT NULL,
    target_id       BIGINT NOT NULL,
    score           NUMERIC(5,2),            -- 0-100, NULL = not enough evidence (NEW)
    tier            VARCHAR(20) NOT NULL DEFAULT 'NEW',  -- NEW | TRUSTED | POPULAR
    rating_count    INT NOT NULL DEFAULT 0,
    rating_avg      NUMERIC(3,2),            -- Bayesian average (0-5) when stars exist
    download_count  INT NOT NULL DEFAULT 0,
    success_rate    NUMERIC(5,4),            -- Wilson success rate, when install events exist
    signals         JSONB,                   -- raw breakdown for the "why?" explainer
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (target_type, target_id)
);

CREATE INDEX idx_reputation_scores_type_score
    ON reputation_scores (target_type, score DESC NULLS LAST, target_id);

CREATE TABLE contributor_reputation (
    user_id            BIGINT PRIMARY KEY REFERENCES "user"(id),
    tier               VARCHAR(30) NOT NULL DEFAULT 'NEW_MEMBER',
    -- NEW_MEMBER | VERIFIED | TRUSTED_AUTHOR | EXPERT (own names, not the content tiers)
    item_count         INT NOT NULL DEFAULT 0,
    avg_score          NUMERIC(5,2),
    fulfilled_requests INT NOT NULL DEFAULT 0,
    report_count       INT NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
