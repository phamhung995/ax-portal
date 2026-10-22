CREATE TABLE desktop_source (
    id varchar(256) PRIMARY KEY,
    label varchar(256) NOT NULL CHECK (length(trim(label)) > 0)
);
-- Opaque reference to upstream skill ID; deliberately no FK/trigger on old tables.
CREATE TABLE desktop_skill_source (
    skill_id bigint NOT NULL CHECK (skill_id > 0),
    source_id varchar(256) NOT NULL REFERENCES desktop_source(id),
    PRIMARY KEY (skill_id, source_id)
);
CREATE INDEX desktop_skill_source_source_idx ON desktop_skill_source(source_id, skill_id);
