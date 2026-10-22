# Sitemap — AI Skill Management (AISTEA)

| Mục | Nội dung |
|-----|----------|
| **Dự án** | AISTEA — AI Skill Management |
| **Loại** | Sitemap & User Flows (bản đồ màn hình + luồng sử dụng) |
| **Phiên bản** | 1.2 |
| **Ngày tạo** | 21/09/2026 |
| **Cập nhật** | 16/10/2026 — thêm VOC Module, Skill Update Version, Category/Stage filter |
| **Đối tượng** | PO / BA / UX/UI / Developer / QA / User |
| **Nguồn** | Tổng hợp từ các SRS trong `AIX-Design-System_ver5/AIX-Design-System/SRS/` |

---

## 1. Cấu Trúc Tổng Thể Hệ Thống

```text
AI SKILL MANAGEMENT
│
├─── KHU VỰC TRƯỚC ĐĂNG NHẬP (không có GNB)
│    ├── Login ────────────────┬──► Register
│    │                         └──► Forgot Password
│    │                                ├── Request OTP (nhập Email)
│    │                                ├── Verify OTP (6 số, 5 phút)
│    │                                └── Reset Password
│    └── Register
│
└─── KHU VỰC SAU ĐĂNG NHẬP (có GNB: Header + Sidebar)
     ├── Home (màn hình mặc định sau Login)
     ├── Skill Management (list + search + popup)
     ├── User Management        ← chỉ ADMIN_USER
     ├── Feedback (VOC)         ← chỉ ADMIN_USER
     └── Settings (qua avatar → My Settings)
```

### Danh sách màn hình

| # | Màn hình | Route | Quyền truy cập | SRS tham chiếu |
|---|----------|-------|----------------|----------------|
| 1 | Login | `/login` | Public | `Login_User Register before login_SRS.md` |
| 2 | Register | `/register` | Public | `Login_User Register before login_SRS.md` |
| 3 | Forgot Password (Request OTP) | `/forgot-password` | Public | `Login_User Register before login_SRS.md` |
| 4 | Verify OTP | `/forgot-password/verify` | Public | `Login_User Register before login_SRS.md` |
| 5 | Reset Password | `/forgot-password/reset` | Public (cần OTP verified) | `Login_User Register before login_SRS.md` |
| 6 | Home | `/home` | Đã đăng nhập | `Home_Screen_SRS.md` |
| 7 | Skill Management | `/skills` | Đã đăng nhập | `Skill_Management_Screen_Spec.md` |
| 8 | User Management | `/users` | ADMIN_USER | `User Management Screen_SRS.md` |
| 9 | Settings | `/settings` | Đã đăng nhập (qua avatar) | `Settings_SRS.md` |
| 10 | Feedback (VOC Management) | `/feedback` | ADMIN_USER | `3. VOC Module/VOC_Feedback_SRS.md` |

### Popup / Dialog (không phải màn hình độc lập)

| Popup / Dialog | Mở từ | SRS tham chiếu |
|----------------|-------|----------------|
| Skill Detail Popup | Skill Management (click tên / double-click row), Home, Notification | `Detail_Popup_SRS.md` |
| Add Skill Popup | Skill Management, Home (Quick Action) | `Add_Popup_SRS.md` |
| Edit Skill Popup | Detail Popup (nút "Sửa") | `Edit_Popup_SRS.md` |
| Search User Popup | Creator field (nút 🔍) trên Skill Management | `Skill_Management_Screen_Spec.md` |
| Confirm Delete Skill | Detail Popup, Bulk Delete | `Skill_Management_Screen_Spec.md` |
| Confirm Withdraw | Detail Popup | `Skill_Management_Screen_Spec.md` |
| Reject Reason Dialog | Detail Popup (Admin Reject) | `Skill_Management_Screen_Spec.md` |
| Confirm Delete Comment | Detail Popup (tab Bình luận) | `Skill_Management_Screen_Spec.md` |
| Confirm Cancel (Add/Edit) | Add / Edit Popup (form có dữ liệu) | `Add_Popup_SRS.md`, `Edit_Popup_SRS.md` |
| Confirm Remove File | Edit Popup | `Edit_Popup_SRS.md` |
| Create User | User Management (toolbar) | `User Management Screen_SRS.md` |
| Bulk Upload User | User Management (toolbar) | `User Management Screen_SRS.md` |
| User Detail | User Management (click row) | `User Management Screen_SRS.md` |
| Notification Panel | GNB (chuông 🔔) | `Home_Screen_SRS.md` |
| User Menu (dropdown) | GNB (avatar) | `Home_Screen_SRS.md` |
| VOC Popup (Add VOC \| My VOCs) | User Menu (item VOC, icon Inbox) | `3. VOC Module/VOC_Feedback_SRS.md` |
| My Feedback Popup | VOC Popup (click item), Notification bell (deep-link feedbackId) | `3. VOC Module/VOC_Feedback_SRS.md` |
| Feedback Detail Popup (admin) | Feedback Management (click Content) | `3. VOC Module/VOC_Feedback_SRS.md` |

---

## 2. Thanh Điều Hướng Toàn Cầu (GNB)

GNB hiển thị trên **mọi màn hình sau đăng nhập**, không build lại theo từng trang.

```text
┌──────────────────────────────────────────────────────────────┐
│ HEADER (sticky)                                              │
│ [☰] [AI SKILL MANAGEMENT]  [breadcrumb]        🔔   (avatar)│
├──────────┬───────────────────────────────────────────────────┤
│ SIDEBAR  │  MAIN CONTENT                                     │
│          │                                                   │
│ 🏠 Home  │                                                   │
│ 📚 Skill │                                                   │
│    Mgmt  │                                                   │
│ 👥 User  │  ← chỉ ADMIN_USER                                 │
│    Mgmt  │                                                   │
│ 💬 Feedback ← chỉ ADMIN_USER                                 │
└──────────┴───────────────────────────────────────────────────┘
```

### Luồng điều hướng GNB

```text
GNB
│
├── Sidebar
│    ├── Home               → /home
│    ├── Skill Management   → /skills
│    ├── User Management    → /users   (chỉ ADMIN_USER)
│    └── Feedback           → /feedback (chỉ ADMIN_USER)
│
├── Header
│    ├── Brand "AI SKILL MANAGEMENT"  → luôn về Home
│    ├── 🔔 Notification bell
│    │    ├── Badge số notification chưa đọc (tối đa "99+")
│    │    └── Click → Notification Panel (10 notification mới nhất)
│    │         ├── Click notification → đánh dấu đã đọc
│    │         │    └── (nếu có liên kết Skill) → mở Skill Detail Popup
│    │         └── [Mark all read] → xóa badge
│    └── (avatar) User Menu
│         ├── Thông tin user (tên, ID, part/group) — read-only
│         ├── My Settings  → /settings
│         └── Logout       → xóa session → /login
│
└── Quy tắc
     ├── Đúng 1 sidebar item active tại 1 thời điểm
     ├── Màn Settings: không có sidebar item active (vào qua avatar)
     ├── Chưa đăng nhập mở route nội bộ → redirect Login
     └── NORMAL_USER mở thẳng /users → 403 + Access Denied
```

### Responsive

| Thiết bị | Sidebar |
|----------|---------|
| Mobile (<900px) | Drawer off-canvas, mở bằng ☰ |
| Tablet (900–1199px) | Collapsible (toggle) |
| Desktop (≥1200px) | Cố định 240px |
| Wide (≥1536px) | Cố định 256px |

---

## 3. Các Luồng User

### 3.1. Luồng Đăng Nhập (Login)

```text
Màn Login
│
├── Nhập Email + Password → [LOG IN]
│    │
│    ├── ✅ Thành công (ACTIVE + có organization — `organization_master` có row `EMPLOYED='Y'` qua `knox_id`)
│    │         └──► HOME (màn hình mặc định)
│    │
│    ├── ❌ Sai password / Email không tồn tại
│    │         └──► "Invalid Email or Password." (ở lại Login)
│    │
│    ├── ❌ User DEACTIVE
│    │         └──► Login thất bại (ở lại Login)
│    │              └──► Khôi phục qua Forgot Password
│    │
│    └── ❌ Part và Group đều NULL
│             ├── Chưa từng có part/group → ORGANIZATION_NOT_SYNCED
│             └── Từng có part/group      → ORGANIZATION_UNAVAILABLE
│
├── [Register]           → màn Register
└── [Forgot Password?]   → màn Forgot Password
```

**Lưu ý bảo mật:** hệ thống không tiết lộ tài khoản có tồn tại hay không (chống enumeration).

---

### 3.2. Luồng Đăng Ký (Register)

```text
Màn Register
│
├── Step 1 — Nhập: User Name*, Email*, Password*, Confirm Password*
│    │
│    └── [SEND OTP] → gửi email OTP xác nhận (6 số, 5 phút)
│
├── Step 2 — Verify OTP
│    │
│    ├── ✅ OTP đúng + còn hạn
│    │    (Role = NORMAL_USER, Status = ACTIVE, Part = NULL, Group = NULL)
│    │         └──► màn Login
│    │              └──► Login thành công → HOME
│    │
│    └── ❌ Thất bại (ở lại màn Register, giữ dữ liệu)
│             ├── Email không hợp lệ
│             ├── Email đã tồn tại
│             ├── Password không đúng policy
│             └── Password ≠ Confirm Password
│
└── [Log In] (đã có tài khoản) → màn Login
```

**Lưu ý:** User ID là BIGINT do DB tự sinh — user không nhập. Email là Login ID và gắn với user trọn đời (immutable).

**Password policy:** ≥8 ký tự, có chữ hoa, chữ thường, ít nhất 1 số (ký tự đặc biệt **không bắt buộc**).

---

### 3.3. Luồng Quên Mật Khẩu (Forgot Password → OTP → Reset)

```text
Màn Forgot Password
│
├── Nhập Email → [SEND OTP]
│    │
│    ├── Hệ thống gửi email OTP (6 số, hợp lệ 5 phút)
│    │    (không tiết lộ email / OTP / tài khoản có tồn tại hay không)
│    │
│    └──► màn Verify OTP
│
├── màn Verify OTP
│    ├── Nhập 6 số + đếm ngược thời gian
│    ├── [VERIFY]
│    │    ├── ✅ OTP đúng + còn hạn → màn Reset Password
│    │    └── ❌ Sai / hết hạn → báo lỗi, có thể [Resend OTP]
│    │         (Resend: OTP cũ hết hiệu lực, chỉ OTP mới nhất hợp lệ)
│    │
│    └── [Back to Login]
│
└── màn Reset Password
     ├── Nhập New Password + Confirm (đúng policy)
     ├── [RESET PASSWORD]
     │    ├── ✅ Thành công
     │    │    ├── User ACTIVE   → giữ ACTIVE
     │    │    ├── User DEACTIVE → chuyển ACTIVE
     │    │    └──► thông báo thành công → màn Login
     │    └── ❌ Thất bại (policy, mismatch, OTP hết hạn)
     │         └──► ở lại màn Reset Password
     │
     └── [Back to Login]
```

**Quy tắc quan trọng:** Verify OTP **không** kích hoạt user DEACTIVE — chỉ Reset Password thành công mới kích hoạt.

---

### 3.4. Luồng Home (màn hình sau Login)

```text
HOME (landing sau Login thành công)
│
├── Greeting: "Hello, {User Name}" + Part + Group + Role
│
├── [Chỉ ADMIN] Banner "You have N skills awaiting review"
│    └── [Review Now] → Skill Management (filter Status = PENDING_APPROVAL)
│
├── KPI Cards (6 thẻ)
│    ├── My Skills        → click → Skill Management (Creator = mình)
│    ├── Approved        → click → Skill Management (Creator = mình + APPROVED)
│    ├── Pending Approval → click → Skill Management (Creator = mình + PENDING_APPROVAL)
│    ├── Total Views      → click → Skill Management (Creator = mình)
│    ├── Average Rating   → click → Skill Management (Creator = mình)
│    └── Subscribed       → chỉ hiển thị (không click)
│
├── My Recent Skills (5 skill mới nhất của mình, mọi status)
│    └── Click skill → Skill Detail Popup
│
└── Quick Actions
     ├── [+ Add Skill] → Add Skill Popup
     └── [Go to Skill Management] → /skills
```

---

### 3.5. Luồng Skill Management

```text
SKILL MANAGEMENT
│
├── Search / Filter Panel
│    ├── Skill Name (contains)
│    ├── Description (contains)
│    ├── Tag (multi, OR logic)
│    ├── Creator (text + 🔍 → Search User Popup)
│    ├── From/To Date
│    ├── Creation Status (multi, OR logic)
│    ├── Skill Scope (multi, OR logic)
│    ├── Category (multi, OR logic)
│    ├── Stage (single)
│    ├── [Search] → áp AND giữa các điều kiện
│    └── [Reset] → xóa toàn bộ điều kiện
│
├── Toolbar
│    ├── [+ Add Skill] → Add Skill Popup
│    ├── [Bulk Delete] → kiểm tra quyền từng skill
│    │    ├── Tất cả có quyền → Confirm → xóa toàn bộ
│    │    └── Có skill không có quyền → Alert (all-or-nothing, không xóa gì)
│    └── [Download Excel] → export (chọn row = export row đó; không chọn = toàn bộ)
│
├── Result Grid
│    ├── Checkbox (select all)
│    ├── Skill Name (click, delay 300ms → Detail Popup)
│    ├── Double-click row → Detail Popup (ưu tiên, hủy delay click đơn)
│    ├── Status (badge), Views, Rating (★ 4.8 (125) / No rating)
│    ├── Subscribe (chuông 🔔, chỉ APPROVED + không phải creator)
│    │    └── Click chuông → toggle subscribe/unsubscribe (không mở Detail)
│    └── Pagination
│
└── SKILL DETAIL POPUP
     ├── Header: tên, rating, status badge, scope, subscribe
     ├── Tab "Thông tin": mô tả
     ├── Tab "Versions" (v1.1): version hiện tại + snapshot version cũ,
     │    Download / Download All (zip) per version
     ├── Tab "Bình luận": đánh giá sao (1–5, step 0.5), comment list,
     │    form viết comment, pagination (chỉ khi APPROVED)
     ├── Lý do từ chối (chỉ khi REJECTED)
     ├── Pending Version section (v1.1, creator/admin): đề xuất thay đổi
     │    + new files (download) + files to be removed
     └── Footer — nút theo Role + Status:
          ├── DRAFT:            Sửa, Submit, Delete, Đóng
          ├── PENDING_APPROVAL: Rút lại (creator) / Approve, Reject (admin), Đóng
          ├── PENDING + version pending: Cancel Version Update (creator/admin),
          │                              Approve, Reject (admin), Đóng
          ├── APPROVED:        Sửa, Update Version (v1.1), Delete, Tải xuống, Đóng
          └── REJECTED:         Sửa, Submit, Delete, Đóng
```

#### Luồng thêm / sửa skill

```text
Add Skill Popup
├── Tên skill*, Phạm vi hiển thị* (default: Private), Category*, Process Stage*,
│    Mô tả (rich text), Hashtag, File
├── [Lưu] → tạo skill status = DRAFT → đóng popup → refresh grid
└── [Hủy] (form có dữ liệu) → Confirm Cancel

Edit Skill Popup (mở từ Detail, nút "Sửa")
├── DRAFT / REJECTED        → edit đầy đủ (kể cả file)
├── APPROVED + non-admin   → chỉ tên skill read-only (scope, mô tả, hashtag edit được;
│                             file KHÔNG quản lý qua Edit)
├── APPROVED + admin       → edit metadata đầy đủ (file vẫn KHÔNG qua Edit)
├── PENDING_APPROVAL        → không cho edit
├── [Lưu thay đổi] → success → refresh Detail + Grid
│    (APPROVED: không bump version, vẫn notify subscribers)
└── 409 Conflict (ai đó sửa trước) → toast → đóng popup

Update Version Popup (mở từ Detail, nút "Update Version" — chỉ skill APPROVED)
├── Sửa TOÀN BỘ: tên, scope, Category*, Process Stage*, mô tả, hashtag
├── File: upload file mới (BẮT BUỘC ≥1) + nút Delete file cũ (gạch ngang, Undo)
├── [Submit for Review] → skill PENDING_APPROVAL (version cũ vẫn live)
│    ├── Admin Approve → snapshot version cũ, version +1, APPROVED, notify
│    ├── Admin Reject  → về APPROVED, hiển thị lý do
│    └── Creator Cancel Version Update → về APPROVED
└── [Hủy] (form có dữ liệu) → Confirm Cancel
```

#### Luồng duyệt skill (workflow)

```text
Creator:
  DRAFT ──[Submit for Review]──► PENDING_APPROVAL
                                    │
  PENDING_APPROVAL ──[Rút lại]─────┤──► DRAFT
                                    │
Admin:                              ▼
  PENDING_APPROVAL ──[Approve]──► APPROVED
  PENDING_APPROVAL ──[Reject]───► REJECTED (nhập lý do, ≤500 ký tự)

REJECTED ──[Submit lại]──► PENDING_APPROVAL
```

#### Luồng Update Version (skill APPROVED, v1.1)

```text
Skill APPROVED (version N đang live)
  │
  ├─ Edit thường (metadata only, KHÔNG bump version, vẫn notify subscribers)
  │     └─ APPROVED: không quản lý file (SKILL_FILE_LOCKED)
  │
  └─ [Update Version] (creator/admin, bắt buộc ≥1 file mới)
        │  stage: pending_metadata + files pending_yn='Y' + removeFileIds
        │  skill → PENDING_APPROVAL (version N VẪN LIVE cho mọi user)
        │
        ├─ Admin APPROVE ──► snapshot version N vào skill_version
        │                    apply pending, version N+1, APPROVED, notify
        ├─ Admin REJECT ───► skill về APPROVED (version N giữ nguyên),
        │                    hiển thị rejectionReason
        └─ Creator CANCEL ─► skill về APPROVED, bỏ pending
```

```text
        ┌─────────────────────────────────────────────┐
        │            SKILL STATE MACHINE              │
        ├─────────────────────────────────────────────┤
        │                                             │
        │   DRAFT ──Submit──► PENDING_APPROVAL        │
        │     ▲                      │                │
        │     │                      │                │
        │     └────Withdraw──────────┘                │
        │                                             │
        │   REJECTED ◄──Reject── PENDING_APPROVAL     │
        │       │                          │          │
        │       └────Submit────────────────┘          │
        │                                             │
        │   PENDING_APPROVAL ──Approve──► APPROVED   │
        │                                             │
        │   APPROVED ──Update Version──► PENDING_     │
        │     ▲                    APPROVAL (pending) │
        │     │                      │                │
        │     │   Approve (vN+1)     │ Reject/Cancel  │
        │     └──────────────────────┘                │
        │                                             │
        │   Delete (soft) từ DRAFT/REJECTED/APPROVED │
        └─────────────────────────────────────────────┘
```

Quy tắc hiển thị theo Status + Scope:

| | DRAFT | PENDING_APPROVAL | APPROVED | REJECTED |
|---|---|---|---|---|
| **Creator** (mọi scope) | ✓ | ✓ | ✓ | ✓ |
| **Normal User khác** | ✕ | ✕ (✓ nếu đang version-update pending — hiển thị như Approved) | ✓ (theo scope) | ✕ |
| **Admin** | ✓ | ✓ | ✓ | ✓ |

> **Ngoại lệ version-update pending (v1.1):** skill APPROVED đang có version update pending
> (`PENDING_APPROVAL` + `pending_metadata`) vẫn hiển thị cho user khác như APPROVED
> (version cũ live — xem, download, rate, comment, subscribe đều hoạt động).

Scope: `Private` (chỉ creator) · `Part` (cùng Part) · `Group` (cùng Group) · `Public` (mọi người). Admin bypass scope.

---

### 3.6. Luồng User Management (chỉ ADMIN_USER)

```text
USER MANAGEMENT
│
├── Search Area
│    ├── User Name, Email (contains)
│    ├── Organization (searchable dropdown, single)
│    ├── Role (multi-select: NORMAL_USER / ADMIN_USER)
│    ├── Status (multi-select: ACTIVE / DEACTIVE)
│    ├── Created Date From ~ To
│    ├── [Search] (AND giữa các điều kiện, OR trong Role/Status)
│    └── [Reset]
│
├── Toolbar
│    ├── [Create User] → popup nhập user mới
│    ├── [Bulk Upload] → upload file nhiều user
│    └── [Export User List]
│
├── User Grid
│    ├── Checkbox, User Name, Email,
│    │   Organization, Role, Status
│    └── Click User Name → User Detail
│
└── USER DETAIL
     ├── Xem thông tin user (User ID BIGINT read-only, Email read-only)
     ├── Sửa: User Name, Role, Status
     ├── [Save] → validation (frontend + backend)
     │    ├── ✅ → lưu + tạo USER_HISTORY
     │    ├── ❌ Validation → báo lỗi, giữ form
     │    └── ⚠️ Dữ liệu bị thay đổi (concurrency) → thông báo
     └── [Back] → về grid
```

**Quy tắc status user:**

```text
ACTIVE ──(Admin deactivate)──► DEACTIVE
DEACTIVE ──(user tự Reset Password thành công)──► ACTIVE
```

- DEACTIVE: không Login được, nhưng dùng được Forgot Password.
- Admin có thể đổi Role (NORMAL_USER ↔ ADMIN_USER).

---

### 3.7. Luồng Settings (qua avatar → My Settings)

```text
SETTINGS (4 section, mỗi section có Save/Cancel riêng)
│
├── Profile
│    ├── Avatar (upload, max 2MB, .jpg/.png/.webp)
│    ├── Display name* (≤50 ký tự, counter)
│    └── Email, Department (read-only)
│
├── Preferences
│    ├── Ngôn ngữ hệ thống
│    ├── Múi giờ
│    └── Định dạng ngày
│
├── Notifications
│    ├── In-app notification (ON/OFF)
│    └── Email notification (ON/OFF)
│         (áp dụng cho thông báo skill subscribed có phiên bản mới)
│
└── Security
     └── Đổi mật khẩu (current + new + confirm, đúng policy)

Quy tắc:
├── Section mặc định = Profile
├── Dirty-guard: rời section khi form có thay đổi → xác nhận
├── [Save] → commit section đó
└── [Cancel] → revert về dữ liệu cũ
```

---

### 3.8. Luồng Notification (in-app)

```text
Sự kiện kích hoạt (ví dụ: skill đã subscribe có phiên bản mới)
│
├── User bật in-app (Settings > Notifications) → hiện notification in-app
├── User bật email → gửi email
└── Kênh tắt → không thông báo

GNB:
🔔 Chuông (badge unread count, max "99+")
└── Click → Panel 10 notification mới nhất
     ├── ● Chưa đọc / ○ Đã đọc
     ├── Click notification → đánh dấu đã đọc
     │    └── (nếu liên kết Skill) → mở Skill Detail Popup
     └── [Mark all read] → badge biến mất
```

---

## 4. Ma Trận Truy Cập Theo Role

| Màn hình / Chức năng | NORMAL_USER | ADMIN_USER |
|----------------------|:-----------:|:----------:|
| Login / Register / Forgot Password | ✓ | ✓ |
| Home | ✓ | ✓ + Review banner |
| Skill Management | ✓ (skill của mình mọi status + skill Approved của người khác theo scope) | ✓ (mọi skill, mọi status) |
| Add / Edit / Delete Skill | ✓ (skill của mình) | ✓ (mọi skill) |
| Approve / Reject Skill | ✕ | ✓ |
| Update Version (skill APPROVED) | ✓ (skill của mình) | ✓ (mọi skill) |
| User Management | ✕ (403) | ✓ |
| Feedback (VOC Management) | ✕ (403) | ✓ |
| Gửi VOC / xem VOC của mình | ✓ | ✓ |
| Settings (cá nhân) | ✓ | ✓ |
| Đăng ký mới | ✓ (luôn NORMAL_USER) | — (role do Admin gán) |

---

## 5. Tổng Hợp Luồng Chính (End-to-End)

```text
┌─────────────────────────────────────────────────────────────────────┐
│ 1. ONBOARDING                                                       │
│    Register → Login → Home                                          │
├─────────────────────────────────────────────────────────────────────┤
│ 2. RECOVERY                                                         │
│    Login (fail, DEACTIVE) → Forgot Password → OTP → Reset → Login   │
├─────────────────────────────────────────────────────────────────────┤
│ 3. SKILL LIFECYCLE (creator)                                        │
│    Home/Skill Mgmt → Add Skill (DRAFT) → Edit → Submit              │
│    → [Admin Approve] → APPROVED → khác subscribe + rate + comment  │
│    → Edit (version mới) → thông báo subscriber                      │
├─────────────────────────────────────────────────────────────────────┤
│ 4. SKILL REVIEW (admin)                                             │
│    Home (banner) → Skill Mgmt (filter PENDING_APPROVAL)             │
│    → Detail → Approve / Reject (lý do)                             │
├─────────────────────────────────────────────────────────────────────┤
│ 5. USER ADMINISTRATION (admin)                                      │
│    User Management → Create / Bulk Upload / Detail / Edit / Export  │
├─────────────────────────────────────────────────────────────────────┤
│ 6. PERSONALIZATION                                                  │
│    Avatar → My Settings → Profile / Preferences / Notifications /   │
│    Security                                                         │
├─────────────────────────────────────────────────────────────────────┤
│ 7. VOC / FEEDBACK                                                   │
│    User: Avatar → VOC → Add VOC (submit) → My VOCs (theo dõi)       │
│    Admin: Sidebar Feedback → Search → Detail → In Review/Resolve/   │
│    Ignore (notify creator)                                          │
├─────────────────────────────────────────────────────────────────────┤
│ 8. EXIT                                                             │
│    Avatar → Logout → Login                                          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. Tài Liệu Tham Chiếu

| Luồng | SRS |
|-------|-----|
| Login / Register / Forgot Password / OTP / Reset | `SRS/Login_User Register before login_SRS.md` |
| Home / GNB / Notification bell | `SRS/Home_Screen_SRS.md` |
| Skill Management (search, grid, permission) | `SRS/Skill_Management_Screen_Spec.md` |
| Skill Detail Popup | `SRS/Detail_Popup_SRS.md`, `SRS/Detail_Popup_Spec.md` |
| Add Skill Popup | `SRS/Add_Popup_SRS.md`, `SRS/Add_Popup_Spec.md` |
| Edit Skill Popup | `SRS/Edit_Popup_SRS.md`, `SRS/Edit_Popup_Spec.md` |
| User Management | `SRS/User Management Screen_SRS.md` |
| Settings | `SRS/Settings_SRS.md`, `SRS/Settings_Spec.md` |
| VOC / Feedback | `SRS/3. VOC Module/VOC_Feedback_SRS.md` |
| Sitemap chi tiết Skill Management (bản cũ) | `Draft/Detailed_Sitemap.md` |
