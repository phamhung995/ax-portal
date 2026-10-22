-- Skill request comments: threading (replies) + likes.
--
-- parent_id: self-reference for threaded replies (NULL = top-level comment).
--   The UI limits the tree to 3 levels (top-level → reply → reply-to-reply);
--   the backend enforces the depth limit on write.
-- like_count: denormalized like counter (same pattern as vote_count).
-- skill_request_comment_likes: one like per user per comment (toggle).

ALTER TABLE skill_request_comments
    ADD COLUMN parent_id BIGINT REFERENCES skill_request_comments(id),
    ADD COLUMN like_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_skill_request_comments_parent
    ON skill_request_comments (parent_id);

CREATE TABLE skill_request_comment_likes (
    comment_id BIGINT NOT NULL REFERENCES skill_request_comments(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);
