# Skill AI — Detail Popup BA Spec

## 1. Tổng quan

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | Detail Popup — Xem chi tiết Skill AI |
| **Khởi động** | Click vào Skill Name (delay 300ms) hoặc Double-click vào một dòng trên grid Skill List. Double-click ưu tiên — nếu user double-click, hủy delay click đơn, tránh popup nháy |
| **Mục đích** | Hiển thị thông tin chi tiết của skill, cho phép người dùng đánh giá sao, bình luận, tải xuống skill, và quản lý trạng thái phê duyệt |
| **Công nghệ** | Frontend: Vue 3 + Design System (tham chiếu folder `../design-system/`) · Backend: Java (Spring Boot) · Database: PostgreSQL |

---

## 2. Wireframe

### 2.1. Trạng thái APPROVED

```
┌─────────────────────────────────────────────────────┐
│  [Skill name]          ★ 4.5/5 (12)        [✕]     │
│  Status: Approved  🌍 Public    [🔔 Subscribe]    │
├─────────────────────────────────────────────────────┤
│  Creator: Nguyen Van A          Created: 07/09/2026 · Updated: 08/09/2026   │
├─────────────────────────────────────────────────────┤
│  📄 skill.zip (200 KB)                              │
│  #code-review  #java  #automation                  │
├─────────────────────────────────────────────────────┤
│  Tab [Information] [Comment]                        │
│                                                    │
│  === Tab Information ===                              │
│  ┌────────────────────────────────────────────┐    │
│  │ Description:                                     │    │
│  │ Lorem ipsum dolor sit amet...              │    │
│  │ (scroll if content is long)                │    │
│  └────────────────────────────────────────────┘    │
│                                                    │
│  === Tab Comment ===                              │
│  ┌────────────────────────────────────────────┐    │
│  │ ★ Your vote:  ★★★★★  (5 stars)      │    │
│  │ ─────────────────────────────────────────  │    │
│  │ 💬 Comments (3):                          │    │
│  │ ┌──────────────────────────────────────┐  │    │
│  │ │ Nguyen Van B  — 07/09/2026 14:30    │  │    │
│  │ │ Very useful skill for the team...    │  │    │
│  │ └──────────────────────────────────────┘  │    │
│  │ [Write a comment...] [Send]               │    │
│  │ 0/1000 characters                         │    │
│  │ [ < 1 2 3 ... N > ]                       │    │
│  │ (scroll if many comments)                 │    │
│  └────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────┤
│                        [Download]  [Close]          │
└─────────────────────────────────────────────────────┘
```

### 2.2. Trạng thái REJECTED

```
┌─────────────────────────────────────────────────────┐
│  [Skill name]                        [✕]            │
│  Status: Rejected  🔒 Private                 │
├─────────────────────────────────────────────────────┤
│  Creator: Nguyen Van A          Created: 07/09/2026 · Updated: 08/09/2026   │
├─────────────────────────────────────────────────────┤
│  📄 skill.zip (200 KB)                              │
│  #api  #wrapper                                  │
├─────────────────────────────────────────────────────┤
│  Tab [Information] [Comment]                        │
│  ...                                                │
├─────────────────────────────────────────────────────┤
│  ❌ Rejection reason: Skill violates policy...      │
├─────────────────────────────────────────────────────┤
│  [Submit for Review]           [Close]               │
└─────────────────────────────────────────────────────┘
```

---

## 3. Cấu trúc Popup

| Vùng | Nội dung |
|------|----------|
| **Header — Dòng 1** | Tên skill (trái), Điểm sao trung bình (phải), Nút đóng ✕ (góc trên phải, cùng dòng với tên skill) |
| **Header — Dòng 2** | Status badge + Visibility Scope badge (cạnh nhau, bên trái), Nút Subscribe 🔔 (phải, **mép phải căn thẳng hàng với nút đóng ✕**) |
| **Banner (nếu PENDING_APPROVAL)** | Thông báo trạng thái đặc biệt |
| **Meta** | Người tạo (trái) \| Ngày tạo + Ngày cập nhật (phải) — cùng 1 dòng, căn 2 đầu |
| **File Info** | Hiển thị tên file + kích thước (nếu skill có file) |
| **Hashtag** | Hiển thị hashtag dạng pill/badge (nếu skill có hashtag). Click hashtag → filter grid theo hashtag |
| **Tab Bar** | 3 tab: "Information" (mặc định active), "Versions", "Comments" |
| **Tab "Information"** | Mô tả skill |
| **Tab "Versions"** | Lịch sử phiên bản (snapshot) + version hiện tại (v1.1) |
| **Tab "Comments"** | Đánh giá sao + Danh sách bình luận + Form viết bình luận |
| **Section Lý do từ chối (nếu REJECTED)** | Hiển thị lý do admin từ chối |
| **Footer** | Nút hành động (Download / Submit / Delete) + Nút Close |

---

## 4. Mô hình trạng thái

### 4.1. `approval_status` (Chu kỳ sống & Phê duyệt)

| Giá trị | Tên hiển thị | Badge màu | Mô tả | Ai thấy? |
|---------|--------------|-----------|-------|----------|
| `DRAFT` | Draft | Xám (#999) | Đang soạn thảo, chưa hoàn thiện | Chỉ người tạo |
| `PENDING_APPROVAL` | Pending Approval | Vàng (#FAAD14) | Đã submit cho admin xem xét | Người tạo + Admin |
| `APPROVED` | Approved | Xanh (#52C41A) | Đã được duyệt, chính thức hoạt động | Theo `public_scope` |
| `REJECTED` | Rejected | Đỏ (#FF4D4F) | Admin từ chối (có lý do) | Người tạo + Admin |

> **Lưu ý:** Skill bị xóa (soft delete) dùng trường `deleted_YN` trong DDL — không phải là trạng thái riêng.

### 4.2. State Machine

```
DRAFT ──(Submit)──► PENDING_APPROVAL ──(Approve)──► APPROVED
  ▲  │                          │
  │  └──(Withdraw)──────────────┘
  │
  └──(Submit)◄── REJECTED ◄──(Reject)
```

> - **Withdraw**: creator rút lại skill đang chờ duyệt (PENDING_APPROVAL → DRAFT)
> - **Delete (soft)**: gán `deleted_YN = 'Y'` — skill ẩn khỏi grid, không khôi phục

### 4.3. `public_scope` (Phạm vi hiển thị)

| Giá trị | Tên hiển thị | Icon | Phạm vi hiển thị |
|---------|--------------|------|------------------|
| `PRIVATE` | Riêng tư | 🔒 | Chỉ người tạo |
| `PART` | Theo Part | 🏛️ | Các user cùng Part |
| `GROUP` | Theo Group | 🏢 | Các user cùng Group |
| `PUBLIC` | Công khai | 🌍 | Tất cả user trong hệ thống |

> `public_scope` ưu tiên hơn role. Skill APPROVED + PRIVATE → chỉ creator thấy, bất kể role.

---

## 5. Chi tiết từng thành phần

### 5.1. Header — Dòng 1

| Trường | Nguồn dữ liệu | Kiểu hiển thị | Ghi chú |
|--------|---------------|---------------|---------|
| Tên skill | `skills_master.name` | Text, font bold, 20px | Truncate nếu quá dài, tooltip đầy đủ khi hover |
| Điểm sao TB | `AVG(skill_ratings.rating)` | Star icon + số | Hiển thị định dạng `★ 4.5/5 (12)` — sao visual + điểm + số người đánh giá. Nếu chưa có đánh giá → "No rating" |
| Nút đóng ✕ | — | Icon button | Đặt **góc trên phải** của popup, cùng dòng với tên skill. Click → đóng popup |

**Công thức tính điểm TB:**
```
averageRating = ROUND(SUM(rating) / COUNT(rating), 1)
FROM skill_ratings WHERE skill_id = :id
```
Làm tròn 1 chữ số thập phân (vd: 4.5, 3.8).

### 5.2. Header — Dòng 2

| Trường | Nguồn dữ liệu | Kiểu hiển thị | Ghi chú |
|--------|---------------|---------------|---------|
| Status | `skills_master.approval_status` | Badge pill | Màu theo bảng §4.1 |
| Public Scope | `skills_master.public_scope` | Icon + text | Icon + tên (vd: 🌍 Public). Theo bảng §4.3. Hiển thị **cạnh Status badge, cùng bên trái** |
| Nút Subscribe | `skill_subscriptions` (user_id) | Button 🔔 + text | Text "Subscribe" (chưa subscribe) / "Subscribed" (đã subscribe). Icon chuông: outline (chưa) / đổ đầy + màu primary (đã). **Chỉ hiển thị khi `APPROVED` và user không phải creator.** Đặt bên phải, **mép phải căn thẳng hàng với nút đóng ✕** |

**Quy tắc nút Subscribe:**

| Điều kiện | Hiển thị |
|-----------|----------|
| `APPROVED` + non-creator | Hiện nút |
| `APPROVED` + creator | **Ẩn** (không subscribe skill của chính mình) |
| Không `APPROVED` (DRAFT/PENDING_APPROVAL/REJECTED) | **Ẩn** |

- Click → toggle subscription (chi tiết flow: §6.10).
- **Không hiển thị nút Subscribe ở footer** — chỉ có ở header dòng 2 (tránh trùng lặp).

### 5.3. Banner cảnh báo

| Trạng thái | Hiển thị |
|------------|----------|
| `PENDING_APPROVAL` | Banner vàng nhạt (warning): "ℹ️ Skill is pending approval" |
| `REJECTED` | Không hiện banner (lý do reject hiện ở section riêng) |
| Các trạng thái khác | Không hiển thị |

### 5.4. Meta

| Trường | Nguồn dữ liệu | Định dạng | Ghi chú |
|--------|---------------|-----------|---------|
| Người tạo | `"user".name` (JOIN qua `skills_master.created_by`) | Text, 13px, màu secondary | — |
| Ngày tạo | `skills_master.created_at` | `dd/MM/yyyy` | — |
| Ngày cập nhật | `skills_master.updated_at` | `dd/MM/yyyy` | Chỉ hiển thị nếu khác `created_at`. Hiển thị dạng "Created: 07/09/2026 · Updated: 08/09/2026" |
| File (nếu có) | `skill_files` (JOIN qua `skills_master.id`) | "📄 filename.ext (200 KB)" | Hiển thị dưới meta row, chỉ khi skill có file. **Click file info → trigger download** (theo phân quyền download). Nút "Download" ở footer là chính; file info là shortcut. |

### 5.5. Tab "Information"

| Trường | Nguồn dữ liệu | Kiểu hiển thị | Ghi chú |
|--------|---------------|---------------|---------|
| Mô tả | `skills_master.description` | Text, 14px, line-height 1.7, `white-space: pre-wrap` | Giữ nguyên format xuống dòng. **Scroll** nếu nội dung dài. |

### 5.5.1. Tab "Versions" (v1.1)

| Trường | Nguồn dữ liệu | Kiểu hiển thị | Ghi chú |
|--------|---------------|---------------|---------|
| Version hiện tại | `skills_master.version` + detail | "Version N (current)" + name, scope, hashtags, creator, date, files | Dòng đầu, không có snapshot row |
| Các version cũ | `GET /api/v1/skills/{id}/versions` (`skill_version`) | Version number, name, description, scope, hashtags, creator, createdAt, files[] | Mới nhất trước |
| File của version | `skill_version_file` | "📄 filename.ext (size)" + nút **Download** | Download từ disk (file snapshot) |
| Download All | `GET .../versions/{v}/files/download-all` | Nút zip per version | |

- Load lazy: chỉ gọi API khi user mở tab lần đầu.
- **Tự refresh** sau approve/reject/cancel version update và sau edit (không cần đóng/mở lại popup).
- Skill chưa từng có version update approve → chỉ hiện "Version 1 (current)".

### 5.5.2. Pending Version Review Section (v1.1)

Hiển thị **trên tab bar** (không nằm trong tab), chỉ khi `hasPendingVersion` + viewer là creator/admin:

| Thành phần | Nội dung |
|------------|----------|
| Header | "Pending Version N+1 — under review" + badge "Under Review" (warning) |
| Metadata đề xuất | Name, scope, categories, process stages, hashtags, description mới |
| New files | List file mới + Download từng file + **Download All** (zip) — `GET .../pending-versions/files/...` |
| Files to be removed | List file sẽ bị xóa (gạch ngang) |

Nội dung chính của popup (header, files, description) vẫn là **version hiện tại (live)** — user khác không thấy section này.

### 5.6. Tab "Comments"

#### 5.6.1. Đánh giá sao

| Thuộc tính | Giá trị |
|------------|---------|
| Component | Rating (5 sao, click để đánh giá) |
| Range | 1.0 – 5.0, step 0.5 |
| Constraint | 1 user chỉ đánh giá 1 lần cho 1 skill. Click lại = cập nhật điểm cũ |
| Hiển thị | Sao đã chọn + text điểm số (vd: "5.0 sao") |
| Label | "Your rating" |
| Dữ liệu ban đầu | Lấy từ `GET /api/v1/skills/{id}/rating/me` |
| **Disabled khi** | `approval_status` khác `APPROVED` (DRAFT, PENDING_APPROVAL, REJECTED) |

#### 5.6.2. Danh sách bình luận

| Thuộc tính | Giá trị |
|------------|---------|
| Nguồn | `skill_comments` ORDER BY `created_at` DESC |
| Hiển thị mỗi comment | Tên người bình luận, thời gian (`dd/MM/yyyy HH:mm`), nội dung |
| Nút "✕ Delete" | Chỉ hiển thị trên comment của chính user hiện tại, hoặc trên mọi comment nếu user là admin |
| Pagination | 10 comment/trang |
| Component phân trang | Nút Previous/Next + số trang, tối đa 5 nút trang |
| Empty state | "No comments yet" |
| Header | "Comments (N)" — N = tổng số comment |
| **Read-only khi** | `approval_status` khác `APPROVED` (DRAFT, PENDING_APPROVAL, REJECTED). **Read-only = hiển thị danh sách comment và rating (xem được), nhưng không cho phép đánh giá, viết comment mới, hoặc xóa comment.**
| **Pagination — Xóa comment** | Nếu user xóa comment và trang hiện tại không còn comment → tự động chuyển về trang trước |
| **Pagination — Gửi comment mới** | Sau khi gửi comment mới → tự động chuyển về trang 1, comment mới hiển thị ở đầu danh sách |
| **Pagination — Overflow** | Sau auto-refresh/reload, nếu `currentPage > totalPages` → tự động chuyển về `totalPages` (hoặc trang 1 nếu không có comment) |
| **Auto-refresh 30s** | Auto-refresh giữ nguyên `currentPage`. Sau refresh, áp dụng quy tắc overflow: nếu `currentPage > totalPages` → chuyển về `totalPages`. Nếu trang hiện tại vẫn có comment → user không bị mất vị trí đọc |
| **Self-rating** | Không có ràng buộc ngăn creator đánh giá skill của chính mình. Đây là thiết kế có chủ đích — có thể xem xét lại ở Phase 2 |
| **Ẩn khi không có comment** | Khi `commentCount === 0` → ẩn danh sách comment, ẩn header "Comments (N)", ẩn pagination. Chỉ hiển thị form viết bình luận (nếu APPROVED) |

#### 5.6.3. Form viết bình luận

| Trường | Kiểu | Required | Ghi chú |
|--------|------|----------|---------|
| Nội dung | Textarea | Có | Placeholder: "Write a comment...". Max 1000 ký tự. Counter "X/1000" hiển thị dưới textarea, màu đỏ khi ≥ 900 ký tự |
| Nút Send | Button (primary) | — | Disabled nếu nội dung rỗng hoặc skill **không phải `APPROVED`** (DRAFT, PENDING_APPROVAL, REJECTED) |

### 5.7. Section Lý do từ chối (chỉ khi `REJECTED`)

| Trường | Nguồn | Ghi chú |
|--------|-------|---------|
| Lý do | `skills_master.rejection_reason` | Read-only, hiển thị dưới tab content, trên footer. Icon ❌ + text đỏ |

### 5.8. Footer — Nút hành động

| `approval_status` | Người tạo (creator) | Admin | Non-Creator |
|-------------------|----------------------|-------|-------------|
| `DRAFT` | "Edit" + "Submit for Review" + "Delete" + "Close" | "Edit" + "Submit for Review" + "Delete" + "Close" | "Close" |
| `PENDING_APPROVAL` | "Withdraw" + "Close" (read-only) | "Approve" + "Reject" + "Close" | "Close" |
| `PENDING_APPROVAL` + version pending | "Cancel Version Update" + "Close" | "Approve" + "Reject" + "Cancel Version Update" + "Close" | "Close" (thấy như Approved) |
| `APPROVED` | "Edit" + "Update Version" + "Delete" + "Download" + "Close" | "Edit" + "Update Version" + "Delete" + "Download" + "Close" | "Download" + "Close" |
| `REJECTED` | "Edit" + "Submit for Review" + "Delete" + "Close" | "Edit" + "Submit for Review" + "Delete" + "Close" | "Close" |

> Nút "Edit" mở Edit Skill Popup. Khi `APPROVED`, edit bị hạn chế (chỉ tên skill bị khóa; phạm vi hiển thị, mô tả, hashtag được chỉnh sửa; **file không quản lý qua Edit**).
> **Nút "Update Version" (v1.1)** chỉ hiện khi `APPROVED` + (creator hoặc admin) + chưa có version pending — mở Update Version Popup (sửa metadata + file, bắt buộc ≥1 file mới).
> **Nút "Cancel Version Update" (v1.1)** chỉ hiện khi `PENDING_APPROVAL` + version pending + (creator hoặc admin).
> **Nút Subscribe không nằm ở footer** — chỉ hiển thị ở header dòng 2 (phải) để tránh trùng lặp.
> **Phân quyền hiển thị:** Cột "Người tạo" chỉ áp dụng cho creator của skill. Non-creator (không phải admin) **không hiển thị** nút quản lý (Edit, Delete, Submit, Withdraw) — chỉ thấy nút "Close" (và "Download" nếu APPROVED + có file).
> **Phân quyền Delete:** Creator có thể delete skill của mình. Admin có thể delete bất kỳ skill nào. Delete là soft delete (`deleted_YN = 'Y'`).
> **Nút tương tác (đánh giá sao, bình luận, tải xuống)** áp dụng cho mọi user có quyền xem skill (theo `public_scope`), **không giới hạn creator**.
> **Nút "Download"** chỉ hiển thị khi skill có file VÀ user đủ phân quyền download (creator: mọi status; admin: PENDING_APPROVAL+; user khác: APPROVED). Nếu skill không có file, nút này bị ẩn.
> **Quy tắc public_scope:** `public_scope` ưu tiên hơn role. Skill APPROVED + PRIVATE → chỉ creator thấy, bất kể role.

---

## 6. Flow — Luồng tương tác

### 6.1. Mở Detail Popup

```
[User] Click vào tên skill (delay 300ms) HOẶC Double-click vào dòng trên grid
    │
    ├── Double-click ưu tiên: nếu user double-click, hủy delay click đơn
    │       → tránh popup nháy khi click thứ 2 kích hoạt
    │
    ▼
[Frontend] Gọi GET /api/v1/skills/{id}
    │
    ├── Thành công ──► Gọi song song:
    │                   ├── GET /api/v1/skills/{id}/rating          (điểm TB)
    │                   ├── GET /api/v1/skills/{id}/rating/me       (đánh giá của user)
    │                   └── GET /api/v1/skills/{id}/comments?page=1 (danh sách comment)
    │
    ├── Hiển thị popup, active tab "Information"
    ├── Hiển thị banner (vàng) nếu PENDING_APPROVAL
    ├── Hiển thị lý do reject nếu REJECTED
    ├── Render nút footer theo status + role
    │
    ├── Dữ liệu rating và comment được cache trong popup. Khi chuyển tab, hiển thị từ cache ngay.
    │   Nếu user ở tab "Comments" quá 30 giây không hoạt động → auto-refresh (gọi lại API)
    │
    ├── Sau khi popup render xong → gọi async POST /api/v1/skills/{id}/view (tăng view_count)
    │   Không block UI, idempotent: 1 user chỉ đếm 1 lần/skill trong 15 phút
    │
    ├── Nếu Detail popup đang mở cho skill khác → đóng Detail cũ, mở Detail mới
    │   (luôn chỉ có 1 Detail popup tại một thời điểm)
    │
    └── Thất bại ──► Hiển thị toast "Unable to load skill information" (ERR-DTL-001)
```

### 6.2. Đánh giá sao

```
[User] Click vào số sao (1–5, step 0.5)
    │
    ├── Nếu skill không APPROVED ──► Toast "Only published skills can be rated" (ERR-DTL-003)
    │
    ▼
[Frontend] Gọi PUT /api/v1/skills/{id}/rating  Body: { "rating": 4.5 }
    │
    ├── Thành công ──►
    │       ├── Cập nhật sao hiển thị ngay (optimistic update)
    │       └── Dùng averageRating/totalRatings trong response để cập nhật điểm TB
    │
    └── Thất bại ──► Toast "Unable to save rating" (ERR-DTL-004), khôi phục sao cũ
```

### 6.3. Gửi bình luận

```
[User] Nhập nội dung → Click "Send"
    │
    ├── Nếu skill không APPROVED ──► Toast "Only published skills can be commented" (ERR-DTL-005)
    │
    ▼
[Frontend] Validate: nội dung không rỗng, ≤ 1000 ký tự
    │
    ├── Không hợp lệ ──► Highlight textarea, toast lỗi
    │
    └── Hợp lệ
          │
          ▼
    [Frontend] Gọi POST /api/v1/skills/{id}/comments  Body: { "content": "..." }
        │
        ├── Thành công ──►
        │       ├── Thêm comment mới vào đầu danh sách (optimistic)
        │       ├── Chuyển về trang 1 (nếu đang ở trang khác)
        │       ├── Xóa textarea
        │       └── Cập nhật số lượng "Comments (N)"
        │
        └── Thất bại ──► Toast "Unable to send comment" (ERR-DTL-006)
```

### 6.4. Tải xuống skill

```
[User] Click nút "Download" (từng file) hoặc "Download All" — chỉ hiện khi đủ phân quyền (creator: mọi status; admin: PENDING_APPROVAL+; user khác: APPROVED)
    │
    ├── Nếu skill không có file ──► Nút bị ẩn, không hiển thị
    │
    ▼
[Frontend] Gọi GET /api/v1/skills/{id}/files/{fileId}/download (từng file)
            HOẶC GET /api/v1/skills/{id}/files/download-all (zip)
    │
    ├── Thành công ──► Download file (trigger browser download)
    │
    └── Thất bại ──► Toast "Unable to download file" (ERR-DTL-011)
```

### 6.5. Submit for Review

```
[User (creator)] Click "Submit for Review" (footer, khi DRAFT/REJECTED)
    │
    ▼
[Frontend] Gọi PUT /api/v1/skills/{id}/status  Body: { "approvalStatus": "PENDING_APPROVAL" }
    │
    ├── Thành công ──►
    │       ├── Backend set `rejection_reason = NULL`
    │       ├── Cập nhật badge status → "Pending Approval"
    │       ├── Cập nhật nút footer
    │       └── Toast "Review request submitted successfully" (ERR-DTL-012)
    │
    └── Thất bại ──► Toast "Unable to submit review request" (ERR-DTL-013)
```

### 6.5.1. Rút lại (Withdraw)

```
[User (creator)] Click "Withdraw" (footer, khi PENDING_APPROVAL)
    │
    ▼
[Frontend] Hiện dialog xác nhận: "Are you sure you want to withdraw this skill?
          The skill will return to Draft status." (ERR-DTL-014)
    │
    ├── Người dùng Cancel ──► Đóng dialog, không làm gì
    │
    └── Người dùng Confirm
            │
            ▼
      [Frontend] Gọi PUT /api/v1/skills/{id}/status  Body: { "approvalStatus": "DRAFT" }
          │
          ├── Thành công ──►
          │       ├── Cập nhật badge status → "Draft"
          │       ├── Cập nhật nút footer (hiện "Edit" + "Submit for Review")
          │       └── Toast "Skill withdrawn successfully" (ERR-DTL-015)
          │
          └── Thất bại ──► Toast "Unable to withdraw skill" (ERR-DTL-016)
```

> **Phân quyền:** Chỉ người tạo mới được rút lại skill của mình.

### 6.6. Approve / Reject (Admin)

```
[Admin] Click "Approve" (footer, khi PENDING_APPROVAL)
    │
    ▼
[Frontend] Gọi PUT /api/v1/skills/{id}/status  Body: { "approvalStatus": "APPROVED" }
    │
    ├── Thành công ──►
    │       ├── Refresh popup, toast "Skill approved successfully" (ERR-DTL-017)
    │       └── Gửi notification cho creator: "Skill [name] has been approved"
    │
    └── Thất bại ──► Toast "Unable to approve skill" (ERR-DTL-018)

[Admin] Click "Reject" (footer, khi PENDING_APPROVAL)
    │
    ▼
[Frontend] Hiện dialog nhập lý do từ chối
    │
    ├── Admin nhập lý do → Click "Confirm"
    │       │
    │       ▼
    │   [Frontend] Gọi PUT /api/v1/skills/{id}/status  Body: { "approvalStatus": "REJECTED", "rejectionReason": "..." }
    │       │
    │       ├── Thành công ──►
    │       │       ├── Refresh popup, toast "Skill rejected successfully" (ERR-DTL-019)
    │       │       └── Gửi notification cho creator: "Skill [name] was rejected — [reason]"
    │       │
    │       └── Thất bại ──► Toast "Unable to reject skill" (ERR-DTL-020)
    │
    └── Admin Cancel → Đóng dialog
```

### 6.7. Delete Skill (Soft Delete)

```
[User (creator) hoặc Admin] Click "Delete" (footer)
    │
    ▼
[Frontend] Hiện dialog xác nhận: "Are you sure you want to delete this skill?
          The skill will be hidden from the list." (ERR-DTL-021)
    │
    ├── Người dùng Cancel ──► Đóng dialog, không làm gì
    │
    └── Người dùng Confirm
            │
            ▼
      [Frontend] Gọi DELETE /api/v1/skills  Body: { "ids": [skillId] }
          │
          ├── Thành công ──►
          │       ├── Đóng popup
          │       ├── Refresh grid Skill List
          │       └── Toast "Skill deleted successfully" (ERR-DTL-022)
          │
          └── Thất bại ──► Toast "Unable to delete skill" (ERR-DTL-023)
```

> Delete là soft delete: gán `deleted_YN = 'Y'`, không xóa khỏi DB.
> **Phân quyền:** Creator delete skill của mình, Admin delete bất kỳ skill nào.

### 6.8. Đóng popup

```
[User] Click nút "Close" / Click overlay / Nhấn Esc
    │
    ▼
[Frontend] Đóng popup, reset state (xoá data cache của popup)
```

### 6.9. Auto-refresh khi tab được focus lại

```
[Browser] User quay lại tab chứa Detail popup (visibility change event)
    │
    ▼
[Frontend] Gọi lại GET /api/v1/skills/{id} để refresh toàn bộ data
    │
    ├── Thành công ──► Cập nhật badge status, nút footer, banner, lý do reject
    │                   Không đóng popup, không làm mất tab đang active
    │
    └── Thất bại (404) ──► Đóng popup, toast "Skill has been deleted" (ERR-DTL-002)
```

> Không polling liên tục. Refresh chỉ khi user quay lại tab → cân bằng giữa độ chính xác và hiệu năng.
> Kết hợp với auto-refresh comment 30s (khi user ở tab "Comments" không hoạt động).

### 6.10. Subscribe / Unsubscribe

```
[User] Click nút "Subscribe" (Detail header dòng 2) HOẶC icon chuông (cột Subscribe trên Grid)
    │
    ├── Skill không APPROVED, hoặc là skill của chính user
    │       ──► Nút/icon bị ẩn — không thể click
    │
    ▼
[Frontend] Gọi POST /api/v1/skills/{id}/subscription
           (hoặc DELETE nếu đã subscribe)
    │
    ├── Thành công ──►
    │       ├── Cập nhật nút header: "Subscribe" ↔ "Subscribed"
    │       │   (icon chuông outline ↔ đổ đầy, màu primary)
    │       ├── Đồng bộ icon chuông trên Grid
    │       └── Toast "Subscribed to [name]" / "Unsubscribed from [name]"
    │
    └── Thất bại ──► Toast "Unable to update subscription" (ERR-DTL-026)
```

> Click icon chuông trên Grid **không mở Detail Popup** (khác với click Skill Name).

### 6.11. Thông báo subscriber khi Skill có phiên bản mới

```
[User] Edit + Save Skill (PUT /api/v1/skills/{id})
    │
    ▼
[Backend] Lưu thành công → version = version + 1
    │
    ▼
[Backend] Lấy danh sách subscriber của Skill (skill_subscriptions)
    │
    ▼
Với mỗi subscriber: đọc user_settings (email_notify, inapp_notify)
    │
    ├── inapp_notify = TRUE ──► In-app: "[name] has a new version (vN)"
    │
    └── email_notify = TRUE ──► Email: "[name] new version (vN)"
```

> Kênh thông báo tuân theo **Settings > Notifications** của từng subscriber (tắt kênh nào → không gửi kênh đó).
> Chi tiết API: §7.9.

---

## 7. API Endpoint

### 7.1. Lấy chi tiết skill

```
GET /api/v1/skills/{id}
Authorization: Bearer <JWT>
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Auto Code Review",
    "description": "Auto code review skill...\n\nHighlights:\n- Detects 50+ code smell types\n- Suggests automatic fixes",
    "status": "APPROVED",
    "scope": "PUBLIC",
    "hashtags": ["code-review", "java", "automation"],
    "rejectionReason": null,
    "files": [
      { "id": 11, "fileName": "skill.zip", "fileSize": 204800, "createdAt": "2026-09-07T00:00:00" }
    ],
    "viewCount": 128,
    "version": 3,
    "creatorId": 1,
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

> `isCreator`: `true` nếu user hiện tại là người tạo skill.
> `isAdmin`: `true` nếu user hiện tại có role admin.
> `canEdit`: `true` nếu `(isCreator || isAdmin) && !deleted && status != PENDING_APPROVAL`. Frontend dùng field này để quyết định hiển thị nút "Edit".
> `version`: Số phiên bản hiện tại của skill. Backend tự động tăng mỗi lần skill được lưu thành công.
> `files`: danh sách file đính kèm (1 skill : N file).
> `subscribed`: user hiện tại đã subscribe skill này chưa.

**Response (404 Not Found):**
```json
{ "success": false, "errorCode": "SKILL_NOT_FOUND", "message": "Skill not found" }
```

> **Quy tắc public_scope:** Nếu user không thuộc phạm vi `public_scope` của skill, API trả về **404** (không phải 403) — không tiết lộ skill tồn tại.

---

### 7.2. Lấy điểm sao trung bình

```
GET /api/v1/skills/{id}/rating
Authorization: Bearer <JWT>
```

**Response (200 OK):**
```json
{ "success": true, "data": { "averageRating": 4.5, "totalRatings": 12 } }
```

**Response (200 OK — chưa có đánh giá):**
```json
{ "success": true, "data": { "averageRating": 0.0, "totalRatings": 0 } }
```

---

### 7.3. Lấy đánh giá của user hiện tại

```
GET /api/v1/skills/{id}/rating/me
Authorization: Bearer <JWT>
```

**Response (200 OK — đã đánh giá):**
```json
{ "success": true, "data": 4.5 }
```

**Response (200 OK — chưa đánh giá):**
```json
{ "success": true, "data": null }
```

---

### 7.4. Tạo / Cập nhật đánh giá sao

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
| Skill không ở trạng thái APPROVED | SKILL_NOT_APPROVED | "Only published skills can be rated" |

**Response (200 OK):** trả về điểm TB cập nhật (cùng cấu trúc §7.2):
```json
{ "success": true, "data": { "averageRating": 4.5, "totalRatings": 12 } }
```

**Response (400 Bad Request):**
```json
{ "success": false, "errorCode": "SKILL_INVALID_RATING", "message": "Rating must be between 1 and 5 in steps of 0.5." }
```

---

### 7.5. Lấy danh sách bình luận

```
GET /api/v1/skills/{id}/comments?page={page}&size={size}
Authorization: Bearer <JWT>
```

> API PHẢI filter `WHERE deleted_at IS NULL`. Comment đã soft-delete không xuất hiện trong danh sách và không tính vào `totalCount`.

| Query Param | Required | Default | Constraint |
|-------------|----------|---------|------------|
| `page` | Không | 1 | ≥ 1 |
| `size` | Không | 10 | 1–50 |

**Response (200 OK):**
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

### 7.6. Thêm bình luận

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
| Skill không ở trạng thái APPROVED | SKILL_NOT_APPROVED | "Only published skills can be commented" |

**Response (201 Created):** *(empty body)*

---

### 7.6.1. Xóa bình luận (Soft Delete)

```
DELETE /api/v1/skills/{id}/comments/{commentId}
Authorization: Bearer <JWT>
```

**Flow:**
1. User click nút "✕ Delete" trên comment
2. Hiện dialog xác nhận: "Are you sure you want to delete this comment?" (ERR-DTL-009)
3. Nếu xác nhận → gọi DELETE API
4. Nếu thành công:
   - Xóa comment khỏi danh sách ngay (optimistic update)
   - Cập nhật số lượng "Comments (N)"
   - Nếu trang hiện tại không còn comment → chuyển về trang trước
   - Toast "Comment deleted successfully" (ERR-DTL-007)
5. Nếu thất bại: toast "Unable to delete comment" (ERR-DTL-008)

**Phân quyền:** Chỉ người tạo comment hoặc admin mới được xóa.

**Response (204 No Content):** *(empty body — xóa thành công)*

**Response (403 Forbidden):**
```json
{ "code": "SKILL_DELETE_FORBIDDEN", "message": "You do not have permission to delete this comment" }
```

> Soft delete: comment không bị xóa khỏi DB mà gán `deleted_at = NOW()`. Comment bị xóa không hiển thị trong danh sách và không tính vào tổng số.

---

### 7.7. Tải xuống skill

```
GET /api/v1/skills/{id}/files/download-all      (tất cả file → zip)
GET /api/v1/skills/{id}/files/{fileId}/download (từng file)
Authorization: Bearer <JWT>
```

**Quy tắc phân quyền download:**
| Người | Trạng thái được phép |
|-------|----------------------|
| Creator | Mọi trạng thái |
| Admin | `PENDING_APPROVAL` hoặc `APPROVED` |
| User khác | Chỉ `APPROVED` |

**Response (200 OK):**
- `Content-Type`: `application/octet-stream`
- `Content-Disposition`: `attachment; filename="skill-name.zip"` (Download All) hoặc tên file gốc (từng file)
- Body: binary file

**Response (404 Not Found):**
```json
{ "success": false, "errorCode": "SKILL_FILE_NOT_FOUND", "message": "Skill file does not exist." }
```

**Response (403 Forbidden):**
```json
{ "success": false, "errorCode": "SKILL_NOT_APPROVED", "message": "This skill cannot be downloaded in its current status." }
```

---

### 7.7.1. Tăng view_count

```
POST /api/v1/skills/{id}/view
Authorization: Bearer <JWT>
```

**Mô tả:** Tăng `view_count` của skill. Gọi async sau khi popup render xong, không block UI.

**Idempotent:** 1 user chỉ đếm 1 lần/skill trong cửa sổ **15 phút** (cấu hình `app.skill.view-window-minutes`, mặc định 15). Nếu user mở lại popup trong cùng cửa sổ → API trả 200 nhưng không tăng thêm.

**Response (200 OK):**
```json
{ "success": true, "data": { "viewCount": 129 } }
```

**Response (404 Not Found):**
```json
{ "success": false, "errorCode": "SKILL_NOT_FOUND", "message": "Skill not found" }
```

---

### 7.8. Cập nhật trạng thái skill

```
PUT /api/v1/skills/{id}/status
Authorization: Bearer <JWT>
Content-Type: application/json

{ "approvalStatus": "PENDING_APPROVAL" }
```

**Request Body:**
| Trường | Required | Ghi chú |
|--------|----------|---------|
| `approvalStatus` | Có | Giá trị mới: `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `DRAFT` |
| `rejectionReason` | Chỉ khi `REJECTED` | Lý do từ chối, max 500 ký tự |

**Phân quyền:**
| Hành động | Người tạo | Admin |
|-----------|-----------|-------|
| `DRAFT` → `PENDING_APPROVAL` | Chỉ skill của mình | Bất kỳ |
| `PENDING_APPROVAL` → `APPROVED` | Không | Chỉ admin |
| `PENDING_APPROVAL` → `REJECTED` | Không | Chỉ admin |
| `PENDING_APPROVAL` → `DRAFT` (Withdraw) | Chỉ skill của mình | Không |
| `REJECTED` → `PENDING_APPROVAL` | Chỉ skill của mình | Bất kỳ |

> Khi chuyển `REJECTED` → `PENDING_APPROVAL`, backend PHẢI set `rejection_reason = NULL` — lý do từ chối cũ không còn hiệu lực.

**Response (204 No Content):** *(empty body)*

**Response (400 Bad Request):**
```json
{ "success": false, "errorCode": "SKILL_INVALID_TRANSITION", "message": "This status change is not allowed." }
```

---

### 7.9. Subscribe / Unsubscribe

```
POST   /api/v1/skills/{id}/subscription
DELETE /api/v1/skills/{id}/subscription
Authorization: Bearer <JWT>
```

**Validation (POST):**
| Rule | Error Code | Message |
|------|-----------|---------|
| Skill không ở trạng thái APPROVED | SKILL_NOT_APPROVED | "Only published skills can be subscribed." |
| User là creator của skill | SKILL_SUBSCRIBE_FORBIDDEN | "You cannot subscribe to your own skill." |

**Response (201 Created):** *(empty body)*

**Response (404 Not Found):**
```json
{ "success": false, "errorCode": "SKILL_NOT_FOUND", "message": "Skill not found" }
```

> - `DELETE` idempotent — trả 204 cả khi user chưa subscribe.
> - `GET /api/v1/skills/{id}` (mục 7.1) bổ sung field `subscribed` (boolean) cho user hiện tại.
> - Backend enforce: không cho subscribe skill không APPROVED hoặc skill của chính mình (không chỉ ẩn nút trên UI).
> - Khi skill được save thành công (version +1) → backend gửi notification cho toàn bộ subscriber theo `user_settings` (in-app/email). Flow: §6.11.

---

## 8. CSDL Liên Quan

### 8.1. Bảng `skills_master`

```sql
CREATE TABLE skills_master (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    description       TEXT,
    approval_status   VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                        CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    public_scope  VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE'
                        CHECK (public_scope IN ('PRIVATE','PART', 'GROUP', 'PUBLIC')),
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
```

> Tên skill unique **theo creator** (case-insensitive, chưa xóa): `name` + `created_by` + `deleted_yn = 'N'` — không unique toàn hệ thống.

### 8.2. Bảng `skill_ratings`

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

### 8.3. Bảng `skill_comments`

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

### 8.4. Bảng `skill_status_history` (Audit trail)

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

### 8.5. Bảng `skill_subscriptions`

```sql
CREATE TABLE skill_subscriptions (
    skill_id   BIGINT NOT NULL REFERENCES skills_master(id),
    user_id    BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (skill_id, user_id)
);
```

> - Lưu quan hệ N:M giữa Skill và User đã subscribe.
> - Backend enforce: không tạo row khi `user_id = created_by` của skill (creator không subscribe skill của mình).
> - Khi skill có phiên bản mới (edit + save → `version + 1`), backend đọc bảng này để gửi notification theo `user_settings` của từng subscriber (in-app/email).
> - Khi skill bị soft delete (`deleted_yn = 'Y'`), row subscription được giữ lại (không xóa) — phục vụ khôi phục/audit.

---

## 9. Quy tắc hiển thị sao

### 9.1. Điểm sao trung bình (header)

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

> **Sao visual** làm tròn đến 0.5 gần nhất cho mục đích hiển thị. **Số điểm** hiển thị chính xác 1 chữ số thập phân (vd: 4.2, 3.7, 4.8).
> CSS partial star: `width: ${(point % 1) * 100}%` cho phần sao không đầy.

### 9.2. Đánh giá của user (tab Bình luận)

- Step 0.5: hover vào giữa sao = half sao
- Click vào sao = gán điểm tương ứng
- Đã đánh giá → hiển thị sao đã chọn (editable để cập nhật)
- **Disabled** khi `approval_status` khác `APPROVED` (DRAFT, PENDING_APPROVAL, REJECTED)

---

## 10. Xử lý lỗi & Edge Case

| Tình huống | Hành vi |
|------------|---------|
| Skill bị xóa khi popup đang mở | Popup đóng, toast "Skill đã bị xóa" |
| User chưa đăng nhập | Redirect về trang login |
| Mất kết nối mạng | Hiển thị skeleton/loading, retry 1 lần, sau đó toast lỗi |
| Nội dung mô tả quá dài | Scroll trong tab "Thông tin" |
| Comment quá nhiều | Pagination, mặc định 10/trang |
| User đánh giá rồi đánh giá lại | Cập nhật điểm cũ (UPSERT), tính lại điểm TB |
| File skill không tồn tại | Toast "File skill không tồn tại" khi click Tải xuống |
| Đánh giá skill không APPROVED | Toast "Chỉ có thể đánh giá skill đã công bố" |
| Bình luận skill không APPROVED | Toast "Chỉ có thể bình luận skill đã công bố" |
| Chuyển trạng thái không hợp lệ | Toast "Không thể chuyển trạng thái", hiển thị lỗi từ API |

---

## 11. Security

| Yêu cầu | Giải pháp |
|----------|-----------|
| Xác thực | JWT trong `Authorization` header cho mọi request |
| Phân quyền đánh giá | Chỉ user đã login, chỉ skill `APPROVED` |
| Phân quyền bình luận | Chỉ user đã login, chỉ skill `APPROVED` |
| Phân quyền tải xuống | Kiểm tra `approval_status = 'APPROVED'` + `public_scope` |
| Phân quyền approve/reject | Chỉ admin |
| Phân quyền delete | Creator hoặc admin |
| Input validation | Validate `rating` (1.0–5.0, step 0.5), `content` (≤ 1000 ký tự), `rejectionReason` (≤ 500 ký tự) |
| XSS protection | Escape nội dung comment và rejection_reason khi render |
| Rate limiting | 10 đánh giá/phút/user, 30 comment/phút/user |

---

## 12. Z-Index Stacking Policy

Khi nhiều popup cùng tồn tại (Detail mở Edit), z-index đảm bảo lớp phủ đúng:

| Lớp | Z-Index | Ghi chú |
|-----|---------|---------|
| Detail Popup overlay | 1000 | Popup chi tiết skill |
| Edit Popup overlay | 2000 | Mở từ Detail, che Detail |
| Add Popup overlay | 2000 | Mở từ grid |
| Confirm Dialog overlay | 3000 | Luôn cao nhất |
| Toast | 4000 | Luôn hiển thị trên mọi popup |

> Khi Edit/Add đóng, Detail popup phía dưới vẫn giữ nguyên trạng thái.

---

## 13. Phạm vi bổ sung (Edit Skill Popup)

> Edit Skill Popup là chức năng bổ sung cho phép chỉnh sửa skill đã tồn tại.
> Spec riêng: `Edit Skill Popup/Edit_Popup_Spec.md`

### 13.1. Khi nào được edit?

| `approval_status` | Cho phép edit? | Ghi chú |
|-------------------|----------------|---------|
| `DRAFT` | Có | Edit đầy đủ (tên, mô tả, scope, file) |
| `PENDING_APPROVAL` | Không | Phải reject trước hoặc withdraw |
| `APPROVED` | Có hạn chế | Creator: không đổi tên; scope/mô tả/hashtag/file được phép. Admin: edit đầy đủ |
| `REJECTED` | Có | Edit đầy đủ, sau đó submit lại |
| `DRAFT` (đã xóa) | Không | Skill đã bị xóa (soft delete) |

### 13.2. API

```
PUT /api/v1/skills/{id}
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

### 13.3. Quy tắc

- Tên skill: nếu thay đổi, phải check `SKILL_NAME_DUPLICATE` (case-insensitive)
- File: nếu upload file mới, file cũ bị thay thế

---

## 14. Notification

### 14.1. Khi nào gửi?

| Sự kiện | Người nhận | Nội dung | Kênh |
|---------|------------|----------|------|
| Admin Approve | Creator | "Skill [name] đã được phê duyệt" | In-app |
| Admin Reject | Creator | "Skill [name] bị từ chối — [lý do]" | In-app |
| Delete skill | Creator (nếu admin thực hiện) | "Skill [name] đã bị xóa" | In-app |
| Skill có phiên bản mới (edit + save) | Toàn bộ subscriber của skill | "Skill [name] có phiên bản mới (vN)" | In-app + **Email** |

### 14.2. Kênh

- In-app notification (nếu hệ thống có module notification)
- Nếu không có module notification → grid Skill List cập nhật status real-time
- **Email chỉ gửi cho sự kiện "skill đã subscribe có phiên bản mới"** (skill status APPROVED) — không gửi email cho các sự kiện khác (approve, reject, delete, comment, rating).
- **Kênh áp dụng theo Settings > Notifications của từng người nhận** (`email_notify` / `inapp_notify` — xem `Settings_Spec.md` §4.3). Tắt kênh nào → không gửi kênh đó.
