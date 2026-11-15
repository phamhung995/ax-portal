-- Flyway migration V34: index for the Home org-stats active-rate query
-- (login_management scanned by login_at + login_result, last-7-days window).
CREATE INDEX IF NOT EXISTS idx_login_at_result ON login_management (login_at, login_result);
