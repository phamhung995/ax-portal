# AI SKILL MANAGEMENT
## Skill Management Screen – Business Analysis Specification

**Document Version:** 1.0  
**Status:** Baseline  
**Module:** Skill Management  
**Screen:** Skill Management / Skill Search  
**Prepared by:** Business Analyst  
**Target:** PO / BA / UIUX / Developer / QA

---

# 1. Document Purpose

Tài liệu mô tả yêu cầu nghiệp vụ và yêu cầu hệ thống cho màn hình **Skill Management** thuộc hệ thống **AI Skill Management**.

Mục tiêu của màn hình:

- Cho phép User tìm kiếm Skill.
- Hiển thị danh sách Skill theo quyền truy cập.
- Cho phép truy cập Skill Detail (click Skill Name hoặc double-click row).
- Hiển thị View Count và Rating.
- Cho phép Delete Skill theo quyền.
- Cho phép Add Skill mới (mở Add Skill Popup).
- Cho phép Download Search Result dưới dạng Excel.
- Cho phép Edit Skill (click nút "Edit" trong Detail Popup footer mở Edit Skill Popup).
- Cho phép tìm kiếm User (click nút kính lúp 🔍 trong Creator search field mở Search User Popup).
- Hỗ trợ sorting và filtering.
- Đảm bảo dữ liệu hiển thị đúng theo Role, Creator và Skill Scope.

---

# 2. Scope

## 2.1 In Scope

### Search / Filter
- Skill Name
- Description
- Tag
- Creator
- Created Date
- Creation Status (checkbox, chọn nhiều giá trị)
- Skill Scope (checkbox, chọn nhiều giá trị)
- Category (multi-select, OR logic — danh sách từ taxonomy master)
- Process Stage (single-select — danh sách từ taxonomy master)

### Search Result Grid
- Checkbox
- Skill Name
- Description
- Creator
- Created Date
- Status
- Views
- Rating
- Subscribe

### Permission
- Normal User
- Admin User
- Creator-based visibility
- Creator-based Delete permission
- Skill Scope-based visibility ( Part / Group / Public / Private)

### Skill Detail
- Mở Skill Detail khi click Skill Name (delay 300ms).
- Mở Skill Detail khi double-click vào row trên Grid.
- Double-click ưu tiên: nếu user double-click, hủy delay click đơn, tránh popup nháy.
- Tăng View Count khi mở Detail thành công.
- Detail Popup logic defined in `Detail_Popup_Spec.md` and `Detail_Popup_SRS.md`.

### Edit Skill
- Click nút "Edit" trong Detail Popup footer mở Edit Skill Popup.
- Edit đầy đủ khi DRAFT/REJECTED (tên, scope, category, stage, mô tả, hashtag, file).
- Edit hạn chế khi APPROVED + non-admin (chỉ tên skill read-only — scope, category, stage, mô tả, hashtag edit được; **file KHÔNG quản lý qua Edit**).
- Edit metadata đầy đủ khi APPROVED + admin (**file vẫn KHÔNG quản lý qua Edit** — chỉ qua Update Version).
- Không cho phép edit khi PENDING_APPROVAL hoặc đã xóa.
- Edit Skill Popup logic defined in `Edit_Popup_Spec.md` and `Edit_Popup_SRS.md`.

### Search User
- Click nút kính lúp (🔍) trong Creator search field mở Search User Popup.
- Popup cho phép tìm kiếm user theo tên hoặc ID.
- Grid hiển thị: Name, ID, thông tin phòng ban (Part > Group).
- Chỉ hiển thị user có trạng thái là APPROVE.
- Chọn user → điền vào Creator field.

### Bulk Delete
- Select one hoặc nhiều Skill.
- Delete theo permission.
- Bulk Delete áp dụng nguyên tắc all-or-nothing.

### Add Skill
- Add Skill button placed next to Download Excel.
- Click Add → opens Add Skill Popup.
- Add Skill Popup logic defined in `Add_Popup_Spec.md` and `Add_Popup_SRS.md`.

### Excel Export
- Export toàn bộ Search Result nếu không chọn row.
- Export selected rows nếu có chọn row.

### Sorting
- Skill Name
- Creator
- Created Date
- Status
- Views
- Rating

---

## 2.2 Out of Scope

Các chức năng sau không thuộc Skill Management Screen:

- Submit for Review
- Approve Skill
- Reject Skill
- Chi tiết workflow Review/Approval

Các chức năng trên sẽ được đặc tả trong tài liệu **Skill Detail / Skill Workflow** riêng.

> **Cập nhật v1.1 (16/10/2026):** Version Management (Update Version + Versions tab) đã được implement — xem §15.10 và `Detail_Popup_SRS.md`.

---

# 3. Actors

| Actor | Description |
|---|---|
| Normal User | User thông thường, có quyền xem Skill của bản thân và Approved Skill của User khác |
| Admin User | User có quyền quản trị toàn bộ Skill |
| System | Xử lý Search, Permission, Delete, View Count, Rating và Export |

---

# 4. Business Requirements

| ID | Business Requirement |
|---|---|
| BR-SM-001 | System phải cung cấp chức năng Search Skill |
| BR-SM-002 | Normal User được xem Skill của chính mình ở tất cả status |
| BR-SM-003 | Normal User chỉ được xem Skill của User khác khi status = Approved |
| BR-SM-004 | Admin User được xem Skill của tất cả Creator và tất cả status |
| BR-SM-005 | User được Delete Skill do chính mình tạo |
| BR-SM-006 | Admin được Delete Skill của bất kỳ Creator nào |
| BR-SM-007 | Bulk Delete phải xử lý theo nguyên tắc all-or-nothing |
| BR-SM-008 | View Count tăng khi Skill Detail được mở thành công |
| BR-SM-009 | Rating hiển thị Average Rating và Number of Raters |
| BR-SM-010 | Không có Rating phải hiển thị "No rating" |
| BR-SM-011 | Không chọn row khi Export thì export toàn bộ Search Result |
| BR-SM-012 | Có chọn row khi Export thì chỉ export các row được chọn |
| BR-SM-013 | Status filter cho phép chọn nhiều trạng thái|
| BR-SM-014 | Nhiều Status được chọn áp dụng OR logic (Skill thỏa một trong các Status được chọn)|
| BR-SM-015 | Scope filter cho phép chọn nhiều phạm vi|
| BR-SM-016 | Nhiều Scope được chọn áp dụng OR logic (Skill thỏa một trong các Scope được chọn)|
| BR-SM-017 | Status filter và Scope filter kết hợp với nhau bằng AND logic|
| BR-SM-018 | Status/Scope filter kết hợp với các điều kiện Search khác bằng AND logic|
| BR-SM-019 | Tag là một điều kiện Search|
| BR-SM-020 | Một hoặc nhiều Tag có thể được chọn|
| BR-SM-021 | Nhiều Tag được chọn sẽ áp dụng OR logic|
| BR-SM-022 | Tag Filter kết hợp với các Search Criteria khác bằng AND logic|
| BR-SM-023 | Skill phải có Scope xác định phạm vi chia sẻ|
| BR-SM-024 | Scope = Private thì chỉ Creator mới được tìm kiếm và xem|
| BR-SM-025 | Scope = Part thì chỉ các User cùng Part với Creator mới được tìm kiếm và xem|
| BR-SM-026 | Scope = Group thì chỉ các User cùng Group với Creator mới được tìm kiếm và xem|
| BR-SM-027 | Scope = Public thì tất cả User đều có thể tìm kiếm và xem|
| BR-SM-028 | Admin User có thể xem toàn bộ Skill bất kể Scope|
| BR-SM-029 | System phải cung cấp chức năng Add Skill mới|
| BR-SM-030 | Button Add Skill được đặt cạnh button Download Excel|
| BR-SM-031 | Click button Add Skill mở Add Skill Popup|
| BR-SM-032 | Add Skill Popup tuân theo logic trong Add_Popup_Spec.md và Add_Popup_SRS.md|
| BR-SM-033 | Skill mới được tạo ở trạng thái Draft|
| BR-SM-034 | Sau khi tạo Skill thành công, Grid được refresh và hiển thị Skill mới|
| BR-SM-035 | User có thể mở Skill Detail bằng cách click Skill Name|
| BR-SM-036 | User có thể mở Skill Detail bằng cách double-click vào row trên Grid|
| BR-SM-037 | Double-click ưu tiên: nếu user double-click, hủy delay click đơn (300ms), tránh popup nháy|
| BR-SM-038 | Detail Popup tuân theo logic trong Detail_Popup_Spec.md và Detail_Popup_SRS.md|
| BR-SM-039 | System phải cung cấp chức năng Edit Skill từ Detail Popup|
| BR-SM-040 | Click nút "Edit" trong Detail Popup footer mở Edit Skill Popup|
| BR-SM-041 | Edit đầy đủ khi DRAFT/REJECTED (tên, scope, category, stage, mô tả, hashtag, file)|
| BR-SM-042 | Edit hạn chế khi APPROVED + non-admin (chỉ tên skill read-only — scope, category, stage, mô tả, hashtag edit được; file KHÔNG quản lý qua Edit)|
| BR-SM-043 | Edit metadata đầy đủ khi APPROVED + admin (file vẫn KHÔNG quản lý qua Edit — chỉ qua Update Version)|
| BR-SM-044 | Không cho phép edit khi PENDING_APPROVAL hoặc đã xóa|
| BR-SM-045 | Edit Skill Popup tuân theo logic trong Edit_Popup_Spec.md và Edit_Popup_SRS.md|
| BR-SM-046 | System phải cung cấp chức năng tìm kiếm User|
| BR-SM-047 | Click nút kính lúp (🔍) trong Creator search field mở Search User Popup|
| BR-SM-048 | Search User Popup cho phép tìm kiếm user theo tên hoặc ID|
| BR-SM-049 | Grid Search User hiển thị Name, ID, thông tin phòng ban (Part > Group)|
| BR-SM-050 | Search User chỉ hiển thị user có trạng thái APPROVE|
| BR-SM-051 | Chọn user trong Search User Popup điền vào Creator field|
| BR-SM-052 | System phải cung cấp chức năng Subscribe cho Skill|
| BR-SM-053 | User được subscribe Skill APPROVED do User khác tạo để theo dõi cập nhật phiên bản mới|
| BR-SM-054 | Creator không được subscribe Skill của chính mình (nút bị ẩn)|
| BR-SM-055 | Subscribe hiển thị bằng icon chuông trên cột "Subscribe" của Grid và nút "Subscribe" trên Detail Popup (header dòng 2)|
| BR-SM-056 | Khi Skill đã subscribe có phiên bản mới, System thông báo cho subscriber theo cài đặt Notifications của User (in-app và/hoặc email)|

---

# 5. Business Rules
| ID |	| Rule |
|---|---|
| BR-SM-001 |	Login user là Normal User thì xem Skill của mình ở mọi status|
| BR-SM-002 |	Login user là Normal User thì chỉ xem Skill của User khác khi Approved|
| BR-SM-003 |	Login user là Admin user thì xem Skill của mọi Creator|
| BR-SM-004 |	Login user là Admin user thì xem mọi status|
| BR-SM-005 |	Login user là Normal User thì chỉ Delete Skill của mình|
| BR-SM-006 |	Login user là Admin user thì Delete được Skill của mọi Creator|
| BR-SM-007 |	Bulk Delete là all-or-nothing|
| BR-SM-008 |	View Count +1 khi Detail mở thành công|
| BR-SM-009 |	Search Result không làm tăng View Count|
| BR-SM-010 |	Rating = Average Rating + Number of Raters|
| BR-SM-011 |	Không có Rating → No rating|
| BR-SM-012 |	Không chọn row → Export toàn bộ Search Result|
| BR-SM-013 |	Có chọn row → Export selected rows|
| BR-SM-014 |	Export phải áp dụng authorization|
| BR-SM-015 |	Creator filter sử dụng Creator ID|
| BR-SM-016 |	Backend phải enforce Visibility|
| BR-SM-017 |	Backend phải enforce Delete Permission|
| BR-SM-018 |	Scope = Private → chỉ Creator xem được (Normal User)|
| BR-SM-019 |	Scope = Part → chỉ User cùng Part với Creator xem được (Normal User)|
| BR-SM-020 |	Scope = Group → chỉ User cùng Group với Creator xem được (Normal User)|
| BR-SM-021 |	Scope = Public → tất cả User xem được|
| BR-SM-022 |	Admin User xem được mọi Skill bất kể Scope|
| BR-SM-023 |	Scope Rule kết hợp với Status Rule bằng AND logic|
| BR-SM-024 |	Scope filter sử dụng Scope Enum|
| BR-SM-025 |	Backend phải enforce Scope Visibility|

---

# 6. Visibility & Permission Matrix

## 6.1 Skill Visibility

Skill Visibility được xác định bởi **hai điều kiện**: Status Rule và Scope Rule.
Hai điều kiện được kết hợp bằng **AND logic** — cả hai phải thỏa mãn thì Skill mới hiển thị.

### Status Rule

| Login Role | Creator | Draft | Pending Approval | Approved | Rejected |
|---|---|---:|---:|---:|---:|
| Normal User | Current User | ✓ | ✓ | ✓ | ✓ |
| Normal User | Other User | ✕ | ✕ | ✓ | ✕ |
| Admin User | Current User | ✓ | ✓ | ✓ | ✓ |
| Admin User | Other User | ✓ | ✓ | ✓ | ✓ |

Nếu Normal User không chọn Creator:

```text
Own Skill
→ All statuses

Other User's Skill
→ Approved only
```

> **Ngoại lệ — Skill đang có version update pending (v1.1):** skill của user khác có
> `status = PENDING_APPROVAL` **và** `pending_metadata IS NOT NULL` (đang chờ duyệt
> version update, version cũ vẫn live) → **vẫn hiển thị như APPROVED** cho user khác
> (xem, download, rate, comment, subscribe đều hoạt động trên version cũ).
> Skill PENDING_APPROVAL lần đầu (DRAFT → PENDING, không có `pending_metadata`) vẫn ẩn.
> Creator/Admin của skill đó thấy badge "Pending Approval" + nút Approve/Reject/Cancel.

Nếu Admin không chọn Creator:

```text
All Creators
→ All statuses
```

### Scope Rule

Skill Scope xác định phạm vi chia sẻ của Skill.

| Scope | Description | Normal User Visibility | Admin User Visibility |
|---|---|---|---|
| Private | Chỉ người tạo | Chỉ Creator | Tất cả Skill |
| Part | Cùng Part với Creator | User cùng Part với Creator | Tất cả Skill |
| Group | Cùng Group với Creator | User cùng Group với Creator | Tất cả Skill |
| Public | Toàn bộ User | Tất cả User | Tất cả Skill |

Admin User **bypass** Scope Rule — chỉ áp dụng Status Rule.

### Combined Rule (Status + Scope)

```text
Normal User
      ↓
Apply Status Rule
      ↓
Apply Scope Rule
      ↓
Both must pass → Skill is visible

Admin User
      ↓
Apply Status Rule only
      ↓
Scope Rule is bypassed
```

Ví dụ:

```text
Skill: Scope = Part, Status = Approved
Creator: Part A

Normal User (Part A) → Visible (Status ✓, Scope ✓)
Normal User (Part B) → Not visible (Status ✓, Scope ✕)
Normal User (Part A, Status = Draft, Creator = Other) → Not visible (Status ✕, Scope ✓)
Admin User → Visible (Status Rule only)
```

Nếu Scope = Private:

```text
Normal User (Creator) → Visible (all statuses — Status ✓, Scope ✓)
Normal User (Other) → Not visible (Scope ✕)
Admin User → Visible (Status Rule only)
```

---

## 6.2 Delete Permission Matrix

| Login Role | Skill Creator | Delete |
|---|---|---:|
| Normal User | Current User | ✓ |
| Normal User | Other User | ✕ |
| Admin User | Current User | ✓ |
| Admin User | Other User | ✓ |

Permission phải được kiểm tra tại Backend/API.

Không được chỉ disable Button trên UI mà bỏ qua Backend authorization.

---

# 7. Search Specification

| Field | Type | Required | Search Logic |
|---|---|---:|---|
| Skill Name | Text Box | No | Contains |
| Description | Text Box | No | Contains |
| Tag | Tag selector/Text | No | Match selected Tag |
| Creator | Text Box + Search Icon | No | Creator = selected User |
| From Date | Date | No | Created Date >= From Date |
| To Date | Date | No | Created Date <= To Date |
| Creation Status | Multi-select | No | Match selected status |
| Skill Scope | Multi-select | No | Match selected scope |
| Category | Multi-select (chip) | No | Skill thuộc ít nhất 1 category đã chọn (OR) |
| Process Stage | Single-select | No | Skill thuộc stage đã chọn |

---

## 7.1 Skill name Search 

Condition:

Skill Name contains input keyword

Ví dụ:

Input = "Java"

Result:
Java
Java Programming
Advanced Java
Java Spring Boot

Search matching được định nghĩa là Contains.

---

## 7.2 Description Search

Condition:

Description contains input keyword

Ví dụ:
Input = "machine learning"
System trả về các Skill có Description chứa keyword.

---

## 7.3 Tag Search 

Tag là Multi-select Search Field, enter để nhập tag mới

User có thể:
•	Chọn một Tag.
•	Chọn nhiều Tag.
•	Clear Tag.
•	Không chọn Tag.

UI:

Tag
┌────────────────────────────────────┐
│ AI ×   Python ×                    │
└────────────────────────────────────┘

Khi User chọn nhiều Tag:
AI
Python
Data

System áp dụng OR:
Tag = AI
OR
Tag = Python
OR
Tag = Data

Equivalent:

Tag IN ('AI', 'Python', 'Data')

---

## 7.4 Creator Search

Creator không cho phép nhập tự do.

UI:

```text
Creator [Selected User Name] [🔍]
```

Khi click icon Search:

```text
Skill Management
       ↓
Click Creator Search
       ↓
Search User
       ↓
Select User
       ↓
Return User
       ↓
Display User Name on Skill Management
```

Backend sử dụng:

```text
creator_id
```

làm filter key.

UI hiển thị:

```text
creator_name
```

Có thể cung cấp icon `×` để clear Creator.

Nếu Creator không được chọn:

```text
Apply default visibility rule
```

---

## 7.5 Date Search

Created Date hỗ trợ:

- From Date
- To Date

### Case 1 – Cả hai được nhập

```text
From Date <= Created Date <= To Date
```

### Case 2 – Chỉ From Date

```text
Created Date >= From Date
```

### Case 3 – Chỉ To Date

```text
Created Date <= To Date
```

### Case 4 – Không nhập

Không giới hạn Created Date.

### Validation

Nếu:

```text
From Date > To Date
```

System hiển thị:

> From Date cannot be later than To Date.

---

## 7.6 Status

System hỗ trợ đúng 4 Creation Status:

1. Draft
2. Pending Approval
3. Approved
4. Rejected

> **Enum value:** `PENDING_APPROVAL` (label hiển thị: "Pending Approval").
> Giá trị enum `PENDING_APPROVAL` được dùng thống nhất trong toàn bộ tài liệu SRS (Detail/Edit/Add Popup).

Status nên được hiển thị bằng **Status Badge**.

---

## 7.7 Skill Scope

System hỗ trợ đúng 5 Skill Scope:

1. Private
2. Part
3. Group
4. Public

Scope nên được hiển thị bằng **Scope Badge**.

---

## 7.11 Category & Process Stage Filter (v1.1)

| Field | Component | Logic |
|-------|-----------|-------|
| Category | Multi-select (chip) | Skill thuộc ít nhất 1 category đã chọn (OR giữa các category, AND với các filter khác) |
| Process Stage | Single-select (clearable) | Skill thuộc stage đã chọn |

- Danh sách category/stage lấy từ taxonomy master (admin CRUD tại `GET /api/v1/skills/taxonomy`).
- Backend: `categoryIds` (CSV) + `stageId` (single) trong `GET /api/v1/skills`.
- Reset xóa cả 2 filter.

---

## 7.8 Search Processing

Search phải được xử lý theo thứ tự:

```text
Login User
      ↓
Identify Role
      ↓
Apply Status Visibility Rule
      ↓
Apply Scope Visibility Rule
      ↓
Build Accessible Skill Dataset
      ↓
Apply Search Criteria
      ↓
Apply Sorting
      ↓
Return Search Result
```

Visibility Rule (Status + Scope) không được phụ thuộc vào UI.

---

## 7.9 Multiple Search Criteria

Các điều kiện Search có thể sử dụng đồng thời.

Ví dụ:

```text
Skill Name = "Java"
Creator = "John"
Status = Approved
Scope = Public
Created Date = 01/01/2026 ~ 31/03/2026
```

Kết quả phải thỏa mãn **tất cả các điều kiện đã nhập**, đồng thời phải thỏa mãn Visibility Rule (Status + Scope).

---

## 7.10 Reset

Khi click **Reset**:

- Skill Name → Clear
- Description → Clear
- Tag → Clear
- Creator → Clear
- From Date → Clear
- To Date → Clear
- Status → Clear
- Scope → Clear

Sau Reset, Search Condition trở về trạng thái mặc định.

---

# 8. Search Result Grid

Grid gồm các column:

| # | Field | Label | Sort |
|---:|---|---|---:|
| 0 | selection | Checkbox | — |
| 1 | skill_name | Skill Name | ✓ |
| 2 | description | Description | ✕ |
| 3 | creator_name | Creator | ✓ |
| 4 | created_date | Created Date | ✓ |
| 5 | status | Status | ✓ |
| 6 | view_count | Views | ✓ |
| 7 | rating | Rating | ✓ |
| 8 | subscribe | Subscribe | ✕ |

Không có **Action Column**.

---

## 8.0 Grid Width & Scrolling

- Grid PHẢI vừa vặn với chiều rộng vùng nội dung chính (main canvas) khi render lần đầu — **không xuất hiện thanh trượt ngang (horizontal scrollbar)** mặc định.
- Chiều rộng các column được thiết kế để các vùng nội dung chính hiển thị đầy đủ trong chiều rộng khả dụng; giá trị dài được truncate bằng `...` kèm tooltip hiển thị nội dung đầy đủ.
- User có thể kéo dãn (drag) mép header của column để mở rộng column và hiển thị thêm thông tin.
- Thanh trượt ngang chỉ xuất hiện khi tổng chiều rộng các column (do user kéo dãn) vượt quá chiều rộng container.

---

## 8.1 Checkbox

Mỗi row có checkbox.

Header Grid có:

```text
[ ] Select All
```

Khi chọn row:

```text
Selected rows > 0
       ↓
Bulk Action enabled
       ↓
Delete / Download Excel
```

Checkbox không được export sang Excel.

---

## 8.2 Skill Name

Skill Name:

- Hiển thị tên Skill.
- Nếu quá dài → truncate bằng `...`.
- Tooltip hiển thị Full Skill Name.
- Click Skill Name → mở Skill Detail (delay 300ms).
- Double-click row → mở Skill Detail (ngay lập tức, ưu tiên hơn click đơn).

Ví dụ:

```text
AI Skill Management for Enter...
```

Tooltip:

```text
AI Skill Management for Enterprise Knowledge Sharing
```

---

## 8.3 Description

Description:

- Hiển thị short description.
- Nếu quá dài → truncate bằng `...`.
- Tooltip hiển thị Full Description.
- Không expand row khi hover/click.

---

## 8.4 Creator

Grid hiển thị:

```text
Creator Name
```

Backend lưu:

```text
creator_id
creator_name
```

`creator_id` dùng cho:

- Filter
- Visibility
- Delete Permission

---

## 8.5 Created Date

UI recommendation:

```text
DD/MM/YYYY
```

Ví dụ:

```text
07/09/2026
```

Sorting phải dựa trên giá trị DateTime thực tế, không dựa trên chuỗi đã format.

---

## 8.6 Views

Views là số lần Skill Detail được mở thành công.

### View Count Rule

```text
Search Result displayed
→ No View Count increase

Search / Filter
→ No View Count increase

Grid Refresh
→ No View Count increase

Click Skill Name
→ Open Skill Detail successfully
→ View Count +1
```

Mỗi lần mở Skill Detail thành công được tính là một View.

Ví dụ:

```text
Current Views = 100

User opens Detail successfully

New Views = 101
```

---

## 8.7 Rating

Rating hiển thị:

```text
★ Average Rating (Number of Raters)
```

Ví dụ:

```text
★ 4.8 (125)
```

Trong đó:

- `4.8` = Average Rating
- `125` = Number of people who rated

Nếu chưa có Rating:

```text
No rating
```

Không hiển thị:

```text
0.0
-
N/A
```

---

## 8.8 Sorting

Các column hỗ trợ Sort:

- Skill Name
- Creator
- Created Date
- Status
- Views
- Rating

Không hỗ trợ Sort:

- Checkbox
- Description

Sorting phải dựa trên underlying value.

Ví dụ Skill Name trên UI:

```text
Artificial Intelligence Skill Man...
```

Sort phải dùng:

```text
Artificial Intelligence Skill Management
```

không dùng chuỗi đã truncate.

Default sort: 

```text
Created Date DESC
```

Skill mới nhất được hiển thị trước.

---

## 8.9 Subscribe

Cột Subscribe hiển thị nút chuông (bell) để user subscribe/unsubscribe Skill.

| Thuộc tính | Giá trị |
|------------|---------|
| Component | Icon button chuông (bell) |
| Vị trí | Cột "Subscribe" (sau cột Rating) |
| Hiển thị | Chỉ khi `status = APPROVED` **và** login user **không phải** Creator của Skill |
| Ẩn | Skill không APPROVED, hoặc Skill của chính login user → cell trống |
| Chưa subscribe | Icon chuông outline, màu secondary |
| Đã subscribe | Icon chuông đổ đầy, màu primary |

### Quy tắc tương tác

```text
Click icon chuông
      ↓
Toggle subscription (POST/DELETE /api/v1/skills/{id}/subscription)
      ↓
Toast "Subscribed to [name]" / "Unsubscribed from [name]"
      ↓
Đồng bộ state với nút Subscribe trên Detail Popup header
```

- Click icon chuông **không mở Detail Popup** (khác với click Skill Name).
- Subscribe state của Grid và Detail Popup luôn đồng bộ.

---


# 9. Bulk Delete
Select Skills
      ↓
Click Delete
      ↓
Validate every selected Skill
      ↓
 ┌──────────────┴──────────────┐
 │                                        │
All Allowed                           Any Denied
 │                                        │
 ▼                                        ▼
Confirmation                             Alert
 │                                        │
 ▼                                        ▼
Delete ALL                            Delete NONE
 │
 ▼
Refresh Grid


## 9.1 Select Skill

User có thể chọn:

```text
1 Skill
2 Skills
3 Skills
...
```

Sau khi chọn, click Delete.

---

## 9.2 Bulk Delete – Permission Validation

System phải kiểm tra quyền Delete của **từng selected Skill**.

### Case A – Tất cả đều được Delete

```text
Selected Skills
       ↓
Permission Validation
       ↓
All Allowed
       ↓
Confirmation Dialog
       ↓
Delete ALL
       ↓
Refresh Grid
```

### Case B – Có ít nhất một Skill không được Delete

```text
Selected Skills
       ↓
Permission Validation
       ↓
At least one Denied
       ↓
Alert (thông báo số lượng + danh sách Skill không có quyền)
       ↓
Delete NONE
```

Alert hiển thị thông tin chi tiết:

```text
You do not have permission to delete one or more selected Skills.

Skills you cannot delete:
• Skill C (created by Le Van C)
• Skill D (created by Pham Thi D)

Total: X Skills selected, Y Skills cannot be deleted.
```

Các checkbox đã chọn vẫn được giữ nguyên để User điều chỉnh selection.

---

## 9.3 Bulk Delete Confirmation

Nếu tất cả selected Skill đều có quyền Delete:

```text
Are you sure you want to delete the selected N Skills?

[Cancel] [Delete]
```

Click Cancel:

```text
No deletion
Keep selected rows
```

Click Delete:

```text
Delete ALL Skills
      ↓
Success
      ↓
Refresh Grid
```

---

## 9.4 Bulk Delete – All or Nothing

Đây là Business Rule bắt buộc. **Không được phép partial delete.**

Ví dụ User chọn:

```text
Skill A → Can Delete
Skill B → Can Delete
Skill C → Cannot Delete (do người khác tạo)
```

System phải:

```text
Delete A → NO
Delete B → NO
Delete C → NO
```

Không có Skill nào được xóa. Alert hiển thị thông tin chi tiết về các Skill không có quyền:

```text
You do not have permission to delete one or more selected Skills.

Skills you cannot delete:
• Skill C (created by Le Van C)

Total: 3 Skills selected, 1 Skill cannot be deleted.
```

---

# 10. Excel Export

Button:

```text
Download Excel
```
Excel File Naming: Skill_Management_YYYYMMDD_HHmmss.xlsx

## Case 1 – Không chọn row

Export:

```text
ALL Search Results
```

Không giới hạn ở current page.

Ví dụ Search Result:

```text
Page 1 → 20 records
Page 2 → 20 records
Page 3 → 10 records
```

Download Excel:

```text
50 records
```

## Case 2 – Có chọn row

Chỉ export các Skill đã selected.

Ví dụ:

```text
Selected = 3
```

Excel:

```text
3 records
```

---

## 10.1 Excel Authorization

Excel Export phải áp dụng Visibility Rule giống Search.

Không được export Skill mà Login User không có quyền xem.

Logic:

```text
Accessible Skills
      ↓
Search Criteria
      ↓
Selected Rows / All Results
      ↓
Export
```

Không được:

```text
Database All Skills
      ↓
Export
```

---

## 10.2 Excel Columns

Excel gồm:

| # | Column |
|---:|---|
| 1 | Skill Name |
| 2 | Description |
| 3 | Creator |
| 4 | Created Date |
| 5 | Status |
| 6 | Views |
| 7 | Rating |

Không export:

- Checkbox
- Action

Skill Name và Description phải export **full value**, không dùng text đã truncate trên Grid.

Rating:

```text
★ 4.8 (125)
```

Nếu chưa có rating:

```text
No rating
```

---


# 31. Recommended Default Sort

Khuyến nghị:

```text
Created Date DESC
```

Skill mới nhất được hiển thị trước.

**Status: Recommended – cần PO confirm nếu muốn lock thành requirement.**

---

# 11. System Requirements

## Search

| ID | Requirement |
|---|---|
| SR-SM-001 | Provide Skill Search |
| SR-SM-002 | Search by Skill Name |
| SR-SM-003 | Search by Description |
| SR-SM-004 | Search by Tag |
| SR-SM-005 | Search by Creator |
| SR-SM-006 | Creator is Text Box + Search Icon |
| SR-SM-007 | Creator Search opens Search User |
| SR-SM-008 | Selected Creator returns to Skill Management |
| SR-SM-009 | Creator selection can be cleared |
| SR-SM-010 | Search by Created Date |
| SR-SM-011 | Search by four statuses |
| SR-SM-012 | Multiple criteria can be applied simultaneously |
| SR-SM-013 | Empty criterion imposes no restriction |
| SR-SM-014 | Reset clears search conditions |
| SR-SM-015 | From Date cannot be later than To Date |
| SR-SM-016 |	Tag support single or multiple selection|
| SR-SM-017 |	Multiple selected tags use OR logic|
| SR-SM-018 |	Tag filter combines with other criteria using AND|
| SR-SM-019 |	Tag Filter respect visibility rule|
| SR-SM-020 |	Search by Skill Scope|
| SR-SM-021 |	Scope filter supports single or multiple selection|
| SR-SM-022 |	Multiple selected Scope values use OR logic|
| SR-SM-023 |	Scope filter combines with other criteria using AND|
| SR-SM-024 |	Scope filter respects scope visibility rule|
| SR-SM-025 |	Status filter supports single or multiple selection|
| SR-SM-026 |	Multiple selected Status values use OR logic|
| SR-SM-027 |	Status filter combines with Scope filter using AND logic|
| SR-SM-028 |	Status/Scope filter combines with other search criteria using AND|



## Grid

| ID | Requirement |
|---|---|
| SR-SM-020 | Display Search Result as Grid |
| SR-SM-021 | Each row has selection checkbox |
| SR-SM-022 | Header has Select All checkbox |
| SR-SM-023 | Display required Grid columns |
| SR-SM-024 | Truncate long Skill Name + tooltip |
| SR-SM-025 | Truncate long Description + tooltip |
| SR-SM-026 | Display Creator |
| SR-SM-027 | Display Created Date |
| SR-SM-028 | Display Status |
| SR-SM-029 | Display Views |
| SR-SM-030 | Display Rating |
| SR-SM-031 | No rating → No rating |
| SR-SM-032 | Clicking Skill Name opens Skill Detail (delay 300ms) |
| SR-SM-033 | Double-clicking Grid row opens Skill Detail |
| SR-SM-034 | Double-click cancels single-click delay to prevent popup flicker |
| SR-SM-035 | Detail Popup follows logic defined in Detail_Popup_Spec.md and Detail_Popup_SRS.md |

## View & Rating

| ID | Requirement |
|---|---|
| SR-SM-036 | Successful Skill Detail open increments View Count |
| SR-SM-037 | Grid display does not increment View Count |
| SR-SM-038 | Search/Filter/Refresh does not increment View Count |
| SR-SM-039 | Each successful Detail open records one View |
| SR-SM-040 | Calculate Average Rating |
| SR-SM-041 | Display Number of Raters |
| SR-SM-042| Rating format = Average + Rater Count |
| SR-SM-043 | No rating = No rating |

## Visibility

| ID | Requirement |
|---|---|
| SR-SM-041 | Identify login user and role |
| SR-SM-042 | Normal User sees own Skills in all statuses |
| SR-SM-043 | Normal User sees other users' Skills only when Approved |
| SR-SM-044 | Admin sees Skills of any Creator |
| SR-SM-045 | Admin sees all statuses |
| SR-SM-046 | No Creator selection applies default visibility |
| SR-SM-047 | Creator filter combines with visibility rules |
| SR-SM-048 | Visibility enforced by Backend/API |
| SR-SM-049 | Search Result contains only authorized Skills |
| SR-SM-050 | Skill Scope is one of: Private, Part, Group, Public |
| SR-SM-051 | Normal User sees Private Skill only when they are the Creator |
| SR-SM-052 | Normal User sees Part Skill only when they share the same Part with Creator |
| SR-SM-053 | Normal User sees Group Skill only when they share the same Group with Creator |
| SR-SM-054 | Normal User sees Public Skill regardless of Part/Group |
| SR-SM-055 | Admin User sees all Skills regardless of Scope |
| SR-SM-056 | Scope visibility combines with Status visibility using AND logic |
| SR-SM-057 | Scope visibility enforced by Backend/API |

## Delete

| ID | Requirement |
|---|---|
| SR-SM-059 | Support selection for Bulk Delete |
| SR-SM-060 | Normal User can delete own Skills |
| SR-SM-061 | Normal User cannot delete another user's Skills |
| SR-SM-062 | Admin can delete any Skill |
| SR-SM-063 | Validate permission for every selected Skill |
| SR-SM-064 | All deletable → confirmation |
| SR-SM-065 | Any non-deletable → Alert |
| SR-SM-066 | Any non-deletable → delete none |
| SR-SM-067 | Successful deletion refreshes Grid |
| SR-SM-068 | Delete permission enforced by Backend/API |

## Excel

| ID | Requirement |
|---|---|
| SR-SM-069 | Provide Download Excel |
| SR-SM-070 | No selection → export all accessible Search Result |
| SR-SM-071 | Selection → export selected Skills |
| SR-SM-072 | Export all results, not current page only |
| SR-SM-073 | Export follows authorization |
| SR-SM-074 | Excel contains required columns |
| SR-SM-075 | Excel contains full Skill Name and Description |
| SR-SM-076 | No rating → No rating |
| SR-SM-077 | Rating contains score and rater count |

## Detail Popup

| ID | Requirement |
|---|---|
| SR-SM-078 | Clicking Skill Name opens Detail Popup (delay 300ms) |
| SR-SM-079 | Double-clicking Grid row opens Detail Popup |
| SR-SM-080 | Double-click cancels single-click delay to prevent popup flicker |
| SR-SM-081 | Detail Popup follows logic defined in Detail_Popup_Spec.md and Detail_Popup_SRS.md |

## Add

| ID | Requirement |
|---|---|
| SR-SM-082 | Provide Add Skill button |
| SR-SM-083 | Add Skill button is placed next to Download Excel button |
| SR-SM-084 | Clicking Add Skill opens Add Skill Popup |
| SR-SM-085 | Add Skill Popup follows logic defined in Add_Popup_Spec.md and Add_Popup_SRS.md |
| SR-SM-086 | New Skill is created with status DRAFT |
| SR-SM-087 | Successful Skill creation refreshes Grid |

## Sorting

| ID | Requirement |
|---|---|
| SR-SM-088 | Sort Skill Name |
| SR-SM-089 | Sort Creator |
| SR-SM-090 | Sort Created Date |
| SR-SM-091 | Sort Status |
| SR-SM-092 | Sort Views |
| SR-SM-093 | Sort Rating |
| SR-SM-094 | Sort by underlying value |

## Edit Skill

| ID | Requirement |
|---|---|
| SR-SM-095 | Provide "Edit" button in Detail Popup footer |
| SR-SM-096 | "Edit" button visible only to Creator or Admin |
| SR-SM-097 | "Edit" button hidden when status is PENDING_APPROVAL |
| SR-SM-098 | Clicking "Edit" opens Edit Skill Popup |
| SR-SM-099 | Edit Skill Popup fills form with current Skill data |
| SR-SM-100 | Full edit mode when DRAFT or REJECTED (name, scope, description, hashtags, file) |
| SR-SM-101 | Limited edit mode when APPROVED + non-admin (scope, description, hashtags, file editable — only name read-only) |
| SR-SM-102 | Full edit mode when APPROVED + admin |
| SR-SM-103 | Name field locked with info banner when APPROVED + non-admin (scope remains editable) |
| SR-SM-104 | Edit Skill Popup follows logic defined in Edit_Popup_Spec.md and Edit_Popup_SRS.md |
| SR-SM-105 | Successful edit refreshes Detail Popup and Grid |
| SR-SM-106 | Optimistic lock: compare updatedAt, return 409 on conflict |

## Search User

| ID | Requirement |
|---|---|
| SR-SM-107 | Provide 🔍 button in Creator search field |
| SR-SM-108 | Clicking 🔍 opens Search User Popup |
| SR-SM-109 | Search User Popup allows search by name or ID |
| SR-SM-110 | Search User Grid displays Name, ID, and department info ( Part > Group) |
| SR-SM-111 | Search User only displays users with APPROVE status |
| SR-SM-112 | Selecting a User fills Creator field with User Name |
| SR-SM-113 | Search uses selected User ID |
| SR-SM-114 | Clear button (✕) resets Creator field |

## Subscribe

| ID | Requirement |
|---|---|
| SR-SM-115 | Grid displays Subscribe column with bell icon button |
| SR-SM-116 | Subscribe button visible only for APPROVED Skills created by another user |
| SR-SM-117 | Subscribe button hidden for non-APPROVED Skills and own Skills (empty cell) |
| SR-SM-118 | Clicking bell toggles subscription via POST/DELETE /api/v1/skills/{id}/subscription |
| SR-SM-119 | Clicking bell does NOT open Detail Popup |
| SR-SM-120 | Subscribed state: filled bell, primary color; unsubscribed: outline bell, secondary color |
| SR-SM-121 | Grid bell and Detail Popup Subscribe button stay in sync |
| SR-SM-122 | New version of a subscribed Skill notifies subscribers per user Settings > Notifications (in-app and/or email) |

---

# 12. Use Cases

## UC-SM-001 – Search Skill

**Actor:** Normal User / Admin User

### Main Flow

1. User opens Skill Management.
2. System identifies User Role.
3. System applies Status Visibility Rule.
4. System applies Scope Visibility Rule.
5. User enters Search Criteria.
6. User clicks Search.
7. System validates criteria.
8. System queries accessible Skills.
9. System applies filters.
10. System applies sorting.
11. System displays Search Result.

### Alternative

If no result:

> No data found.

---

## UC-SM-002 Search by Tag
1. User opens Tag selector.
2. User selects one or more Tags.
3. System stores selected Tag IDs/values.
4. User clicks Search.
5. System applies OR between selected Tags.
6. System applies AND with other Search Criteria.
7. System applies Visibility Rule (Status + Scope).
8. Search Result is displayed.

---

## UC-SM-009 – Search by Scope

1. User opens Skill Management.
2. User selects one or more Scope values.
3. System stores selected Scope values.
4. User clicks Search.
5. System applies selected Scope filter.
6. System applies AND with other Search Criteria.
7. System applies Status Visibility Rule.
8. System applies Scope Visibility Rule.
9. Search Result is displayed.

---

## UC-SM-010 – Select Creator (Search User Popup)

1. User clicks Creator Search (🔍 button).
2. System opens Search User Popup.
3. User enters search criteria (name or ID).
4. System queries User table and displays matching users with APPROVE status.
5. Grid displays: Name, ID, Part > Group.
6. User selects a User.
7. System returns User ID + User Name.
8. Skill Management displays User Name in Creator field.
9. Search uses selected User ID.

---

## UC-SM-004 – Open Skill Detail

1. User clicks Skill Name.
2. System validates Skill access.
3. System opens Skill Detail.
4. If opening succeeds, View Count increases by 1.

---

## UC-SM-005 – Bulk Delete

1. User selects Skills.
2. User clicks Delete.
3. System validates Delete permission for every selected Skill.
4. If any Skill is unauthorized → Alert with details of unauthorized Skills, no deletion.
5. If all authorized → Confirmation Dialog.
6. User confirms.
7. System deletes all selected Skills.
8. System refreshes Grid.

---

## UC-SM-006 – Download All Search Results

1. User performs Search.
2. User does not select any row.
3. User clicks Download Excel.
4. System retrieves all accessible Search Results.
5. System exports all results.
6. System downloads Excel.

---

## UC-SM-007 – Download Selected Skills

1. User performs Search.
2. User selects one or more rows.
3. User clicks Download Excel.
4. System exports selected authorized Skills.
5. System downloads Excel.

---

## UC-SM-011 – Add New Skill

1. User clicks Add Skill button.
2. System opens Add Skill Popup.
3. User fills in Skill information (name, scope, description, hashtags, file).
4. User clicks Save.
5. System validates input.
6. If validation fails → show error, keep Popup open.
7. If validation passes → call `POST /api/v1/skills`.
8. If creation succeeds → close Popup, refresh Grid, show success toast.
9. If creation fails → show error toast, keep form intact.

Refer to `Add_Popup_Spec.md` and `Add_Popup_SRS.md` for full Popup logic.

---

## UC-SM-012 – Open Detail Popup by Double-Click

1. User double-clicks a row on Skill Management Grid.
2. System cancels any pending single-click delay (300ms) to prevent popup flicker.
3. System identifies the Skill ID of the double-clicked row.
4. System opens Detail Popup for that Skill.
5. System increments View Count.

Refer to `Detail_Popup_Spec.md` and `Detail_Popup_SRS.md` for full Popup logic.

---

## UC-SM-013 – Edit Skill from Detail Popup

1. User opens Skill Detail Popup.
2. User clicks "Edit" button in Detail Popup footer.
3. System checks edit permission:
   - If non-creator + non-admin → toast "You do not have permission to edit this skill"
   - If PENDING_APPROVAL → toast "Cannot edit a skill pending approval"
   - If deleted → toast "Skill has been deleted and cannot be edited"
4. System opens Edit Skill Popup and fills form with current Skill data.
5. If APPROVED + non-admin → lock name field only, show info banner (scope remains editable).
6. If APPROVED + admin → unlock all fields.
7. User modifies form fields (name, scope, description, hashtags, file).
8. User clicks "Save Changes".
9. System validates input.
10. If validation fails → highlight error fields, show error toast.
11. If validation passes → call `PUT /api/v1/skills/{id}` (multipart/form-data).
12. If success → close Edit Popup, refresh Detail Popup, refresh Grid, show success toast.
13. If 409 Conflict → toast "Skill has been changed by another user", close Edit Popup.
14. If failure → toast "Unable to save changes. Please try again.", keep form intact.

Refer to `Edit_Popup_Spec.md` and `Edit_Popup_SRS.md` for full Popup logic.

---

## UC-SM-008 – Sort Search Result

1. User clicks sortable column.
2. System identifies sort field.
3. System sorts using actual underlying value.
4. Grid is refreshed.

---

# 13. Acceptance Criteria

## AC-SM-001 – Multiple Search Criteria

**Given** User has permission to view Skills  
**When** User enters multiple search criteria  
**Then** Search Result must satisfy all entered criteria and Visibility Rule.

---

## AC-SM-002 – Normal User Own Skill

**Given** User is Normal User  
**And** Skill belongs to current User  
**When** User searches Skill  
**Then** Skill can be displayed regardless of status.

---

## AC-SM-003 – Normal User Other Skill

**Given** User is Normal User  
**And** Skill belongs to another User  
**When** User searches Skill  
**Then** Skill is displayed only if status = Approved.

---

## AC-SM-004 – Admin Visibility

**Given** User is Admin  
**When** User searches Skill  
**Then** Skills from all Creators and all four statuses can be displayed.

---

## AC-SM-005 – Creator Selection

**Given** User is on Skill Management  
**When** User clicks Creator Search  
**Then** Search User is opened  
**And** selected User is returned  
**And** Creator field displays selected User  
**And** search uses selected Creator ID.

---

## AC-SM-006 – Long Text

**Given** Skill Name or Description exceeds Grid display length  
**When** Grid is displayed  
**Then** text is truncated with `...`  
**And** full value is available through tooltip.

---

## AC-SM-007 – View Count

**Given** Skill currently has View Count = 100  
**When** User successfully opens Skill Detail  
**Then** View Count becomes 101.

**And**

Displaying the Skill in Search Result must not increase View Count.

---

## AC-SM-008 – Rating

**Given** Skill has Average Rating = 4.8  
**And** Number of Raters = 125  
**When** Grid is displayed  
**Then** Rating displays `★ 4.8 (125)`.

If no rating exists:

**Then** display `No rating`.

---

## AC-SM-009 – Bulk Delete Allowed

**Given** all selected Skills are deletable  
**When** User clicks Delete  
**Then** confirmation dialog is displayed.

After confirmation:

**Then** selected Skills are deleted  
**And** Grid is refreshed.

---

## AC-SM-010 – Bulk Delete Denied

**Given** at least one selected Skill is not deletable  
**When** User clicks Delete  
**Then** permission Alert is displayed  
**And** no selected Skill is deleted  
**And** selected checkboxes remain selected.

---

## AC-SM-011 – Export All

**Given** Search Result contains 50 accessible Skills  
**And** no row is selected  
**When** User clicks Download Excel  
**Then** Excel contains all 50 Search Results.

---

## AC-SM-012 – Export Selected

**Given** Search Result contains 50 Skills  
**And** User selects 3 Skills  
**When** User clicks Download Excel  
**Then** Excel contains only the 3 selected Skills.

---

## AC-SM-013 – Full Export Values

**Given** Skill Name and Description are truncated in Grid  
**When** User downloads Excel  
**Then** Excel contains the complete Skill Name and Description.

---

## AC-SM-014 – Export Authorization

**Given** User cannot view a Skill  
**When** User downloads Excel  
**Then** unauthorized Skill must not be included.

---

## AC-SM-015 – Sorting

**Given** Grid contains multiple Skills  
**When** User sorts a supported column  
**Then** system sorts according to the actual underlying value.

---

## AC-SM-016 Single Tag

**Given** User selects Tag AI
**When** User clicks Search
**Then** only Skills containing Tag AI are returned.

---

## AC-SM-017 Multiple Tags

**Given** User selects AI, Python, Data
**When** User searches
**Then** Skill containing at least one selected Tag is returned.

---

## AC-SM-018 No Tag

**Given** User selects no Tag
**When** User searches
**Then** Tag does not restrict Search Result.

---

## AC-SM-019 – Private Scope Visibility

**Given** Skill has Scope = Private  
**And** User is Normal User  
**And** User is the Creator  
**When** User searches Skill  
**Then** Skill is displayed.

---

## AC-SM-020 – Private Scope Hidden from Others

**Given** Skill has Scope = Private  
**And** User is Normal User  
**And** User is NOT the Creator  
**When** User searches Skill  
**Then** Skill is NOT displayed.


---

## AC-SM-021 – Part Scope Visibility

**Given** Skill has Scope = Part  
**And** User is Normal User  
**And** User is in the same Part as Creator  
**When** User searches Skill  
**Then** Skill is displayed.

---

## AC-SM-022 – Part Scope Hidden from Other Parts

**Given** Skill has Scope = Part  
**And** User is Normal User  
**And** User is NOT in the same Part as Creator  
**When** User searches Skill  
**Then** Skill is NOT displayed.

---

## AC-SM-023 – Group Scope Visibility

**Given** Skill has Scope = Group  
**And** User is Normal User  
**And** User is in the same Group as Creator  
**When** User searches Skill  
**Then** Skill is displayed.

---

## AC-SM-024 – Group Scope Hidden from Other Groups

**Given** Skill has Scope = Group  
**And** User is Normal User  
**And** User is NOT in the same Group as Creator  
**When** User searches Skill  
**Then** Skill is NOT displayed.

---

## AC-SM-025 – Public Scope Visibility

**Given** Skill has Scope = Public  
**And** User is Normal User  
**When** User searches Skill  
**Then** Skill is displayed regardless of Part/Group.

---

## AC-SM-026 – Admin Bypasses Scope

**Given** User is Admin  
**And** Skill has any Scope (Private/Part/Group/Public)  
**When** User searches Skill  
**Then** Skill is displayed regardless of Scope.

---

## AC-SM-027 – Add Skill Button

**Given** User is on Skill Management screen  
**When** User looks at the action bar  
**Then** Add Skill button is visible next to Download Excel button.

---

## AC-SM-028 – Add Skill Opens Popup

**Given** User clicks Add Skill button  
**Then** Add Skill Popup is displayed  
**And** form is reset to default state  
**And** visibility scope defaults to Private.

---

## AC-SM-029 – Add Skill Success

**Given** User fills valid Skill information in Add Skill Popup  
**When** User clicks Save  
**Then** Skill is created with status DRAFT  
**And** Popup is closed  
**And** Grid is refreshed  
**And** new Skill appears in Search Result  
**And** success toast is displayed.

---

## AC-SM-030 – Add Skill Validation Failure

**Given** User fills invalid Skill information  
**When** User clicks Save  
**Then** error is highlighted on the invalid field  
**And** error toast is displayed  
**And** Popup remains open.

---

## AC-SM-031 – Double-Click Opens Detail Popup

**Given** User is on Skill Management screen  
**When** User double-clicks a Grid row  
**Then** Detail Popup is opened for the corresponding Skill  
**And** View Count is incremented by 1.

---

## AC-SM-032 – Double-Click Cancels Single-Click Delay

**Given** User clicks a Skill Name and quickly clicks again (double-click)  
**When** second click occurs within 300ms  
**Then** single-click delay is cancelled  
**And** Detail Popup opens immediately without flicker.

---

## AC-SM-033 – Edit Skill Popup Opens

**Given** User opens Detail Popup for a Skill  
**And** User is Creator or Admin  
**And** Skill status is DRAFT, REJECTED, or APPROVED  
**When** User clicks "Edit" button  
**Then** Edit Skill Popup is opened  
**And** form is filled with current Skill data.

---

## AC-SM-034 – Edit Skill Full Mode (DRAFT/REJECTED)

**Given** User opens Edit Skill Popup for DRAFT or REJECTED Skill  
**Then** all fields are editable (name, scope, description, hashtags, file)  
**And** info banner is hidden.

---

## AC-SM-035 – Edit Skill Limited Mode (APPROVED + non-admin)

**Given** User opens Edit Skill Popup for APPROVED Skill  
**And** User is non-admin  
**Then** name field is read-only  
**And** scope, description, hashtags, file are editable  
**And** info banner is displayed explaining limited edit.

---

## AC-SM-036 – Edit Skill Full Mode (APPROVED + admin)

**Given** User opens Edit Skill Popup for APPROVED Skill  
**And** User is Admin  
**Then** all fields are editable (name, scope, description, hashtags, file)  
**And** info banner is hidden.

---

## AC-SM-037 – Edit Skill Save Success

**Given** User modifies Skill information in Edit Popup  
**When** User clicks "Save Changes"  
**Then** changes are saved via PUT /api/v1/skills/{id}  
**And** Edit Popup is closed  
**And** Detail Popup is refreshed  
**And** Grid is refreshed  
**And** success toast is displayed.

---

## AC-SM-038 – Edit Skill Save Conflict

**Given** Another user modifies the same Skill while Edit Popup is open  
**When** User clicks "Save Changes"  
**Then** 409 Conflict is returned  
**And** toast "Skill has been changed by another user" is displayed  
**And** Edit Popup is closed  
**And** Detail Popup is refreshed.

---

## AC-SM-039 – Search User Popup Opens

**Given** User is on Skill Management screen  
**When** User clicks 🔍 button in Creator field  
**Then** Search User Popup is opened  
**And** search input is focused  
**And** Grid displays columns: Name, ID, Part > Group  
**And** only users with APPROVE status are displayed.

---

## AC-SM-040 – Search User by Name or ID

**Given** Search User Popup is open  
**When** User enters name or ID in search field  
**Then** Grid filters to matching users with APPROVE status  
**And** displays Name, ID,  Part > Group.

---

## AC-SM-041 – Search User Select

**Given** Search User Popup is open  
**When** User selects a User from search results  
**Then** Creator field is filled with selected User Name  
**And** Search User Popup is closed.

---

## AC-SM-042 – Subscribe Column Visible

**Given** Skill has status = APPROVED  
**And** Skill is created by another User  
**When** Grid is displayed  
**Then** Subscribe column shows a bell icon button.

---

## AC-SM-043 – Subscribe Toggle

**Given** Subscribe bell is visible for a Skill  
**When** User clicks the bell  
**Then** subscription is toggled (POST/DELETE /api/v1/skills/{id}/subscription)  
**And** bell state updates (outline ↔ filled, primary color when subscribed)  
**And** confirmation toast is displayed  
**And** Detail Popup header button (if open) is synchronized  
**And** Detail Popup is NOT opened.

---

## AC-SM-044 – Subscribe Hidden

**Given** Skill is NOT APPROVED, OR Skill belongs to the current User  
**When** Grid is displayed  
**Then** Subscribe column cell is empty (no bell)  
**And** Detail Popup header shows no Subscribe button.

---

## AC-SM-045 – New Version Notification

**Given** User is subscribed to a Skill  
**When** the Skill is edited and saved (new version)  
**Then** User receives in-app notification if `inapp_notify = ON` in Settings > Notifications  
**And** User receives email if `email_notify = ON` in Settings > Notifications  
**And** disabled channels produce no notification.

---

## AC-SM-046 – Scope Filter

**Given** User selects Scope = Public  
**When** User clicks Search  
**Then** only Skills with Scope = Public that satisfy Visibility Rules are returned.

---

## AC-SM-047 – Multiple Status Filter (OR Logic)

**Given** User selects Status = Draft and Status = Approved  
**When** User clicks Search  
**Then** Skills with status Draft OR Approved that satisfy Visibility Rules are returned.

---

## AC-SM-048 – Multiple Scope Filter (OR Logic)

**Given** User selects Scope = Part and Scope = Public  
**When** User clicks Search  
**Then** Skills with scope Part OR Public that satisfy Visibility Rules are returned.

---

## AC-SM-033 – Status and Scope Combined (AND Logic)

**Given** User selects Status = Draft and Scope = Part  
**When** User clicks Search  
**Then** Skills with status Draft AND scope Part that satisfy Visibility Rules are returned.

---

## AC-SM-034 – Multiple Status and Multiple Scope Combined

**Given** User selects Status = Draft and Status = Approved, and Scope = Part and Scope = Public  
**When** User clicks Search  
**Then** Skills matching (Draft OR Approved) AND (Part OR Public) that satisfy Visibility Rules are returned.


# 14. Data Dictionary

| Field | Type | Description |
|---|---|---|
| skill_id | BIGINT | Unique Skill ID |
| skill_name | String | Skill Name |
| description | Text | Skill Description |
| creator_id | BIGINT | User ID of Skill Creator |
| creator_name | String | Creator display name |
| created_date | DateTime | Skill creation timestamp |
| status | Enum | DRAFT / PENDING_APPROVAL / APPROVED / REJECTED |
| view_count | Integer | Number of successful Skill Detail opens |
| rating_score | Decimal | Average Rating |
| rating_count | Integer | Number of people who rated |
| tag_id       | BIGINT |	Tag ID |
| tag_name     | String	| Tag display name |
| scope        | Enum | PRIVATE/ PART / GROUP / PUBLIC |
| version      | Integer | Current Skill version. Chỉ tăng khi version update được approve (không tăng khi edit thường) |
| subscribers  | BIGINT[] (relation `skill_subscriptions`) | User IDs subscribed to the Skill (new-version tracking) |


---

# 15. API / Backend Requirements

## 15.1 Search Skills

Conceptual API:

```text
GET /api/v1/skills
```

Request (query params):

```text
skillName
description
tags        (CSV: "tag1,tag2" — OR giữa các tag)
creatorId
createdDateFrom   (ISO date)
createdDateTo     (ISO date)
statuses    (CSV: "DRAFT,PENDING_APPROVAL,APPROVED,REJECTED")
scopes      (CSV: "PRIVATE,PART,GROUP,PUBLIC")
categoryIds (CSV: "1,2" — OR giữa các category)
stageId     (single)
sortBy      ("name" | "createdDate", mặc định "createdDate")
sortDirection ("ASC" | "DESC", mặc định "DESC")
page        (mặc định 1)
pageSize    (mặc định 20)
```

Response (envelope `{ success, data }`):

```text
data.totalCount
data.page
data.pageSize
data.items[]:
    id
    name
    description
    creatorId
    creatorName
    createdAt
    status
    scope
    viewCount
    avgRating
    ratingCount
    subscribed
```

> `tags` lưu dưới dạng JSONB array trong `skills_master.hashtags` (không có bảng `tag` riêng).

---

## 15.2 Search Permission Processing

Backend phải xác định:

```text
current_user_id
current_user_role
```

### Normal User

Backend phải xác định:

```text
current_user_id
current_user_role
viewer_parts[]    (distinct Part từ organization_master, EMPLOYED='Y', qua knox_id)
viewer_groups[]   (distinct Group từ organization_master, EMPLOYED='Y', qua knox_id)
```

Conceptual condition (AND các điều kiện, skill `deleted_yn = 'N'`):

```text
-- Status rule: skill của mình (mọi status) HOẶC skill của người khác (chỉ APPROVED)
-- HOẶC skill của người khác đang có version update pending (v1.1 — version cũ vẫn live)
(
    created_by = current_user_id
    OR approval_status = APPROVED
    OR (approval_status = PENDING_APPROVAL AND pending_metadata IS NOT NULL)
)
AND
-- Scope rule
(
    (public_scope = PRIVATE AND created_by = current_user_id)
    OR public_scope = PUBLIC
    OR (public_scope = PART
        AND EXISTS (org row của creator (knox_id, EMPLOYED='Y') có Part ∈ viewer_parts))
    OR (public_scope = GROUP
        AND EXISTS (org row của creator (knox_id, EMPLOYED='Y') có Group ∈ viewer_groups))
)
```

> Part/Group của creator được resolve qua `organization_master` JOIN `"user"` on `knox_id` (subquery EXISTS), không phải cột Part/Group trên `"user"`.

### Admin

```text
All Skills (status rule + scope rule bypassed; chỉ lọc deleted_yn = 'N')
```

Sau đó mới áp dụng:

```text
Skill Name
Description
Tag
Creator
Created Date
Status
Scope
```

---

## 15.3 Search User API

Conceptual:

```text
GET /api/v1/users/search-for-skill
```

Input:

```text
keyword        (search by name or knox_id)
page
pageSize
```

Output (envelope `{ success, data }`):

```text
data.totalCount
data.page
data.pageSize
data.items[]:
    id
    name
    knoxId
    orgs[]: { part, group }
```

Mục đích:

- Search User theo tên hoặc Knox ID
- Grid hiển thị: Name, Knox ID, Part > Group
- Chỉ trả về user có trạng thái ACTIVE
- Select Creator
- Return Creator ID + Name

---

## 15.4 Delete API

Conceptual:

```text
DELETE /api/v1/skills
GET /api/v1/skills/check-delete?ids=1,2,3   (pre-delete validation)
```

Request:

```json
{
  "ids": [1, 2, 3]
}
```

Backend phải:

1. Validate User.
2. Validate Role.
3. Validate Delete Permission của từng Skill (creator hoặc admin).
4. Nếu có một Skill không được phép → reject toàn bộ request (all-or-nothing).
5. Nếu tất cả được phép → soft delete toàn bộ (`deleted_yn = 'Y'`).

---

# 15.5 Skill Detail API

Conceptual:

```text
GET /api/v1/skills/{skill_id}
```

Khi Skill Detail được mở thành công:

```text
view_count = view_count + 1
```

Search API không được trigger View Count.

---

## 15.6 Excel Export API

Conceptual:

```text
GET /api/v1/skills/export
```

Nếu không selected:

```text
Export Search Result
```

Nếu selected:

```text
skill_ids = selected IDs
```

Backend phải kiểm tra authorization trước khi export.

---

## 15.7 Add Skill API

Conceptual:

```text
POST /api/v1/skills
Content-Type: multipart/form-data
```

Full logic defined in `Add_Popup_Spec.md` and `Add_Popup_SRS.md`.

Key points:
- Creates Skill with `approval_status = DRAFT`.
- `public_scope` defaults to `PRIVATE`.
- Skill name must be unique **per creator** (case-insensitive, soft-delete excluded — V23).
- File upload is optional (multiple files, stored in `skill_files`).

---

## 15.8 Edit Skill API

Conceptual:

```text
PUT /api/v1/skills/{id}
Content-Type: multipart/form-data
```

Full logic defined in `Edit_Popup_Spec.md` and `Edit_Popup_SRS.md`.

Key points:
- Updates Skill fields: name, description, publicScope, categoryIds, stageIds, hashtags, files.
- `updatedAt` form field included for optimistic lock.
- Name read-only when APPROVED + non-admin (SKILL_FIELD_READ_ONLY error). Scope remains editable for both creator and admin.
- Name duplicate check **per creator** (case-insensitive, excluding current Skill, soft-delete excluded — V23).
- **Files**: DRAFT/REJECTED → new files appended (multi-file, `skill_files`). APPROVED → files param **bị bỏ qua** (file chỉ quản lý qua Update Version; `DELETE /files/{fileId}` khi APPROVED → `403 SKILL_FILE_LOCKED`).
- **Version KHÔNG bump** khi edit thường — version chỉ tăng khi version update được approve.
- 409 Conflict returned on optimistic lock failure.
- On successful save của skill APPROVED: notify all subscribers of the Skill (see §15.9).

---

## 15.9 Subscribe API

Conceptual:

```text
POST   /api/v1/skills/{skill_id}/subscription
DELETE /api/v1/skills/{skill_id}/subscription
```

Backend phải:

1. Validate User (JWT).
2. Validate Skill tồn tại và User có quyền xem Skill (Visibility Rule).
3. **POST**: chỉ cho phép khi `status = APPROVED` và `creator_id != current_user_id`.
4. **DELETE**: bỏ subscription (idempotent — không lỗi nếu chưa subscribe).
5. Upsert/delete row trong bảng `skill_subscriptions (skill_id, user_id)`.

Notification khi Skill có phiên bản mới (gọi sau mỗi save thành công):

```text
Skill saved → version + 1
      ↓
Fetch subscribers của Skill
      ↓
For each subscriber:
    read user_settings (email_notify, inapp_notify)
      ↓
    inapp_notify = TRUE → in-app notification: "[name] has a new version (vN)"
    email_notify = TRUE → email: "[name] new version (vN)"
```

Kênh notification tuân theo Settings > Notifications của từng subscriber. Chi tiết flow: `Detail_Popup_Spec.md` §6.11.

---

## 15.10 Version Update API (v1.1 — Skill APPROVED)

### 15.10.1 Submit version update

```text
POST /api/v1/skills/{id}/versions
Content-Type: multipart/form-data
```

Params: `name`, `description`, `publicScope`, `hashtags`, `categoryIds[]`, `stageIds[]`, `file[]`, `removeFileIds[]`.

Quy tắc:
- Chỉ **creator hoặc admin** của skill **APPROVED** (không phải PENDING).
- **Bắt buộc ≥ 1 file mới** (không rỗng) — nếu không → `400 SKILL_VERSION_FILE_REQUIRED`.
- `removeFileIds`: các file active (`pending_yn = 'N'`) thuộc skill — file bị xóa khỏi version mới nhưng **giữ trên disk** để version cũ vẫn download được.
- Không được có version update pending thứ 2 (`pending_metadata IS NOT NULL` → `400 SKILL_VERSION_UPDATE_NOT_ALLOWED`).
- Rate limit riêng: 5 lần / user / 60s.

Hành vi: lưu `pending_metadata` (JSONB) + `pending_remove_file_ids` (JSONB) + files mới với `pending_yn = 'Y'` → transition `APPROVED → PENDING_APPROVAL`. **Version cũ vẫn live cho mọi user** (visibility §6.1).

### 15.10.2 Cancel version update

```text
POST /api/v1/skills/{id}/versions/cancel
```

Creator hoặc admin; skill `PENDING_APPROVAL` + `pending_metadata IS NOT NULL`. Xóa files pending (disk + row), clear pending_* → skill về `APPROVED`.

### 15.10.3 Approve / Reject (qua workflow `PUT /{id}/status`)

- **Approve** (`PENDING_APPROVAL → APPROVED`, có `pending_metadata`): snapshot **trạng thái cũ** vào `skill_version` + `skill_version_file` (metadata + files active + files sắp bị remove), apply pending_metadata, files pending → active, xóa `skill_file` row của files bị remove (giữ disk), `version + 1`, notify subscribers.
- **Reject** (`PENDING_APPROVAL → REJECTED` request, có `pending_metadata`): skill về **APPROVED** (không phải REJECTED — version cũ vẫn live), giữ `rejectionReason` để hiển thị, xóa files pending.

### 15.10.4 List versions

```text
GET /api/v1/skills/{id}/versions
```

Trả về các version đã snapshot (mới nhất trước): `version, name, description, publicScope, hashtags, creatorName, createdAt, files[{id, fileName, fileSize}]`. Version hiện tại không có row — frontend tự thêm "Version N (current)" từ detail.

### 15.10.5 Download files của version / pending

```text
GET /api/v1/skills/{id}/versions/{version}/files/{fileId}/download
GET /api/v1/skills/{id}/versions/{version}/files/download-all      (zip)
GET /api/v1/skills/{id}/pending-versions/files/{fileId}/download   (creator/admin, chỉ khi pending)
GET /api/v1/skills/{id}/pending-versions/files/download-all        (zip, creator/admin)
```

Quy tắc download: creator → mọi status; admin → PENDING_APPROVAL/APPROVED; user khác → APPROVED (hoặc skill đang version-update-pending — vẫn coi là live).

---

# 16. Error Messages

| ID | Message |
|---|---|
| ERR-SM-001 | From Date cannot be later than To Date. |
| ERR-SM-002 | You do not have permission to delete one or more selected Skills. |
| ERR-SM-003 | No data found. |
| ERR-SM-004 | Unable to delete selected Skills. Please try again. |
| ERR-SM-005 | Unable to download Excel. Please try again. |

---

# 17. Non-Functional Requirements

## Security

- Backend phải enforce Visibility.
- Backend phải enforce Delete Permission.
- Không được tin tưởng permission từ UI.
- User không thể truy cập Skill bằng cách sửa Skill ID trên URL/API.

## Performance

Khuyến nghị:

- Search response ≤ 3 giây trong điều kiện tải thông thường.
- Excel Export phải xử lý toàn bộ Search Result mà không giới hạn current page.

## Consistency

View Count phải được cập nhật nhất quán khi Detail mở thành công.

## Data Integrity

Bulk Delete phải đảm bảo:

```text
All Success
OR
All Failed
```

Không partial delete. Nếu có bất kỳ Skill nào không có quyền xóa, tất cả Skill đều không được xóa.

---

# 18. QA Test Scenario

| Test ID | Scenario | Expected |
|---|---|---|
| TC-SM-001 | Search by Skill Name | Correct Skills displayed |
| TC-SM-002 | Search by Description | Correct Skills displayed |
| TC-SM-003 | Search by Tag | Correct Skills displayed |
| TC-SM-004 | Search by Creator | Correct Creator Skills displayed |
| TC-SM-005 | Search by Date Range | Correct date range |
| TC-SM-006 | Search by Status | Correct statuses |
| TC-SM-007 | Multiple filters | AND condition |
| TC-SM-008 | Reset | Conditions cleared |
| TC-SM-009 | Invalid Date Range | Error displayed |
| TC-SM-010 | Normal User own Draft | Visible |
| TC-SM-011 | Normal User other Draft | Not visible |
| TC-SM-012 | Normal User other Approved | Visible |
| TC-SM-013 | Admin other Draft | Visible |
| TC-SM-014 | Admin other Rejected | Visible |
| TC-SM-015 | Long Skill Name | Truncated + tooltip |
| TC-SM-016 | Long Description | Truncated + tooltip |
| TC-SM-017 | Click Skill Name | Detail opens |
| TC-SM-018 | Open Detail | View +1 |
| TC-SM-019 | Search Result display | View unchanged |
| TC-SM-020 | No Rating | No rating |
| TC-SM-021 | Rating display | Correct score/count |
| TC-SM-022 | Normal User delete own | Allowed |
| TC-SM-023 | Normal User delete other's | Denied |
| TC-SM-024 | Admin delete other's | Allowed |
| TC-SM-025 | Bulk delete all allowed | Delete successful |
| TC-SM-026 | Bulk delete mixed permission | Nothing deleted |
| TC-SM-027 | Delete cancel | Nothing deleted |
| TC-SM-028 | Export without selection | All results exported |
| TC-SM-029 | Export selected | Selected only |
| TC-SM-030 | Export multi-page | All results exported |
| TC-SM-031 | Unauthorized export | Unauthorized data excluded |
| TC-SM-032 | Sort Skill Name | Correct sorting |
| TC-SM-033 | Sort Creator | Correct sorting |
| TC-SM-034 | Sort Created Date | Correct sorting |
| TC-SM-035 | Sort Status | Correct sorting |
| TC-SM-036 | Sort Views | Correct sorting |
| TC-SM-037 | Sort Rating | Correct sorting |
| TC-SM-038 | Search by Scope | Correct Scope filter applied |
| TC-SM-039 | Search by single Status | Correct Status filter applied |
| TC-SM-040 | Search by multiple Status (Draft + Approved) — OR logic | Skills with Draft OR Approved returned |
| TC-SM-041 | Search by multiple Scope (Part + Public) — OR logic | Skills with Part OR Public returned |
| TC-SM-042 | Search by multiple Status + multiple Scope — AND logic | Skills matching (Status A OR B) AND (Scope X OR Y) returned |
| TC-SM-043 | Search by single Status + single Scope — AND logic | Skills matching both Status and Scope returned |
| TC-SM-044 | Normal User views own Private Skill | Visible |
| TC-SM-045 | Normal User views other's Private Skill | Not visible |
| TC-SM-046 | Normal User (same Part) views Part Skill | Visible |
| TC-SM-047 | Normal User (diff Part) views Part Skill | Not visible |
| TC-SM-048 | Normal User (same Part) views Part Skill | Visible |
| TC-SM-049 | Normal User (diff Part) views Part Skill | Not visible |
| TC-SM-050 | Normal User (same Group) views Group Skill | Visible |
| TC-SM-051 | Normal User (diff Group) views Group Skill | Not visible |
| TC-SM-052 | Normal User views Public Skill | Visible |
| TC-SM-053 | Admin views Private Skill | Visible |
| TC-SM-054 | Admin views Part Skill | Visible |
| TC-SM-055 | Scope + Status combined (Part + Draft, other user) | Not visible |
| TC-SM-056 | Scope + Status combined (Part + Draft, own skill) | Visible |
| TC-SM-057 | Add Skill button is visible next to Download Excel | Button visible |
| TC-SM-058 | Click Add Skill opens Popup | Popup displayed |
| TC-SM-059 | Add Skill Popup form resets to default | Form reset |
| TC-SM-060 | Add Skill success — new Skill appears in Grid | Grid refreshed |
| TC-SM-061 | Add Skill fails — name duplicate | Error toast |
| TC-SM-062 | Add Skill fails — empty name | Field highlighted |
| TC-SM-063 | Double-click row opens Detail Popup | Popup displayed |
| TC-SM-064 | Double-click cancels single-click delay | No popup flicker |
| TC-SM-065 | Detail Popup view count increments | View +1 |
| TC-SM-066 | Edit Skill button visible in Detail Popup footer (Creator + DRAFT) | Button visible |
| TC-SM-067 | Edit Skill button hidden when PENDING_APPROVAL | Button hidden |
| TC-SM-068 | Edit Skill Popup opens with current Skill data | Form filled |
| TC-SM-069 | Edit Skill full mode (DRAFT) — all fields editable | All fields editable |
| TC-SM-070 | Edit Skill limited mode (APPROVED + non-admin) — name read-only, scope editable | Name locked, scope editable |
| TC-SM-071 | Edit Skill full mode (APPROVED + admin) — all fields editable | All fields editable |
| TC-SM-072 | Edit Skill save success — Detail and Grid refresh | Refreshed |
| TC-SM-068 | Edit Skill save conflict (409) — toast displayed | Conflict toast |
| TC-SM-069 | Edit Skill save fails — name duplicate | Error toast |
| TC-SM-070 | Non-creator click "Edit" — toast no permission | Permission toast |
| TC-SM-071 | Search User Popup opens on 🔍 click | Popup displayed |
| TC-SM-072 | Search User select — Creator field filled | Creator filled |
| TC-SM-073 | Search User clear — Creator field reset | Creator cleared |
| TC-SM-074 | Subscribe column shows bell for APPROVED Skill of another user | Bell icon visible |
| TC-SM-075 | Click bell toggles subscription | State + toast updated, Detail NOT opened |
| TC-SM-076 | Subscribe hidden for own Skill / non-APPROVED Skill | Empty cell |
| TC-SM-077 | Subscribed Skill gets new version (edit + save) | Notification sent per Settings (in-app/email) |
| TC-SM-078 | In-app/email notify disabled in Settings | No notification on disabled channel |

---

# 19. Requirement Traceability Matrix

| Requirement | BR | SR | AC | QA |
|---|---|---|---|---|
| Skill Search | BR-001 | SR-001~015 | AC-001 | TC-001~009 |
| Grid | BR-001 | SR-016~028 | AC-006 | TC-015~017 |
| View Count | BR-008 | SR-029~032 | AC-007 | TC-018~019 |
| Rating | BR-009/010 | SR-033~036 | AC-008 | TC-020~021 |
| Visibility | BR-002~004 | SR-037~045 | AC-002~004 | TC-044~056 |
| Delete | BR-005~007 | SR-046~055 | AC-009~010 | TC-022~027 |
| Excel | BR-011/012 | SR-056~064 | AC-011~014 | TC-028~031 |
| Sorting | BR-001 | SR-065~071 | AC-015 | TC-032~037 |
| Multi-Select Status/Scope | BR-013~018 | SR-020~028 | AC-030~034 | TC-038~043 |
| Add Skill | BR-030~035 | SR-082~087 | AC-031~034 | TC-057~062 |
| Detail Popup (Double-Click) | BR-036~039 | SR-078~081 | AC-035~036 | TC-063~065 |
| Edit Skill | BR-040~046 | SR-095~106 | AC-037~042 | TC-066~073 |
| Search User | BR-047~052 | SR-107~114 | AC-043~045 | TC-071~073 |
| Subscribe | BR-053~057 | SR-115~122 | AC-046~049 | TC-074~078 |

---

# 20. Screen Layout

```text
┌─────────────────────────────────────────────────────────────┐
│ Skill Management                                            │
├─────────────────────────────────────────────────────────────┤
│ Skill Name     [________________]                           │
│ Description    [________________]                           │
│ Tag            [________________]                           │
│ Creator        [________________] [🔍]                      │
│ Created Date   [From ______] ~ [To ______]                 │
│ Status         [All ▼]                                      │
│ Scope          [All ▼]                                      │
│                                                             │
│                 [Search] [Reset]                            │
├─────────────────────────────────────────────────────────────┤
│ [Delete]                         [Download Excel] [Add]     │
├─────────────────────────────────────────────────────────────┤
│ [ ] │ Skill Name │ Description │ Creator │ Created │ Status │
│─────┼────────────┼─────────────┼─────────┼─────────┼────────│
│ [ ] │ Skill A    │ ...         │ John    │ 01/09   │ Draft  │
│ [ ] │ Skill B    │ ...         │ Anna    │ 02/09   │ Pub.   │
│ [ ] │ Skill C    │ ...         │ John    │ 03/09   │ Pend.  │
│                                                             │
│                    Pagination                               │
└─────────────────────────────────────────────────────────────┘
```

Grid columns tiếp:

```text
Views | Rating | Subscribe
```

---

# 21. End-to-End Flow

```text
                    Login
                      │
                      ▼
             Skill Management
                      │
                      ▼
              Identify Role
                      │
             ┌────────┴────────┐
             │                 │
       Normal User          Admin
             │                 │
             ▼                 ▼
     Status + Scope Rule   Status Rule Only
             │                 │
             └────────┬────────┘
                      ▼
               Search Criteria
                      │
                      ▼
                 Search Result
                      │
        ┌─────────────┼──────────────┐
        │             │              │
        ▼             ▼              ▼
     Detail         Delete         Export
        │             │              │
        ▼             ▼              ▼
   View Count     Permission      Excel
      +1          Validation
                      │
             ┌────────┴────────┐
             │                 │
          Allowed           Denied
             │                 │
             ▼                 ▼
        Confirmation        Alert
             │
             ▼
           Delete

Add Skill Flow:

```text
Click Add Button
       │
       ▼
  Add Skill Popup
       │
       ▼
  Fill Form → Save
       │
       ▼
  POST /api/v1/skills
       │
  ┌────┴────┐
  │         │
Success   Failure
  │         │
  ▼         ▼
Close     Toast Error
Refresh
Grid
```

---

# 21. Mô hình dữ lieu logic + quản lý master/history

Các Entity chính của Skill Management gồm:

| Entity | Mục đích |
|---|---|
| User | Lưu thông tin người dùng, phục vụ xác định Creator và phân quyền | 
| Skill | Master	Lưu thông tin hiện tại/mới nhất của Skill | 
| Skill | History	Lưu lịch sử thay đổi và các phiên bản của Skill | 
| Skill | Rating	Lưu thông tin đánh giá Skill | 
| Skill | View	Lưu lịch sử lượt xem Skill khi hệ thống cần tracking chi tiết | 
| Quan hệ | Logic | 

> **Đối chiếu với implementation (v2.0):**
> - `SKILL_MASTER` → bảng `skills_master` (không có cột file; file nằm ở bảng `skill_files` 1:N)
> - `SKILL_HISTORY` → Phase 2 (chưa implement); hiện tại có `skill_status_history` (audit trail chuyển trạng thái)
> - `SKILL_RATING` → bảng `skill_ratings` (1 rating/user/skill, UPSERT)
> - `SKILL_VIEW` → bảng `skill_view_log` (idempotent theo cửa sổ 15 phút)
> - Bổ sung: `skill_comments` (soft delete), `skill_subscriptions` (subscriber tracking)
> - **Bổ sung v1.1 (16/10/2026):** `skill_version` + `skill_version_file` (snapshot version cũ khi version update được approve — V34), `skill_files.pending_yn` (file của version update đang pending), `skills_master.pending_metadata` + `pending_remove_file_ids` (JSONB), taxonomy: `skill_category`, `process_stage`, `skill_category_mapping`, `skill_stage_mapping` (V31)

                         USER
                          │
                          │ Creator
                          │ 1:N
                          ▼
                    SKILL_MASTER
                          │
             ┌────────────┼─────────────┐
             │ 1:N        │ 1:N         │ 1:N
             ▼            ▼             ▼
      SKILL_HISTORY   SKILL_RATING   SKILL_VIEW
             │
             │
             │
SKILL_MASTER │ N:M

**Tổng hợp quan hệ**
| Entity cha | Entity con | Cardinality | Ý nghĩa nghiệp vụ|
|---|---|---|---|
| User | Skill Master | 1:N | Một User có thể tạo nhiều Skill|
| Skill | Master | Skill History | 1:N | Một Skill có thể có nhiều bản ghi lịch sử|
| Skill | Master | Skill Rating | 1:N | Một Skill có thể nhận nhiều đánh giá|
| Skill | Master | Skill View | 1:N | Một Skill có thể có nhiều lượt xem|

---

## 21.1 Skill Master

`SKILL_MASTER` đại diện cho **trạng thái hiện tại/mới nhất của Skill**.

Các chức năng Search và Grid trên màn hình Skill Management sẽ sử dụng dữ liệu hiện tại của Skill Master.

### Các thuộc tính chính

| Thuộc tính | Ý nghĩa nghiệp vụ | Bắt buộc |
|---|---|---|
| skill_id | ID duy nhất của Skill | Có |
| skill_name | Tên Skill hiện tại | Có |
| description | Mô tả Skill hiện tại | Không |
| file_id | ID file upload | Không |
| creator_id | ID User tạo Skill | Có |
| created_date | Ngày giờ tạo Skill | Có |
| created_ip | IP User tạo Skill | Có |
| status | Trạng thái hiện tại của Skill | Có |
| scope | Phạm vi chia sẻ của Skill (PRIVATE/PART/GROUP/PUBLIC) | Có |
| updated_date | Ngày giờ cập nhật gần nhất | Có |
| updated_by | User thực hiện cập nhật gần nhất | Có |
| updated_ip | IP User thực hiện cập nhật gần nhất | Có |
| view_count | Tổng số lượt xem hiện tại | Có |
| rating_score | Điểm đánh giá trung bình hiện tại | Không |
| rating_count | Số lượng User đã đánh giá | Không |
| deleted_YN | Tình trạng Skill đã bị xóa hay không | Không |

### Quy tắc nghiệp vụ

1. Mỗi Skill phải có một `skill_id` duy nhất.
2. `SKILL_MASTER` phải đại diện cho trạng thái hiện tại/mới nhất của Skill.
3. Status của Skill chỉ được phép thuộc một trong 4 trạng thái:
   - Draft
   - Pending Approval
   - Approved
   - Rejected
4. Mỗi Skill phải có một Creator hợp lệ.
5. Dữ liệu hiện tại của Skill được sử dụng cho Search và hiển thị trên Grid.
6. Dữ liệu History không thay thế dữ liệu hiện tại trong Master.

---

## 21.2 Skill History

`SKILL_HISTORY` dùng để lưu **lịch sử thay đổi và các phiên bản của Skill**.

- Skill đã thay đổi như thế nào.
- Ai thực hiện thay đổi.
- Thay đổi vào thời điểm nào.
- Skill đang ở phiên bản nào tại từng thời điểm.
- Status của Skill tại thời điểm thay đổi.

| Thuộc tính | Ý nghĩa nghiệp vụ | Bắt buộc |
|---|---|---|
| history_id | ID duy nhất của bản ghi History | Có |
| skill_id | ID Skill gốc | Có |
| version | Số phiên bản của Skill | Khuyến nghị |
| skill_name | Tên Skill tại thời điểm lưu History | Có |
| description | Mô tả tại thời điểm lưu History | Không |
| file_id | ID file upload | Không |
| creator_id | ID User tạo Skill | Có |
| created_date | Ngày giờ tạo Skill | Có |
| created_ip | IP User tạo Skill | Có |
| status | Trạng thái hiện tại của Skill | Có |
| scope | Phạm vi chia sẻ của Skill (PRIVATE/PART/GROUP/PUBLIC) | Có |
| updated_date | Ngày giờ cập nhật gần nhất | Có |
| updated_by | User thực hiện cập nhật gần nhất | Có |
| updated_ip | IP User thực hiện cập nhật gần nhất | Có |
| deleted_YN | Tình trạng Skill đã bị xóa hay không | Không |

### Quy tắc nghiệp vụ

1. Mỗi bản ghi History phải tham chiếu đến một Skill tồn tại.
2. History không được làm thay đổi trực tiếp dữ liệu hiện tại trong Master.
3. Các thay đổi thuộc phạm vi tracking phải được ghi nhận vào History.
4. History phải được lưu giữ theo chính sách lưu trữ dữ liệu của hệ thống.
5. Bản ghi History sau khi tạo nên được xem là **không thể chỉnh sửa**, trừ trường hợp có nghiệp vụ đặc biệt.
6. Một Skill có thể có nhiều bản ghi History.

---

## 22.3 Vòng đời Master/History

Master và History được cập nhật theo vòng đời của Skill.

| Sự kiện nghiệp vụ | Skill Master | Skill History |
|---|---|---|
| Tạo Skill | Tạo Skill hiện tại | Tạo bản ghi mới |
| Chỉnh sửa Skill | Cập nhật dữ liệu hiện tại | Tạo bản ghi History mới |
| Submit for Review | Cập nhật Status | Tạo bản ghi History mới, update bản ghi cũ |
| Approve | Cập nhật Status | Tạo bản ghi History mới, update bản ghi cũ |
| Reject | Cập nhật Status |Tạo bản ghi History mới, update bản ghi cũ |
| Xóa Skill | Cập nhật trường deleted_YN |Tạo bản ghi History mới, update bản ghi cũ |

Mỗi bản ghi History lưu toàn bộ trạng thái của Skill tại thời điểm thay đổi.

Ví dụ:
```
Version 1
Name        = Python
Description = Basic Python skill
Status      = Draft
```
```
Version 2
Name        = Python
Description = Advanced Python skill
Status      = Pending Approval
```

---

## 21.4 Skill Rating

`SKILL_RATING` lưu thông tin đánh giá của User đối với Skill.

### Các thuộc tính chính

| Thuộc tính | Ý nghĩa |
|---|---|
| skill_id | Skill được đánh giá |
| rating_id | ID duy nhất của Rating |
| user_id | User thực hiện đánh giá |
| rating_score | Điểm đánh giá |
| rated_date | Ngày giờ đánh giá |
| rated_ip | IP User đánh giá |

### Hiển thị trên Grid

Ví dụ:

```text
★ 4.8 (125)
```

Trong đó:

- `4.8`: Điểm Rating trung bình.
- `125`: Số lượng User đã đánh giá.

Nếu Skill chưa có Rating:

```text
No rating
```

Không hiển thị:

```text
0.0
-
N/A
```

---

## 22.5 Skill View

`SKILL_VIEW` dùng để tracking lịch sử User mở Skill Detail nếu hệ thống cần lưu chi tiết từng lượt xem.

### Các thuộc tính chính

| Thuộc tính | Ý nghĩa |
|---|---|
| view_id | ID duy nhất của lượt xem |
| skill_id | Skill được xem |
| user_id | User mở Skill Detail |
| viewed_date | Ngày giờ xem |
| viewed_ip | IP User xem |

### Quy tắc View Count

View Count được tăng khi:

> User mở Skill Detail thành công.

Các thao tác sau **không làm tăng View Count**:

- Search.
- Filter.
- Hiển thị Grid.
- Refresh.
- Sort.

Mỗi lần mở Skill Detail thành công được tính là **1 lượt View**, idempotent: 1 user chỉ được tính 1 lần/skill trong cửa sổ **15 phút** (cấu hình `app.skill.view-window-minutes`).

Việc `View Count` được:

- Tính toán từ `SKILL_VIEW`