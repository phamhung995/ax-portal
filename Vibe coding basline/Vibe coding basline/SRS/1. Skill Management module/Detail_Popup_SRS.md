# SRS — Skill AI Detail Popup

## Software Requirements Specification

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | Skill AI Detail Popup |
| **Dự án** | Hệ thống Quản lý - Đo lường & Đánh giá Hiệu năng AI-SR |
| **Phiên bản** | 2.1 |
| **Ngày tạo** | 08/09/2026 |
| **Tác giả** | BA Team (tq.hop) |
| **Trạng thái** | Draft |

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu này mô tả các yêu cầu chức năng và phi chức năng của chức năng **Detail Popup** trong module Skill AI Management của hệ thống. Tài liệu phục vụ cho đội phát triển (frontend, backend, QA) làm cơ sở triển khai và kiểm thử.

### 1.2. Phạm vi

Detail Popup là cửa sổ hiện lên khi người dùng click vào Skill Name hoặc double-click vào một dòng trên grid Skill List, cho phép:
- Xem thông tin chi tiết của skill (tên, mô tả, status, visibility scope, người tạo, ngày tạo)
- Xem điểm đánh giá sao trung bình
- Đánh giá sao cho skill (chỉ khi APPROVED)
- Xem và viết bình luận (chỉ khi APPROVED)
- Tải xuống file skill (chỉ khi APPROVED)
- Quản lý trạng thái: Submit, Approve, Reject, Delete

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
| Detail Popup BA Spec | `Detail Popup/Detail_Popup_Spec.md` |

---

## 2. Mô tả tổng thể

### 2.1. Người dùng

| Vai trò | Mô tả |
|---------|-------|
| **User (thông thường)** | Xem detail, đánh giá sao, bình luận, tải xuống (skill APPROVED). Submit/Delete skill của mình |
| **Admin** | Toàn bộ quyền của User + Approve/Reject/Delete bất kỳ skill nào |

### 2.2. Giả định

- Người dùng đã đăng nhập thành công và có JWT hợp lệ
- Skill được chọn tồn tại trong hệ thống
- Hệ thống có kết nối mạng ổn định

### 2.3. Mô hình trạng thái

#### 2.3.1. `approval_status`

| Giá trị | Tên | Badge | Ai thấy? |
|---------|-----|-------|----------|
| `DRAFT` | Draft | Xám (#999) | Chỉ người tạo |
| `PENDING_APPROVAL` | Chờ duyệt | Vàng (#FAAD14) | Người tạo + Admin |
| `APPROVED` | Đã công bố | Xanh (#52C41A) | Theo `public_scope` |
| `REJECTED` | Bị từ chối | Đỏ (#FF4D4F) | Người tạo + Admin |

> **Lưu ý:** Skill bị xóa (soft delete) dùng trường `deleted_YN` — không phải là trạng thái riêng.
> `public_scope` ưu tiên hơn role. Skill APPROVED + PRIVATE → chỉ creator thấy.

#### 2.3.2. State Machine

```
DRAFT ──(Submit)──► PENDING_APPROVAL ──(Approve)──► APPROVED
  ▲  │                          │
  │  └──(Withdraw)──────────────┘
  │
  └──(Submit)◄── REJECTED ◄──(Reject)

APPROVED ──(Update Version)──► PENDING_APPROVAL [version pending]
  ▲                                │
  ├──(Approve: snapshot vN, vN+1)──┤
  └──(Reject/Cancel: về APPROVED)──┘
```

> - **Withdraw**: creator rút lại skill đang chờ duyệt (PENDING_APPROVAL → DRAFT)
> - **Delete (soft)**: gán `deleted_YN = 'Y'` — skill ẩn khỏi grid, không khôi phục
> - **Update Version (v1.1)**: chỉ skill APPROVED; skill đang có version pending
>   (`PENDING_APPROVAL` + `pending_metadata`) vẫn hiển thị cho user khác như APPROVED
>   (version cũ live). Approve → snapshot version cũ + version bump; Reject/Cancel → về APPROVED.

#### 2.3.3. `public_scope`

| Giá trị | Tên | Icon | Phạm vi |
|---------|-----|------|---------|
| `PRIVATE` | Riêng tư | 🔒 | Chỉ người tạo |
| `PART` | Theo Part | 🏛️ | Cùng Part |
| `GROUP` | Theo Group | 🏢 | Cùng Group |
| `PUBLIC` | Công khai | 🌍 | Tất cả user |

### 2.4. Wireframe

#### Trạng thái APPROVED

```
┌─────────────────────────────────────────────────────┐
│  [Skill Name]          ★ 4.5/5 (12)  │
│  Status: Approved  🌍 Public              │
├─────────────────────────────────────────────────────┤
│  Creator: Nguyen Van A          Created: 07/09/2026 · Updated: 08/09/2026   │
├─────────────────────────────────────────────────────┤
│  📄 skill.zip (200 KB)                              │
│  #code-review  #java  #automation                  │
├─────────────────────────────────────────────────────┤
│  Tab [Info] [Versions] [Comments]                   │
│  ...                                                │
├─────────────────────────────────────────────────────┤
│                        [Download]  [Close]          │
└─────────────────────────────────────────────────────┘
```

#### Trạng thái APPROVED + Version Update Pending (creator/admin)

```
┌─────────────────────────────────────────────────────┐
│  [Skill Name]          ★ 4.5/5 (12)  │
│  Status: Pending Approval  🌍 Public        │
├─────────────────────────────────────────────────────┤
│  ⚠️ A version update is pending review. The content │
│     above shows the current (live) version.         │
├─────────────────────────────────────────────────────┤
│  📄 skill.zip (200 KB)   ← version hiện tại (live)  │
├─────────────────────────────────────────────────────┤
│  Pending Version 2 — under review   [Under Review]  │
│  Name: ...  Scope: Public  Categories: ...          │
│  New files:  📄 skill-v2.zip  [Download]            │
│             [Download All]                          │
│  Files to be removed: ~~📄 old.zip~~                │
├─────────────────────────────────────────────────────┤
│  Tab [Info] [Versions] [Comments]                   │
├─────────────────────────────────────────────────────┤
│  [Cancel Version Update]   [Approve] [Reject] [Close]│
└─────────────────────────────────────────────────────┘
```

#### Trạng thái REJECTED

```
┌─────────────────────────────────────────────────────┐
│  [Skill Name]                         │
│  Status: Rejected  🔒 Private              │
├─────────────────────────────────────────────────────┤
│  Creator: Nguyen Van A          Created: 07/09/2026 · Updated: 08/09/2026   │
├─────────────────────────────────────────────────────┤
│  📄 skill.zip (200 KB)                              │
│  #api  #wrapper                                  │
├─────────────────────────────────────────────────────┤
│  Tab [Info] [Versions] [Comments]                   │
│  ...                                                │
├─────────────────────────────────────────────────────┤
│  ❌ Rejection reason: Skill violates policy...      │
├─────────────────────────────────────────────────────┤
│  [Submit for Review]           [Close]              │
└─────────────────────────────────────────────────────┘
```

---

## 3. Yêu cầu chức năng

### 3.1. FR-01: Mở Detail Popup

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-01 |
| **Tên** | Mở Detail Popup |
| **Ưu tiên** | Cao |
| **Trigger** | Click vào Skill Name (delay 300ms) hoặc double-click vào một dòng trên grid Skill List. Double-click ưu tiên — nếu user double-click, hủy delay click đơn, tránh popup nháy |

**Mô tả:** Khi người dùng click vào Skill Name hoặc double-click vào một dòng trên grid, hệ thống mở popup detail của skill tương ứng.

**Luồng chính:**
1. Hệ thống nhận sự kiện click Skill Name (delay 300ms) hoặc double-click, lấy `skillId` của dòng được chọn
   - Double-click ưu tiên: nếu user double-click, hủy delay click đơn → tránh popup nháy
2. Gọi `GET /api/v1/skills/{id}` để lấy thông tin skill
3. Nếu thành công, gọi song song:
   - `GET /api/v1/skills/{id}/rating` — điểm sao trung bình
   - `GET /api/v1/skills/{id}/rating/me` — đánh giá của user hiện tại
   - `GET /api/v1/skills/{id}/comments?page=1` — danh sách bình luận
4. Hiển thị popup, active tab "Information"
5. Hiển thị banner nếu `PENDING_APPROVAL`
6. Hiển thị lý do reject nếu `REJECTED`
7. Render nút footer theo `approval_status` + role
8. Dữ liệu rating/comment được cache trong popup. Chuyển tab → hiển thị từ cache. Auto-refresh nếu ở tab "Comments" quá 30s không hoạt động.
9. Sau khi popup render xong → gọi async `POST /api/v1/skills/{id}/view` (tăng view_count, idempotent: 1 user/15 phút). Không block UI.
10. Nếu Detail popup đang mở cho skill khác → đóng Detail cũ, mở Detail mới (luôn chỉ có 1 Detail popup tại một thời điểm).

**Luồng ngoại lệ:**
- Nếu skill không tồn tại (404) → đóng popup, toast "Skill has been deleted" (ERR-DTL-002)
- Nếu lỗi kết nối → toast "Unable to load skill information" (ERR-DTL-001)

**Dữ liệu hiển thị:**

| Vùng | Trường | Nguồn | Định dạng |
|------|--------|-------|-----------|
| Header dòng 1 | Tên skill | `skills_master.name` | Text bold, 20px |
| Header dòng 1 | Điểm sao TB | `AVG(skill_ratings.rating)` | `★ 4.5/5 (12)` — sao + điểm + số người |
| Header dòng 1 | Nút đóng ✕ | — | Icon button, góc trên phải, cùng dòng với tên skill |
| Header dòng 2 | Status | `skills_master.approval_status` | Badge theo màu trạng thái |
| Header dòng 2 | Public Scope | `skills_master.public_scope` | Icon + text, cạnh Status badge (bên trái) |
| Header dòng 2 | Nút Subscribe | `skill_subscriptions` (user_id) | Button 🔔 + text "Subscribe"/"Subscribed". Chỉ khi `APPROVED` + non-creator. Mép phải căn thẳng hàng với nút đóng ✕. Không hiện ở footer |
| Meta | Người tạo | `"user".name` (qua `skills_master.created_by`) | Text 13px | — |
| Meta | Ngày tạo | `skills_master.created_at` | dd/MM/yyyy | — |
| Meta | File (nếu có) | `skill_files` (JOIN qua `skills_master.id`) | "📄 filename.ext (200 KB)" | Chỉ khi skill có file (bảng `skill_files` 1:N) |
| Comment — Nút xóa | `skill_comments.created_by` vs `currentUser` | Nút "✕ Delete" | Chỉ hiện trên comment của chính user, hoặc mọi comment nếu user là admin |

---

### 3.2. FR-02: Xem thông tin skill (Tab "Information")

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-02 |
| **Tên** | Xem thông tin skill |
| **Ưu tiên** | Cao |
| **Trigger** | Mặc định active khi mở popup, hoặc click tab "Information" |

**Mô tả:** Hiển thị mô tả chi tiết của skill trong tab "Information".

**Yêu cầu:**
- SH-02-01: Hệ thống PHẢI hiển thị nội dung `skills_master.description`
- SH-02-02: Nội dung PHẢI giữ nguyên format xuống dòng (`white-space: pre-wrap`)
- SH-02-03: Vùng nội dung PHẢI có scroll (`overflow-y: auto`) khi nội dung vượt quá chiều cao popup

---

### 3.3. FR-03: Đánh giá sao

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-03 |
| **Tên** | Đánh giá sao cho skill |
| **Ưu tiên** | Cao |
| **Trigger** | Click vào sao trong tab "Comments" |

**Mô tả:** Người dùng có thể đánh giá skill bằng sao (1.0 – 5.0, step 0.5). Chỉ hoạt động khi skill ở trạng thái `APPROVED`.

**Luồng chính:**
1. User click vào sao (1–5, step 0.5)
2. Nếu skill không `APPROVED` → toast "Only published skills can be rated" (ERR-DTL-003)
3. Frontend gọi `PUT /api/v1/skills/{id}/rating` với body `{ "rating": <giá trị> }`
4. Nếu thành công (response = điểm TB cập nhật):
   - Cập nhật sao hiển thị ngay (optimistic update)
   - Dùng `averageRating`/`totalRatings` trong response để cập nhật điểm TB (không gọi lại GET)
5. Nếu thất bại: toast "Unable to save rating" (ERR-DTL-004), khôi phục sao cũ

**Ràng buộc:**
- SH-03-01: Điểm đánh giá PHẢI là số, từ 1.0 đến 5.0, step 0.5
- SH-03-02: Một user CHỈ ĐƯỢC đánh giá 1 lần cho mỗi skill (UPSERT)
- SH-03-03: Chỉ user đã đăng nhập mới được đánh giá
- SH-03-04: Chỉ skill `APPROVED` mới cho phép đánh giá

---

### 3.4. FR-04: Xem danh sách bình luận

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-04 |
| **Tên** | Xem danh sách bình luận |
| **Ưu tiên** | Cao |
| **Trigger** | Click tab "Comments" |

**Mô tả:** Hiển thị danh sách bình luận của skill, sắp xếp mới nhất lên đầu. Read-only khi `approval_status` khác `APPROVED`.

**Yêu cầu:**
- SH-04-01: Mỗi bình luận PHẢI hiển thị: tên người bình luận, thời gian (dd/MM/yyyy HH:mm), nội dung
- SH-04-02: Danh sách PHẢI sắp xếp theo `created_at` DESC
- SH-04-03: Hệ thống PHẢI hiển thị tổng số bình luận: "Comments (N)"
- SH-04-04: Nếu không có bình luận và skill **APPROVED** → hiển thị "No comments yet" kèm form viết bình luận. Nếu skill **không APPROVED** → ẩn hoàn toàn khu vực comment (danh sách, header, pagination)
- SH-04-05: Danh sách PHẢI có pagination (10 comment/trang)
- SH-04-06: Component phân trang PHẢI hiển thị nút Previous/Next + số trang (tối đa 5 nút)
- SH-04-07: Vùng danh sách PHẢI có scroll khi nội dung vượt quá chiều cao popup
- SH-04-08: **Read-only khi khác APPROVED = hiển thị danh sách comment và rating (xem được), nhưng không cho phép đánh giá, viết comment mới, hoặc xóa comment.**
- SH-04-09: Xóa comment → nếu trang hiện tại không còn comment → tự động chuyển về trang trước
- SH-04-10: Gửi comment mới → tự động chuyển về trang 1, comment mới hiển thị ở đầu danh sách
- SH-04-11: API filter `WHERE deleted_at IS NULL` — comment đã soft-delete không xuất hiện trong danh sách
- SH-04-12: Sau auto-refresh, nếu `currentPage > totalPages` → tự động chuyển về `totalPages`
- SH-04-13: Khi `commentCount === 0` → ẩn danh sách comment, ẩn header "Comments (N)", ẩn pagination
- SH-04-14: Nút "✕ Delete" chỉ hiển thị trên comment của chính user hiện tại, hoặc trên mọi comment nếu user là admin
- SH-04-15: Auto-refresh 30s giữ nguyên `currentPage`. Nếu trang hiện tại vẫn có comment → user không bị mất vị trí đọc. Nếu `currentPage > totalPages` sau refresh → áp dụng quy tắc overflow

---

### 3.5. FR-05: Viết bình luận

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-05 |
| **Tên** | Viết bình luận cho skill |
| **Ưu tiên** | Cao |
| **Trigger** | Nhập nội dung vào textarea và click "Send" |

**Mô tả:** Người dùng có thể viết bình luận cho skill. Chỉ hoạt động khi skill ở trạng thái `APPROVED`.

**Luồng chính:**
1. User nhập nội dung vào textarea
2. User click nút "Send"
3. Nếu skill không `APPROVED` → toast "Only published skills can be commented" (ERR-DTL-005)
4. Frontend validate: nội dung không rỗng, ≤ 1000 ký tự
5. Nếu hợp lệ, gọi `POST /api/v1/skills/{id}/comments` với body `{ "content": "..." }`
6. Nếu thành công:
   - Thêm comment mới vào đầu danh sách (optimistic update)
   - Xóa nội dung textarea
   - Cập nhật số lượng "Comments (N)"
7. Nếu thất bại: toast "Unable to send comment" (ERR-DTL-006)

**Ràng buộc:**
- SH-05-01: Nội dung PHẢI không rỗng và không chỉ chứa khoảng trắng
- SH-05-02: Nội dung PHẢI ≤ 1000 ký tự
- SH-05-03: Nút "Send" PHẢI bị disabled khi nội dung rỗng hoặc skill không `APPROVED`
- SH-05-04: Chỉ user đã đăng nhập mới được bình luận
- SH-05-05: Nội dung comment PHẢI được escape khi render để phòng XSS
- SH-05-06: Form viết bình luận PHẢI hiển thị counter "X/1000" dưới textarea, màu đỏ khi ≥ 900 ký tự

---

### 3.6. FR-06: Tải xuống skill

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-06 |
| **Tên** | Tải xuống file skill |
| **Ưu tiên** | Trung bình |
| **Trigger** | Click nút "Download" trong footer |

**Mô tả:** Người dùng có thể tải xuống file skill từ popup. Mỗi file có nút "Download" riêng; nút "Download All" tải tất cả file dưới dạng zip.

**Quy tắc phân quyền download:**
| Người | Trạng thái được phép |
|-------|----------------------|
| Creator | Mọi trạng thái |
| Admin | `PENDING_APPROVAL` hoặc `APPROVED` |
| User khác | Chỉ `APPROVED` |

**Luồng chính:**
1. User click nút "Download" (từng file) hoặc "Download All"
2. Frontend gọi `GET /api/v1/skills/{id}/files/{fileId}/download` (từng file) hoặc `GET /api/v1/skills/{id}/files/download-all` (zip)
3. Browser tự động download file

**Luồng ngoại lệ:**
- Nếu skill không có file (404) → toast "Skill file does not exist" (ERR-DTL-010)
- Nếu không đủ phân quyền (403) → toast "This skill cannot be downloaded in its current status." (ERR-DTL-011)

---

### 3.7. FR-07: Đóng popup

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-07 |
| **Tên** | Đóng Detail Popup |
| **Ưu tiên** | Cao |
| **Trigger** | Click nút "Close", click overlay, hoặc nhấn phím Esc |

**Mô tả:** Đóng popup và reset toàn bộ state.

**Hành vi:**
- SH-07-01: Popup PHẢI đóng khi click nút "Close"
- SH-07-02: Popup PHẢI đóng khi click vào vùng overlay (nền tối)
- SH-07-03: Popup PHẢI đóng khi nhấn phím Esc
- SH-07-04: Hệ thống PHẢI reset toàn bộ data cache của popup khi đóng

---

### 3.8. FR-08: Submit for Review

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-08 |
| **Tên** | Gửi yêu cầu phê duyệt |
| **Ưu tiên** | Cao |
| **Trigger** | Click "Submit for Review" trong footer (khi DRAFT hoặc REJECTED) |

**Mô tả:** Người tạo gửi skill để admin phê duyệt. Chuyển `approval_status` từ `DRAFT`/`REJECTED` → `PENDING_APPROVAL`.

**Luồng chính:**
1. User click "Submit for Review"
2. Frontend gọi `PUT /api/v1/skills/{id}/status` Body: `{ "approvalStatus": "PENDING_APPROVAL" }`
3. Nếu thành công:
   - Cập nhật badge status → "Pending Approval"
   - Cập nhật nút footer
   - Toast "Review request submitted successfully" (ERR-DTL-012)
4. Nếu thất bại: toast "Unable to submit review request" (ERR-DTL-013)

**Phân quyền:** Người tạo (chỉ skill của mình), Admin (bất kỳ skill nào)

---

### 3.8.1. FR-08.1: Rút lại skill (Withdraw)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-08.1 |
| **Tên** | Rút lại skill đang chờ duyệt |
| **Ưu tiên** | Cao |
| **Trigger** | Click "Withdraw" trong footer (khi PENDING_APPROVAL) |

**Mô tả:** Người tạo rút lại skill đang chờ duyệt, quay về trạng thái DRAFT.

**Luồng chính:**
1. User click "Withdraw"
2. Hiện dialog xác nhận: "Are you sure you want to withdraw this skill? The skill will return to Draft status." (ERR-DTL-014)
3. Nếu xác nhận → gọi `PUT /api/v1/skills/{id}/status` Body: `{ "approvalStatus": "DRAFT" }`
4. Nếu thành công:
   - Cập nhật badge status → "Draft"
   - Cập nhật nút footer (hiện "Edit" + "Submit for Review")
   - Toast "Skill withdrawn successfully" (ERR-DTL-015)
5. Nếu thất bại: toast "Unable to withdraw skill" (ERR-DTL-016)

**Phân quyền:** Chỉ người tạo (chỉ skill của mình)

---

### 3.9. FR-09: Approve / Reject (Admin)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-09 |
| **Tên** | Phê duyệt / Từ chối skill |
| **Ưu tiên** | Cao |
| **Trigger** | Click "Approve" hoặc "Reject" trong footer (khi PENDING_APPROVAL) |

**Mô tả:** Admin phê duyệt hoặc từ chối skill đang chờ duyệt.

**Approve:**
1. Admin click "Approve"
2. Frontend gọi `PUT /api/v1/skills/{id}/status` Body: `{ "approvalStatus": "APPROVED" }`
3. Nếu thành công: refresh popup, toast "Skill approved successfully" (ERR-DTL-017), gửi notification cho creator
4. Nếu thất bại: toast "Unable to approve skill" (ERR-DTL-018)

**Reject:**
1. Admin click "Reject"
2. Hiện dialog nhập lý do từ chối (required, max 500 ký tự)
3. Admin nhập lý do → click "Confirm"
4. Frontend gọi `PUT /api/v1/skills/{id}/status` Body: `{ "approvalStatus": "REJECTED", "rejectionReason": "..." }`
5. Nếu thành công: refresh popup, toast "Skill rejected successfully" (ERR-DTL-019), gửi notification cho creator
6. Nếu thất bại: toast "Unable to reject skill" (ERR-DTL-020)

**Phân quyền:** Chỉ admin (chỉ khi `PENDING_APPROVAL`)

---

### 3.10. FR-10: Delete Skill (Soft Delete)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-10 |
| **Tên** | Xóa skill (soft delete) |
| **Ưu tiên** | Trung bình |
| **Trigger** | Click "Delete" trong footer |

**Mô tả:** Xóa skill khỏi grid bằng cách gán `deleted_YN = 'Y'`. Không thể khôi phục.

**Delete:**
1. User/Admin click "Delete"
2. Hiện dialog xác nhận: "Are you sure you want to delete this skill? The skill will be hidden from the list." (ERR-DTL-021)
3. Nếu xác nhận → gọi `DELETE /api/v1/skills` Body: `{ "ids": [skillId] }`
4. Nếu thành công: đóng popup, refresh grid, toast "Skill deleted successfully" (ERR-DTL-022). Rating và comment được giữ nguyên.
5. Nếu thất bại: toast "Unable to delete skill" (ERR-DTL-023)

**Phân quyền:** Người tạo (chỉ skill của mình), Admin (bất kỳ skill nào)

---

### 3.11. FR-11: Auto-refresh khi tab được focus lại

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-11 |
| **Tên** | Auto-refresh khi tab được focus lại |
| **Ưu tiên** | Cao |
| **Trigger** | Browser tab chứa Detail popup được focus lại (visibility change event) |

**Mô tả:** Khi user quay lại tab browser chứa Detail popup, hệ thống tự động refresh toàn bộ data để đảm bảo trạng thái mới nhất.

**Hành vi:**
- SH-11-01: Khi browser tab được focus lại → gọi `GET /api/v1/skills/{id}` để refresh data
- SH-11-02: Cập nhật badge status, nút footer, banner, lý do reject
- SH-11-03: Không đóng popup, không làm mất tab đang active
- SH-11-04: Không polling liên tục — chỉ refresh khi user quay lại tab
- SH-11-05: Nếu API trả về 404 → đóng popup, toast "Skill has been deleted" (ERR-DTL-002)

---

### 3.12. FR-12: Update Version (v1.1)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-12 |
| **Tên** | Cập nhật phiên bản skill (Update Version) |
| **Ưu tiên** | Cao |
| **Trigger** | Click "Update Version" trong footer (chỉ khi `APPROVED` + creator/admin + chưa có version pending) |
| **API** | `POST /api/v1/skills/{id}/versions` (multipart) |

**Mô tả:** Skill đã APPROVED muốn thay đổi nội dung (metadata + file) phải đi qua **version update** — được review như skill mới, version cũ vẫn live trong thời gian chờ duyệt.

**Quy tắc:**
- SH-12-01: Nút "Update Version" chỉ hiện khi skill `APPROVED` + (creator hoặc admin) + `!hasPendingVersion`
- SH-12-02: Update Version cho phép sửa **TOÀN BỘ**: tên (được đổi — đi qua review), scope, category, stage, description, hashtag, file
- SH-12-03: **Bắt buộc upload ≥ 1 file mới** — nếu không → lỗi "At least one new file is required to update the version." (`SKILL_VERSION_FILE_REQUIRED`)
- SH-12-04: File hiện tại: mỗi file có nút **Delete** — click → tên file **gạch ngang + mờ** (dấu hiệu sẽ bị xóa trong version mới), nút đổi thành **Undo** để bỏ chọn
- SH-12-05: File bị "Delete" vẫn tồn tại trên disk — version cũ (snapshot) vẫn download được
- SH-12-06: Submit → skill chuyển `PENDING_APPROVAL` với `pending_metadata` (version update pending)
- SH-12-07: Trong thời gian pending: skill **vẫn hiển thị cho user khác như APPROVED** (version cũ live — xem, download, rate, comment, subscribe đều hoạt động); chỉ creator/admin thấy "Pending Approval" + khu review
- SH-12-08: Banner trong popup: "This update will be reviewed before going live. The current version stays available until it is approved."

**Update Version Popup** (layout giống Add Skill Popup):
- Skill name* (counter /100), Visibility scope*, Category* (chip multi), Process Stage* (chip multi), Description (Rich Text), Hashtag
- Files: list file hiện tại (nút Delete/Undo + gạch ngang) + upload zone file mới (drag & drop, multi, white-list extension, ≤10MB)
- [Submit for Review] → `POST /versions` → toast "Version update submitted for review." → refresh Detail + Grid

---

### 3.13. FR-13: Cancel Version Update (v1.1)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-13 |
| **Tên** | Hủy version update đang chờ duyệt |
| **Ưu tiên** | Cao |
| **Trigger** | Click "Cancel Version Update" trong footer (khi `PENDING_APPROVAL` + `hasPendingVersion` + creator/admin) |
| **API** | `POST /api/v1/skills/{id}/versions/cancel` |

**Mô tả:** Creator (hoặc admin) hủy version update đang chờ duyệt — skill quay về `APPROVED`, version cũ giữ nguyên, files pending bị xóa (disk + row).

**Hành vi:**
- SH-13-01: Dialog xác nhận trước khi hủy
- SH-13-02: Thành công → toast "Version update cancelled." → refresh Detail + Grid
- SH-13-03: Khác với Withdraw (FR-08.1): Withdraw chỉ cho skill PENDING lần đầu (về DRAFT); Cancel Version Update chỉ cho skill đang có version pending (về APPROVED)

---

### 3.14. FR-14: Versions Tab (v1.1)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-14 |
| **Tên** | Xem lịch sử phiên bản |
| **Ưu tiên** | Trung bình |
| **Trigger** | Click tab "Versions" trong Detail Popup |
| **API** | `GET /api/v1/skills/{id}/versions` |

**Mô tả:** Tab "Versions" hiển thị các phiên bản đã được thay thế (snapshot khi version update được approve), mới nhất trước.

**Hành vi:**
- SH-14-01: Dòng đầu: "Version N (current)" — từ skill detail (version hiện tại không có snapshot row)
- SH-14-02: Mỗi version cũ hiển thị: số version, tên, mô tả, scope, hashtags, creator, ngày tạo, list file (tên + size)
- SH-14-03: Mỗi file có nút **Download**; mỗi version có nút **Download All** (zip)
- SH-14-04: Load lazy — chỉ gọi API khi user mở tab lần đầu
- SH-14-05: **Tự refresh** sau approve / reject / cancel version update và sau edit — không cần đóng/mở lại popup
- SH-14-06: Skill chưa từng có version update approve → tab chỉ hiện "Version 1 (current)"

---

### 3.15. FR-15: Pending Version Review Section (v1.1)

| Thuộc tính | Giá trị |
|------------|---------|
| **ID** | FR-15 |
| **Tên** | Xem version đang chờ duyệt (review) |
| **Ưu tiên** | Cao |
| **Trigger** | Skill `hasPendingVersion` + viewer là creator/admin |
| **API** | `GET /api/v1/skills/{id}` (trường `pendingVersion` trong detail) |

**Mô tả:** Khi có version update đang chờ duyệt, creator/admin thấy section **"Pending Version N+1 — under review"** (badge "Under Review") trong Detail Popup, hiển thị **đề xuất thay đổi** để review. Nội dung chính (header, files, description) vẫn là **version hiện tại (live)**.

**Section hiển thị:**
- Tên đề xuất, scope, categories, process stages, hashtags, description mới
- **New files**: list file mới (tên + size) + nút Download từng file + nút **Download All** (zip) — chỉ creator/admin
- **Files to be removed**: list file sẽ bị xóa (gạch ngang)

**API pending files:**
```text
GET /api/v1/skills/{id}/pending-versions/files/{fileId}/download
GET /api/v1/skills/{id}/pending-versions/files/download-all
```

**Hành vi:**
- SH-15-01: User khác (không creator/admin) **không thấy** section này — họ chỉ thấy version live như bình thường
- SH-15-02: Banner: "A version update is pending review. The content above shows the current (live) version — the proposed changes are in the 'Pending Version' section."
- SH-15-03: Sau approve → section biến mất, Versions tab tự hiện version mới được snapshot
- SH-15-04: Sau reject (version update) → skill về APPROVED, hiện alert "Last version update was rejected: {reason}"
- SH-15-05: Approve/reject version update dùng chung nút Approve/Reject trong footer (workflow tự phân nhánh theo `pending_metadata`)

---

## 4. Yêu cầu phi chức năng

### 4.1. Hiệu năng

| ID | Yêu cầu |
|----|---------|
| NFR-PERF-01 | Popup PHẢI hiển thị trong vòng **500ms** sau khi double-click (sau cache warm) |
| NFR-PERF-02 | API lấy chi tiết skill PHẢI trả về trong **200ms** |
| NFR-PERF-03 | Danh sách comment PHẢI hỗ trợ pagination, max 50 comment/trang |

### 4.2. Bảo mật

| ID | Yêu cầu |
|----|---------|
| NFR-SEC-01 | Tất cả API PHẢI yêu cầu JWT trong `Authorization` header |
| NFR-SEC-02 | Nội dung comment và rejection_reason PHẢI được escape khi render (phòng XSS) |
| NFR-SEC-03 | Input PHẢI được validate ở cả frontend và backend |
| NFR-SEC-04 | Rate limiting: 10 đánh giá/phút/user, 30 comment/phút/user |
| NFR-SEC-05 | Chỉ skill `APPROVED` mới cho phép đánh giá, bình luận; download theo phân quyền (creator: mọi trạng thái, admin: PENDING_APPROVAL+, user khác: APPROVED) |
| NFR-SEC-06 | Approve/Reject chỉ admin được thực hiện |
| NFR-SEC-07 | Delete chỉ creator hoặc admin |

### 4.3. Khả dụng

| ID | Yêu cầu |
|----|---------|
| NFR-UX-01 | Popup PHẢI có animation mở (fade in + slide up) |
| NFR-UX-02 | Popup PHẢI responsive trên màn hình ≥ 768px |
| NFR-UX-03 | Toast thông báo PHẢI tự ẩn sau 3 giây |
| NFR-UX-04 | Loading state PHẢI hiển thị khi đang tải dữ liệu |
| NFR-UX-05 | Banner cảnh báo PHẢI hiển thị khi skill `PENDING_APPROVAL` |
| NFR-UX-06 | Khi nội dung vượt quá chiều cao popup, `.o-modal__body` PHẢI scroll nội bộ, footer luôn hiển thị trong popup |

### 4.4. Tương thích

| ID | Yêu cầu |
|----|---------|
| NFR-COMP-01 | Hỗ trợ Chrome, Edge, Firefox phiên bản hiện tại và trước đó 1 phiên bản |
| NFR-COMP-02 | UI theo chuẩn Design System (tham chiếu folder `../design-system/`) |

---

## 5. API Specification

### 5.1. Lấy chi tiết skill

```
GET /api/v1/skills/{id}
Authorization: Bearer <JWT>
```

**Response 200** (envelope `{ success, data }`):
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Auto Code Review",
    "description": "Skill tự động review code...\n\nĐặc điểm nổi bật:\n- Phát hiện 50+ loại code smell",
    "status": "APPROVED",
    "scope": "PUBLIC",
    "hashtags": ["code-review", "java", "automation"],
    "rejectionReason": null,
    "files": [
      { "id": 11, "fileName": "skill.zip", "fileSize": 204800, "createdAt": "2026-09-07T00:00:00" }
    ],
    "viewCount": 129,
    "version": 3,
    "creatorId": 5,
    "creatorName": "Nguyễn Văn A",
    "createdAt": "2026-09-07T00:00:00",
    "updatedAt": "2026-09-08T10:00:00",
    "avgRating": 4.5,
    "ratingCount": 12,
    "isCreator": true,
    "isAdmin": false,
    "canEdit": true,
    "subscribed": false
  }
}
```

> `id` là **BIGINT**. `status`/`scope` = approval status + public scope.
> `files` = danh sách file đính kèm (nhiều file, không phải 1 file).
> `isCreator`: user hiện tại là người tạo skill.
> `isAdmin`: user hiện tại có role admin.
> `canEdit`: `(isCreator || isAdmin) && !deleted && status != PENDING_APPROVAL`.
> `version`: Số phiên bản hiện tại của skill. Backend tự động tăng mỗi lần skill được lưu thành công.
> `avgRating`/`ratingCount`: điểm TB + số lượng rating (null/0 khi chưa có rating).
> `subscribed`: user hiện tại đã subscribe skill này chưa.

**Response 404:**
```json
{ "success": false, "errorCode": "SKILL_NOT_FOUND", "message": "Skill not found" }
```

---

### 5.2. Lấy điểm sao trung bình

```
GET /api/v1/skills/{id}/rating
Authorization: Bearer <JWT>
```

**Response 200:**
```json
{ "success": true, "data": { "averageRating": 4.5, "totalRatings": 12 } }
```

**Response 200 (chưa có đánh giá):**
```json
{ "success": true, "data": { "averageRating": 0.0, "totalRatings": 0 } }
```

---

### 5.3. Lấy đánh giá của user hiện tại

```
GET /api/v1/skills/{id}/rating/me
Authorization: Bearer <JWT>
```

**Response 200 (đã đánh giá):**
```json
{ "success": true, "data": 4.5 }
```

**Response 200 (chưa đánh giá):**
```json
{ "success": true, "data": null }
```

---

### 5.4. Tạo / Cập nhật đánh giá sao

```
PUT /api/v1/skills/{id}/rating
Authorization: Bearer <JWT>
Content-Type: application/json

{ "rating": 4.5 }
```

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| Rate limit (10 lần/phút/user) | SKILL_RATE_LIMITED | "Too many requests. Please try again later." |
| `rating` từ 1.0 đến 5.0, step 0.5 | SKILL_INVALID_RATING | "Rating must be between 1 and 5 in steps of 0.5." |
| Skill không APPROVED | SKILL_NOT_APPROVED | "Only published skills can be rated" |

**Response 200:** trả về điểm TB cập nhật (cùng cấu trúc §5.2):
```json
{ "success": true, "data": { "averageRating": 4.5, "totalRatings": 12 } }
```

---

### 5.5. Lấy danh sách bình luận

```
GET /api/v1/skills/{id}/comments?page={page}&size={size}
Authorization: Bearer <JWT>
```

| Query Param | Required | Default | Constraint |
|-------------|----------|---------|------------|
| `page` | Không | 1 | ≥ 1 |
| `size` | Không | 10 | 1–50 |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "totalCount": 3,
    "page": 1,
    "size": 10,
    "items": [
      {
        "id": 101,
        "userId": 8,
        "userName": "Nguyễn Văn B",
        "content": "Skill rất hữu ích cho team phát triển.",
        "createdAt": "2026-09-07T14:30:00",
        "canDelete": false
      }
    ]
  }
}
```

> `canDelete`: user hiện tại có quyền xóa comment này (creator của comment hoặc admin).

---

### 5.6. Thêm bình luận

```
POST /api/v1/skills/{id}/comments
Authorization: Bearer <JWT>
Content-Type: application/json

{ "content": "Skill rất hữu ích cho team phát triển." }
```

**Validation:**
| Rule | Error Code | Message |
|------|-----------|---------|
| `content` không rỗng | SKILL_COMMENT_EMPTY | "Comment content cannot be empty" |
| `content` ≤ 1000 ký tự | SKILL_COMMENT_TOO_LONG | "Comment is at most 1000 characters" |
| Skill không APPROVED | SKILL_NOT_APPROVED | "Only published skills can be commented" |

**Response 201:** *(empty body)*

---

### 5.7. Tải xuống skill

```
GET /api/v1/skills/{id}/files/download-all
Authorization: Bearer <JWT>
```

**Response 200:**
- `Content-Type`: `application/octet-stream`
- `Content-Disposition`: `attachment; filename="skill-name.zip"`

> Tải **từng file**: `GET /api/v1/skills/{id}/files/{fileId}/download`.

**Quy tắc phân quyền download:**
| Người | Trạng thái được phép |
|-------|----------------------|
| Creator | Mọi trạng thái |
| Admin | `PENDING_APPROVAL` hoặc `APPROVED` |
| User khác | Chỉ `APPROVED` |

**Response 403:**
```json
{ "success": false, "errorCode": "SKILL_NOT_APPROVED", "message": "This skill cannot be downloaded in its current status." }
```

---

### 5.7.1. Tăng view_count

```
POST /api/v1/skills/{id}/view
Authorization: Bearer <JWT>
```

**Mô tả:** Tăng `view_count` của skill. Gọi async sau khi popup render xong, không block UI.

**Idempotent:** 1 user chỉ đếm 1 lần/skill trong cửa sổ **15 phút** (cấu hình `app.skill.view-window-minutes`, mặc định 15).

**Response 200:**
```json
{ "success": true, "data": { "viewCount": 129 } }
```

**Response 404:**
```json
{ "success": false, "errorCode": "SKILL_NOT_FOUND", "message": "Skill not found" }
```

---

### 5.8. Cập nhật trạng thái skill

```
PUT /api/v1/skills/{id}/status
Authorization: Bearer <JWT>
Content-Type: application/json

{ "approvalStatus": "PENDING_APPROVAL" }
```

**Request Body:**
| Trường | Required | Ghi chú |
|--------|----------|---------|
| `approvalStatus` | Có | `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `DRAFT` |
| `rejectionReason` | Chỉ khi `REJECTED` | Lý do từ chối, max 500 ký tự |

**Phân quyền chuyển trạng thái:**
| Chuyển đổi | Người tạo | Admin |
|------------|-----------|-------|
| `DRAFT` → `PENDING_APPROVAL` | Chỉ skill của mình | Bất kỳ |
| `REJECTED` → `PENDING_APPROVAL` | Chỉ skill của mình | Bất kỳ |
| `PENDING_APPROVAL` → `APPROVED` | Không | Chỉ admin |
| `PENDING_APPROVAL` → `REJECTED` | Không | Chỉ admin |
| `PENDING_APPROVAL` → `DRAFT` (Withdraw) | Chỉ skill của mình | Không |

> Khi chuyển `REJECTED` → `PENDING_APPROVAL`, backend PHẢI set `rejection_reason = NULL`.

**Response 204:** *(empty body)*

**Response 400:**
```json
{ "success": false, "errorCode": "SKILL_INVALID_TRANSITION", "message": "This status change is not allowed." }
```

---

## 6. Database Schema

### 6.1. Bảng `skills_master`

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
```

> **File đính kèm** nằm trong bảng `skill_files` riêng (1 skill : N file) — không còn các cột `file_name`/`file_path`/`file_size` trên `skills_master`:
>
> ```sql
> CREATE TABLE skill_files (
>     id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
>     skill_id   BIGINT NOT NULL REFERENCES skills_master(id),
>     file_name  VARCHAR(255) NOT NULL,
>     file_path  VARCHAR(500) NOT NULL,
>     file_size  BIGINT,
>     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
> );
> ```
>
> Tên skill unique **theo creator** (case-insensitive, chưa xóa): `name` + `created_by` + `deleted_yn = 'N'`.

### 6.2. Bảng `skill_ratings`

```sql
CREATE TABLE skill_ratings (
    id         SERIAL PRIMARY KEY,
    skill_id   BIGINT NOT NULL REFERENCES skills_master(id),
    user_id    BIGINT NOT NULL REFERENCES "user"(id),
    rating     NUMERIC(2,1) NOT NULL CHECK (rating BETWEEN 1.0 AND 5.0),
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (skill_id, user_id)
);
```

### 6.3. Bảng `skill_comments`

```sql
CREATE TABLE skill_comments (
    id         SERIAL PRIMARY KEY,
    skill_id   BIGINT NOT NULL REFERENCES skills_master(id),
    user_id    BIGINT NOT NULL REFERENCES "user"(id),
    content    TEXT NOT NULL CHECK (char_length(content) <= 1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP  -- Soft delete: NULL = chưa xóa
);
```

### 6.4. Bảng `skill_status_history` (Audit trail)

```sql
CREATE TABLE skill_status_history (
    id                SERIAL PRIMARY KEY,
    skill_id          BIGINT NOT NULL REFERENCES skills_master(id),
    from_status       VARCHAR(20),
    to_status         VARCHAR(20) NOT NULL,
    changed_by        BIGINT NOT NULL REFERENCES "user"(id),
    rejection_reason  TEXT CHECK (char_length(rejection_reason) <= 500),
    changed_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

> `skill_status_history` ghi lại mọi **chuyển trạng thái**: ai, khi nào, từ → đến, lý do reject (nếu có).
> Đây là audit trail **cấp độ trạng thái** — phục vụ trace workflow phê duyệt.
>
> **Phân biệt với `SKILL_HISTORY` (List Mngt §21.2):**
> - `skill_status_history` → chỉ lưu chuyển trạng thái (từ → đến), không lưu snapshot dữ liệu
> - `SKILL_HISTORY` → lưu **toàn bộ snapshot** của skill tại mỗi thời điểm (name, description, file, version, scope, status)
> - 2 bảng phục vụ 2 mục đích khác nhau: audit workflow vs versioning đầy đủ
> - UI xem `SKILL_HISTORY` (Tab "Lịch sử") sẽ được đặc tả trong tài liệu **Version Management** riêng (Phase 2)

---

## 7. Quy tắc hiển thị sao

### 7.1. Điểm sao trung bình (header)

| Điểm TB | Sao visual | Số hiển thị |
|---------|-------------|-------------|
| 5.0 | ★★★★★ | ★ 5.0/5 (12) |
| 4.5 | ★★★★☆ (sao thứ 5: nửa) | ★ 4.5/5 (12) |
| 4.2 | ★★★★☆ (sao thứ 5: 40%) | ★ 4.2/5 (12) |
| 4.0 | ★★★★☆ | ★ 4.0/5 (12) |
| 3.5 | ★★★☆☆ (sao thứ 4: nửa) | ★ 3.5/5 (12) |
| 3.0 | ★★★☆☆ | ★ 3.0/5 (12) |
| 2.5 | ★★☆☆☆ (sao thứ 3: nửa) | ★ 2.5/5 (12) |
| 2.0 | ★★☆☆☆ | ★ 2.0/5 (12) |
| 1.5 | ★☆☆☆☆ (sao thứ 2: nửa) | ★ 1.5/5 (12) |
| 1.0 | ★☆☆☆☆ | ★ 1.0/5 (12) |
| null | — | "No rating" |

> **Sao visual** làm tròn đến 0.5 gần nhất. **Số điểm** hiển thị chính xác 1 chữ số thập phân.
> CSS partial star: `width: ${(point % 1) * 100}%`.

### 7.2. Đánh giá của user (tab Comments)

- Step 0.5: hover vào giữa sao = half sao
- Click vào sao = gán điểm tương ứng
- Đã đánh giá → hiển thị sao đã chọn (editable để cập nhật)
- **Disabled** khi `approval_status` khác `APPROVED` (DRAFT, PENDING_APPROVAL, REJECTED)

---

## 8. Xử lý lỗi & Edge Case

| Tình huống | Hành vi |
|------------|---------|
| Skill bị xóa khi popup đang mở | Popup đóng, toast "Skill has been deleted" (ERR-DTL-002) |
| User chưa đăng nhập | Redirect về trang login |
| Mất kết nối mạng | Skeleton/loading, retry 1 lần, sau đó toast lỗi |
| Nội dung mô tả quá dài | Scroll trong tab "Information" |
| Comment quá nhiều | Pagination, mặc định 10/trang |
| User đánh giá rồi đánh giá lại | Cập nhật điểm cũ (UPSERT), tính lại điểm TB |
| File skill không tồn tại | Toast "Skill file does not exist" (ERR-DTL-010) |
| Đánh giá skill không APPROVED | Toast "Only published skills can be rated" (ERR-DTL-003) |
| Bình luận skill không APPROVED | Toast "Only published skills can be commented" (ERR-DTL-005) |
| Chuyển trạng thái không hợp lệ | Toast "Unable to change status" (ERR-DTL-024) |

---

## 9. Checklist Kiểm thử (QA)

| ID | Mô tả | Loại | Kết quả |
|----|-------|------|---------|
| TC-01 | Mở popup bằng double-click | Functional | ☐ |
| TC-02 | Hiển thị đúng thông tin skill | Functional | ☐ |
| TC-03 | Hiển thị đúng badge status theo trạng thái | Functional | ☐ |
| TC-04 | Hiển thị đúng visibility scope | Functional | ☐ |
| TC-05 | Chuyển tab "Information" / "Comments" | Functional | ☐ |
| TC-06 | Đánh giá sao thành công (skill APPROVED) | Functional | ☐ |
| TC-07 | Đánh giá sao thất bại (skill REJECTED) | Functional | ☐ |
| TC-08 | Đánh giá sao thất bại (skill DRAFT) | Functional | ☐ |
| TC-09 | Cập nhật đánh giá sao (click lại) | Functional | ☐ |
| TC-10 | Điểm TB được tính lại sau khi đánh giá | Functional | ☐ |
| TC-11 | Gửi bình luận thành công (skill APPROVED) | Functional | ☐ |
| TC-12 | Gửi bình luận thất bại (skill DRAFT) | Functional | ☐ |
| TC-13 | Gửi bình luận thất bại (nội dung rỗng) | Functional | ☐ |
| TC-14 | Gửi bình luận thất bại (quá 1000 ký tự) | Functional | ☐ |
| TC-15 | Pagination bình luận | Functional | ☐ |
| TC-16 | Tải xuống skill thành công (user khác, APPROVED) | Functional | ☐ |
| TC-17 | Tải xuống skill thất bại (user khác, DRAFT → 403) | Functional | ☐ |
| TC-17a | Creator tải file skill DRAFT thành công | Functional | ☐ |
| TC-17b | Admin tải file skill PENDING_APPROVAL thành công | Functional | ☐ |
| TC-17c | Download All → file zip chứa toàn bộ file | Functional | ☐ |
| TC-18 | Submit for Review (DRAFT → PENDING) | Functional | ☐ |
| TC-19 | Admin Approve (PENDING → APPROVED) | Functional | ☐ |
| TC-20 | Admin Reject với lý do (PENDING → REJECTED) | Functional | ☐ |
| TC-21 | Hiển thị lý do từ chối (REJECTED) | Functional | ☐ |
| TC-22 | Delete skill (soft delete) | Functional | ☐ |
| TC-23 | Delete có dialog xác nhận | UI | ☐ |
| TC-24 | Banner cảnh báo khi PENDING_APPROVAL | UI | ☐ |
| TC-25 | Scroll tab "Information" khi mô tả dài | UI | ☐ |
| TC-26 | Scroll tab "Comments" khi nhiều comment | UI | ☐ |
| TC-27 | Đóng popup bằng nút "Close" | Functional | ☐ |
| TC-28 | Đóng popup bằng click overlay | Functional | ☐ |
| TC-29 | Đóng popup bằng phím Esc | Functional | ☐ |
| TC-30 | Popup hiển thị trong 500ms | Performance | ☐ |
| TC-31 | XSS trong nội dung comment bị escape | Security | ☐ |
| TC-32 | User chưa login không thể đánh giá/bình luận | Security | ☐ |
| TC-33 | Non-admin không thể Approve/Reject | Security | ☐ |
| TC-34 | Delete có dialog xác nhận | UI | ☐ |
| TC-35 | Delete giữ nguyên rating/comment | Functional | ☐ |
| TC-36 | Xóa comment của chính mình | Functional | ☐ |
| TC-37 | Non-owner không thể xóa comment | Security | ☐ |
| TC-38 | User ngoài scope thấy 404 (không 403) | Security | ☐ |
| TC-39 | Notification khi Approve/Reject | Functional | ☐ |
| TC-40 | Withdraw skill (PENDING_APPROVAL → DRAFT) | Functional | ☐ |
| TC-41 | Creator có thể Delete skill của mình | Functional | ☐ |
| TC-42 | Non-creator không thấy nút "Edit"/"Delete" | Security | ☐ |
| TC-43 | Nút "Download" ẩn khi skill không có file | UI | ☐ |
| TC-44 | Xóa comment có dialog xác nhận | UI | ☐ |
| TC-45 | Xóa comment cuối trang → chuyển về trang trước | Functional | ☐ |
| TC-46 | Gửi comment mới → chuyển về trang 1 | Functional | ☐ |
| TC-47 | Điểm sao TB hiển thị chính xác 1 chữ số thập phân | UI | ☐ |
| TC-48 | Submit lại skill bị reject → rejection_reason bị xóa | Functional | ☐ |
| TC-49 | Non-creator vẫn có thể đánh giá sao/bình luận (APPROVED) | Security | ☐ |
| TC-50 | Nút "✕ Delete" chỉ hiện trên comment của chính user hoặc admin | UI | ☐ |
| TC-51 | API trả về isCreator/isAdmin/canEdit flags | Functional | ☐ |
| TC-52 | Dữ liệu comment cache khi chuyển tab, auto-refresh sau 30s | Functional | ☐ |
| TC-53 | Tên skill trùng (case-insensitive) **của cùng user** bị chặn; user khác được dùng tên trùng | Functional | ☐ |
| TC-54 | Comment soft-delete không hiện trong danh sách | Functional | ☐ |
| TC-55 | Pagination overflow: currentPage > totalPages → chuyển về totalPages | Functional | ☐ |
| TC-56 | Creator có thể đánh giá skill của chính mình | Functional | ☐ |
| TC-57 | Nút "Update Version" hiện khi APPROVED + creator/admin + không có pending | Functional | ☐ |
| TC-58 | Nút "Update Version" ẩn khi DRAFT/REJECTED/PENDING_APPROVAL hoặc đang có version pending | Functional | ☐ |
| TC-59 | Update Version không upload file mới → bị chặn (SKILL_VERSION_FILE_REQUIRED) | Functional | ☐ |
| TC-60 | Update Version: click Delete file cũ → tên gạch ngang, nút đổi thành Undo | UI | ☐ |
| TC-61 | Submit Update Version → skill PENDING_APPROVAL, user khác vẫn thấy skill (version cũ live) | Functional | ☐ |
| TC-62 | Section "Pending Version N+1 — under review" hiện cho creator/admin, ẩn với user khác | Functional | ☐ |
| TC-63 | Download file trong Pending Version section (creator/admin) | Functional | ☐ |
| TC-64 | Admin Approve version update → version bump, snapshot version cũ hiện trong Versions tab (không cần mở lại popup) | Functional | ☐ |
| TC-65 | Admin Reject version update → skill về APPROVED, hiện alert "Last version update was rejected" | Functional | ☐ |
| TC-66 | Creator Cancel Version Update → skill về APPROVED, files pending xóa | Functional | ☐ |
| TC-67 | Versions tab: Download file + Download All (zip) của version cũ | Functional | ☐ |
| TC-68 | Skill APPROVED: Edit thường không quản lý file (khu file ẩn), delete file API → 403 SKILL_FILE_LOCKED | Functional | ☐ |
| TC-69 | Edit thường skill APPROVED → version không bump, subscribers vẫn nhận notification | Functional | ☐ |

---

## 9. Version History

| Version | Status | Change |
|---|---|---|
| 2.0 | Updated | (baseline trước lần cập nhật này) |
| 2.1 | Updated | Aligned with implementation: API path là `/api/v1/skills/*` với envelope `{ success, data }`; detail response dùng `status`/`scope` (thay `approvalStatus`/`publicScope`), `files[]` (nhiều file, thay 1 file), `creatorId`+`creatorName` (thay `createdBy` object), thêm `viewCount`, `avgRating`, `ratingCount`, `subscribed`; `rating/me` trả `data: number | null` (không còn 204); `PUT rating` trả về điểm TB cập nhật (frontend không gọi lại GET); comments trả `totalCount/page/size/items[]` với `userId`/`userName`/`canDelete`; `POST comments` trả 204 empty; download tách 2 endpoint `files/{fileId}/download` + `files/download-all` (zip) với phân quyền creator/admin/user; view_count idempotent theo cửa sổ **15 phút** (cấu hình `app.skill.view-window-minutes`, thay 24h); `PUT status` trả 204; DB: `skills_master` không còn cột file (chuyển sang bảng `skill_files` 1:N), thêm cột `version`, tên skill unique theo creator (không unique toàn hệ thống) |
| 2.2 | Updated | **Version Update feature (16/10/2026)**: thêm FR-12 Update Version (chỉ skill APPROVED, bắt buộc ≥1 file mới, Delete button + gạch ngang cho file bị xóa, `POST /skills/{id}/versions`), FR-13 Cancel Version Update (`POST /skills/{id}/versions/cancel`), FR-14 Versions tab (`GET /skills/{id}/versions`, snapshot version cũ + Download/Download All, tự refresh sau approve/reject/cancel), FR-15 Pending Version review section (creator/admin, download files pending). Visibility: skill đang version-update-pending vẫn hiển thị cho user khác như APPROVED (version cũ live — xem/download/rate/comment/subscribe). Edit thường skill APPROVED: không quản lý file (SKILL_FILE_LOCKED), không bump version, vẫn notify subscribers. State machine bổ sung nhánh APPROVED ⇄ PENDING_APPROVAL (version pending). |
