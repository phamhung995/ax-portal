# SRS — Skill AI Edit Skill Popup

## Software Requirements Specification

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | Edit Skill Popup |
| **Dự án** | AISTEA — Hệ thống Quản lý - Đo lường & Đánh giá Hiệu năng AI-SR |
| **Phiên bản** | 1.0 |
| **Ngày tạo** | 08/09/2026 |
| **Tác giả** | BA Team (tq.hop) |
| **Trạng thái** | Draft |

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu này mô tả các yêu cầu chức năng và phi chức năng của chức năng **Edit Skill Popup** trong module Skill AI Management của hệ thống AISTEA. Tài liệu phục vụ cho đội phát triển (frontend, backend, QA) làm cơ sở triển khai và kiểm thử.

### 1.2. Phạm vi

Edit Skill Popup là cửa sổ hiện lên khi người dùng click nút "Edit" trong Detail Popup, cho phép:
- Chỉnh sửa thông tin skill (tên, mô tả, phạm vi hiển thị, file)
- Edit đầy đủ khi DRAFT/REJECTED
- Edit hạn chế (chỉ tên skill bị khóa) khi APPROVED
- Không cho phép edit khi PENDING_APPROVAL hoặc đã xóa (deleted_YN = 'Y')

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
| Edit Skill Popup BA Spec | `SRS/Edit_Popup_Spec.md` |
| HTML Preview | `SRS/Edit_Popup_Preview.html` |
| Detail Popup BA Spec | `SRS/Detail_Popup_Spec.md` |
| Add Skill Popup BA Spec | `SRS/Add_Popup_Spec.md` |

---

## 2. Mô tả tổng thể

### 2.1. Người dùng

| Vai trò | Mô tả |
|---------|-------|
| **Creator** | Edit skill của mình (DRAFT, REJECTED: đầy đủ; APPROVED: hạn chế — chỉ tên skill bị khóa) |
| **Admin** | Edit bất kỳ skill nào, bao gồm cả tên của APPROVED |

### 2.2. Giả định

- Người dùng đã đăng nhập thành công và có JWT hợp lệ
- Skill được chọn tồn tại trong hệ thống
- Hệ thống có kết nối mạng ổn định
- Storage có dung lượng đủ để lưu file upload

### 2.3. Quy tắc chỉnh sửa theo trạng thái

| Trường | DRAFT | REJECTED | APPROVED | PENDING_APPROVAL | Đã xóa |
|--------|-------|----------|-----------|-------------------|----------|
| Tên skill | ✏️ Edit | ✏️ Edit | 👁️ Read-only (non-admin) / ✏️ Edit (admin) | ❌ Không mở popup | ❌ Không mở popup |
| Phạm vi hiển thị | ✏️ Edit | ✏️ Edit | ✏️ Edit | ❌ Không mở popup | ❌ Không mở popup |
| Mô tả | ✏️ Edit | ✏️ Edit | ✏️ Edit | ❌ Không mở popup | ❌ Không mở popup |
| Hashtag | ✏️ Edit | ✏️ Edit | ✏️ Edit | ❌ Không mở popup | ❌ Không mở popup |
| File | ✏️ Edit | ✏️ Edit | ✏️ Edit | ❌ Không mở popup | ❌ Không mở popup |

---

## 3. Yêu cầu chức năng

### 3.1. FR-01: Mở Edit Skill Popup

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-01 |
| **Tên** | Mở Edit Skill Popup |
| **Ưu tiên** | Cao |
| **Trigger** | Click nút "Edit" trong Detail Popup |

**Mô tả:** Khi người dùng click nút "Edit", hệ thống mở popup và điền form bằng dữ liệu hiện tại của skill.

**Luồng chính:**
1. User click "Edit" trong Detail Popup footer
2. Kiểm tra phân quyền:
   - Nếu non-creator + non-admin → toast "You do not have permission to edit this skill" (ERR-EDT-001)
   - Nếu `PENDING_APPROVAL` → toast "Cannot edit a skill pending approval" (ERR-EDT-002)
   - Nếu đã xóa (`deleted_YN = 'Y'`) → toast "Skill has been deleted and cannot be edited" (ERR-EDT-003)
   - Nếu `DRAFT`, `REJECTED`, `APPROVED` → tiếp tục
3. Gọi `GET /api/v1/skills/{id}` để lấy dữ liệu hiện tại
4. Điền form bằng dữ liệu nhận được
5. Nếu `APPROVED` → khóa trường tên, mở khóa phạm vi, hiển thị info banner

**Hành vi:**
- SH-01-01: Popup PHẢI điền đầy đủ dữ liệu hiện tại của skill
- SH-01-02: Trường tên PHẢI bị khóa khi `APPROVED` + non-admin (admin vẫn được chỉnh sửa). Phạm vi hiển thị luôn mở khóa cho cả creator và admin.
- SH-01-03: Info banner PHẢI hiển thị khi `APPROVED`
- SH-01-04: File cũ PHẢI hiển thị nếu skill có file

**Luồng ngoại lệ:**
- Nếu skill không tồn tại (404) → đóng popup, toast "Skill has been deleted"
- Nếu lỗi kết nối → toast "Unable to load skill information" (ERR-EDT-004)

---

### 3.2. FR-02: Chỉnh sửa thông tin skill

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-02 |
| **Tên** | Chỉnh sửa thông tin skill |
| **Ưu tiên** | Cao |
| **Trigger** | Người dùng thay đổi giá trị trong form |

**Mô tả:** Người dùng chỉnh sửa thông tin skill bao gồm tên, phạm vi hiển thị, mô tả, category, stage, hashtag, và file (tùy status).

**Yêu cầu:**

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Tên skill | Text input | Có | Không rỗng, max 100 ký tự, không trùng | Read-only khi APPROVED + non-admin |
| Phạm vi hiển thị | Dropdown | Có | — | Creator và admin đều có thể chỉnh sửa khi APPROVED |
| Category | Chip multi-select | **Có** | ≥ 1 category | Danh sách từ taxonomy master |
| Process Stage | Chip multi-select | **Có** | ≥ 1 stage | Danh sách từ taxonomy master |
| Mô tả | Rich Text Editor | Không | **Không giới hạn độ dài** (V32); ảnh trong description: base64 Data URI (jpg/jpeg/png/webp, max 5MB, tự compress >500KB) | Placeholder: "Enter skill description..." |
| Hashtag | Tag input | Không | Max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]` | Không phân biệt hoa/thường. **Nếu tag trùng → toast "Hashtag already exists"**. **Nếu đạt 10 tag → toast "Maximum 10 hashtags"** |
| File | File upload | Không | White-list extension, max 10MB | **Chỉ DRAFT/REJECTED** — xem quy tắc quản lý file bên dưới |

**White-list extension:**
`.zip`, `.7z`, `.json`, `.yaml`, `.yml`, `.txt`, `.md`, `.py`, `.js`, `.ts`

**Quy tắc quản lý file theo status (quan trọng):**

| Status | Thêm/xóa file qua Edit thường |
|--------|-------------------------------|
| DRAFT / REJECTED (chưa từng approve) | ✓ Được (upload mới + Delete từng file) |
| APPROVED | ✕ **KHÔNG** — khu file bị ẩn; file chỉ quản lý qua **Update Version** (nút riêng trong Detail Popup). Gọi `DELETE /files/{fileId}` khi APPROVED → `403 SKILL_FILE_LOCKED` |

**Yêu cầu chi tiết:**
- SH-02-01: Tên skill PHẢI không rỗng và không chỉ chứa khoảng trắng
- SH-02-02: Tên skill PHẢI ≤ 100 ký tự
- SH-02-03: Nếu tên thay đổi so với giá trị ban đầu → check `SKILL_NAME_DUPLICATE`
- SH-02-04: Nếu tên giữ nguyên → không check duplicate
- SH-02-05: File upload PHẢI hỗ trợ drag & drop (chỉ DRAFT/REJECTED)
- SH-02-06: Upload file mới PHẢI thêm vào danh sách file (multi-file, không thay thế)
- SH-02-07: Click "Delete" file → xóa row `skill_files` + file trên disk (chỉ DRAFT/REJECTED)
- SH-02-08: Hashtag PHẢI max 10 tag, mỗi tag max 30 ký tự
- SH-02-09: Hashtag PHẢI chỉ chứa ký tự `[a-zA-Z0-9_-]`
- SH-02-10: Hashtag trùng trong cùng skill PHẢI bị reject và toast "Hashtag already exists"
- SH-02-11: Hashtag đạt 10 tag PHẢI toast "Maximum 10 hashtags"
- SH-02-12: Hashtag không phân biệt hoa/thường — "Java" và "java" coi là trùng
- SH-02-13: Skill APPROVED: Edit thường **KHÔNG bump version** (version chỉ tăng khi version update được approve)
- SH-02-14: Skill APPROVED lưu thành công → **notify subscribers** (in-app + email theo cài đặt)

---

### 3.3. FR-03: Lưu thay đổi

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-03 |
| **Tên** | Lưu thay đổi skill |
| **Ưu tiên** | Cao |
| **Trigger** | Click nút "Save Changes" |

**Mô tả:** Lưu thông tin skill đã chỉnh sửa vào hệ thống.

**Luồng chính:**
1. User click "Save Changes"
2. Frontend validate form:
   - Tên skill: không rỗng, ≤ 100 ký tự
   - Nếu tên thay đổi: check `SKILL_NAME_DUPLICATE`
   - Hashtag: max 10 tag, max 30 ký tự/tag, ký tự `[a-zA-Z0-9_-]`
   - File (nếu có): extension trong white-list, size ≤ 10MB
3. Nếu không hợp lệ → highlight trường lỗi, toast thông báo
4. Nếu hợp lệ → gọi `PUT /api/v1/skills/{id}` (multipart/form-data), body thêm `updatedAt` (optimistic lock)
5. Nếu thành công:
   - Đóng Edit popup
   - Emit event `skill:updated` → Detail popup lắng nghe và gọi lại `GET /api/v1/skills/{id}` để refresh
   - Refresh grid Skill List
   - Toast "Changes saved successfully" (ERR-EDT-005)
6. Nếu thất bại → toast "Unable to save changes. Please try again." (ERR-EDT-006), giữ nguyên form
7. Nếu 409 Conflict → toast "Skill has been changed by another user. Your changes were not saved." (ERR-EDT-007), đóng popup

**Dữ liệu gửi đi:**
```json
{
  "name": "<tên skill>",
  "description": "<mô tả>",
  "publicScope": "<PRIVATE|PART|GROUP|PUBLIC>",
  "categoryIds": [1, 2],
  "stageIds": [3],
  "hashtags": ["<tag1>", "<tag2>"],
  "updatedAt": "<ISO timestamp — optimistic lock>"
}
```
+ File mới (nếu có, chỉ DRAFT/REJECTED) trong `multipart/form-data`

**Dữ liệu cập nhật DB:**
| Trường | Giá trị |
|--------|---------|
| `name` | Từ form (không đổi khi APPROVED + non-admin) |
| `description` | Từ form |
| `public_scope` | Từ form (creator và admin đều có thể đổi) |
| `skill_category_mapping` | Thay toàn bộ mapping bằng `categoryIds` từ form |
| `skill_stage_mapping` | Thay toàn bộ mapping bằng `stageIds` từ form |
| `hashtags` | Từ form hoặc giữ nguyên |
| `skill_files` | DRAFT/REJECTED: thêm file mới; APPROVED: **bỏ qua** (không đổi) |
| `version` | **KHÔNG đổi** (chỉ bump khi version update được approve) |
| `updated_at` | Current timestamp |

**Luồng ngoại lệ:**
- API trả về `SKILL_NAME_DUPLICATE` → highlight trường tên, toast "Skill name already exists"
- API trả về `SKILL_FIELD_READ_ONLY` → highlight trường tương ứng, toast lỗi
- API trả về 500 → toast "System error. Please try again later." (ERR-EDT-008)

---

### 3.4. FR-04: Xóa file skill

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-04 |
| **Tên** | Xóa file skill |
| **Ưu tiên** | Trung bình |
| **Trigger** | Click nút "Delete" trên file đã chọn |

**Mô tả:** Xóa file skill khỏi hệ thống. **Chỉ áp dụng cho skill DRAFT/REJECTED** — skill APPROVED bị chặn (`403 SKILL_FILE_LOCKED`: "Files can only be managed via Update Version for approved skills.").

**Luồng chính:**
1. User click "Delete" trên file đã hiển thị (chỉ hiện khi DRAFT/REJECTED)
2. Frontend gọi `DELETE /api/v1/skills/{id}/files/{fileId}`
3. Nếu thành công:
   - Xóa file khỏi form
   - Toast "Skill file deleted successfully" (ERR-EDT-009)
4. Nếu thất bại: toast "Unable to delete file" (ERR-EDT-010)

---

### 3.5. FR-05: Hủy bỏ

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-05 |
| **Tên** | Hủy bỏ chỉnh sửa skill |
| **Ưu tiên** | Cao |
| **Trigger** | Click "Cancel", click overlay, hoặc nhấn Esc |

**Mô tả:** Đóng popup và xác nhận hủy thay đổi.

**Hành vi:**
- SH-05-01: Frontend PHẢI so sánh form hiện tại với snapshot dữ liệu ban đầu
- SH-05-02: Nếu form **không thay đổi** → đóng popup ngay, không hiện confirm
- SH-05-03: Nếu form **có thay đổi** → hiển thị dialog xác nhận: "Are you sure you want to cancel? Your changes will not be saved." (ERR-EDT-011)
- SH-05-04: Nếu xác nhận → đóng popup, không lưu
- SH-05-05: Nếu hủy → giữ nguyên popup
- SH-05-06: Popup PHẢI đóng khi click vào vùng overlay (áp dụng quy tắc SH-05-02/03)
- SH-05-07: Popup PHẢI đóng khi nhấn phím Esc (áp dụng quy tắc SH-05-02/03)

> **Snapshot:** Frontend lưu snapshot (tên, mô tả, scope, hashtag, file) khi mở popup. So sánh từng trường để quyết định có hiện confirm hay không.

---

## 4. Yêu cầu phi chức năng

### 4.1. Hiệu năng

| ID | Yêu cầu |
|----|---------|
| NFR-PERF-01 | Popup PHẢI hiển thị trong vòng **300ms** sau khi click "Edit" |
| NFR-PERF-02 | API cập nhật skill PHẢI trả về trong **1s** (bao gồm upload file) |
| NFR-PERF-03 | File upload PHẢI hỗ trợ progress indicator khi file > 1MB |

### 4.2. Bảo mật

| ID | Yêu cầu |
|----|---------|
| NFR-SEC-01 | Tất cả API PHẢI yêu cầu JWT trong `Authorization` header |
| NFR-SEC-02 | File upload PHẢI được validate extension ở cả frontend và backend |
| NFR-SEC-03 | File upload PHẢI được kiểm tra size (≤ 10MB) ở cả frontend và backend |
| NFR-SEC-04 | Nội dung mô tả PHẢI được escape khi render (phòng XSS) |
| NFR-SEC-05 | Chỉ creator hoặc admin mới được edit skill |
| NFR-SEC-06 | APPROVED skill: tên PHẢI read-only cho non-admin; scope vẫn edit được |
| NFR-SEC-07 | Rate limiting: tối đa 10 chỉnh sửa/phút/user |

### 4.3. Khả dụng

| ID | Yêu cầu |
|----|---------|
| NFR-UX-01 | Popup PHẢI có animation mở (fade in + slide up) |
| NFR-UX-02 | Popup PHẢI responsive trên màn hình ≥ 768px |
| NFR-UX-03 | Toast thông báo PHẢI tự ẩn sau 3 giây |
| NFR-UX-04 | Trường bắt buộc PHẢI có dấu `*` màu đỏ |
| NFR-UX-05 | Error message PHẢI hiển thị dưới trường có lỗi |
| NFR-UX-06 | Dialog xác nhận hủy PHẢI hiển thị trước khi đóng popup |
| NFR-UX-07 | Khi nội dung vượt quá chiều cao popup, `.o-modal__body` PHẢI scroll nội bộ, footer luôn hiển thị trong popup |
| NFR-UX-08 | Khi file được chọn, upload zone PHẢI thu gọn thành 1 dòng compact ("🔄 Replace file") để tiết kiệm không gian, tránh scroll |

### 4.4. Tương thích

| ID | Yêu cầu |
|----|---------|
| NFR-COMP-01 | Hỗ trợ Chrome, Edge, Firefox phiên bản hiện tại và trước đó 1 phiên bản |
| NFR-COMP-02 | UI theo chuẩn Design System (tham chiếu folder `../design-system/`) |

---

## 5. API Specification

### 5.1. Cập nhật skill

```
PUT /api/v1/skills/{id}
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
| `updatedAt` | String | Có | Form field trong multipart (không phải JSON). Giá trị `updatedAt` hiện tại (optimistic lock) |

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `name` rỗng | SKILL_NAME_REQUIRED | "Skill name is required." |
| `name` > 100 ký tự | SKILL_NAME_TOO_LONG | "Skill name is at most 100 characters." |
| `name` đổi khi APPROVED + non-admin | SKILL_FIELD_READ_ONLY | "The skill name cannot be changed for a published skill." |
| `name` trùng **trong cùng creator** (case-insensitive, khác skill hiện tại, soft-delete excluded) | SKILL_NAME_DUPLICATE | "You already have a skill with this name." |
| `publicScope` không hợp lệ | SKILL_INVALID_SCOPE | "Invalid public scope." |
| `description` > 2000 ký tự | SKILL_DESC_TOO_LONG | "Description is at most 2000 characters." |
| `hashtags` > 10 tag | SKILL_HASHTAGS_TOO_MANY | "Maximum 10 hashtags" |
| `hashtags` tag > 30 ký tự | SKILL_HASHTAG_TOO_LONG | "Hashtag is at most 30 characters" |
| `hashtags` ký tự không hợp lệ | SKILL_INVALID_HASHTAG | "Hashtag contains only letters, digits, '-', '_'" |
| File sai extension | SKILL_FILE_INVALID_TYPE | "File format is not supported" |
| File > 10MB | SKILL_FILE_TOO_LARGE | "File size is at most 10MB" |
| Skill PENDING_APPROVAL | SKILL_NOT_EDITABLE | "A skill pending approval cannot be edited." |
| Non-creator, non-admin | SKILL_NOT_EDITABLE | "You do not have permission to edit this skill." |
| Optimistic lock conflict (`updatedAt` khác) | SKILL_OPTIMISTIC_LOCK | "Skill has been changed by another user." |

**Response (200 OK):**
```json
{ "success": true, "data": { "version": 2 } }
```

> `version`: Số phiên bản của skill, tự động tăng mỗi lần lưu thành công.
> Optimistic lock dùng `updatedAt` (form field multipart) — nếu `updatedAt` gửi lên khác `updated_at` trong DB → 409.
> Khi skill đang `APPROVED` được save thành công (version +1) → backend gửi notification cho toàn bộ subscriber theo `user_settings` (in-app/email).

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

### 5.2. Xóa file skill

```
DELETE /api/v1/skills/{id}/files/{fileId}
Authorization: Bearer <JWT>
```

**Response (204 No Content):** *(empty body — xóa thành công)*

**Response (403 Forbidden):**
```json
{ "success": false, "errorCode": "SKILL_NOT_EDITABLE", "message": "You do not have permission to edit this skill." }
```

**Response (404 Not Found):**
```json
{ "success": false, "errorCode": "SKILL_FILE_NOT_FOUND", "message": "Skill file does not exist." }
```

---

## 6. Database Schema

### 6.1. Bảng `skills_master` + `skill_files` (cập nhật)

```sql
-- Khi PUT /api/v1/skills/{id}:
UPDATE skills_master SET
    name              = :name,
    description       = :description,
    public_scope      = :publicScope,
    hashtags          = :hashtags,
    version           = version + 1,
    updated_at        = CURRENT_TIMESTAMP
WHERE id = :id;

-- File mới upload → insert vào skill_files (1 skill : N file)
INSERT INTO skill_files (skill_id, file_name, file_path, file_size, created_at)
VALUES (:skillId, :fileName, :filePath, :fileSize, CURRENT_TIMESTAMP);

-- Khi DELETE /api/v1/skills/{id}/files/{fileId}:
DELETE FROM skill_files WHERE id = :fileId AND skill_id = :id;
```

---

## 7. Xử lý lỗi & Edge Case

| Tình huống | Hành vi |
|------------|---------|
| Tên skill trùng với skill khác | Toast "Skill name already exists", highlight trường tên |
| Tên skill giữ nguyên | Không check duplicate |
| File quá lớn (> 10MB) | Toast "File size is at most 10MB" |
| File sai định dạng | Toast "File format is not supported" |
| Mất kết nối mạng | Toast "Unable to save changes. Please try again." (ERR-EDT-006) |
| Skill bị xóa khi popup đang mở | Popup đóng, toast "Skill has been deleted" |
| Skill chuyển trạng thái khi đang edit | Popup đóng, toast "Skill has been changed by another user" |
| Upload file mới thay thế file cũ | File cũ bị xóa khỏi storage |
| Edit APPROVED nhưng đổi tên (non-admin) | Backend trả về SKILL_FIELD_READ_ONLY, frontend highlight trường |
| Người khác edit cùng skill | Optimistic lock: so sánh `updatedAt`, trả 409 nếu conflict |
| User chưa đăng nhập | Redirect về trang login |
| API trả về lỗi server (500) | Toast "System error. Please try again later." (ERR-EDT-008) |

---

## 8. Checklist Kiểm thử (QA)

| ID | Mô tả | Loại | Kết quả |
|----|-------|------|---------|
| TC-01 | Mở popup từ Detail (DRAFT) | Functional | ☐ |
| TC-02 | Mở popup từ Detail (REJECTED) | Functional | ☐ |
| TC-03 | Mở popup từ Detail (APPROVED) | Functional | ☐ |
| TC-04 | Không thể mở popup (PENDING_APPROVAL) | Functional | ☐ |
| TC-05 | Không thể mở popup (đã xóa) | Functional | ☐ |
| TC-06 | Form điền đúng dữ liệu hiện tại | Functional | ☐ |
| TC-07 | Tên read-only khi APPROVED (non-admin); scope vẫn edit được | Functional | ☐ |
| TC-08 | Info banner hiển thị khi APPROVED (non-admin) | UI | ☐ |
| TC-09 | Admin có thể edit tên và scope khi APPROVED | Security | ☐ |
| TC-10 | File cũ hiển thị nếu có | UI | ☐ |
| TC-11 | Lưu thay đổi thành công (DRAFT) | Functional | ☐ |
| TC-12 | Lưu thay đổi thành công (REJECTED) | Functional | ☐ |
| TC-13 | Lưu thay đổi thành công (APPROVED, chỉ mô tả) | Functional | ☐ |
| TC-14 | Lưu thay đổi thành công (APPROVED, admin edit tên/scope) | Functional | ☐ |
| TC-15 | Lưu thất bại (tên rỗng) | Functional | ☐ |
| TC-16 | Lưu thất bại (tên > 100 ký tự) | Functional | ☐ |
| TC-17 | Lưu thất bại (tên trùng) | Functional | ☐ |
| TC-18 | Không check duplicate khi tên giữ nguyên | Functional | ☐ |
| TC-19 | Mô tả > 2000 ký tự bị chặn | Functional | ☐ |
| TC-20 | Upload file mới thay thế file cũ | Functional | ☐ |
| TC-21 | Upload file .zip thành công | Functional | ☐ |
| TC-22 | Upload file .exe bị reject | Functional | ☐ |
| TC-23 | Upload file > 10MB bị reject | Functional | ☐ |
| TC-24 | Xóa file skill thành công | Functional | ☐ |
| TC-25 | Drag & drop file thành công | UI | ☐ |
| TC-26 | Dialog xác nhận khi hủy | UI | ☐ |
| TC-27 | Đóng popup bằng nút "Cancel" | Functional | ☐ |
| TC-28 | Đóng popup bằng click overlay | Functional | ☐ |
| TC-29 | Đóng popup bằng phím Esc | Functional | ☐ |
| TC-30 | Detail popup refresh sau khi lưu | Functional | ☐ |
| TC-31 | Grid refresh sau khi lưu | Functional | ☐ |
| TC-32 | Non-creator không thể edit skill | Security | ☐ |
| TC-33 | Admin có thể edit APPROVED tên/scope | Security | ☐ |
| TC-34 | Optimistic lock conflict (409) | Functional | ☐ |
| TC-35 | XSS trong mô tả bị escape | Security | ☐ |
| TC-36 | Non-creator click "Edit" → toast không có quyền | Security | ☐ |
| TC-37 | Scope hint text khác nhau khi APPROVED vs DRAFT | UI | ☐ |
| TC-38 | Tên skill counter "X/100" hiển thị, đỏ khi ≥ 90 | UI | ☐ |
| TC-39 | API PUT trả về isCreator/isAdmin/canEdit | Functional | ☐ |
| TC-40 | Tên skill case-insensitive: "Auto Code Review" và "auto code review" bị trùng | Functional | ☐ |
| TC-41 | Optimistic lock: updatedAt giữ nguyên string gốc, không reformat | Functional | ☐ |
| TC-42 | Thêm hashtag thành công (Enter) | Functional | ☐ |
| TC-43 | Hashtag max 10 tag — toast "Maximum 10 hashtags" | Functional | ☐ |
| TC-44 | Hashtag trùng — toast "Hashtag already exists" | Functional | ☐ |
| TC-45 | Hashtag ký tự không hợp lệ — toast lỗi | Functional | ☐ |
| TC-46 | Hashtag > 30 ký tự — toast "Hashtag is at most 30 characters" | Functional | ☐ |
| TC-47 | Xóa hashtag bằng click × | UI | ☐ |
| TC-48 | Hashtag không phân biệt hoa/thường — "Java" và "java" coi là trùng | Functional | ☐ |
