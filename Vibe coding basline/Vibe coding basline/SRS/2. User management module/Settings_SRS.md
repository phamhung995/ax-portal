# SRS — Skill AI User Settings

## Software Requirements Specification

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | User Settings |
| **Dự án** | AISTEA — Hệ thống Quản lý - Đo lường & Đánh giá Hiệu năng AI-SR |
| **Phiên bản** | 1.1 |
| **Ngày tạo** | 17/09/2026 |
| **Tác giả** | BA Team |
| **Trạng thái** | Draft |

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu này mô tả các yêu cầu chức năng và phi chức năng của chức năng **User Settings** trong hệ thống AISTEA. Tài liệu phục vụ cho đội phát triển (frontend, backend, QA) làm cơ sở triển khai và kiểm thử.

### 1.2. Phạm vi

User Settings là màn hình cho phép người dùng quản lý cài đặt cá nhân, bao gồm:
- **Profile**: display name, avatar, email ( department read-only)
- **Preferences**: ngôn ngữ hệ thống, múi giờ, định dạng ngày
- **Notifications**: nhận thông báo qua email / in-app
- **Change Password**: đổi mật khẩu

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
| User Settings BA Spec | `SRS/Settings_Spec.md` |
| Skill Management BA Spec | `SRS/Skill_Management_Screen_Spec.md` |
| Design System Settings Pattern | `design-system/patterns/pattern-library.md` — Pattern 8 (Settings) |

---

## 2. Mô tả tổng thể

### 2.1. Người dùng

| Vai trò | Mô tả |
|---------|-------|
| **User (thông thường)** | Quản lý profile, preferences, notifications, đổi mật khẩu của chính mình |
| **Admin** | Toàn bộ quyền của User (không có quyền settings cho user khác trong phạm vi này) |

### 2.2. Giả định

- Người dùng đã đăng nhập thành công và có JWT hợp lệ
- Bảng `"user"` đã có cột `password` (BCrypt)
- Hệ thống có kết nối mạng ổn định

### 2.3. Layout

Theo Design System **Settings pattern (Pattern 8)**:
```
[Settings Navigation (secondary left nav)] → [Settings Section (Form)] → [Footer (Save | Cancel)]
```

4 section: **Profile**, **Preferences**, **Notifications**, **Change Password**. Mỗi section có Save/Cancel riêng.

---

## 3. Yêu cầu chức năng

### 3.1. FR-01: Mở màn Settings

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-01 |
| **Tên** | Mở màn Settings |
| **Ưu tiên** | Cao |
| **Trigger** | Click avatar user trên Header → "My Settings" |

**Mô tả:** Hệ thống mở màn Settings và tải dữ liệu cài đặt hiện tại của user.

**Hành vi:**
- SH-01-01: Màn Settings PHẢI hiển thị với 4 section (Profile, Preferences, Notifications, Change Password)
- SH-01-02: Section mặc định active PHẢI là "Profile"
- SH-01-03: Hệ thống PHẢI gọi `GET /api/v1/users/me` để lấy dữ liệu hiện tại
- SH-01-04: Form PHẢI được điền bằng dữ liệu hiện tại
- SH-01-05: Nếu tải thất bại → toast "Unable to load settings"

---

### 3.2. FR-02: Profile

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-02 |
| **Tên** | Quản lý Profile |
| **Ưu tiên** | Cao |
| **Trigger** | User ở section Profile |

**Mô tả:** Người dùng cập nhật display name và avatar. Email hiển thị read-only (immutable — quyết định Login SRS v1.6). Organization hiển thị read-only.

**Yêu cầu:**

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Avatar | File upload (image) | Không | Max 2MB, `.jpg/.png/.webp` | "Change photo" + "Remove" |
| Display name | Text input | Có | Không rỗng, max 50 ký tự | Counter "X/50", đỏ khi ≥ 45 |
| Email | Text input (read-only) | — | — | Hiển thị email user, **không thể sửa** (immutable) |
| Organization | Text (read-only) | — | — | Danh sách Part > Group (từ `organization_master`, `EMPLOYED='Y'` qua `knox_id`) |

**Yêu cầu chi tiết:**
- SH-02-01: Display name PHẢI không rỗng và không chỉ chứa khoảng trắng
- SH-02-02: Display name PHẢI ≤ 50 ký tự
- SH-02-03: Counter display name PHẢI cập nhật real-time, màu đỏ khi ≥ 45 ký tự
- SH-02-04: Avatar sai extension PHẢI bị reject với thông báo lỗi
- SH-02-05: Avatar > 2MB PHẢI bị reject với thông báo lỗi
- SH-02-06: Xóa avatar → `avatar_url = NULL`, hiển thị placeholder (initials)
- SH-02-07: Email PHẢI read-only (immutable — không nằm trong payload profile); Organization PHẢI read-only
- SH-02-08: Lưu thành công → toast "Settings saved successfully"

---

### 3.3. FR-03: Preferences

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-03 |
| **Tên** | Cài đặt Preferences |
| **Ưu tiên** | Cao |
| **Trigger** | User ở section Preferences |

**Mô tả:** Người dùng cài đặt ngôn ngữ, múi giờ, định dạng ngày.

**Yêu cầu:**

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| System language | Select | Có | Enum: `en`, `vi` | Mặc định `en` |
| Timezone | Select | Có | IANA timezone hợp lệ | Mặc định `Asia/Ho_Chi_Minh` |
| Date format | Select | Có | Enum: `DD/MM/YYYY`, `MM/DD/YYYY`, `YYYY-MM-DD` | Mặc định `DD/MM/YYYY` |

**Yêu cầu chi tiết:**
- SH-03-01: Language PHẢI thuộc enum {`en`, `vi`}
- SH-03-02: Timezone PHẢI là IANA timezone hợp lệ
- SH-03-03: Date format PHẢI thuộc enum {`DD/MM/YYYY`, `MM/DD/YYYY`, `YYYY-MM-DD`}
- SH-03-04: Sau khi Save, language & date format PHẢI được áp dụng cho toàn hệ thống
- SH-03-05: Hiển thị info note: "Language & date format apply to the entire system after saving."

---

### 3.4. FR-04: Notifications

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-04 |
| **Tên** | Cài đặt Notifications |
| **Ưu tiên** | Trung bình |
| **Trigger** | User ở section Notifications |

**Mô tả:** Người dùng bật/tắt nhận thông báo qua email và in-app.

**Yêu cầu:**

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Email notifications | Switch | — | Boolean | Mặc định ON. Gửi email cho: (1) creator khi skill của họ được **approved/rejected**; (2) subscriber khi skill đã subscribe có **phiên bản mới** |
| In-app notifications | Switch | — | Boolean | Mặc định ON. Tạo notification in-app cho 2 sự kiện trên (bell icon) |

**Yêu cầu chi tiết:**
- SH-04-01: Email notifications PHẢI là switch toggle
- SH-04-02: In-app notifications PHẢI là switch toggle
- SH-04-03: Khi bật email/in-app → user nhận thông báo cho 2 sự kiện: (a) **creator**: skill được approve ("has been approved and is now published") hoặc reject (kèm lý do); (b) **subscriber**: skill đã subscribe có phiên bản mới. Không gửi cho new comment / new rating
- SH-04-04: Lưu thành công → toast "Settings saved successfully"

---

### 3.5. FR-05: Change Password

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-05 |
| **Tên** | Đổi mật khẩu |
| **Ưu tiên** | Cao |
| **Trigger** | User ở section Change Password, click "Save" |

**Mô tả:** Người dùng đổi mật khẩu bằng cách xác thực mật khẩu hiện tại.

**Yêu cầu:**

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Current password | Password input | Có | Phải khớp mật khẩu hiện tại | Nút 👁 show/hide |
| New password | Password input | Có | Min 8 ký tự , hoa + thường, ≥1 số, ( ký tự đặc biệt), ≠ current | Strength meter + rules. Nút 👁 |
| Confirm new password | Password input | Có | Phải khớp New password | Nút 👁 |

**Password strength rules:**
| Rule | Bắt buộc |
|------|----------|
| Min 8 ký tự | Có |
| Có chữ hoa & chữ thường | Có |
| Ít nhất 1 chữ số | Có |
| Ít nhất 1 ký tự đặc biệt | Tuỳ chọn |

**Yêu cầu chi tiết:**
- SH-05-01: Current password PHẢI khớp mật khẩu hiện tại, nếu sai → `PASSWORD_CURRENT_WRONG`
- SH-05-02: New password PHẢI ≥ 8 ký tự
- SH-05-03: New password PHẢI có chữ hoa, chữ thường và ít nhất 1 số
- SH-05-04: New password PHẢI khác current password
- SH-05-05: Confirm password PHẢI khớp New password
- SH-05-06: Strength meter PHẢI cập nhật real-time (Weak/Medium/Strong)
- SH-05-07: Rules checklist PHẢI cập nhật real-time (✓/○)
- SH-05-08: Mỗi password input PHẢI có nút show/hide (👁)
- SH-05-09: Đổi thành công → toast "Password updated successfully", xóa form
- SH-05-10: Password PHẢI được hash (BCrypt) ở backend, không lưu plain

---

### 3.6. FR-06: Dirty-guard khi đổi section

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-06 |
| **Tên** | Dirty-guard khi đổi section |
| **Ưu tiên** | Trung bình |
| **Trigger** | User click section khác khi form hiện tại có thay đổi chưa lưu |

**Hành vi:**
- SH-06-01: Nếu form section hiện tại **không thay đổi** → đổi section ngay
- SH-06-02: Nếu form **có thay đổi** → dialog xác nhận: "You have unsaved changes. Continue and discard changes?"
- SH-06-03: Xác nhận → bỏ thay đổi, đổi section
- SH-06-04: Hủy → giữ nguyên section hiện tại

---

## 4. Yêu cầu phi chức năng

### 4.1. Hiệu năng

| ID | Yêu cầu |
|----|---------|
| NFR-PERF-01 | Màn Settings PHẢI hiển thị trong vòng **500ms** sau khi click |
| NFR-PERF-02 | API lưu settings PHẢI trả về trong **1s** |
| NFR-PERF-03 | Avatar upload PHẢI hỗ trợ progress indicator khi file > 500KB |

### 4.2. Bảo mật

| ID | Yêu cầu |
|----|---------|
| NFR-SEC-01 | Tất cả API PHẢI yêu cầu JWT trong `Authorization` header |
| NFR-SEC-02 | Password PHẢI được hash (BCrypt), không log, không trả về trong response |
| NFR-SEC-03 | Avatar upload PHẢI được validate extension + size ở cả frontend và backend |
| NFR-SEC-04 | Input PHẢI được validate ở cả frontend và backend |
| NFR-SEC-05 | Rate limiting đổi password: tối đa 5 lần/15 phút/user |
| NFR-SEC-06 | `displayName` PHẢI được escape khi render (phòng XSS) |

### 4.3. Khả dụng

| ID | Yêu cầu |
|----|---------|
| NFR-UX-01 | Chuyển section PHẢI có animation (fade) |
| NFR-UX-02 | Màn Settings PHẢI responsive trên màn hình ≥ 768px |
| NFR-UX-03 | Toast thông báo PHẢI tự ẩn sau 3 giây |
| NFR-UX-04 | Trường bắt buộc PHẢI có dấu `*` màu đỏ |
| NFR-UX-05 | Error message PHẢI hiển thị dưới trường có lỗi |
| NFR-UX-06 | Section có thay đổi chưa lưu mà đổi section → dialog xác nhận (dirty-guard) |
| NFR-UX-07 | Password input PHẢI có nút show/hide (👁) |
| NFR-UX-08 | New password PHẢI có strength meter + rules checklist real-time |

### 4.4. Tương thích

| ID | Yêu cầu |
|----|---------|
| NFR-COMP-01 | Hỗ trợ Chrome, Edge, Firefox phiên bản hiện tại và trước đó 1 phiên bản |
| NFR-COMP-02 | UI theo chuẩn Design System (tham chiếu folder `../design-system/`) |

---

## 5. API Specification

### 5.1. Lấy profile + settings

```
GET /api/v1/users/me
Authorization: Bearer <JWT>
```

**Response 200** (envelope chuẩn `{ success, data }`):
```json
{
  "success": true,
  "data": {
    "id": 12,
    "displayName": "Nguyễn Văn A",
    "email": "nva@aix.com",
    "avatarPath": "/avatars/avatar_12_1726500000000.png",
    "orgs": [ { "part": "Part X", "group": "Group 1" } ],
    "settings": {
      "language": "en",
      "timezone": "Asia/Ho_Chi_Minh",
      "dateFormat": "DD/MM/YYYY",
      "emailNotify": true,
      "inappNotify": true
    }
  }
}
```

> `id` là **BIGINT** (không phải UUID). `orgs` = danh sách Part/Group từ `organization_master` (`EMPLOYED='Y'`, liên kết qua `knox_id`).

### 5.2. Cập nhật Profile

```
PUT /api/v1/users/me/profile
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

**Request (multipart):**
| Field | Type | Required | Ghi chú |
|-------|------|----------|---------|
| `profile` | JSON part | Có | `{ "displayName": "..." }` — max 50 ký tự |
| `avatar` | File | Không | Max 2MB, `.jpg/.png/.webp` |
| `removeAvatar` | Boolean | Không | `true` để xóa avatar (mặc định `false`) |

> Email **không nằm trong payload** — email là immutable (quyết định Login SRS v1.6).

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `displayName` rỗng | NAME_REQUIRED | "Display name cannot be empty" |
| `displayName` > 50 | NAME_TOO_LONG | "Display name is at most 50 characters" |
| Avatar > 2MB | AVATAR_TOO_LARGE | "Image size is at most 2MB" |
| Avatar sai type | AVATAR_INVALID_TYPE | "Image format is not supported" |

**Response 200:** trả về toàn bộ `MeResponse` (cùng cấu trúc §5.1).

### 5.3. Cập nhật Preferences

```
PUT /api/v1/users/me/preferences
Authorization: Bearer <JWT>
Content-Type: application/json

{ "language": "vi", "timezone": "Asia/Ho_Chi_Minh", "dateFormat": "DD/MM/YYYY" }
```

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `language` không hợp lệ | INVALID_LANGUAGE | "Invalid language" |
| `timezone` không hợp lệ | INVALID_TIMEZONE | "Invalid timezone" |
| `dateFormat` không hợp lệ | INVALID_DATE_FORMAT | "Invalid date format" |

**Response 200:** trả về toàn bộ `MeResponse` (cùng cấu trúc §5.1).

### 5.4. Cập nhật Notifications

```
PUT /api/v1/users/me/notifications
Authorization: Bearer <JWT>
Content-Type: application/json

{ "emailNotify": true, "inappNotify": false }
```

**Response 200:** trả về toàn bộ `MeResponse` (cùng cấu trúc §5.1).

### 5.5. Đổi mật khẩu

```
PUT /api/v1/users/me/password
Authorization: Bearer <JWT>
Content-Type: application/json

{ "currentPassword": "...", "newPassword": "...", "confirmPassword": "..." }
```

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| Rate limit (5 lần/15 phút) | TOO_MANY_ATTEMPTS | "Too many attempts. Please try again later." |
| Current sai | PASSWORD_CURRENT_WRONG | "Current password is incorrect" |
| New < 8 ký tự | PASSWORD_TOO_SHORT | "Password must be at least 8 characters" |
| New thiếu hoa/thường/số | PASSWORD_WEAK | "Password must contain uppercase, lowercase and number" |
| New = current | PASSWORD_SAME_AS_CURRENT | "New password must be different from current password" |
| Confirm ≠ New | PASSWORD_MISMATCH | "Password confirmation mismatch" |

**Response 200:**
```json
{ "success": true, "data": null }
```

**Response 400:**
```json
{ "success": false, "errorCode": "PASSWORD_CURRENT_WRONG", "message": "Current password is incorrect" }
```

---

## 6. Database Schema

### 6.1. Bảng `user_settings`

```sql
CREATE TABLE user_settings (
    user_id        BIGINT PRIMARY KEY REFERENCES "user"(id),
    language       VARCHAR(10)  NOT NULL DEFAULT 'en',
    timezone       VARCHAR(64)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    date_format    VARCHAR(10)  NOT NULL DEFAULT 'DD/MM/YYYY',
    email_notify   BOOLEAN      NOT NULL DEFAULT TRUE,
    inapp_notify   BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

> Tạo row mặc định khi user lần đầu đăng nhập (hoặc lazy-init khi đọc).

### 6.2. Cột liên quan trên bảng `"user"`

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `name` | VARCHAR(50) | Display name (cập nhật qua Profile) |
| `email` | VARCHAR(254) | Immutable (không sửa được qua Settings) |
| `avatar_url` | VARCHAR(500) | URL avatar (NULL → placeholder initials) |
| `knox_id` | VARCHAR | Local-part của email; liên kết tới `organization_master` |
| `password` | VARCHAR | Hash BCrypt (không trả về trong response) |

> Password lưu hash (BCrypt) trong cột `password` của `"user"`.

---

## 7. Xử lý lỗi & Edge Case

| Tình huống | Hành vi |
|------------|---------|
| Đổi password sai current | Toast "Current password is incorrect" (ERR-SET-010), giữ form |
| Đổi password thành công | Toast "Password updated successfully" (ERR-SET-015), xóa form, (tuỳ chọn) logout session khác |
| Upload avatar sai type/size | Toast lỗi (ERR-SET-008/009), giữ avatar cũ |
| Xóa avatar | `avatar_url = NULL`, hiển thị placeholder (initials) |
| Form dirty mà đổi section | Dialog xác nhận trước khi bỏ thay đổi |
| Mất kết nối mạng | Toast "Unable to save. Please try again." (ERR-SET-016) |
| User chưa đăng nhập | Redirect về trang login |
| API lỗi server (500) | Toast "System error. Please try again later." (ERR-SET-017) |
| Rate limit đổi password | Toast "Too many attempts. Please try again later." (ERR-SET-018) |

### 7.1. Error Message Catalog

All user-facing error messages are in **English** and identified by a stable ID.
The frontend maps `errorCode` → message ID.

| ID | Message | Error Code(s) | Where |
|---|---|---|---|
| ERR-SET-001 | "Unable to load settings" | (load failure) | Open Settings |
| ERR-SET-002 | "Display name cannot be empty" | `NAME_REQUIRED` | Profile |
| ERR-SET-003 | "Display name is at most 50 characters" | `NAME_TOO_LONG` | Profile |
| ERR-SET-004 | *(Removed v1.1)* — email is immutable, not editable in Profile | ~~`EMAIL_REQUIRED`~~ | — |
| ERR-SET-005 | *(Removed v1.1)* — email is immutable | ~~`INVALID_EMAIL`~~ | — |
| ERR-SET-006 | *(Removed v1.1)* — email is immutable | ~~`EMAIL_TOO_LONG`~~ | — |
| ERR-SET-007 | *(Removed v1.1)* — email is immutable | ~~`EMAIL_ALREADY_USED`~~ | — |
| ERR-SET-008 | "Image size is at most 2MB" | `AVATAR_TOO_LARGE` | Profile (avatar) |
| ERR-SET-009 | "Image format is not supported" | `AVATAR_INVALID_TYPE` | Profile (avatar) |
| ERR-SET-010 | "Current password is incorrect" | `PASSWORD_CURRENT_WRONG` | Change Password |
| ERR-SET-011 | "Password must be at least 8 characters" | `PASSWORD_TOO_SHORT` | Change Password |
| ERR-SET-012 | "Password must contain uppercase, lowercase and number" | `PASSWORD_WEAK` | Change Password |
| ERR-SET-013 | "New password must be different from current password" | `PASSWORD_SAME_AS_CURRENT` | Change Password |
| ERR-SET-014 | "Password confirmation mismatch" | `PASSWORD_MISMATCH` | Change Password |
| ERR-SET-015 | "Password updated successfully" | — (success) | Change Password |
| ERR-SET-016 | "Unable to save. Please try again." | (network failure) | All sections |
| ERR-SET-017 | "System error. Please try again later." | (HTTP 500) | All sections |
| ERR-SET-018 | "Too many attempts. Please try again later." | (rate limit) | Change Password |
| ERR-SET-019 | "Settings saved successfully" | — (success) | Profile / Preferences / Notifications |
| ERR-SET-020 | "Invalid language" | `INVALID_LANGUAGE` | Preferences |
| ERR-SET-021 | "Invalid timezone" | `INVALID_TIMEZONE` | Preferences |
| ERR-SET-022 | "Invalid date format" | `INVALID_DATE_FORMAT` | Preferences |
| ERR-SET-023 | "You have unsaved changes. Continue and discard changes?" | — (dialog) | Dirty-guard (section switch) |
| ERR-SET-024 | "Discard changes?" | — (dialog) | Cancel |

---

## 8. Checklist Kiểm thử (QA)

| ID | Mô tả | Loại | Kết quả |
|----|-------|------|---------|
| TC-01 | Mở màn Settings bằng avatar dropdown | Functional | ☐ |
| TC-02 | Section mặc định là Profile | Functional | ☐ |
| TC-03 | Form điền đúng dữ liệu hiện tại | Functional | ☐ |
| TC-04 | Đổi display name thành công | Functional | ☐ |
| TC-05 | Display name rỗng bị chặn | Functional | ☐ |
| TC-06 | Display name > 50 ký tự bị chặn | Functional | ☐ |
| TC-07 | Counter display name cập nhật real-time | UI | ☐ |
| TC-08 | Upload avatar .jpg thành công | Functional | ☐ |
| TC-9 | Upload avatar .png thành công | Functional | ☐ |
| TC-10 | Upload avatar .webp thành công | Functional | ☐ |
| TC-12 | Upload avatar .exe bị reject | Functional | ☐ |
| TC-12 | Upload avatar > 2MB bị reject | Functional | ☐ |
| TC-13 | Xóa avatar → placeholder | Functional | ☐ |
| TC-14 | Email read-only (immutable), không nằm trong payload profile & Organization read-only | Functional | ☐ |
| TC-15 | Đổi language thành công | Functional | ☐ |
| TC-16 | Language áp dụng toàn hệ thống sau Save | Functional | ☐ |
| TC-17 | Đổi timezone thành công | Functional | ☐ |
| TC-18 | Đổi date format thành công | Functional | ☐ |
| TC-19 | Date format áp dụng toàn hệ thống sau Save | Functional | ☐ |
| TC-20 | Toggle email notification thành công | Functional | ☐ |
| TC-21 | Toggle in-app notification thành công | Functional | ☐ |
| TC-22 | Đổi password thành công | Functional | ☐ |
| TC-23 | Password sai current bị chặn | Functional | ☐ |
| TC-24 | Password < 8 ký tự bị chặn | Functional | ☐ |
| TC-25 | Password thiếu hoa/thường/số bị chặn | Functional | ☐ |
| TC-26 | Password = current bị chặn | Functional | ☐ |
| TC-27 | Confirm không khớp bị chặn | Functional | ☐ |
| TC-28 | Strength meter cập nhật real-time | UI | ☐ |
| TC-29 | Rules checklist cập nhật real-time | UI | ☐ |
| TC-30 | Nút show/hide password hoạt động | UI | ☐ |
| TC-31 | Đổi section khi form clean → đổi ngay | Functional | ☐ |
| TC-32 | Đổi section khi form dirty → dialog confirm | UI | ☐ |
| TC-33 | Cancel reset form về giá trị ban đầu | Functional | ☐ |
| TC-34 | Password không trả về trong response | Security | ☐ |
| TC-35 | User chưa login không thể mở Settings | Security | ☐ |
| TC-36 | Rate limit đổi password | Security | ☐ |
| TC-37 | XSS trong displayName bị escape | Security | ☐ |
| TC-38 | Màn Settings hiển thị trong 500ms | Performance | ☐ |
| TC-39 | Responsive trên màn hình ≥ 768px | UI | ☐ |

---

## 9. Version History

| Version | Status | Change |
|---|---|---|
| 1.0 | Baseline | Tạo SRS User Settings |
| 1.1 | Updated | Aligned with implementation (per Login SRS v1.7): API path là `/api/v1/users/me/*` với envelope `{ success, data }`; `id` là **BIGINT** (không phải UUID); **Email immutable** — read-only, không nằm trong payload profile (bỏ SH-02-09/10/11, ERR-SET-004…007); `department` object thay bằng `orgs[]` (Part/Group từ `organization_master` qua `knox_id`); profile payload là JSON part `profile` (chỉ `displayName`) + `avatar` + `removeAvatar`; response của profile/preferences/notifications trả về toàn bộ `MeResponse`; đổi password có rate limit `TOO_MANY_ATTEMPTS` (5 lần/15 phút); bảng DB là `"user"` (cột `name`, `avatar_url`, `password`, `knox_id`) — không có bảng `users`/`display_name`/`avatar_path`/`password_hash` |
