CREATE FUNCTION desktop_guard_package_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Published desktop package records are immutable';
    END IF;
    IF NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Published desktop package records are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER desktop_package_immutable BEFORE UPDATE OR DELETE ON desktop_package
    FOR EACH ROW EXECUTE FUNCTION desktop_guard_package_immutable();

CREATE FUNCTION desktop_guard_release_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Published desktop releases are immutable';
    END IF;
    IF ROW(NEW.id,NEW.skill_id,NEW.sequence,NEW.metadata_json,NEW.package_id)
        IS DISTINCT FROM ROW(OLD.id,OLD.skill_id,OLD.sequence,OLD.metadata_json,OLD.package_id) THEN
        RAISE EXCEPTION 'Published desktop releases are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER desktop_release_immutable BEFORE UPDATE OR DELETE ON desktop_release
    FOR EACH ROW EXECUTE FUNCTION desktop_guard_release_immutable();
