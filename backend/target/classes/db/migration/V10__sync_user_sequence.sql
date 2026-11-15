-- Flyway migration V10: Sync "user" identity sequence with existing max id
-- PostgreSQL 17 compatible
--
-- The sequence may lag behind existing ids (e.g. after manual inserts),
-- causing duplicate key errors on the next insert.

SELECT setval(pg_get_serial_sequence('"user"', 'id'),
              COALESCE((SELECT MAX(id) FROM "user"), 0) + 1,
              false);
