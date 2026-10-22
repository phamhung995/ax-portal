# SRS — VOC Module (Voice of Customer / Feedback)

## Software Requirements Specification

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | VOC Module — User Feedback (Bug / Suggestion / Question) |
| **Dự án** | AX Portal — Hệ thống Quản lý AI Skill (AISTEA) |
| **Phiên bản** | 1.0 |
| **Ngày tạo** | 16/10/2026 |
| **Tác giả** | BA Team |
| **Trạng thái** | Baseline (đã implement) |
| **Module code** | Backend: `com.skillmanagement.feedback` · Frontend: `src/features/feedback/` |

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu mô tả các yêu cầu chức năng và phi chức năng của module **VOC (Voice of Customer)** — cho phép người dùng gửi feedback (Bug / Suggestion / Question) về hệ thống, và cho phép Admin quản lý, xử lý feedback.

### 1.2. Phạm vi

Module VOC gồm 2 phía:

**Phía người dùng (mọi user đã đăng nhập):**
- Gửi feedback mới (rich text + screenshot tùy chọn)
- Xem danh sách feedback của chính mình ("My VOCs")
- Xem chi tiết feedback của mình (read-only, theo dõi trạng thái xử lý)

**Phía Admin (chỉ `ADMIN_USER`):**
- Màn Feedback Management: tìm kiếm (5 bộ lọc), xem grid, export Excel
- Xem chi tiết feedback (full detail + screenshot)
- Xử lý feedback: Mark In Review / Resolve / Ignore (kèm note)

### 1.3. Công nghệ

| Lớp | Công nghệ |
|-----|-----------|
| Frontend | Vue 3 + TypeScript + Design System (Prism) |
| Backend | Java 21 (Spring Boot 3.4.2) |
| Database | PostgreSQL (Flyway migrations V29, V30, V32, V36) |
| Authentication | JWT (access 30 phút + refresh 24h) |
| Email | Knox C&C Suite Mail API |

### 1.4. Tài liệu tham chiếu

| Tài liệu | Path |
|----------|------|
| Sitemap tổng thể | `../sitemap.md` |
| Error codes tổng thể | `../0. Error_Codes_Messages.md` |

---

## 2. Mô tả tổng thể

### 2.1. Người dùng (Actors)

| Vai trò | Mô tả |
|---------|-------|
| **User (thông thường)** | Gửi feedback, xem feedback của mình |
| **Admin User** | Toàn bộ quyền của User + quản lý/xử lý mọi feedback |
| **System** | Lưu feedback, gửi notification cho admin (khi có feedback mới) và cho creator (khi feedback được xử lý) |

### 2.2. Điểm vào (Entry Points)

| Entry Point | Vị trí | Mở ra |
|-------------|--------|-------|
| Menu **VOC** (icon Inbox) | User dropdown (header, giữa My Settings và Logout) | VOC Popup (2 tab: Add VOC \| My VOCs) |
| **Notification bell** (deep-link) | Chuông 🔔 header, notification có `feedbackId` | My Feedback Popup (detail read-only) |
| Sidebar **Feedback** (chỉ Admin) | Sidebar trái | Màn Feedback Management (`/feedback`) |

### 2.3. Wireframe tổng quan

```
VOC POPUP (mọi user, từ user dropdown)
┌─────────────────────────────────────────────────┐
│  VOC                                    [×]    │
├─────────────────────────────────────────────────┤
│  [ Add VOC ]   [ My VOCs ]                      │
├─────────────────────────────────────────────────┤
│  Tab "Add VOC":                                 │
│    Type:  ( 🐞 Bug ) ( 💡 Suggestion ) ( ❓ Question ) │
│    Description:  [Rich Text Editor] *           │
│    Screenshot:   [📎 Attach screenshot (optional)] │
│                                [Submit]         │
│                                                 │
│  Tab "My VOCs":                                 │
│    [🐞 Bug] [New]                               │
│    "Content preview (stripHtml, 160 ký tự)..."  │
│    16/10/2026 14:30                             │
│    ─────────────────────────────────            │
│    [💡 Suggestion] [Resolved]                   │
│    "Content preview..."                         │
│    12/10/2026 09:15                             │
└─────────────────────────────────────────────────┘
        Click item → My Feedback Popup (detail read-only)

FEEDBACK MANAGEMENT (chỉ Admin, sidebar /feedback)
┌─────────────────────────────────────────────────┐
│  Content [____]  Type [All▼]  Status [All▼]     │
│  Requester [🔍]  Handler [All▼]                 │
│  [Download Excel]        [Search] [Reset]       │
├─────────────────────────────────────────────────┤
│  Type │ Content │ Requester │ Handler │ Status │ Created │
│  🐞   │ ...     │ Name      │ Admin A │ New    │ dd/mm   │
│       │         │ email     │         │        │         │
├─────────────────────────────────────────────────┤
│  Rows [20▼]            Pagination               │
└─────────────────────────────────────────────────┘
        Click Content → Feedback Detail Popup (admin)
```

### 2.4. Vòng đời trạng thái Feedback

```text
                    (User submit)
                         │
                         ▼
                       NEW
                         │
        ┌────────────────┼─────────────────┐
        │                │                 │
        ▼                ▼                 ▼
   IN_REVIEW          RESOLVED ◄──────── IGNORED
  (Mark In Review)   (Resolve + note)   (Ignore + note)
                     │
                     └──► Feedback CLOSED (không thao tác thêm)
```

| Trạng thái | Ý nghĩa | Ai chuyển | Note bắt buộc |
|------------|---------|-----------|---------------|
| `NEW` | Mới gửi, chưa ai xem | System (khi submit) | — |
| `IN_REVIEW` | Admin đang xem xét | Admin (từ NEW) | Không |
| `RESOLVED` | Đã xử lý xong | Admin (từ NEW/IN_REVIEW) | **Có** (≤500 ký tự) |
| `IGNORED` | Không xử lý | Admin (từ NEW/IN_REVIEW) | **Có** (≤500 ký tự) |

Quy tắc:
- `RESOLVED` / `IGNORED` là trạng thái **chốt** (closed) — không thao tác thêm, không đổi trạng thái ngược.
- `resolved_by` + `resolved_at` được ghi khi chuyển sang IN_REVIEW / RESOLVED / IGNORED.
- Khi chuyển sang RESOLVED/IGNORED → **notify creator** (in-app + email theo cài đặt).

---

## 3. Yêu cầu chức năng

### 3.1. FR-01: Gửi Feedback (Add VOC)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-01 |
| **Tên** | Gửi feedback mới |
| **Ưu tiên** | Cao |
| **Trigger** | User mở VOC Popup → tab "Add VOC" → [Submit] |
| **API** | `POST /api/v1/feedback` (JSON) |

**Mô tả:** Người dùng đã đăng nhập gửi feedback về hệ thống.

**Các trường:**

| Trường | Type | Bắt buộc | Quy tắc |
|--------|------|:--------:|---------|
| Type | Enum | Có | `BUG` / `SUGGESTION` / `QUESTION` (3 nút segmented, mặc định BUG) |
| Content | Rich Text (HTML) | Có | Không rỗng sau khi strip HTML; tối đa **1000 ký tự** (tính trên text đã strip — enforce phía client); không giới hạn độ dài HTML phía server (V32 đã bỏ CHECK) |
| Screenshot | Base64 Data URI | Không | Chỉ nhận `data:image/...` (jpg/jpeg/png/webp, max 5MB); ảnh >500KB tự động compress (canvas, ≤1280px, JPEG 0.85) qua `imageCompress.ts` |

**Hành vi:**
- SH-01-01: Content rỗng (chỉ whitespace/HTML tag) → lỗi "Please describe your feedback."
- SH-01-02: Content >1000 ký tự (strip) → lỗi "Feedback text is at most 1000 characters."
- SH-01-03: Screenshot không phải `data:image/` → backend bỏ qua (lưu NULL), không lỗi.
- SH-01-04: Submit thành công → toast "Thank you for your feedback!" → tự chuyển sang tab "My VOCs" → refresh list.
- SH-01-05: **Rate limit**: 10 feedback / user / 60 giây → lỗi `FEEDBACK_RATE_LIMITED` (429).
- SH-01-06: Feedback mới luôn có status `NEW`.

**Notification (backend, sau khi lưu):**
- Gửi cho **TẤT CẢ admin** (role `ADMIN_USER`):
  - In-app notification (nếu admin bật `inapp_notify`): title "New {type} feedback from {creator}." — notification có `feedbackId` để deep-link.
  - Email (nếu admin bật `email_notify`): nội dung type + creator (name + email) + content.

---

### 3.2. FR-02: Xem danh sách feedback của mình (My VOCs)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-02 |
| **Tên** | Danh sách feedback của user |
| **Ưu tiên** | Cao |
| **Trigger** | Mở VOC Popup → tab "My VOCs" |
| **API** | `GET /api/v1/feedback/mine?page=1&pageSize=50` |

**Mô tả:** List feedback do chính user gửi, mới nhất trước.

**Hành vi:**
- SH-02-01: Mỗi item hiển thị: badge Type, badge Status, preview content (stripHtml, 160 ký tự, max 2 dòng), thời gian tạo (dd/mm/yyyy HH:mm).
- SH-02-02: Click item → mở My Feedback Popup (detail read-only).
- SH-02-03: List trống → empty state "No VOCs yet".
- SH-02-04: Danh sách **không bao gồm** screenshot (chỉ `hasScreenshot` boolean) — screenshot chỉ load trong detail.

---

### 3.3. FR-03: Xem chi tiết feedback của mình (My Feedback Popup)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-03 |
| **Tên** | Detail feedback (owner, read-only) |
| **Ưu tiên** | Cao |
| **Trigger** | Click item trong My VOCs, hoặc notification bell deep-link |
| **API** | `GET /api/v1/feedback/{id}` |

**Mô tả:** Xem chi tiết feedback của chính mình. **Read-only** — user không sửa được gì.

**Quy tắc truy cập:**
- SH-03-01: Chỉ owner hoặc Admin xem được.
- SH-03-02: User khác (không phải owner, không phải admin) truy cập → **404** `FEEDBACK_NOT_FOUND` (không phải 403 — không tiết lộ tồn tại).

**Hiển thị:**
- Badges: Type + Status
- Meta: thời gian gửi
- Content (rich text, render qua `sanitizeHtml()` — whitelist, chống XSS)
- Screenshot (nếu có)
- Khi RESOLVED/IGNORED: khối "Resolution" — "{status} by {admin name} on {dd/mm/yyyy HH:mm}" + resolution note.

---

### 3.4. FR-04: Feedback Management — Search (Admin)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-04 |
| **Tên** | Tìm kiếm feedback (admin) |
| **Ưu tiên** | Cao |
| **Trigger** | Màn `/feedback` (sidebar, chỉ Admin) → [Search] |
| **API** | `POST /api/v1/admin/feedback/search` (`@Secured("ROLE_ADMIN_USER")`) |

**5 bộ lọc:**

| Field | Component | Logic |
|-------|-----------|-------|
| Content | Text input | Contains — mỗi từ (whitespace-separated) phải LIKE-match (AND, case-insensitive, match trên raw HTML) |
| Type | Combobox (single) | `BUG` / `SUGGESTION` / `QUESTION` |
| Status | Combobox (single) | `NEW` / `IN_REVIEW` / `RESOLVED` / `IGNORED` |
| Requester | Search User Popup (🔍, cùng pattern Creator trong Skill Management) | `user_id = selected` — chọn user → điền name, có nút ✕ clear |
| Handler | Combobox (single, **chỉ user role ADMIN_USER** — `GET /api/v1/admin/users/search-users?role=ADMIN_USER`) | `resolved_by = selected` |

**Hành vi:**
- SH-04-01: Các bộ lọc kết hợp bằng **AND**; bộ lọc trống = không giới hạn.
- SH-04-02: Sort mặc định `createdAt DESC`.
- SH-04-03: Pagination: page size 10/20/50/100 (mặc định 20).
- SH-04-04: [Reset] xóa toàn bộ 5 bộ lọc.
- SH-04-05: Màn load tự động chạy search lần đầu.
- SH-04-06: Lỗi load → empty state "Unable to load feedback list." + nút [Retry].

---

### 3.5. FR-05: Feedback Management — Grid (Admin)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-05 |
| **Tên** | Grid danh sách feedback |
| **Ưu tiên** | Cao |

**Cột grid:**

| # | Cột | Nội dung |
|---|-----|----------|
| 1 | Type | Badge: 🐞 Bug (danger) / 💡 Suggestion (info) / ❓ Question (neutral) |
| 2 | Content | Link (accent) — stripHtml, truncate 320px + tooltip; icon 📷 nếu có screenshot. **Click → mở Feedback Detail Popup** |
| 3 | Requester | User name + email (dòng 2, nhỏ) |
| 4 | Handler | Tên admin đã xử lý (nếu chưa: "—") |
| 5 | Status | Badge: New (warning) / In Review (info) / Resolved (success) / Ignored (neutral) |
| 6 | Created | dd/mm/yyyy HH:mm |

- Không có checkbox / bulk action.
- Empty: "No feedback found."

---

### 3.6. FR-06: Feedback Detail + Xử lý (Admin)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-06 |
| **Tên** | Xem chi tiết + xử lý feedback |
| **Ưu tiên** | Cao |
| **API** | `GET /api/v1/admin/feedback/{id}` · `PATCH /api/v1/admin/feedback/{id}/status` |

**Hiển thị:** badges Type + Status, meta (User name + email, thời gian tạo), content (rich text), screenshot, khối Resolution (khi đã xử lý).

**Khu vực thao tác (chỉ hiện khi feedback chưa closed):**

| Nút | Hiện khi | Hành vi | Note |
|-----|----------|---------|------|
| Mark In Review | Status = NEW | → `IN_REVIEW` | Không bắt buộc |
| Resolve | Status = NEW / IN_REVIEW | → `RESOLVED` | **Bắt buộc** (≤500 ký tự) |
| Ignore | Status = NEW / IN_REVIEW | → `IGNORED` | **Bắt buộc** (≤500 ký tự) |

**Hành vi:**
- SH-06-01: Resolve/Ignore mà không nhập note → lỗi "A note is required."
- SH-06-02: Note >500 ký tự → lỗi `FEEDBACK_RESOLUTION_NOTE_TOO_LONG`.
- SH-06-03: Xử lý thành công → toast → đóng popup → refresh grid (nếu đã search).
- SH-06-04: **Notify creator** (khi RESOLVED/IGNORED):
  - In-app (nếu creator bật `inapp_notify`): "Your feedback has been resolved." / "Your feedback has been reviewed." — có `feedbackId` deep-link.
  - Email (nếu creator bật `email_notify`): kèm resolution note.
- SH-06-05: Feedback đã closed (RESOLVED/IGNORED) → không hiện khu vực thao tác.

---

### 3.7. FR-07: Export Excel (Admin)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-07 |
| **Tên** | Export danh sách feedback |
| **Ưu tiên** | Trung bình |
| **API** | `POST /api/v1/admin/feedback/export` |

**Hành vi:**
- SH-07-01: Export **toàn bộ** kết quả theo bộ lọc hiện tại (không giới hạn page).
- SH-07-02: Chưa có dữ liệu (chưa search / kết quả rỗng) → toast "No data to export. Run a search first."
- SH-07-03: In-memory XSSFWorkbook (NASC-safe — không ghi file ra disk).
- SH-07-04: Cột Excel: `ID | Type | Content (stripHtml) | Requester | Requester Email | Handler | Status | Created` (dd/MM/yyyy HH:mm).
- SH-07-05: Tên file: `Feedback_YYYYMMDD_HHmmss.xlsx`.

---

## 4. Yêu cầu phi chức năng

| ID | Yêu cầu |
|----|---------|
| NFR-01 | **Security**: quyền admin enforce ở backend (`@Secured("ROLE_ADMIN_USER")`), không tin UI. Detail owner: 404 (không 403) cho user không liên quan. |
| NFR-02 | **XSS**: content là rich text HTML — render bắt buộc qua `sanitizeHtml()` (whitelist tag) phía frontend; strip HTML khi preview/export. |
| NFR-03 | **Storage**: screenshot lưu base64 data URI trong cột TEXT — không ghi file ra disk (NASC-safe). |
| NFR-04 | **Rate limit**: 10 feedback/user/phút (in-memory RateLimiter). |
| NFR-05 | **Performance**: search response ≤ 3 giây; export xử lý toàn bộ kết quả lọc. |
| NFR-06 | **Notification**: tôn trọng `user_settings` (inapp_notify / email_notify) của từng người nhận; lỗi gửi email không làm fail transaction (log warn). |
| NFR-07 | **i18n**: toàn bộ message hiển thị bằng tiếng Anh, error code ổn định `FEEDBACK_*`. |

---

## 5. API Specification

Envelope chung: `{ success, data, errorCode?, message? }` — prefix `/api/v1`, context path `/skm`.

### 5.1. Submit feedback (mọi user)

```text
POST /api/v1/feedback
Content-Type: application/json
```

Request:

```json
{
  "type": "BUG",
  "content": "<p>...</p>",
  "screenshot": "data:image/png;base64,...."
}
```

Response: `201` (void).

### 5.2. List feedback của mình (mọi user)

```text
GET /api/v1/feedback/mine?page=1&pageSize=50
```

Response `data`:

```text
totalCount, page, pageSize
items[]: { id, userId, userName, userEmail, type, content,
           status, createdAt, hasScreenshot, resolvedByName }
```

> Không có `screenshot` (tránh trả về chuỗi base64 nhiều MB trong list).

### 5.3. Detail feedback của mình (owner hoặc admin)

```text
GET /api/v1/feedback/{id}
```

Response `data`:

```text
id, userId, userName, userEmail, type, content, screenshot,
status, resolutionNote, resolvedByName, resolvedAt, createdAt
```

Không phải owner và không phải admin → `404 FEEDBACK_NOT_FOUND`.

### 5.4. Search (admin)

```text
POST /api/v1/admin/feedback/search
```

Request:

```json
{
  "type": "BUG",
  "status": "NEW",
  "content": "login fails",
  "requesterId": 12,
  "resolvedById": 3,
  "page": 1,
  "pageSize": 20
}
```

Response `data`: cùng cấu trúc `items[]` như 5.2.

### 5.5. Detail (admin)

```text
GET /api/v1/admin/feedback/{id}
```

Response: như 5.3.

### 5.6. Đổi trạng thái (admin)

```text
PATCH /api/v1/admin/feedback/{id}/status
```

Request:

```json
{
  "status": "RESOLVED",
  "resolutionNote": "Fixed in release 1.2"
}
```

- `status` ∈ `IN_REVIEW` / `RESOLVED` / `IGNORED`.
- `RESOLVED`/`IGNORED` bắt buộc `resolutionNote` (≤500).
- Response: `204`.

### 5.7. Export Excel (admin)

```text
POST /api/v1/admin/feedback/export
```

Request: 5 bộ lọc (không có page). Response: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (blob), header `Content-Disposition: attachment`.

---

## 6. Database Schema

### 6.1. Bảng `feedback` (V29, sửa V32, V36)

| Cột | Type | Ghi chú |
|-----|------|---------|
| id | BIGSERIAL PK | |
| user_id | BIGINT NOT NULL | FK user — người gửi |
| type | VARCHAR(20) NOT NULL | CHECK IN (BUG, SUGGESTION, QUESTION) |
| content | TEXT NOT NULL | Rich text HTML (V32: bỏ CHECK 1000 ký tự) |
| screenshot | TEXT | Base64 data URI (tùy chọn) |
| status | VARCHAR(20) NOT NULL DEFAULT 'NEW' | CHECK IN (NEW, IN_REVIEW, RESOLVED, IGNORED) |
| resolution_note | VARCHAR(500) | Note khi Resolve/Ignore |
| resolved_by | BIGINT | Admin xử lý |
| resolved_at | TIMESTAMPTZ | Thời điểm xử lý |
| created_at | TIMESTAMPTZ NOT NULL | |

> ~~`page_url VARCHAR(500)`~~ — **đã xóa bởi V36** (16/10/2026): không còn thu thập/hiển thị page context.

### 6.2. Liên quan

| Bảng | Cột | Ghi chú |
|------|-----|---------|
| `notification` | `feedback_id BIGINT` (V30) | Deep-link từ notification bell → mở detail feedback |
| `user_settings` | `inapp_notify`, `email_notify` | Kênh notification của từng người nhận |

---

## 7. Error Codes

| Error Code | HTTP | Message |
|------------|:----:|---------|
| `FEEDBACK_NOT_FOUND` | 404 | Feedback not found. |
| `FEEDBACK_INVALID_TYPE` | 400 | Feedback type must be BUG, SUGGESTION or QUESTION. |
| `FEEDBACK_INVALID_STATUS` | 400 | Status must be IN_REVIEW, RESOLVED or IGNORED. |
| `FEEDBACK_CONTENT_EMPTY` | 400 | Feedback content cannot be empty. |
| `FEEDBACK_CONTENT_TOO_LONG` | 400 | Feedback content is too long. |
| `FEEDBACK_RESOLUTION_NOTE_REQUIRED` | 400 | A resolution note is required. |
| `FEEDBACK_RESOLUTION_NOTE_TOO_LONG` | 400 | Resolution note is at most 500 characters. |
| `FEEDBACK_RATE_LIMITED` | 429 | Too many requests. Please try again later. |

---

## 8. Xử lý lỗi & Edge Case

| # | Edge Case | Xử lý |
|---|-----------|-------|
| 1 | User không phải owner truy cập `GET /feedback/{id}` | 404 (không tiết lộ tồn tại) |
| 2 | Normal user gọi API admin | 403 (`@Secured`) |
| 3 | Screenshot sai format (không phải `data:image/`) | Backend lưu NULL, không lỗi |
| 4 | Content chỉ chứa HTML tag, không có text | Lỗi "Feedback content cannot be empty." (client strip trước) |
| 5 | Gửi quá 10 feedback/phút | 429 rate limit |
| 6 | Resolve/Ignore không có note | 400, giữ form, highlight lỗi |
| 7 | Gửi email notification fail | Log warn, transaction không fail |
| 8 | Admin không tồn tại (role chưa tạo) | Không gửi notification, feedback vẫn lưu |
| 9 | Export khi chưa search / rỗng | Toast "No data to export. Run a search first." |
| 10 | HTML trong content chứa tag nguy hiểm (`<script>`, `onerror`) | `sanitizeHtml()` whitelist khi render — bỏ tag/attribute ngoài whitelist |

---

## 9. Checklist Kiểm thử (QA)

| Test ID | Scenario | Expected |
|---------|----------|----------|
| TC-VOC-001 | Submit feedback hợp lệ (BUG, có text) | 201, toast, tự sang tab My VOCs, item hiện |
| TC-VOC-002 | Submit content rỗng | Lỗi inline "Please describe your feedback." |
| TC-VOC-003 | Submit content >1000 ký tự (strip) | Lỗi "Feedback text is at most 1000 characters." |
| TC-VOC-004 | Submit kèm screenshot (PNG <500KB) | Lưu data URI, detail hiện ảnh |
| TC-VOC-005 | Submit screenshot >500KB | Tự compress, vẫn lưu |
| TC-VOC-006 | Gửi 11 feedback trong 60s | Request thứ 11 → 429 |
| TC-VOC-007 | Admin nhận feedback mới (bật in-app) | Notification hiện, badge +1, deep-link mở detail |
| TC-VOC-008 | Admin nhận feedback mới (bật email) | Nhận email kèm content |
| TC-VOC-009 | Admin tắt cả 2 kênh | Không nhận notification, feedback vẫn lưu |
| TC-VOC-010 | User xem My VOCs | Chỉ hiện feedback của mình, mới nhất trước |
| TC-VOC-011 | User A mở detail feedback của User B | 404 |
| TC-VOC-012 | Admin search Content "login fails" (2 từ) | Kết quả chứa cả 2 từ (AND) |
| TC-VOC-013 | Admin search Type + Status + Requester + Handler | AND giữa các bộ lọc |
| TC-VOC-014 | Admin search Requester qua Search User Popup | Chọn user → filter đúng user |
| TC-VOC-015 | Handler filter chỉ hiện admin | Options = user role ADMIN_USER |
| TC-VOC-016 | Click Content trong grid | Mở Feedback Detail Popup |
| TC-VOC-017 | Mark In Review (NEW) | Status → In Review, không cần note |
| TC-VOC-018 | Resolve không note | Lỗi "A note is required." |
| TC-VOC-019 | Resolve có note | Status → Resolved, creator nhận notification + email |
| TC-VOC-020 | Ignore có note | Status → Ignored, creator nhận notification |
| TC-VOC-021 | Feedback Resolved → mở detail | Không hiện khu thao tác, hiện khối Resolution |
| TC-VOC-022 | Creator mở detail feedback đã Resolved | Hiện "Resolved by {admin} on {date}" + note |
| TC-VOC-023 | Export khi có bộ lọc | Excel = toàn bộ kết quả lọc (multi-page) |
| TC-VOC-024 | Export khi chưa search | Toast "No data to export." |
| TC-VOC-025 | Content chứa `<script>` | Render không chạy script (sanitized) |
| TC-VOC-026 | Normal user gọi POST /admin/feedback/search | 403 |
| TC-VOC-027 | Notification bell có feedbackId | Click → mở My Feedback Popup đúng feedback |
| TC-VOC-028 | Grid item có screenshot | Hiện icon 📷 cạnh content |
| TC-VOC-029 | Reset search | 5 bộ lọc về trống |
| TC-VOC-030 | Pagination đổi page size 10/50/100 | List refresh đúng size |

---

## 10. Version History

| Version | Ngày | Thay đổi |
|---------|------|----------|
| 1.0 | 16/10/2026 | Baseline — viết theo implementation đã ship (commit 294730b) |
