# Skill AI — Edit Skill Popup Spec

## 1. Tổng quan

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | Edit Skill Popup — Chỉnh sửa skill |
| **Khởi động** | Click nút "Edit" trong Detail Popup (khi DRAFT, REJECTED, hoặc APPROVED) |
| **Mục đích** | Cho phép người dùng chỉnh sửa thông tin skill đã tồn tại |
| **Công nghệ** | Frontend: Vue 3 + Design System (tham chiếu folder `../design-system/`) · Backend: Java (Spring Boot) · Database: PostgreSQL |

---

## 2. Wireframe

### 2.1. Khi DRAFT / REJECTED (edit đầy đủ, kể cả file)

```
┌─────────────────────────────────────────────────────┐
│  Edit Skill                               [×]       │
├─────────────────────────────────────────────────────┤
│  Skill name:  [Auto Code Review________] *          │
│                                                     │
│  Visibility scope:  [Public ▼] *                    │
│                  (Private, Part, Group, Public)     │
│                                                     │
│  Category:     [chip1 ×] [chip2 ×] *                │
│  Process Stage:[chipA ×] *                          │
│                                                     │
│  Description:       [Rich Text Editor]              │
│              [Auto code review skill..._]           │
│              [Detect bugs, code smells..._]         │
│  Hashtag:     [#code-review #java #automation]      │
│              [Type a hashtag and press Enter...]    │
│  File:         [Auto Code Review.zip]  ✕ Remove     │
│              [Drag & drop new files or click]       │
├─────────────────────────────────────────────────────┤
│  ⚠️ Changing the skill name will check for duplicates.│
├─────────────────────────────────────────────────────┤
│                                     [Cancel]  [Save]│
└─────────────────────────────────────────────────────┘
```

### 2.2. Khi APPROVED + non-admin (edit hạn chế — tên khóa, KHÔNG quản lý file)

```
┌─────────────────────────────────────────────────────┐
│  Edit Skill                               [×]       │
├─────────────────────────────────────────────────────┤
│  ⚠️ Approved skill: the skill name cannot be      │
│     changed. Files can only be managed via          │
│     "Update Version".                               │
├─────────────────────────────────────────────────────┤
│  Skill name:  [Auto Code Review________] (read-only)│
│                                                     │
│  Visibility scope:  [Public ▼] *                    │
│                                                     │
│  Category:     [chip1 ×] [chip2 ×] *                │
│  Process Stage:[chipA ×] *                          │
│                                                     │
│  Description:       [Rich Text Editor]              │
│              [Auto code review skill..._]           │
│  Hashtag:     [#code-review #java #automation]      │
│              [Type a hashtag and press Enter...]    │
│  (không có khu File — chỉ Update Version quản lý)   │
├─────────────────────────────────────────────────────┤
│                                    [Cancel]  [Save ]│
└─────────────────────────────────────────────────────┘
```

### 2.3. Khi APPROVED + admin (edit metadata đầy đủ, KHÔNG quản lý file)

```
┌─────────────────────────────────────────────────────┐
│  Edit Skill                               [×]       │
├─────────────────────────────────────────────────────┤
│  Skill name:  [Auto Code Review________] *          │
│                                                     │
│  Visibility scope:  [Public ▼] *                    │
│                                                     │
│  Category:     [chip1 ×] [chip2 ×] *                │
│  Process Stage:[chipA ×] *                          │
│                                                     │
│  Description:       [Rich Text Editor]              │
│              [Auto code review skill..._]           │
│  Hashtag:     [#code-review #java #automation]      │
│              [Type a hashtag and press Enter...]    │
│  (không có khu File — chỉ Update Version quản lý)   │
├─────────────────────────────────────────────────────┤
│  ⚠️ Changing the skill name will check for duplicates.│
├─────────────────────────────────────────────────────┤
│                                 [Cancel]  [Save]    │
└─────────────────────────────────────────────────────┘
```

> **Quy tắc file (v1.1):** Skill `APPROVED` — khu File **bị ẩn** ở Edit Popup (cả creator lẫn admin).
> File chỉ quản lý qua **Update Version** (nút riêng trong Detail Popup). Skill DRAFT/REJECTED
> vẫn thêm/xóa file qua Edit thường.

---

## 3. Cấu trúc Popup

| Vùng | Nội dung |
|------|----------|
| **Header** | Tiêu đề "Edit Skill" + nút đóng |
| **Info Banner (APPROVED + non-admin)** | Giải thích edit hạn chế (tên khóa, file chỉ qua Update Version) |
| **Form** | Tên skill, Phạm vi hiển thị, Category*, Process Stage*, Mô tả (Rich Text), Hashtag, File upload (chỉ DRAFT/REJECTED) |
| **Warning Notes (khi có quyền edit tên)** | Lưu ý về trùng tên |
| **Footer** | Nút Cancel + Nút "Save Changes" |

---

## 4. Quy tắc chỉnh sửa theo trạng thái

| Trường | DRAFT | REJECTED | APPROVED |
|--------|-------|----------|-----------|
| Tên skill | ✏️ Edit | ✏️ Edit | 👁️ Read-only (non-admin) / ✏️ Edit (admin) |
| Phạm vi hiển thị | ✏️ Edit | ✏️ Edit | ✏️ Edit |
| Category | ✏️ Edit (required) | ✏️ Edit (required) | ✏️ Edit (required) |
| Process Stage | ✏️ Edit (required) | ✏️ Edit (required) | ✏️ Edit (required) |
| Mô tả | ✏️ Edit | ✏️ Edit | ✏️ Edit |
| Hashtag | ✏️ Edit | ✏️ Edit | ✏️ Edit |
| File | ✏️ Edit | ✏️ Edit | ✕ (chỉ qua Update Version) |

> Khi skill ở trạng thái `APPROVED`:
> - **Tên skill** bị khóa cho non-admin (admin vẫn edit được).
> - **File** bị ẩn cho cả creator lẫn admin — file chỉ quản lý qua **Update Version** (nút riêng trong Detail Popup). Backend chặn `deleteFile` trên skill APPROVED (SKILL_FILE_LOCKED).
> - Phạm vi hiển thị, Category, Process Stage, mô tả, hashtag vẫn edit được.
> - **Không bump version** khi save edit thường (version chỉ tăng khi approve version update).
> - **Vẫn notify subscribers** khi save edit trên skill APPROVED.
> Popup nhận `isAdmin` flag từ Detail Popup để xác định quyền chỉnh sửa.

---

## 5. Chi tiết trường

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Tên skill | Text input | Có | Không rỗng, max 100 ký tự, không trùng | Placeholder: "Enter skill name...". Counter "X/100" hiển thị, màu đỏ khi ≥ 90 ký tự. Read-only khi APPROVED + non-admin |
| Phạm vi hiển thị | Dropdown | Có | — | Mặc định: giá trị hiện tại. Creator và admin đều có thể chỉnh sửa khi APPROVED. Hint text: "This scope takes effect when the skill is approved and approved" (khi DRAFT/REJECTED); "Current visibility scope of the skill" (khi APPROVED) |
| Category | Chip multi-select | **Có** | ≥ 1 category, phải tồn tại trong taxonomy master | Danh sách từ taxonomy master (admin CRUD). "A skill can belong to several categories." |
| Process Stage | Chip multi-select | **Có** | ≥ 1 stage, phải tồn tại trong taxonomy master | Danh sách từ taxonomy master (admin CRUD). "Pick every stage this skill applies to." |
| Mô tả | Rich Text Editor | Không | **Không giới hạn độ dài** (V32); ảnh trong description: base64 Data URI (jpg/jpeg/png/webp, max 5MB, tự compress >500KB) | Placeholder: "Enter skill description..." |
| Hashtag | Tag input | Không | Max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]` | Placeholder: "Enter a hashtag and press Enter...". Tự động thêm `#`. Không phân biệt hoa/thường. **Nếu tag trùng trong cùng skill → toast "Hashtag already exists"**. **Nếu đạt 10 tag → toast "Maximum 10 hashtags"**. Edit được khi APPROVED + non-admin (cùng nhóm với mô tả) |
| File | File upload | Không | Max 10MB, white-list extension | **Chỉ hiển thị khi DRAFT/REJECTED.** Hiển thị file hiện tại + upload thêm file mới (append, không thay thế). **Khi APPROVED → khu File bị ẩn** (chỉ quản lý qua Update Version) |

### 5.1. File upload — Xử lý (chỉ DRAFT/REJECTED)

| Hành động | Kết quả |
|-----------|---------|
| Upload file mới | Thêm file mới vào skill (append — không xóa file cũ) |
| Click "✕ Remove" file cũ | Xóa file khỏi skill (`DELETE /api/v1/skills/{id}/files/{fileId}`) |
| Không làm gì | Giữ nguyên file hiện tại |

> **Skill APPROVED:** khu File bị ẩn hoàn toàn ở Edit Popup (cả creator lẫn admin).
> File chỉ quản lý qua **Update Version** (nút riêng trong Detail Popup).
> Nếu client vẫn gọi `DELETE /api/v1/skills/{id}/files/{fileId}` trên skill APPROVED → backend trả 403 `SKILL_FILE_LOCKED`.

---

## 6. Flow — Luồng tương tác

### 6.1. Mở Edit Skill Popup

```
[User] Click nút "Edit" trong Detail Popup footer
    │
    ├── Nếu non-creator + non-admin ──► Toast "You do not have permission to edit this skill" (ERR-EDT-001)
    │
    ├── Nếu PENDING_APPROVAL ──► Toast "Cannot edit a skill pending approval" (ERR-EDT-002)
    │
    ├── Nếu đã xóa (deleted_YN = 'Y') ──► Toast "Skill has been deleted and cannot be edited" (ERR-EDT-003)
    │
    ▼
[Frontend] Gọi GET /api/v1/skills/{id} để lấy dữ liệu hiện tại
    │
    ├── Thành công ──►
    │       ├── Hiển thị popup, điền form bằng dữ liệu hiện tại (tên, scope, category, stage, mô tả, hashtag, file)
    │       ├── Nếu APPROVED + non-admin → khóa tên, mở khóa scope, ẩn khu File, hiện info banner
    │       ├── Nếu APPROVED + admin → mở khóa tên, ẩn khu File, ẩn info banner
    │       └── Lưu updatedAt để kiểm tra optimistic lock
    │
    └── Thất bại ──► Toast "Unable to load skill information" (ERR-EDT-004)
```

### 6.2. Lưu thay đổi

```
[User] Chỉnh sửa form → Click "Save"
    │
    ▼
[Frontend] Validate form:
    │       ├── Tên skill: không rỗng, ≤ 100 ký tự
    │       ├── Nếu tên thay đổi: check SKILL_NAME_DUPLICATE
    │       ├── Category: ≥ 1 (required)
    │       ├── Process Stage: ≥ 1 (required)
    │       ├── Hashtag: max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]`
    │       └── File (chỉ DRAFT/REJECTED, nếu có): extension white-list, size ≤ 10MB
    │
    ├── Không hợp lệ ──► Highlight trường lỗi, toast thông báo
    │
    └── Hợp lệ
          │
          ▼
    [Frontend] Gọi PUT /api/v1/skills/{id} (multipart/form-data)
              Body thêm form field: `updatedAt` = "<giá trị cũ>" (optimistic lock)
        │
        ├── Thành công ──►
        │       ├── Đóng Edit popup
        │       ├── Emit event `skill:updated` → Detail popup lắng nghe và gọi lại `GET /api/v1/skills/{id}` để refresh
        │       ├── Refresh grid Skill List
        │       └── Toast "Changes saved successfully" (ERR-EDT-005)
        │
        ├── 409 Conflict ──► Toast "Skill has been changed by another user. Your changes were not saved." (ERR-EDT-007)
        │                    Đóng Edit popup, Detail popup tự refresh
        │
        └── Thất bại ──► Toast "Unable to save changes. Please try again." (ERR-EDT-006), giữ nguyên form
```

### 6.3. Hủy bỏ

```
[User] Click "Cancel" / Click overlay / Nhấn Esc
    │
    ▼
[Frontend] Kiểm tra form có thay đổi so với giá trị ban đầu?
    │
    ├── Không thay đổi ──► Đóng popup, không confirm
    │
    └── Có thay đổi ──►
            │
            ▼
      [Frontend] Hiện dialog xác nhận: "Are you sure you want to cancel?
            Your changes will not be saved." (ERR-EDT-011)
          │
          ├── Confirm ──► Đóng popup, không lưu
          │
          └── Cancel ──► Giữ nguyên popup
```

> **So sánh thay đổi:** Frontend lưu snapshot dữ liệu ban đầu khi mở popup. Khi user yêu cầu đóng, so sánh từng trường (tên, scope, mô tả, hashtag, file). Nếu tất cả giống snapshot → đóng luôn, không hiện confirm.

---

## 7. API Endpoint

### 7.1. Cập nhật skill

```
PUT /api/v1/skills/{id}
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

**Request Body (multipart):**
| Field | Type | Required | Ghi chú |
|-------|------|----------|---------|
| `name` | String | Có | Max 100 ký tự. Read-only khi APPROVED + non-admin |
| `description` | String (rich text HTML) | Không | **Không giới hạn độ dài** (V32); ảnh trong description: base64 Data URI (jpg/jpeg/png/webp, max 5MB, tự compress >500KB) |
| `publicScope` | String | Có | `PRIVATE`, `PART`, `GROUP`, `PUBLIC`. Creator và admin đều có thể chỉnh sửa khi APPROVED |
| `categoryIds` | Long (repeated param) | **Có** | ≥ 1, phải tồn tại trong taxonomy master |
| `stageIds` | Long (repeated param) | **Có** | ≥ 1, phải tồn tại trong taxonomy master |
| `hashtags` | JSON array (String) | Không | Max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]` | Không phân biệt hoa/thường, không trùng |
| `file` | File (nhiều) | Không | Max 10MB/file, white-list. **Chỉ áp dụng cho skill DRAFT/REJECTED** (append file mới, không thay thế). Skill APPROVED: không gửi file |
| `updatedAt` | String | Có | Form field trong multipart (không phải JSON). Giá trị `updatedAt` hiện tại (optimistic lock). **Frontend PHẢI giữ nguyên giá trị string gốc từ API response, không parse hay reformat lại.** |

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `name` rỗng | SKILL_NAME_REQUIRED | "Skill name is required." |
| `name` > 100 ký tự | SKILL_NAME_TOO_LONG | "Skill name is at most 100 characters." |
| `name` đổi khi APPROVED + non-admin | SKILL_FIELD_READ_ONLY | "The skill name cannot be changed for a published skill." |
| `name` trùng **trong cùng creator** (khác skill hiện tại, case-insensitive, soft-delete excluded) | SKILL_NAME_DUPLICATE | "You already have a skill with this name." |
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
| Skill PENDING_APPROVAL | SKILL_NOT_EDITABLE | "A skill pending approval cannot be edited." |
| Non-creator, non-admin | SKILL_NOT_EDITABLE | "You do not have permission to edit this skill." |
| Optimistic lock conflict (`updatedAt` khác) | SKILL_OPTIMISTIC_LOCK | "Skill has been changed by another user" |

**Response (200 OK):**
```json
{ "success": true, "data": { "version": 2 } }
```

> `version`: Số phiên bản hiện tại của skill. **Edit thường KHÔNG bump version** — version chỉ tăng khi admin approve một version update (xem `Detail_Popup_SRS.md` §FR-13).
> Optimistic lock dùng `updatedAt` (form field multipart) — nếu `updatedAt` gửi lên khác `updated_at` trong DB → 409.
> Khi skill đang `APPROVED` được save thành công (edit metadata) → backend gửi notification cho toàn bộ subscriber theo `user_settings` (in-app/email).

**Response (400 Bad Request):**
```json
{ "success": false, "errorCode": "SKILL_NAME_DUPLICATE", "message": "You already have a skill with this name." }
```

**Response (404 Not Found):**
```json
{ "success": false, "errorCode": "SKILL_NOT_FOUND", "message": "Skill not found" }
```

**Response (403 Forbidden):**
```json
{ "success": false, "errorCode": "SKILL_NOT_EDITABLE", "message": "A skill pending approval cannot be edited." }
```

**Response (409 Conflict — Optimistic Lock):**
```json
{ "success": false, "errorCode": "SKILL_OPTIMISTIC_LOCK", "message": "Skill has been changed by another user" }
```

### 7.2. Xóa file skill

```
DELETE /api/v1/skills/{id}/files/{fileId}
Authorization: Bearer <JWT>
```

**Response (204 No Content):** *(empty body — xóa thành công)*

**Response (403 Forbidden — skill APPROVED):**
```json
{ "success": false, "errorCode": "SKILL_FILE_LOCKED", "message": "Files can only be managed via Update Version for approved skills." }
```

**Response (404 Not Found):**
```json
{ "success": false, "errorCode": "SKILL_FILE_NOT_FOUND", "message": "Skill file does not exist." }
```

> Chỉ xóa được file trên skill DRAFT/REJECTED. Skill APPROVED → 403 `SKILL_FILE_LOCKED` (file chỉ quản lý qua Update Version).

---

## 8. CSDL Liên Quan

```sql
-- skills_master: không còn cột file — file nằm trong bảng skill_files (1 skill : N file)
--   skill_files: id, skill_id, file_name, file_path, file_size, created_at

-- Khi PUT /api/v1/skills/{id}:
UPDATE skills_master SET
    name              = :name,
    description       = :description,
    public_scope  = :publicScope,
    hashtags          = :hashtags,
    updated_at        = CURRENT_TIMESTAMP
WHERE id = :id;
-- LƯU Ý: KHÔNG bump version (version chỉ tăng khi approve version update)

-- Category mapping (V31): xóa cũ + insert mới
DELETE FROM skill_category_mapping WHERE skill_id = :id;
INSERT INTO skill_category_mapping (skill_id, category_id)
VALUES (:skillId, :categoryId); -- cho mỗi categoryId

-- Stage mapping (V31): xóa cũ + insert mới
DELETE FROM skill_stage_mapping WHERE skill_id = :id;
INSERT INTO skill_stage_mapping (skill_id, stage_id)
VALUES (:skillId, :stageId); -- cho mỗi stageId

-- File mới upload (chỉ DRAFT/REJECTED) → insert vào skill_files
INSERT INTO skill_files (skill_id, file_name, file_path, file_size, created_at)
VALUES (:skillId, :fileName, :filePath, :fileSize, CURRENT_TIMESTAMP);

-- Khi DELETE /api/v1/skills/{id}/files/{fileId} (chỉ DRAFT/REJECTED):
DELETE FROM skill_files WHERE id = :fileId AND skill_id = :id;
```

---

## 9. Xử lý lỗi & Edge Case

| Tình huống | Hành vi |
|------------|---------|
| Tên skill trùng với skill khác | Toast "Skill name already exists", highlight trường tên |
| Tên skill giữ nguyên (không đổi) | Không check duplicate |
| Chưa chọn Category / Process Stage | Toast "At least one category is required" / "At least one process stage is required", highlight trường |
| File quá lớn (> 10MB) | Toast "File size is at most 10MB" (chỉ DRAFT/REJECTED) |
| File sai định dạng | Toast "File format is not supported" (chỉ DRAFT/REJECTED) |
| Mất kết nối mạng | Toast "Unable to save changes. Please try again." (ERR-EDT-006) |
| Skill bị xóa khi popup đang mở | Popup đóng, toast "Skill has been deleted" |
| Skill chuyển trạng thái khi đang edit | Popup đóng, toast "Skill has been changed by another user" |
| Xóa file trên skill APPROVED | Backend trả 403 SKILL_FILE_LOCKED — UI không cho (khu File ẩn) |
| Edit APPROVED nhưng đổi tên (non-admin) | Backend trả về SKILL_FIELD_READ_ONLY, frontend highlight trường |
| Edit thường trên skill APPROVED | Không bump version, vẫn notify subscribers |
| Người khác edit cùng skill | Optimistic lock: so sánh `updatedAt`, nếu khác → trả 409, frontend toast + reload |

---

## 10. Security

| Yêu cầu | Giải pháp |
|----------|-----------|
| Xác thực | JWT trong `Authorization` header |
| Phân quyền edit | Chỉ creator hoặc admin |
| Phân quyền APPROVED | Non-admin: tên read-only; scope/category/stage/mô tả/hashtag edit được. Admin: edit đầy đủ metadata. **File: KHÔNG quản lý qua Edit (cả creator lẫn admin) — chỉ qua Update Version** |
| File lock (APPROVED) | `DELETE /files/{fileId}` trên skill APPROVED → 403 `SKILL_FILE_LOCKED` |
| Input validation | Validate tên, mô tả, category, stage, file ở cả frontend và backend |
| File upload | Kiểm tra extension, size, scan virus (nếu có) — chỉ áp dụng cho skill DRAFT/REJECTED |
| XSS protection | Sanitize nội dung rich text description (allow-list HTML tags) |
| Optimistic lock | So sánh `updatedAt` trong PUT request; trả 409 nếu conflict |
| Rate limiting | Giới hạn 10 chỉnh sửa/phút/user |

---

## 11. Yêu cầu phi chức năng (UX)

| ID | Yêu cầu |
|----|---------|
| NFR-UX-01 | Popup PHẢI có animation mở (fade in + slide up) |
| NFR-UX-02 | Popup PHẢI responsive trên màn hình ≥ 768px |
| NFR-UX-03 | Popup PHẢI tự điều chỉnh chiều cao theo nội dung, không xuất hiện scroll bar khi mở lần đầu (trên màn hình ≥ 768px) |
| NFR-UX-04 | Khi file đã được chọn/upload, upload zone PHẢI thu gọn thành 1 dòng compact để tiết kiệm không gian, tránh scroll (chỉ DRAFT/REJECTED) |
| NFR-UX-05 | Khi nội dung vượt quá chiều cao popup, `.o-modal__body` PHẢI scroll nội bộ, footer luôn hiển thị trong popup |

---

## 12. Z-Index Stacking Policy

| Lớp | Z-Index | Ghi chú |
|-----|---------|---------|
| Detail Popup overlay | 1000 | Popup chi tiết skill |
| Edit Popup overlay | 2000 | Mở từ Detail, che Detail |
| Confirm Dialog overlay | 3000 | Luôn cao nhất |
| Toast | 4000 | Luôn hiển thị trên mọi popup |
