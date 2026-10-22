-- Flyway migration V7: User Management module schema (per SRS v1.6)
-- PostgreSQL 17 compatible
--
-- Strategy (confirmed with owner): update existing tables where possible,
-- add new tables where needed. Existing skill store tables are left as-is
-- (skill module will be rebuilt in a later phase).

-- ============================================================
-- 1. Update "user" table (serves as USER_MASTER)
-- ============================================================

-- Email is the Login ID: max 254 chars (RFC 5321), unique case-insensitive
ALTER TABLE "user" ALTER COLUMN email TYPE VARCHAR(254);
ALTER TABLE "user" DROP CONSTRAINT IF EXISTS uk_user_email;
DROP INDEX IF EXISTS idx_user_email;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email_ci ON "user" (LOWER(email));

-- Organization (Part + Group, both nullable)
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS part       VARCHAR(255);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS group_name VARCHAR(255);

-- Account status: ACTIVE / DEACTIVE (SRS: DEACTIVE users cannot login)
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

-- Audit fields (SRS §26: created/updated by, ip, app id)
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS register_id    VARCHAR(100);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS created_ip     VARCHAR(45);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS created_app_id VARCHAR(100);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS updated_ip     VARCHAR(45);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS updated_app_id VARCHAR(100);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS updated_by     VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_user_status ON "user" (status);
CREATE INDEX IF NOT EXISTS idx_user_part_group ON "user" (part, group_name);

-- ============================================================
-- 2. USER_HISTORY — snapshot of user record on every change
-- ============================================================
CREATE TABLE IF NOT EXISTS user_history (
    history_id        BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    name              VARCHAR(255),
    email             VARCHAR(254) NOT NULL,
    role_name         VARCHAR(50),
    status            VARCHAR(30),
    part              VARCHAR(255),
    group_name        VARCHAR(255),
    register_id       VARCHAR(100),
    created_ip        VARCHAR(45),
    created_app_id    VARCHAR(100),
    updated_ip        VARCHAR(45),
    updated_app_id    VARCHAR(100),
    updated_by        VARCHAR(100),
    created_at        TIMESTAMP WITH TIME ZONE,
    updated_at        TIMESTAMP WITH TIME ZONE,
    history_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_history_user ON user_history (user_id);

-- ============================================================
-- 3. LOGIN_MANAGEMENT — every login attempt (success + failure)
-- ============================================================
CREATE TABLE IF NOT EXISTS login_management (
    login_id       BIGSERIAL PRIMARY KEY,
    user_id        BIGINT,
    email          VARCHAR(254) NOT NULL,
    login_ip       VARCHAR(45),
    login_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_result   VARCHAR(20)  NOT NULL,           -- SUCCESS / FAILURE
    failure_reason VARCHAR(255),
    app_id         VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_login_management_user ON login_management (user_id);
CREATE INDEX IF NOT EXISTS idx_login_management_email ON login_management (LOWER(email));

-- ============================================================
-- 4. OTP_MANAGEMENT — OTP stored as hash, 5 min, latest-only
-- ============================================================
CREATE TABLE IF NOT EXISTS otp_management (
    otp_id       BIGSERIAL PRIMARY KEY,
    user_id      BIGINT,                            -- NULL for pre-registration / unknown user
    email        VARCHAR(254) NOT NULL,
    otp_hash     VARCHAR(255) NOT NULL,
    otp_type     VARCHAR(50)  NOT NULL,             -- EMAIL_VERIFICATION / PASSWORD_RESET
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expired_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at  TIMESTAMP WITH TIME ZONE,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / VERIFIED / EXPIRED
    created_ip   VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_otp_email_type ON otp_management (LOWER(email), otp_type);
CREATE INDEX IF NOT EXISTS idx_otp_user ON otp_management (user_id);

-- ============================================================
-- 5. EMAIL_MANAGEMENT — audit of OTP emails sent
-- ============================================================
CREATE TABLE IF NOT EXISTS email_management (
    email_id       BIGSERIAL PRIMARY KEY,
    user_id        BIGINT,
    email          VARCHAR(254) NOT NULL,
    email_type     VARCHAR(50)  NOT NULL,           -- EMAIL_VERIFICATION / PASSWORD_RESET
    subject        VARCHAR(255),
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / SENT / FAILED
    sent_at        TIMESTAMP WITH TIME ZONE,
    error_code     VARCHAR(50),
    error_message  VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_management_email ON email_management (LOWER(email));

-- ============================================================
-- 6. USER_SETTINGS — 1 row per user (Settings SRS §6.1)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_settings (
    user_id      BIGINT PRIMARY KEY REFERENCES "user"(id),
    language     VARCHAR(10)  NOT NULL DEFAULT 'en',
    timezone     VARCHAR(64)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    date_format  VARCHAR(10)  NOT NULL DEFAULT 'DD/MM/YYYY',
    email_notify BOOLEAN      NOT NULL DEFAULT TRUE,
    inapp_notify BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 7. NOTIFICATIONS — minimal model for GNB bell (full model in Notification SRS)
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES "user"(id),
    title           VARCHAR(255) NOT NULL,
    skill_id        VARCHAR(50),
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, is_read);
