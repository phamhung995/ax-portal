-- Flyway migration V104: remove the dead home.banner.board i18n key.
-- The teacher-board headline span was removed from the banner UI (the board
-- now only frames the written slogan), so the key and its en/vi/ko values
-- are no longer read by any frontend code. V98 inserted the key; this
-- migration removes it so the admin translation list stays clean.

DELETE FROM translations
WHERE key_id IN (SELECT id FROM translation_keys WHERE key = 'home.banner.board');

DELETE FROM translation_keys
WHERE key = 'home.banner.board';
