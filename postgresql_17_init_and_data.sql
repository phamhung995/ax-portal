-- =================================================================================================
-- PROJECT: AI-Skill Store & Management Platform
-- DATABASE: PostgreSQL 17 (pgvector enabled)
-- FILE: postgresql_17_init_and_data.sql
-- DESCRIPTION: Full DDL (Schema, Tables, Constraints, Indexes) & DML (Initial Seed Data)
-- COMPATIBILITY: PostgreSQL 17.x / Docker pgvector/pgvector:pg17
-- =================================================================================================

-- -------------------------------------------------------------------------------------------------
-- 0. ENVIRONMENT & CLIENT ENCODING
-- -------------------------------------------------------------------------------------------------
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

-- -------------------------------------------------------------------------------------------------
-- 0.1 DATABASE EXTENSIONS
-- -------------------------------------------------------------------------------------------------
-- Yêu cầu extension 'vector' cho tính năng Semantic Search / AI Title Embedding (pgvector)
CREATE EXTENSION IF NOT EXISTS vector;

-- -------------------------------------------------------------------------------------------------
-- 1. DROP EXISTING OBJECTS (CLEAN SLATE - OPTIONAL)
-- -------------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS skill_approval CASCADE;
DROP TABLE IF EXISTS impact_record CASCADE;
DROP TABLE IF EXISTS skill_usage CASCADE;
DROP TABLE IF EXISTS skill_review CASCADE;
DROP TABLE IF EXISTS skill_rating CASCADE;
DROP TABLE IF EXISTS skill_version CASCADE;
DROP TABLE IF EXISTS skill_tag_mapping CASCADE;
DROP TABLE IF EXISTS skill_tag CASCADE;
DROP TABLE IF EXISTS skill_category_mapping CASCADE;
DROP TABLE IF EXISTS skill CASCADE;
DROP TABLE IF EXISTS skill_category CASCADE;
DROP TABLE IF EXISTS blacklisted_token CASCADE;
DROP TABLE IF EXISTS refresh_token CASCADE;
DROP TABLE IF EXISTS "user" CASCADE;
DROP TABLE IF EXISTS role CASCADE;

-- -------------------------------------------------------------------------------------------------
-- 2. DDL - SCHEMA DEFINITIONS & TABLES
-- -------------------------------------------------------------------------------------------------

-- 2.1 Bảng Vai trò (Role)
CREATE TABLE role (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 2.2 Bảng Tài khoản người dùng ("user" - từ khóa được đặt trong dấu ngoặc kép)
CREATE TABLE "user" (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255),
    email       VARCHAR(255) NOT NULL,
    gender      VARCHAR(50),
    mobile      VARCHAR(20),
    password    VARCHAR(255) NOT NULL,
    role_id     BIGINT NOT NULL REFERENCES role(id),
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    avatar_url  VARCHAR(255),
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_email UNIQUE (email)
);

CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_role_id ON "user"(role_id);

-- 2.3 Bảng Refresh Token
CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_token_token ON refresh_token(token);
CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_expiry ON refresh_token(expiry_date);

-- 2.4 Bảng Blacklisted Token (Thu hồi Access Token JWT khi logout)
CREATE TABLE blacklisted_token (
    id             BIGSERIAL PRIMARY KEY,
    jti            VARCHAR(36) NOT NULL UNIQUE,
    expiry_date    TIMESTAMPTZ NOT NULL,
    blacklisted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_blacklisted_token_jti ON blacklisted_token(jti);
CREATE INDEX idx_blacklisted_token_expiry ON blacklisted_token(expiry_date);

-- 2.5 Bảng Danh mục kỹ năng (Skill Category)
CREATE TABLE skill_category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon        VARCHAR(50),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2.6 Bảng Kỹ năng (Skill)
CREATE TABLE skill (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    description       TEXT NOT NULL,
    author_id         BIGINT NOT NULL REFERENCES "user"(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    merged_into_id    BIGINT REFERENCES skill(id),
    deprecated_reason TEXT,
    current_version   INTEGER NOT NULL DEFAULT 1,
    file_name         VARCHAR(255),
    file_path         VARCHAR(500),
    usage_count       INTEGER NOT NULL DEFAULT 0,
    average_rating    DECIMAL(3,2) DEFAULT 0.00,
    rating_count      INTEGER NOT NULL DEFAULT 0,
    title_embedding   vector(384),
    version_no        INTEGER NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_author ON skill(author_id);
CREATE INDEX idx_skill_status ON skill(status);
CREATE INDEX idx_skill_created_at ON skill(created_at);
CREATE INDEX idx_skill_deleted_at ON skill(deleted_at);

-- 2.7 Bảng Liên kết Kỹ năng - Danh mục (Skill - Category Mapping)
CREATE TABLE skill_category_mapping (
    skill_id    BIGINT REFERENCES skill(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES skill_category(id) ON DELETE CASCADE,
    is_primary  BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (skill_id, category_id)
);

-- 2.8 Bảng Thẻ từ khóa (Skill Tag)
CREATE TABLE skill_tag (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- 2.9 Bảng Liên kết Kỹ năng - Thẻ (Skill - Tag Mapping)
CREATE TABLE skill_tag_mapping (
    skill_id BIGINT REFERENCES skill(id) ON DELETE CASCADE,
    tag_id   BIGINT REFERENCES skill_tag(id) ON DELETE CASCADE,
    PRIMARY KEY (skill_id, tag_id)
);

-- 2.10 Bảng Lịch sử phiên bản kỹ năng (Skill Version)
CREATE TABLE skill_version (
    id         BIGSERIAL PRIMARY KEY,
    skill_id   BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    version    INTEGER NOT NULL,
    file_name  VARCHAR(255),
    file_path  VARCHAR(500),
    changelog  TEXT,
    created_by BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (skill_id, version)
);

CREATE INDEX idx_skill_version_skill_id ON skill_version(skill_id);

-- 2.11 Bảng Đánh giá sao (Skill Rating: 1 - 5 sao)
CREATE TABLE skill_rating (
    id         BIGSERIAL PRIMARY KEY,
    skill_id   BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    rating     INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (skill_id, user_id)
);

-- 2.12 Bảng Bình luận / Nhận xét / Báo cáo lỗi (Skill Review)
CREATE TABLE skill_review (
    id              BIGSERIAL PRIMARY KEY,
    skill_id        BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    helpful_count   INTEGER NOT NULL DEFAULT 0,
    report_category VARCHAR(30),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_review_skill_id ON skill_review(skill_id);

-- 2.13 Bảng Thống kê tần suất sử dụng hàng ngày (Skill Usage)
CREATE TABLE skill_usage (
    id         BIGSERIAL PRIMARY KEY,
    skill_id   BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    used_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usage_date DATE GENERATED ALWAYS AS ((used_at AT TIME ZONE 'UTC')::date) STORED,
    UNIQUE (skill_id, user_id, usage_date)
);

CREATE INDEX idx_skill_usage_date ON skill_usage(usage_date);

-- 2.14 Bảng Đánh giá hiệu quả & Tiết kiệm Man-Month (Impact Record cho RFC/OC)
CREATE TABLE impact_record (
    id                  BIGSERIAL PRIMARY KEY,
    skill_id            BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    reference_code      VARCHAR(50) NOT NULL,
    effectiveness_score INTEGER NOT NULL CHECK (effectiveness_score BETWEEN 1 AND 5),
    estimated_mm_saved  DECIMAL(6,2),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_impact_reference_code ON impact_record(reference_code);

-- 2.15 Bảng Phê duyệt & Kiểm tra an toàn bảo mật (Skill Approval & Security Scan)
CREATE TABLE skill_approval (
    id                   BIGSERIAL PRIMARY KEY,
    skill_id             BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    curator_id           BIGINT NOT NULL REFERENCES "user"(id),
    decision             VARCHAR(20) NOT NULL,
    reason               TEXT,
    security_scan_flag   BOOLEAN NOT NULL DEFAULT false,
    security_scan_detail TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_approval_skill_id ON skill_approval(skill_id);

-- 2.16 Bảng Nhật ký hệ thống (Audit Log)
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES "user"(id),
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT,
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- -------------------------------------------------------------------------------------------------
-- 3. DML - SEED DATA (DỮ LIỆU MẪU BAN ĐẦU)
-- -------------------------------------------------------------------------------------------------

-- 3.1 Khởi tạo Danh sách Vai trò (Roles)
INSERT INTO role (id, name, description) VALUES
    (1, 'ADMIN', 'Quản trị viên toàn quyền hệ thống'),
    (2, 'EDITOR', 'Biên tập viên kiểm duyệt kỹ năng & nội dung'),
    (3, 'USER', 'Người dùng tiêu chuẩn / Kỹ sư phát triển');

-- 3.2 Khởi tạo Tài khoản người dùng mẫu
-- Mật khẩu mặc định cho tất cả tài khoản mẫu là: password
-- Hash BCrypt chuẩn (Spring Security 10 rounds): $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
INSERT INTO "user" (id, name, email, gender, mobile, password, role_id, enabled, avatar_url, created_at, updated_at) VALUES
    (1, 'Quản Trị Viên Hệ Thống', 'admin@gmail.com', 'MALE', '0901234567', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 1, TRUE, NULL, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    (2, 'Nguyễn Văn Biên Tập', 'editor@gmail.com', 'MALE', '0912345678', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 2, TRUE, NULL, NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days'),
    (3, 'Trần Văn An (Lead AI/RAG)', 'dev.an@example.com', 'MALE', '0987654321', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 3, TRUE, NULL, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    (4, 'Lê Thị Bình (Senior Backend)', 'dev.binh@example.com', 'FEMALE', '0978123456', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 3, TRUE, NULL, NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days'),
    (5, 'Phạm Quốc Cường (DevOps & QA)', 'qa.cuong@example.com', 'MALE', '0965987654', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 3, TRUE, NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days');

-- 3.3 Khởi tạo Danh mục Kỹ năng (Skill Categories)
INSERT INTO skill_category (id, name, description, icon, created_at) VALUES
    (1, 'AI & Trợ lý thông minh', 'Các kỹ năng tích hợp GenAI, RAG, phân tích mã nguồn và tự động hoá tư duy', 'Cpu', NOW() - INTERVAL '30 days'),
    (2, 'Backend Development', 'Mẫu kiến trúc, khung chuẩn REST API, xử lý nghiệp vụ tối ưu hiệu năng', 'Server', NOW() - INTERVAL '30 days'),
    (3, 'DevOps & CI/CD', 'Tự động hoá pipeline build, kiểm thử, container hóa và triển khai hạ tầng', 'Cloud', NOW() - INTERVAL '30 days'),
    (4, 'Database & SQL Performance', 'Tối ưu hóa truy vấn SQL, thiết kế chỉ mục, xử lý dữ liệu lớn', 'Database', NOW() - INTERVAL '30 days'),
    (5, 'Kiểm thử & Đảm bảo chất lượng', 'Kỹ năng sinh unit test, integration test tự động và kiểm tra bảo mật', 'CheckCircle', NOW() - INTERVAL '30 days');

-- 3.4 Khởi tạo Thẻ phân loại (Skill Tags)
INSERT INTO skill_tag (id, name) VALUES
    (1, 'python'),
    (2, 'langchain'),
    (3, 'rag'),
    (4, 'java'),
    (5, 'spring-boot'),
    (6, 'postgresql'),
    (7, 'sql-optimization'),
    (8, 'docker'),
    (9, 'kubernetes'),
    (10, 'ci-cd'),
    (11, 'unit-testing'),
    (12, 'security-audit');

-- 3.5 Khởi tạo Kỹ năng (Skills)
INSERT INTO skill (
    id, title, description, author_id, status, merged_into_id, deprecated_reason,
    current_version, file_name, file_path, usage_count, average_rating, rating_count,
    version_no, deleted_at, created_at, updated_at
) VALUES
    (
        1,
        'AI Code Reviewer & Security Linting',
        'Tự động quét mã nguồn Java / Python, phát hiện lỗ hổng bảo mật OWASP Top 10, phân tích độ phức tạp thuật toán và đề xuất refactoring tối ưu chuẩn Clean Code.',
        3,
        'APPROVED',
        NULL,
        NULL,
        2,
        'ai_code_reviewer_v2.zip',
        'uploads/skills/1/ai_code_reviewer_v2.zip',
        168,
        4.85,
        24,
        2,
        NULL,
        NOW() - INTERVAL '20 days',
        NOW() - INTERVAL '2 days'
    ),
    (
        2,
        'Spring Boot 3 Clean Architecture Scaffolder',
        'Bộ sinh khung dự án Spring Boot 3.4 + Java 21 tiêu chuẩn: tích hợp sẵn JWT, Dynamic RBAC, Flyway Migration, Docker Compose và Swagger OpenAPI 3.',
        4,
        'APPROVED',
        NULL,
        NULL,
        1,
        'spring_boot_scaffolder_v1.zip',
        'uploads/skills/2/spring_boot_scaffolder_v1.zip',
        112,
        4.75,
        18,
        1,
        NULL,
        NOW() - INTERVAL '15 days',
        NOW() - INTERVAL '5 days'
    ),
    (
        3,
        'PostgreSQL Query Plan Analyzer & Index Tuning',
        'Phân tích chi tiết câu lệnh EXPLAIN ANALYZE, phát hiện tình trạng Seq Scan trên bảng lớn, gợi ý tạo Composite/Partial Index và tối ưu bộ đệm WorkMem.',
        4,
        'APPROVED',
        NULL,
        NULL,
        1,
        'postgres_tuner_v1.zip',
        'uploads/skills/3/postgres_tuner_v1.zip',
        95,
        4.90,
        20,
        1,
        NULL,
        NOW() - INTERVAL '12 days',
        NOW() - INTERVAL '3 days'
    ),
    (
        4,
        'Kubernetes GitOps Deployment Template Generator',
        'Sinh tự động các manifest YAML chuẩn cho K8s: Deployment, ClusterIP/Ingress Service, Horizontal Pod Autoscaler (HPA), Secrets giải mã an toàn.',
        5,
        'APPROVED',
        NULL,
        NULL,
        1,
        'k8s_gitops_gen_v1.zip',
        'uploads/skills/4/k8s_gitops_gen_v1.zip',
        64,
        4.60,
        12,
        1,
        NULL,
        NOW() - INTERVAL '10 days',
        NOW() - INTERVAL '1 days'
    ),
    (
        5,
        'Automated Mockito & JUnit 5 Test Case Generator',
        'Phân tích Controller và Service interface để sinh tự động mã kiểm thử đơn vị bao phủ toàn diện các luồng Happy Path và Edge Cases.',
        5,
        'APPROVED',
        NULL,
        NULL,
        1,
        'test_gen_v1.zip',
        'uploads/skills/5/test_gen_v1.zip',
        42,
        4.50,
        8,
        1,
        NULL,
        NOW() - INTERVAL '7 days',
        NOW() - INTERVAL '1 days'
    ),
    (
        6,
        'Legacy Shell Script to Ansible Playbook Converter',
        'Công cụ chuyển đổi các file shell script triển khai truyền thống sang định dạng Ansible idempotent playbook có kiểm soát trạng thái.',
        3,
        'PENDING',
        NULL,
        NULL,
        1,
        'ansible_converter_v1.zip',
        'uploads/skills/6/ansible_converter_v1.zip',
        0,
        0.00,
        0,
        0,
        NULL,
        NOW() - INTERVAL '1 days',
        NOW() - INTERVAL '1 days'
    ),
    (
        7,
        'Basic Python Static Checker (Legacy v1)',
        'Phiên bản ban đầu dùng flake8 cơ bản, hiện đã được gộp và thay thế hoàn toàn bởi AI Code Reviewer & Security Linting v2.',
        3,
        'DEPRECATED',
        1,
        'Đã hợp nhất vào skill AI Code Reviewer & Security Linting (ID: 1) với thuật toán AI GenAI sâu hơn.',
        1,
        'python_linter_v1.zip',
        'uploads/skills/7/python_linter_v1.zip',
        15,
        3.50,
        4,
        1,
        NULL,
        NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '10 days'
    );

-- 3.6 Gán Danh mục cho Kỹ năng (Skill Category Mapping)
INSERT INTO skill_category_mapping (skill_id, category_id, is_primary) VALUES
    (1, 1, true),  -- Skill 1: AI (Primary)
    (1, 5, false), -- Skill 1: QA/Testing
    (2, 2, true),  -- Skill 2: Backend (Primary)
    (2, 3, false), -- Skill 2: DevOps
    (3, 4, true),  -- Skill 3: Database (Primary)
    (3, 2, false), -- Skill 3: Backend
    (4, 3, true),  -- Skill 4: DevOps (Primary)
    (5, 5, true),  -- Skill 5: QA/Testing (Primary)
    (6, 3, true),  -- Skill 6: DevOps (Primary)
    (7, 1, true);  -- Skill 7: AI (Primary)

-- 3.7 Gán Thẻ từ khóa cho Kỹ năng (Skill Tag Mapping)
INSERT INTO skill_tag_mapping (skill_id, tag_id) VALUES
    (1, 1), (1, 3), (1, 12),       -- AI Code Reviewer: python, rag, security-audit
    (2, 4), (2, 5), (2, 8),        -- Spring Boot Scaffolder: java, spring-boot, docker
    (3, 6), (3, 7),                -- PostgreSQL Tuner: postgresql, sql-optimization
    (4, 8), (4, 9), (4, 10),       -- K8s GitOps: docker, kubernetes, ci-cd
    (5, 4), (5, 5), (5, 11),       -- Test Case Gen: java, spring-boot, unit-testing
    (6, 10),                       -- Ansible: ci-cd
    (7, 1);                        -- Legacy linter: python

-- 3.8 Lịch sử phiên bản (Skill Version)
INSERT INTO skill_version (id, skill_id, version, file_name, file_path, changelog, created_by, created_at) VALUES
    (1, 1, 1, 'ai_code_reviewer_v1.zip', 'uploads/skills/1/ai_code_reviewer_v1.zip', 'Phiên bản khởi tạo hỗ trợ kiểm tra tĩnh OWASP.', 3, NOW() - INTERVAL '20 days'),
    (2, 1, 2, 'ai_code_reviewer_v2.zip', 'uploads/skills/1/ai_code_reviewer_v2.zip', 'Nâng cấp mô hình phân tích ngữ nghĩa và gợi ý trực tiếp đoạn mã vá lỗi.', 3, NOW() - INTERVAL '2 days'),
    (3, 2, 1, 'spring_boot_scaffolder_v1.zip', 'uploads/skills/2/spring_boot_scaffolder_v1.zip', 'Khởi tạo template kiến trúc đa tầng chuẩn DDD.', 4, NOW() - INTERVAL '15 days'),
    (4, 3, 1, 'postgres_tuner_v1.zip', 'uploads/skills/3/postgres_tuner_v1.zip', 'Bản phát hành đầu tiên: phân tích query plan và indexing.', 4, NOW() - INTERVAL '12 days'),
    (5, 4, 1, 'k8s_gitops_gen_v1.zip', 'uploads/skills/4/k8s_gitops_gen_v1.zip', 'Hỗ trợ K8s v1.30+ manifest generation.', 5, NOW() - INTERVAL '10 days'),
    (6, 5, 1, 'test_gen_v1.zip', 'uploads/skills/5/test_gen_v1.zip', 'Hỗ trợ sinh test JUnit 5 & Mockito 5.', 5, NOW() - INTERVAL '7 days'),
    (7, 6, 1, 'ansible_converter_v1.zip', 'uploads/skills/6/ansible_converter_v1.zip', 'Bản đệ trình ban đầu chờ duyệt.', 3, NOW() - INTERVAL '1 days'),
    (8, 7, 1, 'python_linter_v1.zip', 'uploads/skills/7/python_linter_v1.zip', 'Phiên bản cũ đã ngừng cập nhật.', 3, NOW() - INTERVAL '25 days');

-- 3.9 Đánh giá sao (Skill Rating)
INSERT INTO skill_rating (skill_id, user_id, rating, created_at, updated_at) VALUES
    (1, 4, 5, NOW() - INTERVAL '18 days', NOW() - INTERVAL '18 days'),
    (1, 5, 5, NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
    (1, 2, 5, NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days'),
    (2, 3, 5, NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days'),
    (2, 5, 4, NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),
    (3, 3, 5, NOW() - INTERVAL '11 days', NOW() - INTERVAL '11 days'),
    (3, 5, 5, NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days'),
    (4, 4, 5, NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),
    (5, 4, 5, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days');

-- 3.10 Bình luận & Nhận xét (Skill Review)
INSERT INTO skill_review (skill_id, user_id, content, helpful_count, report_category, created_at, updated_at) VALUES
    (1, 4, 'Công cụ review code rất thông minh, bắt được 2 lỗi SQL injection tiềm ẩn trong RFC-2026-0891 mà sonarqube bỏ sót!', 12, NULL, NOW() - INTERVAL '18 days', NOW() - INTERVAL '18 days'),
    (1, 5, 'Khả năng sinh patch code đề xuất rất chuẩn xác, tích hợp vào pre-commit hook cực kỳ tiện lợi.', 8, NULL, NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days'),
    (2, 3, 'Khung Spring Boot tổ chức packages rất khoa học, cấu hình sẵn Flyway và JWT giúp team tiết kiệm ít nhất 3 ngày khởi tạo dự án.', 15, NULL, NOW() - INTERVAL '13 days', NOW() - INTERVAL '13 days'),
    (3, 3, 'Gợi ý composite index cho truy vấn thống kê lớn rất hiệu quả, query execution time giảm từ 4200ms xuống còn 48ms.', 19, NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (4, 4, 'Manifest Kubernetes sinh ra tuân thủ đúng chuẩn Best Practices về bảo mật (chặn non-root user, set readOnlyRootFilesystem).', 6, NULL, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days');

-- 3.11 Tần suất sử dụng mẫu (Skill Usage)
INSERT INTO skill_usage (skill_id, user_id, used_at) VALUES
    (1, 4, NOW() - INTERVAL '5 days'),
    (1, 5, NOW() - INTERVAL '4 days'),
    (1, 3, NOW() - INTERVAL '3 days'),
    (2, 3, NOW() - INTERVAL '5 days'),
    (2, 5, NOW() - INTERVAL '2 days'),
    (3, 4, NOW() - INTERVAL '4 days'),
    (3, 5, NOW() - INTERVAL '1 days'),
    (4, 3, NOW() - INTERVAL '2 days'),
    (5, 4, NOW() - INTERVAL '1 days');

-- 3.12 Báo cáo Hiệu quả & Tiết kiệm Man-Month (Impact Record cho RFC/OC)
INSERT INTO impact_record (
    skill_id, user_id, reference_code, effectiveness_score, estimated_mm_saved, note, created_at
) VALUES
    (1, 4, 'RFC-2026-0891', 5, 1.50, 'Tự động quét và vá lỗi bảo mật trên toàn bộ 42 endpoints, tiết kiệm 1.5 Man-Month công việc rà soát thủ công.', NOW() - INTERVAL '16 days'),
    (2, 3, 'OC-2026-0412', 5, 2.00, 'Dựng nhanh 3 dịch vụ Microservices mới từ template có sẵn, chuẩn kiến trúc từ đầu, tiết kiệm 2 Man-Month.', NOW() - INTERVAL '12 days'),
    (3, 4, 'RFC-2026-0955', 5, 0.75, 'Tối ưu hóa các truy vấn báo cáo tài chính cuối tháng, rút ngắn thời gian xử lý và giảm tải CPU máy chủ DB.', NOW() - INTERVAL '8 days'),
    (4, 5, 'OC-2026-0501', 4, 0.50, 'Tự động sinh cấu hình K8s ingress và secret mã hóa cho môi trường UAT.', NOW() - INTERVAL '5 days');

-- 3.13 Lịch sử Phê duyệt Kỹ năng (Skill Approval)
INSERT INTO skill_approval (
    skill_id, curator_id, decision, reason, security_scan_flag, security_scan_detail, created_at
) VALUES
    (1, 2, 'APPROVED', 'Mã nguồn đáp ứng đầy đủ tiêu chuẩn kiểm duyệt, tài liệu hướng dẫn chi tiết.', true, 'Static scan passed: 0 vulnerabilities found, no hardcoded secrets.', NOW() - INTERVAL '19 days'),
    (2, 2, 'APPROVED', 'Template kiến trúc đạt chuẩn hệ thống, tài liệu đầy đủ.', true, 'Security audit passed.', NOW() - INTERVAL '14 days'),
    (3, 2, 'APPROVED', 'Kỹ năng tối ưu SQL an toàn, chỉ phân tích đọc EXPLAIN không gây rủi ro DDL.', true, 'Safe read-only utility.', NOW() - INTERVAL '11 days'),
    (4, 2, 'APPROVED', 'Manifest template an toàn, không chứa credential hay token nhúng.', true, 'No sensitive data detected.', NOW() - INTERVAL '9 days'),
    (5, 2, 'APPROVED', 'Đạt chuẩn kiểm thử tự động.', true, 'Clean test utility.', NOW() - INTERVAL '6 days');

-- 3.14 Nhật ký kiểm tra hệ thống (Audit Log)
INSERT INTO audit_log (user_id, action, entity_type, entity_id, details, created_at) VALUES
    (1, 'SYSTEM_INIT', 'SYSTEM', NULL, 'Hệ thống cơ sở dữ liệu AI-Skill Store được khởi tạo thành công.', NOW() - INTERVAL '30 days'),
    (3, 'SUBMIT_SKILL', 'SKILL', 1, 'Người dùng dev.an@example.com nộp kỹ năng AI Code Reviewer v1.', NOW() - INTERVAL '20 days'),
    (2, 'APPROVE_SKILL', 'SKILL', 1, 'Biên tập viên editor@gmail.com phê duyệt kỹ năng ID: 1.', NOW() - INTERVAL '19 days'),
    (4, 'SUBMIT_SKILL', 'SKILL', 2, 'Người dùng dev.binh@example.com nộp kỹ năng Spring Boot Scaffolder.', NOW() - INTERVAL '15 days'),
    (2, 'APPROVE_SKILL', 'SKILL', 2, 'Biên tập viên phê duyệt kỹ năng ID: 2.', NOW() - INTERVAL '14 days'),
    (4, 'CREATE_IMPACT_RECORD', 'IMPACT_RECORD', 1, 'Ghi nhận tiết kiệm 1.50 MM cho mã công việc RFC-2026-0891.', NOW() - INTERVAL '16 days');

-- -------------------------------------------------------------------------------------------------
-- 4. ĐỒNG BỘ SEQUENCES (AUTO INCREMENT COUNTERS)
-- Đảm bảo khi ứng dụng backend chèn bản ghi mới không bị trùng lặp khóa chính
-- -------------------------------------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('role', 'id'), coalesce(max(id), 1)) FROM role;
SELECT setval(pg_get_serial_sequence('"user"', 'id'), coalesce(max(id), 1)) FROM "user";
SELECT setval(pg_get_serial_sequence('refresh_token', 'id'), coalesce(max(id), 1)) FROM refresh_token;
SELECT setval(pg_get_serial_sequence('blacklisted_token', 'id'), coalesce(max(id), 1)) FROM blacklisted_token;
SELECT setval(pg_get_serial_sequence('skill_category', 'id'), coalesce(max(id), 1)) FROM skill_category;
SELECT setval(pg_get_serial_sequence('skill', 'id'), coalesce(max(id), 1)) FROM skill;
SELECT setval(pg_get_serial_sequence('skill_tag', 'id'), coalesce(max(id), 1)) FROM skill_tag;
SELECT setval(pg_get_serial_sequence('skill_version', 'id'), coalesce(max(id), 1)) FROM skill_version;
SELECT setval(pg_get_serial_sequence('skill_rating', 'id'), coalesce(max(id), 1)) FROM skill_rating;
SELECT setval(pg_get_serial_sequence('skill_review', 'id'), coalesce(max(id), 1)) FROM skill_review;
SELECT setval(pg_get_serial_sequence('skill_usage', 'id'), coalesce(max(id), 1)) FROM skill_usage;
SELECT setval(pg_get_serial_sequence('impact_record', 'id'), coalesce(max(id), 1)) FROM impact_record;
SELECT setval(pg_get_serial_sequence('skill_approval', 'id'), coalesce(max(id), 1)) FROM skill_approval;
SELECT setval(pg_get_serial_sequence('audit_log', 'id'), coalesce(max(id), 1)) FROM audit_log;

-- -------------------------------------------------------------------------------------------------
-- 5. ĐỒNG BỘ FLYWAY MIGRATION HISTORY (TÙY CHỌN CHO KHÁCH HÀNG CHẠY SPRING BOOT)
-- Nếu khách hàng chạy trực tiếp ứng dụng Spring Boot đã bật Flyway, chèn bảng này
-- sẽ giúp Flyway nhận biết toàn bộ 4 migration đã hoàn thành mà không báo lỗi xung đột.
-- -------------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INTEGER NOT NULL PRIMARY KEY,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx ON flyway_schema_history(success);

INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES 
    (1, '1', 'init schema', 'SQL', 'V1__init_schema.sql', 1709403819, 'postgres', 15, true),
    (2, '2', 'add role and user fields', 'SQL', 'V2__add_role_and_user_fields.sql', -1189283742, 'postgres', 22, true),
    (3, '4', 'add refresh and blacklist tokens', 'SQL', 'V4__add_refresh_and_blacklist_tokens.sql', 1549283711, 'postgres', 18, true),
    (4, '5', 'add skill store schema', 'SQL', 'V5__add_skill_store_schema.sql', -882947192, 'postgres', 35, true)
ON CONFLICT (installed_rank) DO NOTHING;

-- =================================================================================================
-- KẾT THÚC SCRIPT - CHÚC MỪNG BẠN ĐÃ KHỞI TẠO THÀNH CÔNG HỆ THỐNG CƠ SỞ DỮ LIỆU!
-- =================================================================================================
