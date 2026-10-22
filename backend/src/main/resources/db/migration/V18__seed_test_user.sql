-- Flyway migration V18: seed test user (NORMAL_USER) for local testing.
--
-- Account: test@gmail.com / Password (BCrypt, same encoder as the app).
-- The organization_master row (Employed=Y) is required by the login flow
-- (SRS §8 step 5: at least one org row with employed='Y' via knox_id).

INSERT INTO "user" (name, gender, email, mobile, password, role_id, enabled, status, knox_id, created_app_id, created_at, updated_at)
SELECT 'Test User', 'M', 'test@gmail.com', NULL,
       '$2a$10$OrXqUROpCL7ArtYNWCek9OdwiKv2nOCbEaNatUqK/1bw79gip2f2.',
       (SELECT id FROM role WHERE name = 'NORMAL_USER'),
       TRUE, 'ACTIVE', 'test', 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM "user" WHERE LOWER(email) = 'test@gmail.com');

INSERT INTO user_settings (user_id, language, timezone, date_format, email_notify, inapp_notify)
SELECT u.id, 'en', 'Asia/Ho_Chi_Minh', 'DD/MM/YYYY', TRUE, TRUE
FROM "user" u
WHERE LOWER(u.email) = 'test@gmail.com'
  AND NOT EXISTS (SELECT 1 FROM user_settings s WHERE s.user_id = u.id);

-- Org row so the login org check (knox_id + employed='Y') passes.
INSERT INTO organization_master (knox_id, user_name, employed, regular_partner, company, part, group_name, employee_number, imported_at)
VALUES ('test', 'Test User', 'Y', 'Regular', 'Samsung', 'TestPart', 'TestGroup', 'TEST0001', CURRENT_TIMESTAMP)
ON CONFLICT (knox_id, employee_number, part, group_name) DO NOTHING;
