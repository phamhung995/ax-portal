# USER MANAGEMENT

## Business Analysis & DEV Handoff Specification

**Document Version:** 1.7
**Status:** Updated
**Module:** User Management
**Target Users:** Admin User
**Document Type:** Business Analysis / Functional Specification / DEV Handoff

---

# 1. Overview

## 1.1 Purpose

User Management cho phép Admin User:

* Search User
* View User List
* Create User
* Bulk Upload User
* Export User List
* View User Detail
* Update User information, including User Name, Role and Status

## 1.2 Access Control

Chỉ **Admin User** được phép truy cập User Management.

| User Type   | Access  |
| ----------- | ------- |
| Admin User  | Allowed |
| Normal User | Denied  |

Backend phải kiểm tra authorization cho toàn bộ API của User Management.

## 1.3 Identifier Model (per Login SRS v1.7)

* `USER_ID` là **BIGINT do DB tự sinh** (Primary Key) — **không do Admin/user nhập**.
* **Knox ID** được **thêm lại** (override so với v1.5): là local-part của email, dùng để liên kết `user` ↔ `organization_master` cho login org check. Hiển thị trên Grid (cột Knox ID).
* `EMAIL` là **định danh unique duy nhất** (case-insensitive) và là Login ID.
* **Email là immutable** — gắn với user trọn đời, **không thể thay đổi** ở bất kỳ màn nào (User Detail, Create User, Bulk Upload).
* "Organization" trên UI là cách hiển thị kết hợp `PART > GROUP` (xem §3.2).

---

# 2. User Management Screen

## 2.1 Screen Structure

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ User Management                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│ Search                                                                       │
│                                                                              │
│ User Name [________________]                Email         [________________] │
│                                                                              │
│ Organization  [____________________________]                                 │
│                                                                              │
│ Role          [ NORMAL_USER × | ADMIN_USER × | ▼ ]                           │
│                                                                              │
│ Status        [ ACTIVE × | DEACTIVE × | ▼ ]                                  │
│                                                                              │
│ Created Date  [ DD/MM/YYYY ]  ~  [ DD/MM/YYYY ]                              │
│                                                                              │
│                                      [ Search ]  [ Reset ]                   │
├──────────────────────────────────────────────────────────────────────────────┤
│ [ Create User ]   [ Bulk Upload ]   [ Export User List ]                     │
├──────────────────────────────────────────────────────────────────────────────┤
│ ☐ │ User Name │ Email │ Organization │ Role │ Status │ Created Date         │
├───┼───────────┼───────┼──────────────┼──────┼──────────┼────────────────────┤
│ ☐ │ John      │ ...   │ ABC          │ Normal│ Active  │ 01/09/2026         │
│ ☐ │ Mary      │ ...   │ XYZ          │ Admin │ Active  │ 02/09/2026         │
└──────────────────────────────────────────────────────────────────────────────┘
```

> **UI clarification:** Role và Status trên Search Area là **Multi-select Dropdown**, không phải Single-select Dropdown và không phải Checkbox Group hiển thị cố định.
>
> **User ID** là BIGINT do DB sinh — **không hiển thị trên Grid** (không phải cột grid).

---

# 3. Search Area

## 3.1 Search Fields

| Field             | UI Type                          | Selection | Search Rule |
| ----------------- | -------------------------------- | --------- | ----------- |
| User Name         | Textbox                          | -         | Contains    |
| Email             | Textbox                          | -         | Contains    |
| Organization      | Searchable Dropdown              | Single    | Exact Match |
| Role              | Multi-select Dropdown + Checkbox | Multiple  | OR / IN     |
| Status            | Multi-select Dropdown + Checkbox | Multiple  | OR / IN     |
| Created Date From | Date Picker                      | -         | >=          |
| Created Date To   | Date Picker                      | -         | <=          |


## 3.2 Organization

Organization sử dụng **Searchable Dropdown – Single Select**.

* Single selection.
* Có thể nhập keyword để tìm.
* Có thể clear selection.
* Không sử dụng free-text textbox.
* Không hỗ trợ multiple selection.

> **Note (per Login SRS v1.7):** Bảng `"user"` **không có** cột `ORGANIZATION`/`PART`/`GROUP` (đã drop ở V13) — Part/Group được lấy từ bảng `organization_master` (JOIN qua `knox_id`, `EMPLOYED = 'Y'`). Cột "Organization" trên Search/Grid/Detail là cách hiển thị kết hợp của `Part` và `Group` (định dạng `Part > Group`). Search Organization khớp trên giá trị Part/Group trong `organization_master`.

## 3.3 Role – Multi-select Dropdown

Role phải được implement dưới dạng:

```text
Multi-select Dropdown
+
Checkbox
+
Pill/Chip selected values
```

Available options:

```text
NORMAL_USER
ADMIN_USER
```

### Closed State – No Selection

```text
Role
┌─────────────────────────────────────────────────────┐
│ Select Role                                       ▼ │
└─────────────────────────────────────────────────────┘
```

Không chọn Role = **All Roles**.

### Closed State – Selected Values

```text
Role
┌─────────────────────────────────────────────────────┐
│ NORMAL_USER ×   ADMIN_USER ×                    ▼  │
└─────────────────────────────────────────────────────┘
```

### Open State

```text
Role
┌─────────────────────────────────────────────────────┐
│ NORMAL_USER ×                                  ▼  │
├─────────────────────────────────────────────────────┤
│ ☑ NORMAL_USER                                       │
│ ☐ ADMIN_USER                                        │
└─────────────────────────────────────────────────────┘
```

### Role UI Rules

* Cho phép chọn nhiều giá trị.
* Mỗi option có checkbox.
* Dropdown không tự đóng sau mỗi lần chọn.
* Selected values hiển thị dạng pill/chip.
* Có thể remove từng selected value bằng `×`.
* Có thể clear toàn bộ selected values.
* Không chọn value nào = All Roles.

### Role Search Logic

```sql
ROLE IN ('NORMAL_USER', 'ADMIN_USER')
```

---

## 3.4 Status – Multi-select Dropdown

Status phải được implement dưới dạng:

```text
Multi-select Dropdown
+
Checkbox
+
Pill/Chip selected values
```

Available options:

```text
ACTIVE
DEACTIVE
```

### Closed State – No Selection

```text
Status
┌─────────────────────────────────────────────────────┐
│ Select Status                                     ▼ │
└─────────────────────────────────────────────────────┘
```

Không chọn Status = **All Statuses**.

### Closed State – Selected Values

```text
Status
┌─────────────────────────────────────────────────────┐
│ ACTIVE ×   DEACTIVE ×                           ▼  │
└─────────────────────────────────────────────────────┘
```

### Open State

```text
Status
┌─────────────────────────────────────────────────────┐
│ ACTIVE ×                                        ▼  │
├─────────────────────────────────────────────────────┤
│ ☑ ACTIVE                                            │
│ ☐ DEACTIVE                                          │
└─────────────────────────────────────────────────────┘
```

### Status UI Rules

* Cho phép chọn nhiều giá trị.
* Mỗi option có checkbox.
* Dropdown không tự đóng sau mỗi lần chọn.
* Selected values hiển thị dạng pill/chip.
* Có thể remove từng selected value bằng `×`.
* Có thể clear toàn bộ selected values.
* Không chọn value nào = All Statuses.

### Status Search Logic

```sql
STATUS IN ('ACTIVE', 'DEACTIVE')
```

---

## 3.5 Role / Status Component Comparison

| Field        | Component             | Multiple Select | Checkbox | Selected Display |
| ------------ | --------------------- | --------------: | -------: | ---------------- |
| Organization | Searchable Dropdown   |              No |       No | Selected value   |
| Role         | Multi-select Dropdown |             Yes |      Yes | Pill/Chip        |
| Status       | Multi-select Dropdown |             Yes |      Yes | Pill/Chip        |

Role và Status **không được implement dưới dạng**:

* Single-select Dropdown.
* Radio Button.
* Checkbox group luôn hiển thị.
* Free-text input.

---

## 3.6 Created Date

Logic:

```text
Created Date >= From Date
AND
Created Date <= To Date
```

Nếu:

```text
From Date > To Date
```

→ hiển thị validation error và không thực hiện Search.

---

## 3.7 Search Combination

Các search criteria khác nhau kết hợp bằng **AND**.

Multiple values trong cùng Role hoặc Status kết hợp bằng **OR / IN**.

Ví dụ:

```text
User Name contains "John"
AND
Role IN ('NORMAL_USER', 'ADMIN_USER')
AND
Status IN ('ACTIVE', 'DEACTIVE')
AND
Organization = 'ABC'
```

---

## 3.8 Reset

Click `Reset`:

* Clear tất cả search fields.
* Clear Organization.
* Clear Role selected values.
* Clear Status selected values.
* Clear date.
* Clear validation message.

Sau Reset:

```text
Role   = All Roles
Status = All Statuses
```

---

# 4. User Grid

## 4.1 Columns

| # | Column | Behavior |
|---|---|---|
| 1 | Select | Checkbox |
| 2 | User Name | Clickable; opens User Detail |
| 3 | Knox ID | Read-only; derived from the email local part (e.g. `tq.dat1@...` → `tq.dat1`) |
| 4 | Email | Read-only |
| 5 | Organization | Read-only; combined `Part > Group` display; updated by Batch |
| 6 | Role | Read-only; display UI label mapped from DB/API code |
| 7 | Status | Read-only badge; display UI label mapped from DB/API code |
| 8 | Created Date | Read-only |

> **User ID không hiển thị trên Grid** — BIGINT do DB sinh, không có giá trị hiển thị cho Admin.
> **Knox ID (v1.6):** Cột Knox ID được **thêm lại** vào Grid (override so với SRS v1.5 đã bỏ Knox ID). Giá trị là phần local-part của email, dùng để liên kết `user` ↔ `organization_master`.

Role mapping:

| DB/API value | UI label |
|---|---|
| `NORMAL_USER` | Normal User |
| `ADMIN_USER` | Admin User |

Status mapping:

| DB/API value | UI label |
|---|---|
| `ACTIVE` | Active |
| `DEACTIVE` | Deactive |

## 4.2 Row Selection

* Each row has a checkbox.
* Header has a **Select All** checkbox.
* **Select All selects all User records displayed on the current page only.**
* Select All does not select Users on other pages.
* Multiple rows can be selected.
* Checkbox is not exported.

## 4.3 Pagination

* Default page size: **20**.
* Available page sizes: **10 / 20 / 50 / 100**.
* Pagination is applied to the search result.
* Changing page does not change the search criteria or sort condition.

## 4.4 Sorting

* Default sort: **Created Date DESC**.
* Sortable columns remain exactly as defined in this specification.
* Checkbox is not sortable.
* Sort direction is displayed by the Grid sort indicator.

## 4.4.1 Grid Width & Scrolling

* The grid must fit the main content area width on initial render — **no horizontal scrollbar by default**.
* Column widths are designed so the primary content regions fit the available width; long values are truncated with `...` plus a tooltip showing the full value.
* Users can drag a column header edge to widen a column and reveal more information.
* The horizontal scrollbar appears only when the total column width (after user resizing) exceeds the container width.

## 4.5 User Name

User Name is clickable and opens User Detail.

## 4.6 Status

Status on Grid is a **Read-only badge**.

No:

* Inline Status Dropdown
* Activate button
* Deactivate button
* Bulk Status Change

Status changes are performed only from User Detail.

## 4.7 Organization (Part / Group)

* Organization is displayed as a single Grid column combining `Part > Group`.
* It is read-only in User Management.
* Admin cannot edit Part or Group from Grid.
* Part and Group are sourced from `organization_master` (JOIN qua `"user".knox_id`, `EMPLOYED = 'Y'`).
* Part and Group are populated by the Org Management import (not a Batch process).
* Part and Group are not included in Create User or Update User request payloads.

## 4.8 Grid Initial / Empty / Loading / Error States

### Initial Load

* Grid is empty.
* System does not automatically execute Search API.
* Admin must click **Search** to retrieve User data.

### Search Result Empty

* Search API is executed successfully but no User matches the criteria.
* Grid displays the defined Empty Result state.

### Loading

* Grid displays a loading indicator while Search API is processing.

### Error

* If Search API fails, Grid displays an Error state/message.

### Reset

* Reset clears all search conditions.
* Grid is cleared and becomes empty.
* Reset does not automatically execute Search API.
* Admin must click **Search** to retrieve data again.

## 4.9 Action Column

**No Action column.**

# 5. Create User

## 5.1 Fields

| Field            | Required | Editable |
| ---------------- | -------: | -------: |
| User Name        |      Yes |      Yes |
| Email            |      Yes |      Yes |
| Password         |      Yes |      Yes |
| Confirm Password |      Yes |      Yes |
| Organization     |       No | Read-only |

> **User ID không phải trường nhập** — DB tự sinh BIGINT khi tạo user.
> Email được nhập **một lần duy nhất** khi tạo user và **không thể thay đổi sau đó**.
> Organization trên màn Create User được hiển thị **rỗng** và **Read-only**.
> Admin không được nhập hoặc chỉnh sửa Organization.

### 5.1.1 Create User Wireframe

```text
┌──────────────────────────────────────────────────────────────────┐
│ Create User                                           [ Back ]   │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│ User Name *                                                      │
│ [____________________________________________]                   │
│                                                                  │
│ Email *                                                          │
│ [____________________________________________]                   │
│ (Email address can not be changed after creating user)           │
│                                                                  │
│ Password *                                                       │
│ [____________________________________________]  [ Show/Hide ]    │
│                                                                  │
│ Confirm Password *                                               │
│ [____________________________________________]  [ Show/Hide ]    │
│                                                                  │
│ Organization                                                     │
│ [                                              ] Read-only       │
│                                                                  │
│ Role                                                             │
│ [ Normal User                                    ] Read-only     │
│                                                                  │
│ Status                                                           │
│ [ Active                                         ] Read-only     │
│                                                                  │
│                              [ Cancel ]      [ Save ]            │
└──────────────────────────────────────────────────────────────────┘
```

### Create User UI Rules

* User ID is **not** an input field — it is a DB-generated BIGINT.
* Email is entered once at creation and is **immutable** afterwards.
* Organization is displayed as an empty Read-only field when the screen is opened.
* Admin cannot enter or edit Organization.
* Part and Group are not displayed as input fields.
* Role is displayed as Read-only with default value **Normal User**.
* Status is displayed as Read-only with default value **Active**.
* Part and Group are not set at user creation — they come from `organization_master` rows linked via `knox_id`.

Không cho Admin nhập:

* User ID
* Role
* Status
* Part
* Group
* Register ID
* Created App ID

---

## 5.2 Default Values

```text
USER_ID        = DB-generated BIGINT
ROLE           = NORMAL_USER
STATUS         = ACTIVE
KNOX_ID        = local-part of email (auto-derived)
REGISTER_ID    = Current Admin Login ID
CREATED_APP_ID = USER_MANAGEMENT
```

> Part/Group không thuộc bảng `"user"` — lấy từ `organization_master` qua `knox_id`.

---

## 5.3 Registration Logic

Create User dùng **cùng Registration Business Logic với Self Registration trước Login**.

```text
Required Validation
        ↓
User Name Validation
        ↓
Email Validation (format + max 254)
        ↓
Email Uniqueness
        ↓
Password Policy
        ↓
Confirm Password
        ↓
Password Hash
        ↓
Generate USER_ID (BIGINT)
        ↓
Insert USER_MASTER
        ↓
Insert USER_HISTORY
        ↓
COMMIT
```

> **Note:** Self Registration (trước Login) bắt buộc xác nhận email qua OTP. Create User bởi Admin **không** yêu cầu OTP email — Admin chịu trách nhiệm nhập email đúng.

### 5.3.1 Email Validation Rules

Email trong Create User áp dụng **cùng validation rule với Self Registration** (Login SRS §14.3) và Settings (max 254 ký tự):

| # | Rule | Error Code |
|---|------|-----------|
| 1 | Email là mandatory (không rỗng, không chỉ whitespace) | `EMAIL_REQUIRED` |
| 2 | Valid email format: `local-part@domain`, đúng một `@`, domain chứa ít nhất một `.`; không space/control character; local-part chỉ chứa chữ, số và `._%+-` | `INVALID_EMAIL` |
| 3 | Tối đa **254 ký tự** (RFC 5321 maximum address length) | `EMAIL_TOO_LONG` |
| 4 | Unique trong toàn bộ `USER_MASTER` (case-insensitive) — xem §5.4 | `EMAIL_ALREADY_USED` |
| 5 | User Name / Password / Confirm Password thiếu | `NAME_REQUIRED` |
| 6 | Password < 8 ký tự | `PASSWORD_TOO_SHORT` |
| 7 | Password thiếu chữ hoa / chữ thường / ít nhất 1 số (ký tự đặc biệt là **không bắt buộc**) | `PASSWORD_WEAK` |
| 8 | Confirm Password ≠ Password | `PASSWORD_MISMATCH` |
| 9 | Transaction tạo user thất bại | `REGISTRATION_FAILED` |

> Error code dùng **cùng naming với Settings SRS/Spec** (`EMAIL_REQUIRED`, `INVALID_EMAIL`, `EMAIL_TOO_LONG`, `EMAIL_ALREADY_USED`, `PASSWORD_TOO_SHORT`, `PASSWORD_WEAK`, `PASSWORD_MISMATCH`) và **cùng bộ code với Self Registration** (Login SRS §16.1/§16.3) để đồng nhất toàn hệ thống.

---

## 5.4 Email Uniqueness Rule – Create

Email là **định danh unique duy nhất** (Login ID) và **immutable** sau khi tạo.

### Rules

1. Email là mandatory.
2. Email phải có valid format (xem §5.3.1) và tối đa **254 ký tự**.
3. Email phải unique trong toàn bộ `USER_MASTER`.
4. Uniqueness được kiểm tra **case-insensitively**.
5. `user@example.com` và `User@example.com` được coi là cùng một Email.
6. Email của user `DEACTIVE` vẫn được reserve.
7. Email của user `DEACTIVE` **không được reuse**.
8. Application phải kiểm tra uniqueness trước khi insert.
9. Database phải enforce uniqueness bằng Unique Constraint/Unique Index (case-insensitive).
10. Backend phải xử lý duplicate phát sinh do concurrent requests.
11. Email **không thể bị thay đổi** sau khi user được tạo (immutable).

### Examples

| Existing Email          | New Email          | Result    |
| ----------------------- | ------------------ | --------- |
| `a@example.com`         | `b@example.com`    | Allowed   |
| `a@example.com`         | `A@example.com`    | Duplicate |
| `a@example.com` ACTIVE  | `a@example.com`    | Duplicate |
| `a@example.com` DEACTIVE | `a@example.com`    | Duplicate |

### Recommended Error

```text
Email already exists.
Please enter another Email.
```

---

## 5.5 Registration Context

Self Registration:

```text
REGISTER_ID = SYSTEM00
CREATED_APP_ID = LOGIN_REGISTER
```

Admin Create User:

```text
REGISTER_ID = Current Admin Login ID
CREATED_APP_ID = USER_MANAGEMENT
```

---

# 6. Bulk Upload

## 6.1 Flow

```text
User Management
      ↓
[ Bulk Upload ]
      ↓
Local File Picker
      ↓
Select File
      ↓
Upload
      ↓
Validation
      ↓
Bulk Upload Validation Result
      ↓
[ Save ]
      ↓
Save SUCCESS rows only
```

## 6.2 File Selection

Chỉ cho phép chọn **một file mỗi lần**.

Allowed:

* `.xlsx`
* `.xls`

Backend phải kiểm tra cả extension và actual file format/content.

**File size limit:** tối đa **10 MB**.

**Data row limit:** tối đa **500 rows/file**, không tính header.

---

## 6.3 Bulk Upload Template

| Column           | Required |
| ---------------- | -------: |
| User Name        |      Yes |
| Email            |      Yes |
| Password         |      Yes |
| Confirm Password |      Yes |

> **User ID không có trong template** — DB tự sinh BIGINT cho mỗi row.
> **Knox ID không có trong template** — tự sinh từ local-part của email.
> Email được nhập **một lần duy nhất** — không thể thay đổi sau khi user được tạo.

Không cho phép nhập:

* User ID
* Role
* Status
* Part
* Group
* Register ID
* Created App ID

---

## 6.4 Validation

### File-level

* File format.
* File structure.
* Required columns.
* Empty file.
* File readability.
* Row limit: maximum **500 data rows/file**, không tính header.
* File size: maximum **10 MB**.
* Invalid template.

### Row-level

#### User Name

* Required.
* Length/character validation theo Registration/Business Rule.

#### Email

* Required.
* Valid email format (xem §5.3.1): `local-part@domain`, đúng một `@`, domain chứa ít nhất một `.`; không space/control character.
* Tối đa **254 ký tự**.
* Case-insensitive duplicate check **within uploaded file**.
* Case-insensitive duplicate check **against USER_MASTER**.
* Email of DEACTIVE user is still considered duplicate.
* Email cannot be reused.

#### Password

* Required.
* Password policy.
* Length.
* Character requirements.

#### Confirm Password

* Required.
* Must match Password.

---

## 6.5 Bulk Duplicate Examples

### Duplicate within same file

```text
Row 2: a@example.com
Row 3: A@example.com
```

Result:

```text
Row 2 → SUCCESS
Row 3 → ERROR: Duplicate Email in uploaded file.
```

### Duplicate against database

Existing DB:

```text
b@example.com
```

Excel:

```text
b@example.com
```

Result:

```text
→ ERROR: Email already exists.
```

If existing DB user is DEACTIVE, the result is still ERROR.

---

## 6.6 Validation Result Wireframe

```text
┌─────────────────────────────────────────────────────────────────────┐
│ Bulk Upload – Validation Result                                    │
├─────────────────────────────────────────────────────────────────────┤
│ File: users.xlsx                                                   │
│                                                                     │
│ Total Rows: 100     Success: 92     Error: 8                      │
├──────┬──────────────────────┬────────────────┬────────────────────┤
│ Row  │ Email                │ Result         │ Error Message      │
├──────┼──────────────────────┼────────────────┼────────────────────┤
│  2   │ a@example.com        │ SUCCESS        │ -                  │
│  3   │ A@example.com        │ ERROR          │ Email duplicated   │
│  4   │ c@@example.com       │ ERROR          │ Invalid Email      │
│  5   │ d@example.com        │ SUCCESS        │ -                  │
│  6   │ b@example.com        │ ERROR          │ Email already exists│
└──────┴──────────────────────┴────────────────┴────────────────────┘

                                      [ Cancel ]  [ Save ]
```

---

## 6.6.1 Validation Result Rules

* Kết quả phải giữ **đúng thứ tự row trong Excel**. Không sort lại theo Result, Error Message hoặc Email.
* Một row có thể có **nhiều lỗi**. Hệ thống phải hiển thị **tất cả lỗi của row**.
* Error Message hiển thị **một dòng trong Grid**; toàn bộ nội dung lỗi được xem qua **tooltip**.

## 6.7 Save Rule

Không yêu cầu toàn bộ file phải SUCCESS.

Ví dụ:

```text
Total = 100
SUCCESS = 92
ERROR = 8
```

Click Save:

```text
92 SUCCESS rows → Save
8 ERROR rows    → Skip
```

ERROR rows không được tạo USER_MASTER/USER_HISTORY.

Nếu một SUCCESS row phát sinh lỗi mới trong quá trình Save (ví dụ concurrent duplicate), chỉ row đó chuyển thành Save Error; các SUCCESS rows khác vẫn được Save.

---

## 6.8 Backend Re-validation

Frontend validation không phải validation cuối cùng.

Backend phải re-check trước khi Save, bao gồm:

* Email uniqueness.
* Duplicate Email trong batch.
* Duplicate Email với `USER_MASTER`.
* Email của DEACTIVE user.
* Business rules (Refer đến rule đăng ký user của file Login_User Register before login_SRS.md)
* Authorization.

Nếu một row đã SUCCESS ở validation stage nhưng trở thành duplicate trước Save do concurrent transaction, row đó phải chuyển thành Save Error và không được insert.

---

## 6.9 Bulk Save Transaction

Mỗi SUCCESS row phải được xử lý theo transaction logic đảm bảo:

```text
USER_MASTER
     +
USER_HISTORY
```

được tạo nhất quán.

Nếu một row save fail:

```text
USER_MASTER insert
        +
USER_HISTORY insert
```

của chính row đó phải rollback.

Các row hợp lệ khác không bị rollback theo row lỗi (partial success).

---

## 6.10 Cancel After Validation

Nếu Admin đã upload file và có Validation Result nhưng chưa Save, click **Cancel** phải hiển thị confirmation trước khi bỏ validation result.

```text
Cancel
  ↓
Discard Validation Result Confirmation
  ↓
[ Stay ] / [ Leave ]
```

Nếu Admin chọn **Stay**: giữ nguyên Validation Result.

Nếu Admin chọn **Leave**: bỏ Validation Result và quay về User Management.

---

## 6.11 Bulk Upload Save Success Navigation

Sau khi Bulk Upload Save thành công:

```text
Save
  ↓
Success Message
  ↓
[ OK ]
  ↓
User Management
  ↓
Auto Search
```

Khi quay về User Management, hệ thống phải preserve:

* Search criteria.
* Sort condition.
* Pagination.

Auto Search phải sử dụng lại search context trước khi Admin mở Bulk Upload.
# 7. User Detail

## 7.1 Purpose

User Detail cho phép Admin:

* View User information.
* Update User Name.
* Change Role của user khác.
* Change Status của user khác.

Không cho:

* Change Email (immutable — gắn với user trọn đời).
* Change Part.
* Change Group.
* View Password.
* View Audit Information.
* Delete User.

---

# 8. User Detail Wireframe

```text
┌──────────────────────────────────────────────────────────────┐
│ User Detail                                      [ Back ]    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│ User Name *                                                  │
│ [ John Smith                                   ]             │
│                                                              │
│ Email *                                                      │
│ [ john@example.com                              ] Read-only │
│                                                              │
│ Organization                                                 │
│ [ ABC Corporation                              ] Read-only   │
│                                                              │
│ Role                                                         │
│ [ NORMAL_USER ▼                                ]             │
│                                                              │
│ Status                                                       │
│ [ ACTIVE ▼                                      ]            │
│                                                              │
│                         [ Cancel ]  [ Save ]                 │
└──────────────────────────────────────────────────────────────┘
```

> Role và Status trong User Detail là single-value fields của một User. Quy tắc Multi-select Dropdown chỉ áp dụng cho Search Area.
> Email là read-only — gắn với user trọn đời, không thể thay đổi.

---

# 9. User Detail Navigation Behavior

## 9.1 Open User Detail

User Detail được mở từ User Management khi Admin click vào User Name trên grid.

* Cùng browser tab/page.
* Không popup/modal.
* Không new tab.

## 9.2 Back Button

Back phải preserve:

* Search criteria.
* Organization.
* Role selection.
* Status selection.
* Created Date From/To.
* Pagination.
* Sort condition nếu có.

## 9.3 Save Success

```text
User Detail
      ↓
Save
      ↓
Success
      ↓
[ OK ]
      ↓
User Management
      ↓
Refresh / Re-query Grid
```

## 9.4 Save Error

Admin vẫn ở User Detail để sửa và retry.

## 9.5 Cancel

Không thay đổi:

```text
Cancel → User Management
```

Có unsaved changes:

```text
Cancel
  ↓
Unsaved Changes Confirmation
  ↓
[ Stay ] / [ Leave ]
```

---

# 10. User Detail Field Rules

| Field             |    Editable | Rule                    |
| ----------------- | ----------: | ----------------------- |
| User ID           |          No | Not displayed  + immutable (BIGINT) |
| User Name         |         Yes | Required                |
| Email             |          No | Read-only + immutable (gắn với user trọn đời) |
| Organization      |          No | Read-only               |
| Role              | Conditional | Other user only         |
| Status            | Conditional | Other user only         |
| Password          |          No | Not displayed           |
| Audit Information |          No | Not displayed           |

---

# 11. User Detail Role Logic

Admin được thay đổi Role của user khác.

Options:

```text
NORMAL_USER
ADMIN_USER
```

Allowed:

```text
NORMAL_USER ↔ ADMIN_USER
```

Nếu Target User ID = Current Admin Login ID:

```text
Role change = Not Allowed
```

Backend bắt buộc reject self-role update.

---

# 12. User Detail Status Logic

Admin được thay đổi Status của user khác.

Options:

```text
ACTIVE
DEACTIVE
```

Allowed:

```text
ACTIVE → DEACTIVE
DEACTIVE → ACTIVE
```

Nếu Target User ID = Current Admin Login ID:

```text
Status change = Not Allowed
```

Backend bắt buộc reject self-status update.

---

# 13. User Detail Save Button

Khi mở User Detail:

```text
[ Save ] = Disabled
```

Save được enable khi có actual change ở:

* User Name
* Role
* Status

Nếu:

```text
Original → Changed → Original
```

→ `Save = Disabled`.

Frontend phải compare với original loaded values.

---

# 14. User Detail Save Flow

```text
[ Save ]
   ↓
Disable Save
   ↓
Show Processing
   ↓
Frontend Validation
   ↓
Permission Validation
   ↓
Send Update API
   ↓
Backend Authorization
   ↓
Backend Re-validation
   ↓
Check Data Changes
   ↓
Update USER_MASTER
   ↓
Insert USER_HISTORY
   ↓
COMMIT
```

---

# 15. User Detail Save – Frontend Validation

### User Name

* Required.
* Length/character validation theo Registration/Business Rule.

### Role

Nếu target là Current Admin:

```text
Error: You cannot change your own role.
```

### Status

Nếu target là Current Admin:

```text
Error: You cannot change your own status.
```

Nếu validation fail:

```text
API không được gọi.
```

---

# 16. User Detail Save – Backend Validation

Backend phải re-check:

1. Current user có phải Admin không.
2. Target User có tồn tại không.
3. Target User có phải Current Admin không.
4. Role change có hợp lệ không.
5. Status change có hợp lệ không.
6. Target data có còn giống trạng thái expected hay không.
7. Có actual change hay không.

### Email Immutability Rule

Email **không bao giờ** được thay đổi qua API Update.

Nếu request chứa email khác với email hiện tại của target user:

→ **Rejected** với error `EMAIL_IMMUTABLE`.

---

# 17. Optimistic Concurrency / Data Change

Nếu User Detail được mở lâu và dữ liệu đã bị thay đổi bởi process khác, request nên truyền version hoặc `updatedAt`.

Nếu current DB version không match original loaded version:

```text
Data has been changed by another process.
Please reload the user information and try again.
```

Không được silently overwrite dữ liệu mới hơn.

---

# 18. User Detail Save – API

```http
PUT /api/v1/admin/users/{userId}
```

Request:

```json
{
  "userName": "John Smith",
  "role": "NORMAL_USER",
  "status": "ACTIVE"
}
```

> Request **không chứa email** — Email là immutable.

Backend xác định:

* Current Admin.
* Target User.
* Authorization.
* Updated By.
* Updated At.
* Updated IP.
* Updated App ID.

Frontend không được tự quyết định audit fields.

**Error Responses:**

| Condition | Error Code | Message |
|-----------|-----------|---------|
| Email trong request khác email hiện tại | EMAIL_IMMUTABLE | "Email cannot be changed." |

---

# 19. User Detail Save – Transaction

```text
BEGIN TRANSACTION
       ↓
Validate Permission
       ↓
Validate Target
       ↓
Validate Business rules (Refer đến rule đăng ký user của file Login_User Register before login_SRS.md)
       ↓
Update USER_MASTER
       ↓
Insert USER_HISTORY
       ↓
COMMIT
```

Nếu fail:

```text
ROLLBACK
```

---

# 20. USER_HISTORY Rule

History được tạo khi có actual change ở:

* User Name
* Role
* Status

Nếu Before = After:

→ Không cần tạo History record.

---

# 21. Save Success

```text
┌──────────────────────────────────────────────┐
│ Success                                      │
│                                              │
│ User information has been updated            │
│ successfully.                                │
│                                              │
│                         [ OK ]               │
└──────────────────────────────────────────────┘
```

---

# 22. Save Error

Nếu business validation fail:

```text
Unable to update user information.
Please correct the highlighted fields.
```

System/database error:

```text
Unable to save user information.
Please try again later.
```

Transaction phải rollback.

## 22.1 Error Message Catalog

All user-facing error messages are in **English** and identified by a stable ID.
The frontend maps `errorCode` → message ID.

| ID | Message | Error Code(s) | Where |
|---|---|---|---|
| ERR-UM-001 | "You do not have permission to access this screen." | (HTTP 403) | Access control |
| ERR-UM-002 | "No data found." | (empty search result) | Search |
| ERR-UM-003 | "From Date cannot be later than To Date." | (date range validation) | Search |
| ERR-UM-004 | "Unable to load user list. Please try again." | (search API failure) | Search |
| ERR-UM-005 | "User name cannot be empty." | `NAME_REQUIRED` | Create User, User Detail |
| ERR-UM-006 | "Email cannot be empty." | `EMAIL_REQUIRED` | Create User, Bulk Upload |
| ERR-UM-007 | "Invalid Email format." | `INVALID_EMAIL` | Create User, Bulk Upload |
| ERR-UM-008 | "Email is too long (maximum 254 characters)." | `EMAIL_TOO_LONG` | Create User, Bulk Upload |
| ERR-UM-009 | "Email already exists. Please enter another Email." | `EMAIL_ALREADY_USED` | Create User, Bulk Upload |
| ERR-UM-010 | "Password must be at least 8 characters." | `PASSWORD_TOO_SHORT` | Create User, Bulk Upload |
| ERR-UM-011 | "Password must contain uppercase, lowercase and at least one number." | `PASSWORD_WEAK` | Create User, Bulk Upload |
| ERR-UM-012 | "Password confirmation mismatch." | `PASSWORD_MISMATCH` | Create User, Bulk Upload |
| ERR-UM-013 | "User created successfully." | — (success) | Create User |
| ERR-UM-014 | "Unable to create user. Please try again." | `REGISTRATION_FAILED` | Create User |
| ERR-UM-015 | "Only .xlsx and .xls files are supported." | (file type validation) | Bulk Upload |
| ERR-UM-016 | "File size is at most 10MB." | (file size validation) | Bulk Upload |
| ERR-UM-017 | "Maximum 500 data rows per file." | (row limit validation) | Bulk Upload |
| ERR-UM-018 | "Invalid file format." | (file format validation) | Bulk Upload |
| ERR-UM-019 | "Invalid template. Please use the provided template." | (template validation) | Bulk Upload |
| ERR-UM-020 | "Email duplicated in uploaded file." | (row-level duplicate) | Bulk Upload validation result |
| ERR-UM-021 | "Email already exists." | (row-level duplicate vs DB) | Bulk Upload validation result |
| ERR-UM-022 | "Bulk upload completed: {success} saved, {error} skipped." | — (success) | Bulk Upload save |
| ERR-UM-023 | "You cannot change your own role." | (self-role update) | User Detail |
| ERR-UM-024 | "You cannot change your own status." | (self-status update) | User Detail |
| ERR-UM-025 | "Email cannot be changed." | `EMAIL_IMMUTABLE` | User Detail (backend) |
| ERR-UM-026 | "Data has been changed by another process. Please reload the user information and try again." | (optimistic lock) | User Detail |
| ERR-UM-027 | "User information has been updated successfully." | — (success) | User Detail save |
| ERR-UM-028 | "Unable to update user information. Please correct the highlighted fields." | (business validation failure) | User Detail save |
| ERR-UM-029 | "Unable to save user information. Please try again later." | (system/database error) | User Detail save |
| ERR-UM-030 | "Unable to export user list. Please try again." | (export failure) | Export User List |
| ERR-UM-031 | "User list exported successfully." | — (success) | Export User List |
| ERR-UM-032 | "You have unsaved changes. Continue and discard changes?" | — (dialog) | User Detail cancel / Bulk Upload cancel |

---

# 23. Save Processing State

Khi Save đang xử lý:

```text
[ Save ] → Disabled
```

Có thể hiển thị:

```text
Saving...
```

Prevent duplicate Save request.

---

# 24. Status Business Rules

## ACTIVE → DEACTIVE

* User remains in USER_MASTER.
* User cannot Login.
* User can use Forgot Password.
* Successful Password Reset can activate according to Authentication rules.

## DEACTIVE → ACTIVE

* User remains in USER_MASTER.
* User can Login.

## Delete

Không có Delete User.

---

# 25. Export User List

## 25.1 Button

```text
[ Export User List ]
```

## 25.2 Export Rule

Export toàn bộ search result theo current search/filter conditions.

Nếu Search Result = 1,250 users thì export cả 1,250 users, không chỉ current page.

## 25.3 Export Columns

* User Name
* Email
* Organization
* Role
* Status
* Created Date

Không export:

* Checkbox
* User ID
* Password
* Password Hash
* Audit Information

---

# 26. Database Logical Model

## 26.1 USER_MASTER (bảng `"user"`)

| Column         | Description          | Constraint / Rule              |
| -------------- | -------------------- | ------------------------------ |
| `id`           | User identifier      | PK, DB-generated BIGINT (`IDENTITY`) |
| `name`         | User name            | Required                       |
| `gender`       | Gender               | Nullable                       |
| `email`        | Email (Login ID)     | Unique + Case-insensitive + Immutable + Max 254 |
| `mobile`       | Mobile               | Nullable                       |
| `password`     | BCrypt-hashed password | Never exposed                  |
| `role_id`      | Role (FK → `role`)   | NORMAL_USER / ADMIN_USER       |
| `enabled`      | Spring Security flag | Default `true`                 |
| `avatar_url`   | Avatar file path     | Nullable                       |
| `status`       | Status               | ACTIVE / DEACTIVE              |
| `knox_id`      | Knox ID (local-part of email) | NOT NULL; links `"user"` ↔ `organization_master` |
| `register_id`  | Actor who registered | Nullable (null for self-registration) |
| `created_at`   | Created datetime     | Required                       |
| `created_ip`   | Created IP           | -                              |
| `created_app_id` | Application         | Required                       |
| `updated_at`   | Updated datetime     | -                              |
| `updated_ip`   | Updated IP           | -                              |
| `updated_app_id` | Application         | -                              |
| `updated_by`   | Actor                | -                              |
| `version`      | Optimistic lock      | `@Version` (Long)              |

> **Note:** Bảng `"user"` **không có** cột `PART`/`GROUP` (đã drop ở V13) — Part/Group lấy từ bảng `organization_master` qua JOIN `"user".knox_id = organization_master.knox_id` với `EMPLOYED = 'Y'`. Cột password là `password` (BCrypt), không phải `password_hash`.

### User ID Database Rule

`USER_ID` là BIGINT do DB tự sinh (identity sequence), là Primary Key. Không do user/admin nhập.

### Email Database Rule

`EMAIL` phải được enforce bằng database Unique Constraint/Unique Index (case-insensitive).

Database collation/index phải đảm bảo:

```text
a@example.com = A@example.com
```

về uniqueness.

Không được thiết kế uniqueness chỉ bằng application validation.

Column type: `VARCHAR(254)` — tối đa 254 ký tự (RFC 5321 maximum address length).

---

## 26.2 USER_HISTORY (bảng `user_history`)

| Column         | Description               |
| -------------- | ------------------------- |
| `history_id`   | PK                        |
| `user_id`      | User                      |
| `name`         | Snapshot                  |
| `email`        | Snapshot                  |
| `role_name`    | Snapshot                  |
| `status`       | Snapshot                  |
| `register_id`  | Registration actor        |
| `created_at`   | Created datetime          |
| `created_ip`   | Created IP                |
| `created_app_id` | Application             |
| `updated_at`   | Updated datetime          |
| `updated_ip`   | Updated IP                |
| `updated_app_id` | Application             |
| `updated_by`   | Actor                     |
| `history_created_at` | Snapshot timestamp   |

> **Note:** `user_history` **không có** cột password snapshot, không có cột `part`/`group` (Part/Group không thuộc bảng `"user"`).

Relationship:

```text
USER_MASTER 1 ───────── N USER_HISTORY
```

USER_HISTORY chỉ dùng backend và không hiển thị trên UI.

---

# 27. API Design

> **API prefix:** `/api/v1` — tất cả endpoint dưới đây đều có prefix này (vd `POST /api/v1/admin/users/search`).
> **Response envelope:** `{ "success": boolean, "data": T, "errorCode"?: string, "message"?: string }`.

## Search

```http
POST /api/v1/admin/users/search
```

Request:

```text
userId
userName
email
part
group
roles[]
statuses[]
createdDateFrom
createdDateTo
page
pageSize
sortBy
sortDirection
```

Response:

```text
totalCount
items[]
```

Password không được trả về.

## Get Detail

```http
GET /api/v1/admin/users/{userId}
```

Không trả:

* Password.
* Password Hash.
* Audit Information.

## Create

```http
POST /api/v1/admin/users
```

Request:

```text
userName
email
password
confirmPassword
```

Backend phải kiểm tra:

```text
Email format + max 254 (xem §5.3.1)
Email unique
```

trước khi insert. `USER_ID` do DB sinh.

## Update

```http
PUT /api/v1/admin/users/{userId}
```

Request:

```text
userName
role
status
```

> Request **không chứa email** — Email là immutable.

## Bulk Upload

```http
POST /api/v1/admin/users/bulk/upload
POST /api/v1/admin/users/bulk/save
```

Business flow:

```text
Upload
→ Validate
→ Display Result
→ Save SUCCESS rows
```

## Export

```http
POST /api/v1/admin/users/export
```

---

# 28. Authorization Matrix

| Function                     | Normal User | Admin User |
| ---------------------------- | ----------: | ---------: |
| Access User Management       |          No |        Yes |
| Search Users                 |          No |        Yes |
| View User Detail             |          No |        Yes |
| Create User                  |          No |        Yes |
| Bulk Upload                  |          No |        Yes |
| Export User List               |          No |        Yes |
| Update another user          |          No |        Yes |
| Change another user's Role   |          No |        Yes |
| Change another user's Status |          No |        Yes |
| Change own Role              |          No |         No |
| Change own Status            |          No |         No |
| Delete User                  |          No |         No |

---

# 29. Acceptance Criteria

## Access

**AC-UM-01**
Normal User cannot access User Management.

**AC-UM-02**
Admin User can access User Management.

## Search – Role

**AC-SEARCH-ROLE-01**
Role is displayed as a Multi-select Dropdown.

**AC-SEARCH-ROLE-02**
Role Dropdown displays a checkbox for each option.

**AC-SEARCH-ROLE-03**
Admin can select both `NORMAL_USER` and `ADMIN_USER`.

**AC-SEARCH-ROLE-04**
Selected Role values are displayed as rounded pills/chips.

**AC-SEARCH-ROLE-05**
Admin can remove an individual selected Role using `×`.

**AC-SEARCH-ROLE-06**
No Role selection means All Roles.

**AC-SEARCH-ROLE-07**
Multiple Role values are searched using OR / IN logic.

## Search – Status

**AC-SEARCH-STATUS-01**
Status is displayed as a Multi-select Dropdown.

**AC-SEARCH-STATUS-02**
Status Dropdown displays a checkbox for each option.

**AC-SEARCH-STATUS-03**
Admin can select both `ACTIVE` and `DEACTIVE`.

**AC-SEARCH-STATUS-04**
Selected Status values are displayed as rounded pills/chips.

**AC-SEARCH-STATUS-05**
Admin can remove an individual selected Status using `×`.

**AC-SEARCH-STATUS-06**
No Status selection means All Statuses.

**AC-SEARCH-STATUS-07**
Multiple Status values are searched using OR / IN logic.

---

## Create User

**AC-UM-03**
Create User reuses Registration Business Logic.

**AC-UM-04**
New Admin-created user has:

```text
USER_ID = DB-generated BIGINT
ROLE = NORMAL_USER
STATUS = ACTIVE
KNOX_ID = local-part of email (auto-derived)
```

**AC-UM-05**
Admin Create User records current Admin Login ID as REGISTER_ID.

**AC-UM-06**
System rejects an Email that already exists in USER_MASTER.

**AC-UM-07**
Email uniqueness is case-insensitive.

**AC-UM-08**
System rejects an Email belonging to a DEACTIVE user because DEACTIVE Emails cannot be reused.

**AC-UM-09**
User ID is a DB-generated BIGINT and is not entered by Admin.

**AC-UM-10**
Email uniqueness is enforced by the backend and database.

**AC-UM-11**
System rejects an Email with invalid format (`INVALID_EMAIL`) — xem §5.3.1.

**AC-UM-12**
System rejects an Email longer than 254 characters (`EMAIL_TOO_LONG`).

---

## Bulk Upload

**AC-BU-01**
Bulk Upload opens Local File Picker.

**AC-BU-02**
Only `.xlsx` and `.xls` are accepted.

**AC-BU-03**
Only one file can be selected per upload.

**AC-BU-04**
Uploaded file is validated.

**AC-BU-05**
Validation result is displayed row by row.

**AC-BU-06**
Validation errors include meaningful error messages.

**AC-BU-07**
Duplicate Email within the same uploaded file is detected case-insensitively.

**AC-BU-08**
Existing Email in USER_MASTER is detected.

**AC-BU-09**
Email belonging to a DEACTIVE user is still treated as duplicate.

**AC-BU-10**
SUCCESS and ERROR rows can coexist.

**AC-BU-11**
Save creates SUCCESS rows only.

**AC-BU-12**
ERROR rows are skipped.

**AC-BU-13**
Backend re-validates Email uniqueness before Save.

**AC-BU-14**
A late duplicate caused by another concurrent transaction is rejected and not inserted.

**AC-BU-15**
USER_MASTER and USER_HISTORY remain transactionally consistent.

**AC-BU-16**
Each SUCCESS row is assigned a DB-generated BIGINT as USER_ID.

**AC-BU-17**
Rows with invalid Email format (`INVALID_EMAIL`) or Email longer than 254 characters (`EMAIL_TOO_LONG`) are marked ERROR and not saved.

---

## User Detail Navigation

**AC-NAV-01**
Clicking User Name opens the corresponding User Detail in the same browser tab.

**AC-NAV-02**
User Detail does not open as popup/modal or new browser tab.

**AC-NAV-03**
Clicking Back returns to User Management.

**AC-NAV-04**
User Management search criteria are preserved when returning from User Detail.

**AC-NAV-05**
Pagination and sorting state are preserved where applicable.

**AC-NAV-06**
Browser Back returns to User Management with previous list state preserved.

**AC-NAV-07**
After successful Save, system displays a success message.

**AC-NAV-08**
After clicking OK, system returns to User Management.

**AC-NAV-09**
After successful Save, User Management Grid is refreshed/re-queried.

**AC-NAV-10**
After Save failure, Admin remains on User Detail.

**AC-NAV-11**
After Save failure, Admin can correct the data and retry.

**AC-NAV-12**
Cancel with no unsaved changes returns directly to User Management.

**AC-NAV-13**
Cancel with unsaved changes displays confirmation.

**AC-NAV-14**
Stay keeps Admin on User Detail.

**AC-NAV-15**
Leave discards unsaved changes and returns to User Management.

---

## User Detail

**AC-UD-01**
User ID is read-only, immutable, and a DB-generated BIGINT.

**AC-UD-02**
Email is read-only and immutable — it cannot be changed by any user or admin.

**AC-UD-03**
Part and Group (Organization) are read-only.

**AC-UD-04**
Password is not displayed.

**AC-UD-05**
Audit Information is not displayed.

**AC-UD-06**
Save is disabled when there is no actual change.

**AC-UD-07**
Save becomes enabled when an editable field changes.

**AC-UD-08**
Changing a field and returning it to the original value disables Save again.

**AC-UD-09**
Save performs Frontend validation before calling API.

**AC-UD-10**
Backend re-validates authorization and Business rules (Refer đến rule đăng ký user của file Login_User Register before login_SRS.md)

**AC-UD-11**
Admin cannot change own Role.

**AC-UD-12**
Admin cannot change own Status.

**AC-UD-13**
An update request that changes the user's Email is rejected with `EMAIL_IMMUTABLE`.

**AC-UD-14**
Successful update changes USER_MASTER and creates USER_HISTORY.

**AC-UD-15**
Failed transaction rolls back.

**AC-UD-16**
Save is disabled during processing to prevent duplicate submission.

**AC-UD-17**
Save error keeps Admin on User Detail so the data can be corrected.

---

## Database / Uniqueness

**AC-DB-01**
USER_ID is a DB-generated BIGINT Primary Key.

**AC-DB-02**
EMAIL has a database-level case-insensitive Unique Constraint/Unique Index.

**AC-DB-03**
Database uniqueness is case-insensitive for EMAIL.

**AC-DB-04**
DEACTIVE users remain included in Email uniqueness checks.

**AC-DB-05**
Concurrent Create/Update requests cannot create duplicate Emails.

---

## Download

**AC-EX-01**
Download exports the complete current search result, not only the current page.

**AC-EX-02**
Password and Password Hash are never exported.

---

# 30. DEV Handoff Checklist

## User Management

* [ ] Admin-only authorization.
* [ ] Backend authorization.
* [ ] Search Area implemented.
* [ ] Grid implemented (no User ID column).
* [ ] No Action column.
* [ ] Grid Status read-only.
* [ ] Create User button.
* [ ] Bulk Upload button.
* [ ] Export User List button.

## Search Area

### Organization

* [ ] Searchable Dropdown.
* [ ] Single selection.
* [ ] Search by keyword.
* [ ] Clear selected value.

### Role

* [ ] Multi-select Dropdown.
* [ ] Checkbox for each option.
* [ ] Multiple selections supported.
* [ ] Selected values displayed as pills/chips.
* [ ] Individual selected value can be removed.
* [ ] Empty selection means All Roles.
* [ ] Search uses IN / OR logic.

### Status

* [ ] Multi-select Dropdown.
* [ ] Checkbox for each option.
* [ ] Multiple selections supported.
* [ ] Selected values displayed as pills/chips.
* [ ] Individual selected value can be removed.
* [ ] Empty selection means All Statuses.
* [ ] Search uses IN / OR logic.

## Create User

* [ ] Reuse Registration Business Logic.
* [ ] Required validation.
* [ ] User ID is DB-generated BIGINT (not an input field).
* [ ] Email validation (format + max 254 — xem §5.3.1).
* [ ] Email uniqueness is case-insensitive.
* [ ] Email uniqueness checked against all USER_MASTER records.
* [ ] Email of DEACTIVE users cannot be reused.
* [ ] Database case-insensitive Unique Constraint/Index for Email.
* [ ] Backend handles concurrent duplicate requests.
* [ ] Password policy.
* [ ] Password hashing.
* [ ] USER_MASTER insert.
* [ ] USER_HISTORY insert.
* [ ] Transaction handling.
* [ ] REGISTER_ID = Admin Login ID.

## Bulk Upload

* [ ] Local File Picker.
* [ ] One file per upload.
* [ ] `.xlsx` supported.
* [ ] `.xls` supported.
* [ ] Other file types rejected.
* [ ] Actual file format checked by backend.
* [ ] File validation.
* [ ] Row validation.
* [ ] Duplicate Email within file detected case-insensitively.
* [ ] Duplicate Email against USER_MASTER detected.
* [ ] DEACTIVE users included in duplicate checks.
* [ ] Each SUCCESS row assigned a DB-generated BIGINT.
* [ ] Validation Result screen.
* [ ] SUCCESS / ERROR status.
* [ ] Meaningful error message.
* [ ] Save SUCCESS rows only.
* [ ] Skip ERROR rows.
* [ ] Backend re-validation.
* [ ] Late concurrent duplicate handled.
* [ ] USER_MASTER / USER_HISTORY transaction.
* [ ] Prevent duplicate Save.
* [ ] Temporary file cleanup.

## User Detail

* [ ] User Name editable.
* [ ] Email read-only + immutable.
* [ ] Update request with changed Email rejected (EMAIL_IMMUTABLE).
* [ ] Organization read-only.
* [ ] Role editable only for other users.
* [ ] Status editable only for other users.
* [ ] Password not displayed.
* [ ] Audit Information not displayed.
* [ ] Save initially disabled.
* [ ] Save enabled only when actual change exists.
* [ ] Revert-to-original disables Save.
* [ ] Frontend validation.
* [ ] Backend validation.
* [ ] Self Role change rejected.
* [ ] Self Status change rejected.
* [ ] Optimistic concurrency handling.
* [ ] USER_MASTER update.
* [ ] USER_HISTORY creation.
* [ ] Transaction rollback.
* [ ] Save loading state.
* [ ] Duplicate Save prevention.
* [ ] Success message.
* [ ] Error message.
* [ ] Keep user on Detail after Save failure.

## Database

* [ ] USER_ID BIGINT primary key (DB-generated, `BIGSERIAL`).
* [ ] EMAIL case-insensitive unique constraint/index.
* [ ] KNOX_ID present on `"user"` (derived from email local part, V13) — used to link `user` ↔ `organization_master`.
* [ ] DEACTIVE users remain subject to Email uniqueness.
* [ ] Application-level uniqueness validation.
* [ ] Database-level uniqueness enforcement.
* [ ] Concurrent request/race condition handled.
* [ ] USER_HISTORY relationship implemented.

## User Detail Navigation

* [ ] User Name opens User Detail.
* [ ] Same browser tab.
* [ ] No popup/modal.
* [ ] No new browser tab.
* [ ] Back button implemented.
* [ ] Browser Back supported.
* [ ] Search criteria preserved.
* [ ] Pagination state preserved where applicable.
* [ ] Sort state preserved where applicable.
* [ ] Save Success → Success message.
* [ ] Success OK → User Management.
* [ ] Grid refresh after successful Save.
* [ ] Save Error → remain on User Detail.
* [ ] Cancel without changes → User Management.
* [ ] Cancel with unsaved changes → confirmation dialog.
* [ ] Stay → remain on User Detail.
* [ ] Leave → discard changes and return.

## Excel Export

* [ ] Current search criteria applied.
* [ ] Complete result exported.
* [ ] Pagination does not limit export.
* [ ] Password excluded.
* [ ] Password Hash excluded.
* [ ] Audit information excluded.
* [ ] Backend authorization applied.

---
# 31. Version History

| Version | Status | Change |
|---|---|---|
| 1.0 | Baseline | Create User Management SRs |
| 1.1 | Updated | USER_MASTER / USER_HISTORY: replaced `ORGANIZATION` column with `PART` + `GROUP` (both nullable) per Login SRS v1.2. UI "Organization" fields remain as combined display of `Part > Group` |
| 1.2 | Updated | Per Login SRS v1.3: removed `KNOX_ID` everywhere; `USER_ID` is now a DB-generated BIGINT (not user-entered — removed from Create User input, Bulk template, and Grid); `EMAIL` is the only unique identifier (Login ID) and is **immutable** (read-only in User Detail, not updatable via API). User Detail opens via User Name click. Grid keeps Organization column as combined `Part > Group` display |
| 1.3 | Updated | Added specific Email format validation rules (format standard, max 254 characters, error codes `EMAIL_REQUIRED` / `INVALID_EMAIL` / `EMAIL_TOO_LONG` / `EMAIL_ALREADY_USED` — same naming as Settings SRS/Spec) to Create User (§5.3.1), Email Uniqueness Rule (§5.4), Bulk Upload row-level validation (§6.4), DB rule (§26.1), API (§27), AC-UM-11/12, AC-BU-17 and DEV checklist |
| 1.4 | Updated | §5.3.1 expanded to the full shared validation error-code set (`REQUIRED_FIELD_MISSING`, `PASSWORD_POLICY_INVALID`, `PASSWORD_MISMATCH`, `REGISTRATION_FAILED`) so Create User and Self Registration (Login SRS v1.5) reference the same codes |
| 1.5 | Updated | §5.3.1 password error codes aligned to the granular Settings SRS/Spec set: `PASSWORD_POLICY_INVALID` → `PASSWORD_TOO_SHORT` + `PASSWORD_WEAK` (per Login SRS v1.6) |
| 1.6 | Updated | Aligned with implementation (per Login SRS v1.7): `USER_ID` is a **BIGINT** (not UUID); **Knox ID re-added** as a Grid column (local-part of email, used to link `user` ↔ `organization_master` for the login org check) — overrides the v1.2/v1.5 "Knox ID removed" decision; §1.3 identifier model + §4.1 Grid columns updated |
| 1.7 | Updated | Aligned with implementation: API paths now use `/api/v1` prefix (§18, §27) with response envelope `{ success, data, errorCode?, message? }`; `REQUIRED_FIELD_MISSING` → `NAME_REQUIRED`; `PASSWORD_WEAK` message no longer requires special character (uppercase + lowercase + at least 1 number); §26 DB schema aligned with actual tables — `"user"` (cột `id`, `name`, `password` BCrypt, `knox_id`, `avatar_url`, `version`; **không có** cột `PART`/`GROUP` — đã drop ở V13, Part/Group lấy từ `organization_master` qua `knox_id`) và `user_history` (không có password snapshot, không có part/group); §3.2/§4.7/§5.2/AC-UM-04: Part/Group không thuộc bảng `"user"`, không có Batch process — nguồn từ `organization_master` (Org Management import) |
