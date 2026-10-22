# Skill AI — Add Skill Popup Spec

## 1. Tổng quan

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | Add Skill Popup — Thêm skill mới |
| **Khởi động** | Click nút "Add Skill" trên toolbar của grid Skill List |
| **Mục đích** | Cho phép người dùng tạo skill mới với thông tin cơ bản và upload file |
| **Công nghệ** | Frontend: Vue 3 + Design System (tham chiếu folder `../design-system/`) · Backend: Java (Spring Boot) · Database: PostgreSQL |

---

## 2. Wireframe

```
┌─────────────────────────────────────────────────────┐
│  Register new skill                             [×] │
├─────────────────────────────────────────────────────┤
│  Skill name:  [________________________] *          │
│                                                     │
│  Visibility scope:  [Private ▼] *                   │
│                  (Private, Part, Group, Public)     │
│                                                     │
│  Category:     [chip1 ×] [chip2 ×] *                │
│  Process Stage:[chipA ×] *                          │
│                                                     │
│  Description:       [Rich Text Editor]              │
│              [________________________]             │
│  Hashtag:     [#java #automation #code-review]      │
│              [Type a hashtag and press Enter...]    │
│  File:         [Drag & drop a file here or click]   │
│              Supported: .zip, .7z, .json, .yaml,    │
│              .yml, .txt, .md, .py, .js, .ts         │
├─────────────────────────────────────────────────────┤
│  ℹ️ The skill is created in Draft status.           │
│     You can edit it and submit for review           │
│     when ready.                                     │
├─────────────────────────────────────────────────────┤
│                              [Cancel]  [Save]       │
└─────────────────────────────────────────────────────┘
```

---

## 3. Cấu trúc Popup

| Vùng | Nội dung |
|------|----------|
| **Header** | Tiêu đề "Register new skill" + nút đóng |
| **Form** | Tên skill, Phạm vi hiển thị, Category*, Process Stage*, Mô tả, Hashtag, File upload |
| **Info Note** | Giải thích skill được tạo ở Draft, hướng dẫn submit phê duyệt |
| **Footer** | Nút Cancel + Nút Save |

---

## 4. Chi tiết trường

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Tên skill | Text input | Có | Không rỗng, max 100 ký tự | Placeholder: "Input skill name...". Counter "X/100" hiển thị dưới trường, màu đỏ khi ≥ 90 ký tự |
| Phạm vi hiển thị | Dropdown | Có | — | Mặc định: `PRIVATE`. Options: Private, Part, Group, Public |
| Category | Chip multi-select | **Có** | ≥ 1 category | Danh sách từ taxonomy master (admin CRUD). "A skill can belong to several categories." |
| Process Stage | Chip multi-select | **Có** | ≥ 1 stage | Danh sách từ taxonomy master (admin CRUD). "Pick every stage this skill applies to." |
| Mô tả | Rich Text Editor | Không | **Không giới hạn độ dài** (không có server-side length check — V32); ảnh trong description: base64 Data URI (jpg/jpeg/png/webp, max 5MB, tự compress >500KB) | Placeholder: "Input skill description..." |
| Hashtag | Tag input | Không | Max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]` | Placeholder: "Enter a hashtag and press Enter...". Tự động thêm `#`. Không phân biệt hoa/thường. **Nếu tag trùng trong cùng skill → toast "Hashtag already exists"**. **Nếu đạt 10 tag → toast "Maximum 10 hashtags"**. |
| File | File upload | Không | Max 10MB, white-list extension | Drag & drop hoặc click chọn file, **nhiều file** |

### 4.1. Phạm vi hiển thị (`public_scope`)

| Giá trị | Label | Icon | Mô tả |
|---------|-------|------|-------|
| `PRIVATE` | Riêng tư | 🔒 | Chỉ người tạo thấy |
| `PART` | Theo Part | 🏛️ | Các user cùng Part thấy |
| `GROUP` | Theo Group | 🏢 | Các user cùng Group thấy |
| `PUBLIC` | Công khai | 🌍 | Tất cả user trong hệ thống thấy |

> Phạm vi này chỉ có hiệu lực khi skill được phê duyệt và chuyển sang `APPROVED`.

---

## 5. Flow — Luồng tương tác

### 5.1. Mở Add Skill Popup

```
[User] Click nút "Add Skill" trên toolbar
    │
    ▼
[Frontend] Hiển thị popup, reset form về trạng thái mặc định
```

### 5.2. Lưu skill mới

```
[User] Điền form → Click "Save"
    │
    ▼
[Frontend] Validate form:
    │       ├── Skill name: không rỗng, ≤ 100 ký tự
    │       ├── Public scope: đã chọn
    │       ├── Category: ≥ 1 (required)
    │       ├── Process Stage: ≥ 1 (required)
    │       ├── Hashtag: max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]`
    │       └── File: nếu có, kiểm tra extension (white-list), size ≤ 10MB
    │
    ├── Không hợp lệ ──► Highlight trường lỗi, toast thông báo
    │
    └── Hợp lệ
          │
          ▼
    [Frontend] Gọi POST /api/v1/skills (multipart/form-data)
        │
        ├── Thành công ──►
        │       ├── Đóng popup
        │       ├── Refresh grid Skill List
        │       └── Toast "Skill created successfully" (ERR-ADD-001)
        │
        ├── 400 SKILL_NAME_DUPLICATE ──► Highlight trường tên, toast "Skill name already exists" (ERR-ADD-005)
        │
        └── Thất bại ──► Toast "Unable to create skill. Please try again." (ERR-ADD-002), giữ nguyên form
```

### 5.3. Hủy bỏ

```
[User] Click "Cancel" / Click overlay / Nhấn Esc
    │
    ▼
[Frontend] Kiểm tra form có dữ liệu không?
    │
    ├── Form trống (tên + mô tả đều rỗng, không có hashtag và file) ──► Đóng popup, reset form
    │
    └── Form có dữ liệu ──► Hiện dialog xác nhận: "Are you sure you want to cancel?
                          The information you entered will not be saved." (ERR-ADD-004)
              │
              ├── Confirm ──► Đóng popup, reset form
              │
              └── Cancel ──► Giữ nguyên popup
```

---

## 6. API Endpoint

### 6.1. Tạo skill mới

```
POST /api/v1/skills
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

**Request Body (multipart):**
| Field | Type | Required | Ghi chú |
|-------|------|----------|---------|
| `name` | String | Có | Max 100 ký tự |
| `description` | String (rich text HTML) | Không | **Không giới hạn độ dài** (V32); ảnh trong description: base64 Data URI (jpg/jpeg/png/webp, max 5MB, tự compress >500KB) |
| `publicScope` | String | Có | `PRIVATE`, `PART`, `GROUP`, `PUBLIC` |
| `categoryIds` | Long (repeated param) | **Có** | ≥ 1, phải tồn tại trong taxonomy master |
| `stageIds` | Long (repeated param) | **Có** | ≥ 1, phải tồn tại trong taxonomy master |
| `hashtags` | JSON array (String) | Không | Max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]` | Không phân biệt hoa/thường, không trùng |
| `file` | File (nhiều) | Không | Max 10MB/file, white-list: .zip, .7z, .json, .yaml, .yml, .txt, .md, .py, .js, .ts |

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `name` rỗng | SKILL_NAME_REQUIRED | "Skill name is required." |
| `name` > 100 ký tự | SKILL_NAME_TOO_LONG | "Skill name is at most 100 characters." |
| `name` trùng **trong cùng creator** (case-insensitive, soft-delete excluded) | SKILL_NAME_DUPLICATE | "You already have a skill with this name." |
| `publicScope` không hợp lệ | SKILL_INVALID_SCOPE | "Invalid public scope." |
| `categoryIds` rỗng | SKILL_CATEGORY_REQUIRED | "At least one category is required." |
| `categoryIds` không tồn tại | SKILL_INVALID_CATEGORY_ID | "One or more categories do not exist." |
| `stageIds` rỗng | SKILL_STAGE_REQUIRED | "At least one process stage is required." |
| `stageIds` không tồn tại | SKILL_INVALID_STAGE_ID | "One or more process stages do not exist." |
| `hashtags` > 10 tag | SKILL_HASHTAGS_TOO_MANY | "Maximum 10 hashtags" |
| `hashtags` tag > 30 ký tự | SKILL_HASHTAG_TOO_LONG | "Hashtag is at most 30 characters" |
| `hashtags` ký tự không hợp lệ | SKILL_INVALID_HASHTAG | "Hashtag contains only letters, digits, '-', '_'" |
| File sai extension | SKILL_FILE_INVALID_TYPE | "File format is not supported" |
| File > 10MB | SKILL_FILE_TOO_LARGE | "File size is at most 10MB" |

**Response (201 Created):**
```json
{ "success": true, "data": 2 }
```

> `data` = id (BIGINT) của skill mới tạo. `version` mặc định = 1.

**Response (400 Bad Request):**
```json
{ "success": false, "errorCode": "SKILL_NAME_REQUIRED", "message": "Skill name is required." }
```

---

## 7. CSDL Liên Quan

```sql
CREATE TABLE skills_master (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    description       TEXT,
    approval_status   VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                        CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    public_scope  VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE'
                        CHECK (public_scope IN ('PRIVATE', 'PART', 'GROUP', 'PUBLIC')),
    rejection_reason  TEXT CHECK (char_length(rejection_reason) <= 500),
    hashtags          JSONB,
    view_count        INT NOT NULL DEFAULT 0,
    version           INT NOT NULL DEFAULT 1,
    deleted_yn        CHAR(1)      NOT NULL DEFAULT 'N' CHECK (deleted_yn IN ('Y', 'N')),
    created_by        BIGINT NOT NULL REFERENCES "user"(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- File đính kèm: bảng riêng (1 skill : N file)
CREATE TABLE skill_files (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    skill_id   BIGINT NOT NULL REFERENCES skills_master(id),
    file_name  VARCHAR(255) NOT NULL,
    file_path  VARCHAR(500) NOT NULL,
    file_size  BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Case-insensitive unique constraint for skill name PER CREATOR (V23)
CREATE UNIQUE INDEX uk_skill_name_creator_ci
    ON skills_master (LOWER(name), created_by)
    WHERE deleted_yn = 'N';

-- Taxonomy (V31): category + process stage
CREATE TABLE skill_category (
    id   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE process_stage (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    sort_order  INT NOT NULL DEFAULT 0
);
CREATE TABLE skill_category_mapping (
    skill_id   BIGINT NOT NULL REFERENCES skills_master(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES skill_category(id),
    PRIMARY KEY (skill_id, category_id)
);
CREATE TABLE skill_stage_mapping (
    skill_id BIGINT NOT NULL REFERENCES skills_master(id) ON DELETE CASCADE,
    stage_id BIGINT NOT NULL REFERENCES process_stage(id),
    PRIMARY KEY (skill_id, stage_id)
);
```

> Khi tạo skill mới, `approval_status` luôn được gán mặc định là `DRAFT`, `version = 1`.
> Tên skill (`name`) phải **duy nhất trong cùng creator** (case-insensitive, chỉ skill chưa xóa) — 2 user khác nhau được đặt tên skill trùng nhau (V23).
> File upload được lưu vào bảng `skill_files` (metadata), không còn cột file trên `skills_master`.
> **Skill không có file vẫn có thể được công bố.** Nút "Download" sẽ bị ẩn khi skill không có file.
> **Category + Process Stage bắt buộc** (V31) — skill phải thuộc ≥ 1 category và ≥ 1 process stage; danh sách do admin CRUD (public: `GET /api/v1/skills/taxonomy`).

---

## 8. Xử lý lỗi & Edge Case

| Tình huống | Hành vi |
|------------|---------|
| Tên skill trùng | Toast "Skill name already exists" (ERR-ADD-005), highlight trường tên |
| File quá lớn | Toast "File size is at most 10MB" (ERR-ADD-006) |
| File sai định dạng | Toast "File format is not supported" (ERR-ADD-007) |
| Mất kết nối mạng | Toast "Unable to create skill. Please try again." (ERR-ADD-002) |
| User chưa đăng nhập | Redirect về trang login |
| Form chưa điền đầy đủ | Highlight các trường bắt buộc chưa điền |
| API trả về lỗi server (500) | Toast "System error. Please try again later." (ERR-ADD-003) |

> Error Message Catalog (ID `ERR-ADD-xxx`): xem `Error_Codes_Messages.md` §4.6.

---

## 9. Security

| Yêu cầu | Giải pháp |
|----------|-----------|
| Xác thực | JWT trong `Authorization` header |
| Input validation | Validate tên, mô tả, file ở cả frontend và backend |
| File upload | Kiểm tra extension, size, scan virus (nếu có) |
| XSS protection | Escape nội dung mô tả khi render |
| Rate limiting | Giới hạn 5 skill/phút/user |

---

## 10. Yêu cầu phi chức năng (UX)

| ID | Yêu cầu |
|----|---------|
| NFR-UX-01 | Popup PHẢI có animation mở (fade in + slide up) |
| NFR-UX-02 | Popup PHẢI responsive trên màn hình ≥ 768px |
| NFR-UX-03 | Popup PHẢI tự điều chỉnh chiều cao theo nội dung, không xuất hiện scroll bar khi mở lần đầu |
| NFR-UX-04 | Khi file được chọn, upload zone PHẢI thu gọn thành 1 dòng compact ("🔄 Replace file") để tiết kiệm không gian, tránh scroll |
| NFR-UX-05 | Khi nội dung vượt quá chiều cao popup, `.o-modal__body` PHẢI scroll nội bộ, footer luôn hiển thị trong popup |
