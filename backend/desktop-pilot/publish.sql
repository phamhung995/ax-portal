BEGIN;
SELECT set_config('desktop.pilot.expected_database', :'database', true);
SELECT set_config('desktop.pilot.expected_marker', :'marker', true);
SELECT set_config('desktop.pilot.expected_port', :'port', true);
SELECT set_config('desktop.pilot.new_package', :'package_id', true);
DO $$ BEGIN
    IF current_database() <> current_setting('desktop.pilot.expected_database') OR
       host(inet_server_addr()) NOT IN ('127.0.0.1','::1') OR
       inet_server_port()::text <> current_setting('desktop.pilot.expected_port') OR
       (SELECT coalesce(shobj_description(oid,'pg_database'),'') FROM pg_database WHERE datname=current_database()) <> current_setting('desktop.pilot.expected_marker')
    THEN RAISE EXCEPTION 'Dedicated pilot identity mismatch before publication'; END IF;
END $$;
-- Local operator publication only. No update of upstream skill/version or release bytes.
INSERT INTO desktop_package(id,format,file_name,size_bytes,sha256,storage_key)
VALUES (:'package_id'::uuid,:'format',:'file_name',:'size_bytes'::bigint,:'sha256',:'storage_key'::uuid);
INSERT INTO desktop_release(id,skill_id,sequence,metadata_json,package_id,withdrawn)
SELECT :'release_id'::uuid,s.id,:'sequence'::bigint,:'metadata',:'package_id'::uuid,false
FROM skills_master s WHERE s.id=:'skill_id'::bigint AND s.deleted_yn='N';
DO $$ BEGIN
    -- The package must have a matching release before commit, even if the skill
    -- was concurrently deleted. The caller uses this session-local package ID.
    IF NOT EXISTS (SELECT 1 FROM desktop_release WHERE package_id=current_setting('desktop.pilot.new_package')::uuid)
    THEN RAISE EXCEPTION 'Pilot skill is unavailable'; END IF;
END $$;
COMMIT;
