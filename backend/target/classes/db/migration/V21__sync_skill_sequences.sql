-- Flyway migration V21: Sync identity sequences with existing max ids
-- PostgreSQL 17 compatible
--
-- Manual inserts with explicit ids (or failed inserts that consumed a
-- sequence value) can leave the identity sequence behind the actual
-- max id, causing duplicate key errors on the next insert.
-- setval(seq, max_id + 1, false) makes the next generated id = max_id + 1.

SELECT setval('skill_comments_id_seq',
              COALESCE((SELECT MAX(id) FROM skill_comments), 0) + 1, false);

SELECT setval('skill_files_id_seq',
              COALESCE((SELECT MAX(id) FROM skill_files), 0) + 1, false);

SELECT setval('skill_ratings_id_seq',
              COALESCE((SELECT MAX(id) FROM skill_ratings), 0) + 1, false);

SELECT setval('skill_status_history_id_seq',
              COALESCE((SELECT MAX(id) FROM skill_status_history), 0) + 1, false);

SELECT setval('skill_view_log_id_seq',
              COALESCE((SELECT MAX(id) FROM skill_view_log), 0) + 1, false);

SELECT setval('skills_master_id_seq',
              COALESCE((SELECT MAX(id) FROM skills_master), 0) + 1, false);
