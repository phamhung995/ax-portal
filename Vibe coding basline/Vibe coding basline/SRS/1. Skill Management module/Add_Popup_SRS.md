# SRS — Skill AI Add Skill Popup

## Software Requirements Specification

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | Add Skill Popup |
| **Dự án** | Hệ thống Quản lý - Đo lường & Đánh giá Hiệu năng AI Skill |
| **Phiên bản** | 1.0 |
| **Ngày tạo** | 08/09/2026 |
| **Tác giả** | BA Team (tq.hop) |
| **Trạng thái** | Draft |

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu này mô tả các yêu cầu chức năng và phi chức năng của chức năng **Add Skill Popup** trong module Skill AI Management của hệ thống. Tài liệu phục vụ cho đội phát triển (frontend, backend, QA) làm cơ sở triển khai và kiểm thử.

### 1.2. Phạm vi

Add Skill Popup là cửa sổ hiện lên khi người dùng click nút "Add Skill" trên toolbar của grid Skill List, cho phép:
- Nhập thông tin cơ bản của skill (tên, mô tả, phạm vi hiển thị)
- Upload file skill (tùy chọn)
- Tự động tạo skill ở trạng thái `DRAFT`

### 1.3. Công nghệ

| Lớp | Công nghệ |
|-----|-----------|
| Frontend | Vue 3 + Design System (tham chiếu folder `../design-system/`) |
| Backend | Java (Spring Boot) |
| Database | PostgreSQL |
| Authentication | JWT (JSON Web Token) |

### 1.4. Tài liệu tham khảo

| Tài liệu | Path |
|----------|------|
| Add Skill Popup BA Spec | `Add Skill Popup/Add_Popup_Spec.md` |

---

## 2. Mô tả tổng thể

### 2.1. Người dùng

| Vai trò | Mô tả |
|---------|-------|
| **User (thông thường)** | Tạo skill mới |
| **Admin** | Toàn bộ quyền của User |

### 2.2. Giả định

- Người dùng đã đăng nhập thành công và có JWT hợp lệ
- Hệ thống có kết nối mạng ổn định
- Storage có dung lượng đủ để lưu file upload

### 2.3. Wireframe

```
┌─────────────────────────────────────────────────────┐
│  Register new skill                        [×]    │
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
│              (When a file is set → compact: 🔄 Replace file)│
├─────────────────────────────────────────────────────┤
│  ℹ️ The skill is created in Draft status.           │
│     You can edit it and submit for review           │
│     when ready.                                     │
├─────────────────────────────────────────────────────┤
│                              [Cancel]  [Save]       │
└─────────────────────────────────────────────────────┘
```

---

## 3. Yêu cầu chức năng

### 3.1. FR-01: Mở Add Skill Popup

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-01 |
| **Tên** | Mở Add Skill Popup |
| **Ưu tiên** | Cao |
| **Trigger** | Click nút "Add Skill" trên toolbar |

**Mô tả:** Khi người dùng click nút "Add Skill", hệ thống mở popup và reset form về trạng thái mặc định.

**Hành vi:**
- SH-01-01: Popup PHẢI hiển thị với form trống
- SH-01-02: `public_scope` PHẢI mặc định là `PRIVATE`
- SH-01-03: Character count PHẢI hiển thị "0/2000"
- SH-01-04: File upload area PHẢI trống

---

### 3.2. FR-02: Nhập thông tin skill

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-02 |
| **Tên** | Nhập thông tin skill |
| **Ưu tiên** | Cao |
| **Trigger** | Người dùng điền form |

**Mô tả:** Người dùng nhập thông tin skill bao gồm tên, phạm vi hiển thị, mô tả, và upload file.

**Yêu cầu:**

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Skill name | Text input | Có | Không rỗng, max 100 ký tự | Placeholder: "Enter skill name..." |
| Public scope | Dropdown | Có | — | Mặc định: `PRIVATE` |
| Category | Chip multi-select | **Có** | ≥ 1 category | Danh sách từ taxonomy master (admin CRUD). "A skill can belong to several categories." |
| Process Stage | Chip multi-select | **Có** | ≥ 1 stage | Danh sách từ taxonomy master (admin CRUD). "Pick every stage this skill applies to." |
| Description | Rich Text Editor | Không | **Không giới hạn độ dài** (không có server-side length check — V32); ảnh trong description: base64 Data URI (jpg/jpeg/png/webp, max 5MB, tự compress >500KB) | Placeholder: "Enter skill description..." |
| Hashtag | Tag input | Không | Max 10 tag, max 30 ký tự/tag, `[a-zA-Z0-9_-]` | Không phân biệt hoa/thường, không trùng. **Nếu tag trùng → toast "Hashtag already exists"**. **Nếu đạt 10 tag → toast "Maximum 10 hashtags"** |
| File | File upload | Không | White-list extension, max 10MB | Drag & drop hoặc click, nhiều file |

**White-list extension:**
| Định dạng | Nhóm | Use case |
|-----------|------|----------|
| `.zip`, `.7z` | Package | Bundle nhiều file |
| `.json` | Config | Cấu hình JSON |
| `.yaml`, `.yml` | Config | Cấu hình YAML |
| `.txt`, `.md` | Text | Prompt, document |
| `.py`, `.js`, `.ts` | Script | Script đơn file |

**Yêu cầu chi tiết:**
- SH-02-01: Tên skill PHẢI không rỗng và không chỉ chứa khoảng trắng
- SH-02-02: Tên skill PHẢI ≤ 100 ký tự
- SH-02-03: Character count tên PHẢI hiển thị "X/100", màu đỏ khi ≥ 90 ký tự
- SH-02-04: Character count mô tả PHẢI cập nhật real-time khi nhập
- SH-02-05: File upload PHẢI hỗ trợ drag & drop
- SH-02-06: File upload PHẢI hỗ trợ click để chọn file
- SH-02-07: File sai extension PHẢI bị reject với thông báo lỗi
- SH-02-08: File > 10MB PHẢI bị reject với thông báo lỗi
- SH-02-09: Hashtag PHẢI max 10 tag, mỗi tag max 30 ký tự
- SH-02-10: Hashtag PHẢI chỉ chứa ký tự `[a-zA-Z0-9_-]`
- SH-02-11: Hashtag trùng trong cùng skill PHẢI bị reject và toast "Hashtag already exists"
- SH-02-12: Hashtag đạt 10 tag PHẢI toast "Maximum 10 hashtags"
- SH-02-13: Hashtag không phân biệt hoa/thường — "Java" và "java" coi là trùng

---

### 3.3. FR-03: Lưu skill mới

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-03 |
| **Tên** | Lưu skill mới |
| **Ưu tiên** | Cao |
| **Trigger** | Click nút "Save" |

**Mô tả:** Lưu thông tin skill mới vào hệ thống.

**Luồng chính:**
1. User click "Save"
2. Frontend validate form:
   - Tên skill: không rỗng, ≤ 100 ký tự
   - Hashtag: max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]`
   - File (nếu có): extension trong white-list, size ≤ 10MB
3. Nếu không hợp lệ → highlight trường lỗi, toast thông báo
4. Nếu hợp lệ → gọi `POST /api/v1/skills` (multipart/form-data)
5. Nếu thành công:
   - Đóng popup
   - Refresh grid Skill List
   - Toast "Skill created successfully" (ERR-ADD-001)
6. Nếu thất bại → toast "Unable to create skill. Please try again." (ERR-ADD-002), giữ nguyên form

**Dữ liệu gửi đi:**
```json
{
  "name": "<tên skill>",
  "description": "<mô tả>",
  "publicScope": "<PRIVATE|PART|GROUP|PUBLIC>",
  "categoryIds": [1, 2],
  "stageIds": [3],
  "hashtags": ["<tag1>", "<tag2>"]
}
```
+ File (nếu có) trong `multipart/form-data` (`categoryIds`/`stageIds` là repeated form params)

**Dữ liệu lưu vào DB:**
| Trường | Giá trị |
|--------|---------|
| `name` | Từ form |
| `description` | Từ form |
| `approval_status` | `DRAFT` (tự động) |
| `public_scope` | Từ form |
| `hashtags` | Từ form (JSONB) |
| `skill_category_mapping` | 1 row per `categoryId` (V31) |
| `skill_stage_mapping` | 1 row per `stageId` (V31) |
| `created_by` | User ID từ JWT |
| `created_at` | Current timestamp |

---

### 3.4. FR-04: Hủy bỏ

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-04 |
| **Tên** | Hủy bỏ tạo skill |
| **Ưu tiên** | Cao |
| **Trigger** | Click "Cancel", click overlay, hoặc nhấn Esc |

**Mô tả:** Đóng popup và reset form.

**Hành vi:**
- SH-04-01: Popup PHẢI đóng khi click nút "Cancel"
- SH-04-02: Popup PHẢI đóng khi click vào vùng overlay (nền tối)
- SH-04-03: Popup PHẢI đóng khi nhấn phím Esc
- SH-04-04: Form PHẢI được reset khi đóng
- SH-04-05: Nếu form có dữ liệu (tên skill, mô tả, hashtag, hoặc file) → PHẢI hiển thị dialog xác nhận trước khi đóng: "Are you sure you want to cancel? The information you entered will not be saved." (ERR-ADD-004)
- SH-04-06: Nếu form trống → đóng ngay, không hiển thị dialog xác nhận

---

## 4. Yêu cầu phi chức năng

### 4.1. Hiệu năng

| ID | Yêu cầu |
|----|---------|
| NFR-PERF-01 | Popup PHẢI hiển thị trong vòng **200ms** sau khi click "Add Skill" |
| NFR-PERF-02 | API tạo skill PHẢI trả về trong **1s** (bao gồm upload file) |
| NFR-PERF-03 | File upload PHẢI hỗ trợ progress indicator khi file > 1MB |

### 4.2. Bảo mật

| ID | Yêu cầu |
|----|---------|
| NFR-SEC-01 | Tất cả API PHẢI yêu cầu JWT trong `Authorization` header |
| NFR-SEC-02 | File upload PHẢI được validate extension ở cả frontend và backend |
| NFR-SEC-03 | File upload PHẢI được kiểm tra size (≤ 10MB) ở cả frontend và backend |
| NFR-SEC-04 | Nội dung mô tả PHẢI được escape khi render (phòng XSS) |
| NFR-SEC-05 | Rate limiting: tối đa 5 skill/phút/user |

### 4.3. Khả dụng

| ID | Yêu cầu |
|----|---------|
| NFR-UX-01 | Popup PHẢI có animation mở (fade in + slide up) |
| NFR-UX-02 | Popup PHẢI responsive trên màn hình ≥ 768px |
| NFR-UX-03 | Toast thông báo PHẢI tự ẩn sau 3 giây |
| NFR-UX-04 | Trường bắt buộc PHẢI có dấu `*` màu đỏ |
| NFR-UX-05 | Error message PHẢI hiển thị dưới trường có lỗi |
| NFR-UX-06 | Popup PHẢI tự điều chỉnh chiều cao theo nội dung, không xuất hiện scroll bar khi mở lần đầu (trên màn hình ≥ 768px) |
| NFR-UX-07 | Khi nội dung vượt quá chiều cao popup, `.o-modal__body` PHẢI scroll nội bộ, footer luôn hiển thị trong popup |
| NFR-UX-08 | Khi file được chọn, upload zone PHẢI thu gọn thành 1 dòng compact ("🔄 Replace file") để tiết kiệm không gian, tránh scroll |

### 4.4. Tương thích

| ID | Yêu cầu |
|----|---------|
| NFR-COMP-01 | Hỗ trợ Chrome, Edge, Firefox phiên bản hiện tại và trước đó 1 phiên bản |
| NFR-COMP-02 | UI theo chuẩn Design System (tham chiếu folder `../design-system/`) |

---

## 5. Z-Index Stacking Policy

| Lớp | Z-Index | Ghi chú |
|-----|---------|---------|
| Detail Popup overlay | 1000 | Popup chi tiết skill |
| Add Popup overlay | 2000 | Mở từ grid |
| Confirm Dialog overlay | 3000 | Luôn cao nhất |
| Toast | 4000 | Luôn hiển thị trên mọi popup |

---

## 6. API Specification

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
| `description` | String | Không | Max 2000 ký tự |
| `publicScope` | String | Có | `PRIVATE`, `PART`, `GROUP`, `PUBLIC` |
| `hashtags` | JSON array (String) | Không | Max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]` |
| `file` | File (nhiều) | Không | Max 10MB/file, white-list extension |

**White-list extension:**
`.zip`, `.7z`, `.json`, `.yaml`, `.yml`, `.txt`, `.md`, `.py`, `.js`, `.ts`

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `name` rỗng | SKILL_NAME_REQUIRED | "Skill name is required." |
| `name` > 100 ký tự | SKILL_NAME_TOO_LONG | "Skill name is at most 100 characters." |
| `name` trùng **trong cùng creator** (case-insensitive, soft-delete excluded) | SKILL_NAME_DUPLICATE | "You already have a skill with this name." |
| `publicScope` không hợp lệ | SKILL_INVALID_SCOPE | "Invalid public scope." |
| `description` > 2000 ký tự | SKILL_DESC_TOO_LONG | "Description is at most 2000 characters." |
| `hashtags` > 10 tag | SKILL_HASHTAGS_TOO_MANY | "Maximum 10 hashtags." |
| `hashtags` tag > 30 ký tự | SKILL_HASHTAG_TOO_LONG | "Hashtag is at most 30 characters." |
| `hashtags` ký tự không hợp lệ | SKILL_INVALID_HASHTAG | "Hashtag contains only letters, digits, '-', '_'." |
| File sai extension | SKILL_FILE_INVALID_TYPE | "File format is not supported." |
| File > 10MB | SKILL_FILE_TOO_LARGE | "File size is at most 10MB." |

> **v1.7:** Skill name uniqueness is **per creator** (composite index
> `(LOWER(name), created_by) WHERE deleted_yn = 'N'`, V23 migration) — not
> global. Two different users may create skills with the same name.

**Response (201 Created):**
```json
{ "success": true, "data": 42 }
```

> `data` = id (BIGINT) của skill mới tạo. Frontend dùng id để refresh grid.

**Response (400 Bad Request):**
```json
{ "success": false, "errorCode": "SKILL_NAME_REQUIRED", "message": "Skill name is required." }
```

**Response (413 Payload Too Large):**
```json
{ "success": false, "errorCode": "SKILL_FILE_TOO_LARGE", "message": "File size is at most 10MB" }
```

---

## 7. Database Schema

### 7.1. Bảng `skills_master`

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

-- Case-insensitive unique constraint for skill name PER CREATOR (V23)
CREATE UNIQUE INDEX uk_skill_name_creator_ci
    ON skills_master (LOWER(name), created_by)
    WHERE deleted_yn = 'N';

-- File đính kèm: bảng riêng (1 skill : N file)
CREATE TABLE skill_files (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    skill_id   BIGINT NOT NULL REFERENCES skills_master(id),
    file_name  VARCHAR(255) NOT NULL,
    file_path  VARCHAR(500) NOT NULL,
    file_size  BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

> Khi tạo skill mới, `approval_status` luôn được gán mặc định là `DRAFT`, `version = 1`.
> Tên skill (`name`) phải **duy nhất trong cùng creator** (case-insensitive, chỉ skill chưa xóa) — 2 user khác nhau được đặt tên skill trùng nhau (V23).
> File upload được lưu vào bảng `skill_files` (metadata), không còn cột file trên `skills_master`.
> **Skill không có file vẫn có thể được công bố.** Nút "Download" sẽ bị ẩn khi skill không có file.

---

## 8. Xử lý lỗi & Edge Case

| Tình huống | Hành vi |
|------------|---------|
| Tên skill trùng | Toast "Skill name already exists" (ERR-ADD-005), highlight trường tên |
| File quá lớn (> 10MB) | Toast "File size is at most 10MB" (ERR-ADD-006) |
| File sai định dạng | Toast "File format is not supported" (ERR-ADD-007) |
| Mất kết nối mạng | Toast "Unable to create skill. Please try again." (ERR-ADD-002) |
| User chưa đăng nhập | Redirect về trang login |
| Form chưa điền đầy đủ | Highlight các trường bắt buộc chưa điền |
| API trả về lỗi server (500) | Toast "System error. Please try again later." (ERR-ADD-003) |
| Upload file thành công nhưng tạo skill thất bại | Xóa file đã upload, toast lỗi |

> Error Message Catalog (ID `ERR-ADD-xxx`): xem `Error_Codes_Messages.md` §4.6.

---

## 9. Checklist Kiểm thử (QA)

| ID | Mô tả | Loại | Kết quả |
|----|-------|------|---------|
| TC-01 | Mở popup bằng click "Add Skill" | Functional | ☐ |
| TC-02 | Form reset về trạng thái mặc định | Functional | ☐ |
| TC-03 | Visibility scope mặc định là Private | Functional | ☐ |
| TC-04 | Lưu skill thành công (có file) | Functional | ☐ |
| TC-05 | Lưu skill thành công (không có file) | Functional | ☐ |
| TC-06 | Lưu skill thất bại (tên rỗng) | Functional | ☐ |
| TC-07 | Lưu skill thất bại (tên > 100 ký tự) | Functional | ☐ |
| TC-08 | Mô tả dài (rich text, không giới hạn độ dài) lưu thành công | Functional | ☐ |
| TC-09 | Character count cập nhật real-time | UI | ☐ |
| TC-10 | Upload file .zip thành công | Functional | ☐ |
| TC-11 | Upload file .json thành công | Functional | ☐ |
| TC-12 | Upload file .yaml thành công | Functional | ☐ |
| TC-13 | Upload file .txt thành công | Functional | ☐ |
| TC-14 | Upload file .py thành công | Functional | ☐ |
| TC-15 | Upload file .js thành công | Functional | ☐ |
| TC-16 | Upload file .ts thành công | Functional | ☐ |
| TC-17 | Upload file .md thành công | Functional | ☐ |
| TC-18 | Upload file .exe bị reject | Functional | ☐ |
| TC-19 | Upload file .pdf bị reject | Functional | ☐ |
| TC-20 | Upload file > 10MB bị reject | Functional | ☐ |
| TC-21 | Drag & drop file thành công | UI | ☐ |
| TC-22 | Xóa file đã chọn | Functional | ☐ |
| TC-23 | Đóng popup bằng nút "Cancel" | Functional | ☐ |
| TC-24 | Đóng popup bằng click overlay | Functional | ☐ |
| TC-25 | Đóng popup bằng phím Esc | Functional | ☐ |
| TC-26 | Popup hiển thị trong 200ms | Performance | ☐ |
| TC-27 | XSS trong mô tả bị escape | Security | ☐ |
| TC-28 | User chưa login không thể mở popup | Security | ☐ |
| TC-29 | Skill mới có approval_status = DRAFT | Functional | ☐ |
| TC-30 | Skill mới có public_scope đúng | Functional | ☐ |
| TC-31 | Thêm hashtag thành công (Enter) | Functional | ☐ |
| TC-32 | Hashtag max 10 tag — toast "Maximum 10 hashtags" | Functional | ☐ |
| TC-33 | Hashtag trùng — toast "Hashtag already exists" | Functional | ☐ |
| TC-34 | Hashtag ký tự không hợp lệ — toast lỗi | Functional | ☐ |
| TC-35 | Hashtag > 30 ký tự — toast "Hashtag is at most 30 characters" | Functional | ☐ |
| TC-36 | Xóa hashtag bằng click × | UI | ☐ |
| TC-37 | Skill mới có hashtags đúng trong DB | Functional | ☐ |
| TC-38 | Không chọn Category → lưu bị chặn (required) | Functional | ☐ |
| TC-39 | Không chọn Process Stage → lưu bị chặn (required) | Functional | ☐ |
| TC-40 | Chọn nhiều Category + nhiều Stage → lưu thành công | Functional | ☐ |
| TC-41 | Insert ảnh vào Description (rich text) → lưu base64 Data URI | Functional | ☐ |
