-- Flyway migration V2: Dynamic Role table, link User to Role, add enabled and avatar_url
-- PostgreSQL 17 compatible

-- 1. Create role table
CREATE TABLE IF NOT EXISTS role (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 2. Seed initial default roles
INSERT INTO role (name, description) VALUES
    ('ADMIN', 'Quản trị viên hệ thống'),
    ('EDITOR', 'Biên tập viên nội dung'),
    ('USER', 'Người dùng tiêu chuẩn')
ON CONFLICT (name) DO NOTHING;

-- 3. Add role_id foreign key column to "user"
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS role_id BIGINT REFERENCES role(id);

-- 4. Migrate existing data from old 'role' string column to 'role_id' if 'role' column exists
DO $do$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'user' AND column_name = 'role'
    ) THEN
        -- Map matching roles (e.g. 'ADMIN' -> role.id, 'USER' -> role.id)
        UPDATE "user" u
        SET role_id = r.id
        FROM role r
        WHERE r.name = u.role AND u.role_id IS NULL;
        
        -- Fallback: assign 'USER' role to any user where role couldn't be matched
        UPDATE "user"
        SET role_id = (SELECT id FROM role WHERE name = 'USER')
        WHERE role_id IS NULL;
        
        -- Drop old index and old column
        DROP INDEX IF EXISTS idx_user_role;
        ALTER TABLE "user" DROP COLUMN role;
    END IF;
END $do$;

-- Fallback in case of fresh records with null role_id
UPDATE "user"
SET role_id = (SELECT id FROM role WHERE name = 'USER')
WHERE role_id IS NULL;

-- 5. Enforce NOT NULL on role_id and create index
ALTER TABLE "user" ALTER COLUMN role_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_role_id ON "user"(role_id);

-- 6. Add enabled and avatar_url columns
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);
