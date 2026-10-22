-- V5__add_skill_store_schema.sql
-- NOTE: pgvector extension removed — the DB user has no superuser privilege
-- and the Skill entity does not use the embedding column (unused).

CREATE TABLE skill_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    author_id BIGINT NOT NULL REFERENCES "user"(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    merged_into_id BIGINT REFERENCES skill(id),
    deprecated_reason TEXT,
    current_version INTEGER NOT NULL DEFAULT 1,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    usage_count INTEGER NOT NULL DEFAULT 0,
    average_rating DECIMAL(3,2) DEFAULT 0.00,
    rating_count INTEGER NOT NULL DEFAULT 0,
    version_no INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_category_mapping (
    skill_id BIGINT REFERENCES skill(id),
    category_id BIGINT REFERENCES skill_category(id),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (skill_id, category_id)
);

CREATE TABLE skill_tag (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE skill_tag_mapping (
    skill_id BIGINT REFERENCES skill(id),
    tag_id BIGINT REFERENCES skill_tag(id),
    PRIMARY KEY (skill_id, tag_id)
);

CREATE TABLE skill_version (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    version INTEGER NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    changelog TEXT,
    created_by BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (skill_id, version)
);

CREATE TABLE skill_rating (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (skill_id, user_id)
);

CREATE TABLE skill_review (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    content TEXT NOT NULL,
    helpful_count INTEGER NOT NULL DEFAULT 0,
    report_category VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_usage (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usage_date DATE GENERATED ALWAYS AS ((used_at AT TIME ZONE 'UTC')::date) STORED,
    UNIQUE (skill_id, user_id, usage_date)
);

CREATE TABLE impact_record (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    reference_code VARCHAR(50) NOT NULL,
    effectiveness_score INTEGER NOT NULL CHECK (effectiveness_score BETWEEN 1 AND 5),
    estimated_mm_saved DECIMAL(6,2),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_approval (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    curator_id BIGINT NOT NULL REFERENCES "user"(id),
    decision VARCHAR(20) NOT NULL,
    reason TEXT,
    security_scan_flag BOOLEAN NOT NULL DEFAULT false,
    security_scan_detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES "user"(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
