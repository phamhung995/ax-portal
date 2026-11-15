-- Flyway migration V47: Skill Cowork.
--
-- A skill can have cowork users: they get creator-like management rights
-- (edit, submit/withdraw, version update, delete file) but CANNOT delete
-- the skill (only the creator or an admin can). Only the creator or an
-- admin may add/remove cowork users.
CREATE TABLE skill_cowork_users (
    skill_id BIGINT NOT NULL REFERENCES skills_master(id),
    user_id  BIGINT NOT NULL REFERENCES "user"(id),
    PRIMARY KEY (skill_id, user_id)
);

CREATE INDEX idx_skill_cowork_users_user ON skill_cowork_users(user_id);
