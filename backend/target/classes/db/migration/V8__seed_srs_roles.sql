-- Flyway migration V8: Seed SRS roles and map existing users
-- PostgreSQL 17 compatible
--
-- SRS roles: NORMAL_USER / ADMIN_USER (replaces ADMIN / EDITOR / USER).
-- Old role rows are kept (skill store tables still reference them until the
-- skill module is rebuilt). Existing users are mapped:
--   ADMIN  -> ADMIN_USER
--   EDITOR -> ADMIN_USER (curator privileges fold into admin in the new SRS)
--   USER   -> NORMAL_USER
--
-- The default admin account is created by DataSeeder at application startup
-- (BCrypt hash generated at runtime; credentials logged to console).

INSERT INTO role (name, description) VALUES
    ('NORMAL_USER', 'Người dùng tiêu chuẩn'),
    ('ADMIN_USER', 'Quản trị viên hệ thống')
ON CONFLICT (name) DO NOTHING;

UPDATE "user" u
SET role_id = (SELECT id FROM role WHERE name = 'ADMIN_USER')
WHERE u.role_id IN (SELECT id FROM role WHERE name IN ('ADMIN', 'EDITOR'));

UPDATE "user" u
SET role_id = (SELECT id FROM role WHERE name = 'NORMAL_USER')
WHERE u.role_id IN (SELECT id FROM role WHERE name = 'USER');
