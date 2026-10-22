# Skill AI — User Settings Spec

## 1. Tổng quan

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | User Settings — Cài đặt cá nhân |
| **Khởi động** | Click avatar user trên Header → "My Settings" |
| **Mục đích** | Cho phép người dùng quản lý thông tin cá nhân, ngôn ngữ, múi giờ, định dạng ngày, kênh thông báo và đổi mật khẩu |
| **Công nghệ** | Frontend: Vue 3 + Design System (tham chiếu folder `../design-system/`) · Backend: Java (Spring Boot) · Database: PostgreSQL |

---

## 2. Wireframe

### 2.1. Access — Avatar dropdown (Header)

```
Click [U] avatar
      │
      ▼
┌─────────────────────────┐
│  [U]  Nguyen Van A      │
│       nva@aix.com       │
├─────────────────────────┤
│  ⚙  My Settings         │  → opens Settings (Profile section)
│  ─────────────────────  │
│  ⏻  Logout              │
└─────────────────────────┘
```

### 2.2. Tổng quan màn Settings (default section = Profile)

```
┌──────────────────────────────────────────────────────────────────────┐
│  AIX   Home / Settings                              [📄] [🌙]  [U]   │ ← Header (existing)
├───────────────┬──────────────────────────────────────────────────────┤
│  Sidebar      │  My Settings                                         │
│  ┌──────────┐ │                                                      │
│  │Dashboard │ │   ┌───────────────┬────────────────────────────────┐ │
│  │Skill Mgmt│ │   │ Settings Nav  │  Profile                      │ │
│  │Users     │ │   │               │                                │ │
│  │Reports   │ │   │ ● Profile     │  Avatar                        │ │
│  └──────────┘ │   │ ○ Preferences │  ┌──────────────────────────┐  │ │
│               │   │ ○ Notifications│ │   [  📷  ]               │  │ │
│               │   │ ○ Change Password    │   Change photo   Remove      │  │ │
│               │   │               │  └──────────────────────────┘  │ │
│               │   │               │  Max 2MB · .jpg .png .webp     │ │
│               │   │               │                                │ │
│               │   │               │  Display name *                │ │
│               │   │               │  [Nguyen Van A________]        │ │
│               │   │               │  12/50                         │ │
│               │   │               │                                │ │
│               │   │               │  Email (read-only)             │ │
│               │   │               │  nva@aix.com                   │ │
│               │   │               │                                │ │
│               │   │               │  Department (read-only)        │ │
│               │   │               │  Part X > Group 1     │ │
│               │   │               │                                │ │
│               │   │               │                     [Cancel][Save]│ │
│               │   └───────────────┴────────────────────────────────┘ │
└───────────────┴──────────────────────────────────────────────────────┘
```

### 2.3. Section: Profile

```
┌───────────────┬──────────────────────────────────────────────────────┐
│ Settings Nav  │  Profile                                             │
│               │                                                      │
│ ● Profile     │  Avatar                                              │
│ ○ Preferences │  ┌──────────────────────────────────────────────┐    │
│ ○ Notifications│ │           [  📷  ]                            │    │
│ ○ Change Password    │  │        Change photo        Remove            │    │
│               │  └──────────────────────────────────────────────┘    │
│               │  Max 2MB. Formats: .jpg, .png, .webp                │
│               │                                                      │
│               │  Display name *                                      │
│               │  [Nguyen Van A________________]                      │
│               │  12/50                                               │
│               │                                                      │
│               │  Email  (read-only)                                  │
│               │  nva@aix.com                                         │
│               │                                                      │
│               │  Department (read-only)                              │
│               │  Part X > Group 1                           │
│               │                                                      │
│               │                                    [Cancel]  [Save]  │
└───────────────┴──────────────────────────────────────────────────────┘
```

### 2.4. Section: Preferences

```
┌───────────────┬──────────────────────────────────────────────────────┐
│ Settings Nav  │  Preferences                                         │
│               │                                                      │
│ ○ Profile     │  System language *                                   │
│ ● Preferences │  [English ▼]                                         │
│ ○ Notifications│  (English, Vietnamese)                             │
│ ○ Change Password    │                                                      │
│               │  Timezone *                                          │
│               │  [Asia/Ho Chi Minh (GMT+7) ▼]                        │
│               │                                                      │
│               │  Date format *                                       │
│               │  [DD/MM/YYYY ▼]                                      │
│               │  (DD/MM/YYYY, MM/DD/YYYY, YYYY-MM-DD)                │
│               │                                                      │
│               │  ℹ️ Language & date format apply system-wide        │
│               │     after Save.                                    │
│               │                                                      │
│               │                                    [Cancel]  [Save]  │
└───────────────┴──────────────────────────────────────────────────────┘
```

### 2.5. Section: Notifications

```
┌───────────────┬──────────────────────────────────────────────────────┐
│ Settings Nav  │  Notifications                                       │
│               │                                                      │
│ ○ Profile     │  Email notifications                                 │
│ ○ Preferences │  ┌──────────────────────────────────────────────┐    │
│ ● Notifications│ │ Receive email notifications            [  ]  │    │
│ ○ Change Password    │  │ When enabled, you'll receive an email        │    │
│               │  │ when a subscribed skill has a new            │    │
│               │  │ version (published).                         │    │
│               │  └──────────────────────────────────────────────┘    │
│               │                                                      │
│               │  In-app notifications                                │
│               │  ┌──────────────────────────────────────────────┐    │
│               │  │ Receive in-app notifications           [  ]  │    │
│               │  │ Show notifications inside the app.           │    │
│               │  └──────────────────────────────────────────────┘    │
│               │                                                      │
│               │                                    [Cancel]  [Save]  │
└───────────────┴──────────────────────────────────────────────────────┘
```

> In-app notification là toggle kênh bổ trợ cho Email (không phải chọn sự kiện granular). Nếu không cần → bỏ card In-app, chỉ giữ Email.

### 2.6. Section: Change Password

```
┌───────────────┬──────────────────────────────────────────────────────┐
│ Settings Nav  │  Change Password                                     │
│               │                                                      │
│ ○ Profile     │  Current password *                                  │
│ ○ Preferences │  [••••••••••••]  [👁]                                │
│ ○ Notifications│                                                     │
│ ● Change Password    │  New password *                                      │
│               │  [••••••••••••]  [👁]                                │
│               │  ▓▓▓▓▓▓▓▓░░  Strong                                  │
│               │  Rules:                                              │
│               │    ✓ Min 8 characters                                │
│               │    ✓ Uppercase & lowercase letter                    │
│               │    ✓ At least 1 number                               │
│               │    ○ At least 1 special character                    │
│               │                                                      │
│               │  Confirm new password *                              │
│               │  [••••••••••••]  [👁]                                │
│               │  ✓ Matches new password                              │
│               │                                                      │
│               │                                    [Cancel]  [Save]  │
└───────────────┴──────────────────────────────────────────────────────┘
```

- `[👁]` = nút show/hide password.
- Strength meter: Weak / Medium / Strong (dựa trên rules).
- "Save" ở section Change Password = "Update password".

---

## 3. Cấu trúc màn Settings

| Vùng | Nội dung |
|------|----------|
| **Header** | Breadcrumb "Home / Settings" + global actions (existing) |
| **Change Password Nav** | Secondary left nav: Profile, Preferences, Notifications, Change Password (Navigation Item, active state) |
| **Content** | Form của section đang chọn (Form Section + Form Field) |
| **Footer** | Nút Cancel + Nút Save (per-section) |

> Layout theo Design System **Settings pattern (Pattern 8)**: `[Settings Navigation] → [Settings Section (Form)] → [Footer (Save | Cancel)]`.
> Mỗi section có Save/Cancel riêng. Dirty-guard khi đổi section mà form chưa lưu.

---

## 4. Chi tiết từng section

### 4.1. Profile

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Avatar | File upload (image) | Không | Max 2MB, `.jpg/.png/.webp` | "Change photo" (upload) + "Remove" (xóa). Hiển thị avatar hiện tại |
| Display name | Text input | Có | Không rỗng, max 50 ký tự | Counter "X/50", đỏ khi ≥ 45 |
| Email | Text input (read-only) | — | — | Hiển thị email user, **không thể sửa** (immutable) |
| Department | Text (read-only) | — | — | Hiển thị "Part > Group" (từ `orgs[]`, liên kết qua `knox_id`) |

### 4.2. Preferences

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| System language | Select | Có | Enum: `en` (English), `vi` (Tiếng Việt) | Mặc định: `en`. Áp dụng toàn hệ thống sau Save |
| Timezone | Select | Có | IANA timezone hợp lệ | Mặc định: `Asia/Ho_Chi_Minh` (GMT+7) |
| Date format | Select | Có | Enum: `DD/MM/YYYY`, `MM/DD/YYYY`, `YYYY-MM-DD` | Mặc định: `DD/MM/YYYY` (theo SRS Skill Management) |

> Info note: "Language & date format apply to the entire system after saving."

### 4.3. Notifications

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Email notifications | Switch | — | Boolean | Mặc định: ON. Khi bật → nhận email cho 2 sự kiện: (1) creator khi skill của họ được **approved/rejected**; (2) subscriber khi skill đã subscribe có **phiên bản mới** (status APPROVED). Không gửi email cho new comment / new rating |
| In-app notifications | Switch | — | Boolean | Mặc định: ON. Hiển thị notification trong app |

> Hai toggle này cũng chi phối **thông báo cập nhật skill cho subscriber** (chức năng Subscribe — xem `Skill_Management_Screen_Spec.md` §8.9 và `Detail_Popup_Spec.md` §6.11): khi skill đã subscribe có phiên bản mới, hệ thống gửi in-app nếu `inapp_notify = ON` và/hoặc email nếu `email_notify = ON`.

### 4.4. Change Password

| Trường | Loại | Required | Validation | Ghi chú |
|--------|------|----------|------------|---------|
| Current password | Password input | Có | Phải khớp mật khẩu hiện tại | Nút 👁 show/hide |
| New password | Password input | Có | Min 8, hoa+thường, ≥1 số, (tuỳ chọn ≥1 ký tự đặc biệt), ≠ current | Strength meter + rules checklist. Nút 👁 show/hide |
| Confirm new password | Password input | Có | Phải khớp New password | Hiển thị "✓ Matches" / lỗi. Nút 👁 show/hide |

**Password strength rules:**
| Rule | Bắt buộc |
|------|----------|
| Min 8 ký tự | Có |
| Có chữ hoa & chữ thường | Có |
| Ít nhất 1 chữ số | Có |
| Ít nhất 1 ký tự đặc biệt | Tuỳ chọn (○) |

**Strength level:**
- **Weak**: chưa đủ 3/4 rule
- **Medium**: đủ 3/4 rule
- **Strong**: đủ 4/4 rule

---

## 5. Flow — Luồng tương tác

### 5.1. Mở màn Settings

```
[User] Click avatar → "My Settings"
    │
    ▼
[Frontend] Gọi GET /api/v1/users/me
    │
    ├── Thành công ──► Hiển thị màn Settings, active section "Profile"
    │                   Điền form bằng dữ liệu hiện tại
    │
    └── Thất bại ──► Toast "Unable to load settings"
```

### 5.2. Đổi section (dirty-guard)

```
[User] Click section khác trên Settings Nav
    │
    ▼
[Frontend] Form section hiện tại có thay đổi chưa lưu?
    │
    ├── Không ──► Đổi section ngay
    │
    └── Có ──► Dialog xác nhận: "You have unsaved changes.
                Continue and discard changes?"
                │
                ├── Continue ──► Bỏ thay đổi, đổi section
                └── Cancel ──► Giữ nguyên section hiện tại
```

### 5.3. Lưu section (Profile / Preferences / Notifications)

```
[User] Chỉnh form → Click "Save"
    │
    ▼
[Frontend] Validate form
    │
    ├── Không hợp lệ ──► Highlight trường lỗi, toast thông báo
    │
    └── Hợp lệ
          │
          ▼
    [Frontend] Gọi PUT /api/v1/users/me/{profile|preferences|notifications}
        │
        ├── Thành công ──► Toast "Settings saved successfully"
        │                  (Preferences: áp dụng language/date ngay)
        │
        └── Thất bại ──► Toast lỗi, giữ nguyên form
```

### 5.4. Đổi mật khẩu

```
[User] Nhập current + new + confirm → Click "Save"
    │
    ▼
[Frontend] Validate:
    │       ├── Current: không rỗng
    │       ├── New: min 8, hoa+thường, ≥1 số, (đặc biệt), ≠ current
    │       └── Confirm: khớp New
    │
    ├── Không hợp lệ ──► Highlight trường lỗi, toast
    │
    └── Hợp lệ
          │
          ▼
    [Frontend] Gọi PUT /api/v1/users/me/password
        │
        ├── Thành công ──► Toast "Password updated successfully"
        │                  Xóa form (3 trường password)
        │                  (Tuỳ chọn: logout các session khác)
        │
        ├── PASSWORD_CURRENT_WRONG ──► Toast "Current password is incorrect",
        │                              giữ form
        │
        └── Thất bại ──► Toast "Unable to update password. Please try again.", giữ form
```

### 5.5. Hủy (Cancel)

```
[User] Click "Cancel"
    │
    ▼
[Frontend] Form có thay đổi so với giá trị ban đầu?
    │
    ├── Không ──► Reset form về giá trị ban đầu
    │
    └── Có ──► Dialog xác nhận: "Discard changes?"
                │
                ├── Confirm ──► Reset form
                └── Cancel ──► Giữ nguyên form
```

---

## 6. API Endpoint

### 6.1. Lấy profile + settings

```
GET /api/v1/users/me
Authorization: Bearer <JWT>
```

**Response (200 OK):**
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

> `id` là **BIGINT**. `orgs` = danh sách Part/Group từ `organization_master` (`EMPLOYED='Y'` qua `knox_id`); UI hiển thị "Department" = `Part > Group`.

### 6.2. Cập nhật Profile

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

> Email **không nằm trong payload** — email là immutable.

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `displayName` rỗng | NAME_REQUIRED | "Display name cannot be empty" |
| `displayName` > 50 | NAME_TOO_LONG | "Display name is at most 50 characters" |
| Avatar > 2MB | AVATAR_TOO_LARGE | "Image size is at most 2MB" |
| Avatar sai type | AVATAR_INVALID_TYPE | "Image format is not supported" |

**Response (200 OK):** trả về toàn bộ `MeResponse` (cùng cấu trúc §6.1).

### 6.3. Cập nhật Preferences

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

**Response (200 OK):** trả về toàn bộ `MeResponse` (cùng cấu trúc §6.1).

### 6.4. Cập nhật Notifications

```
PUT /api/v1/users/me/notifications
Authorization: Bearer <JWT>
Content-Type: application/json

{ "emailNotify": true, "inappNotify": false }
```

**Response (200 OK):** trả về toàn bộ `MeResponse` (cùng cấu trúc §6.1).

### 6.5. Đổi mật khẩu

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

**Response (200 OK):**
```json
{ "success": true, "data": null }
```

**Response (400 Bad Request):**
```json
{ "success": false, "errorCode": "PASSWORD_CURRENT_WRONG", "message": "Current password is incorrect" }
```

---

## 7. CSDL Liên Quan

### 7.1. Bảng `user_settings` (1 row/user)

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

### 7.2. Cột liên quan trên bảng `"user"` (profile)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `name` | VARCHAR(50) | Display name (cập nhật qua Profile) |
| `email` | VARCHAR(254) | Immutable (không sửa được qua Settings) |
| `avatar_url` | VARCHAR(500) | URL avatar (NULL → placeholder initials) |
| `knox_id` | VARCHAR | Local-part của email; liên kết tới `organization_master` |
| `password` | VARCHAR | Hash BCrypt — không lưu plain, không trả về trong response |

---

## 8. Xử lý lỗi & Edge Case

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

> Error Message Catalog (ID `ERR-SET-xxx`) được định nghĩa trong `Settings_SRS.md` §7.1.

---

## 9. Security

| Yêu cầu | Giải pháp |
|----------|-----------|
| Xác thực | JWT trong `Authorization` header cho mọi request |
| Password | Hash BCrypt, không log, không trả về trong response |
| Input validation | Validate cả frontend và backend |
| Avatar upload | Kiểm tra extension + size ở cả frontend và backend |
| Rate limiting | Đổi password: tối đa 5 lần/15 phút/user |
| XSS protection | Escape `displayName` khi render |

---

## 10. Yêu cầu phi chức năng (UX)

| ID | Yêu cầu |
|----|---------|
| NFR-UX-01 | Chuyển section có animation (fade) |
| NFR-UX-02 | Màn Settings responsive trên màn hình ≥ 768px |
| NFR-UX-03 | Toast thông báo tự ẩn sau 3 giây |
| NFR-UX-04 | Trường bắt buộc có dấu `*` màu đỏ |
| NFR-UX-05 | Error message hiển thị dưới trường có lỗi |
| NFR-UX-06 | Section có thay đổi chưa lưu mà đổi section → dialog xác nhận (dirty-guard) |
| NFR-UX-07 | Password input có nút show/hide (👁) |
| NFR-UX-08 | New password có strength meter + rules checklist cập nhật real-time |
