# SRS — MCP Management Module

## Kiểm soát tài liệu

| Thuộc tính | Giá trị |
| --- | --- |
| Dự án | AX Portal (skill-management) |
| Module | MCP Management + tích hợp Skill–MCP |
| Phiên bản tài liệu | 2.0 — **REBUILD: module MCP xây lại hoàn toàn theo model Skill Management (public-only)** |
| Ngày lập | 26/10/2026 |
| Ngày cập nhật | 05/11/2026 (v2.0 — rebuild toàn bộ module theo quyết định PO 2026-11-05) |
| Ngôn ngữ tài liệu | Tiếng Việt |
| Người lập | Business Analyst |
| Người review / phê duyệt | Product Owner, BA — chờ xác nhận |
| Mục đích | Đặc tả nghiệp vụ + hợp đồng API cho module MCP Management (sau rebuild) và tích hợp Skill–MCP |
| Tài liệu tham chiếu | `SRS/1. Skill Management module/` (mẫu cấu trúc + logic clone), codebase hiện có |

> **Ghi chú đường dẫn:** Thư mục `Vibe coding basline` có typo ("basline") — giữ nguyên vì compat với git history.

### Lịch sử thay đổi

| Phiên bản | Ngày | Nội dung |
| --- | --- | --- |
| 1.0–1.3 | 26–27/10/2026 | Kiến trúc cũ: scope PRIVATE/PART/GROUP/PUBLIC, transport HTTP/STDIO, config template, credential fields, install spec, archive, app Windows contract (D-1…D-25, Q-1…Q-57). **TẤT CẢ ĐÃ BỎ** (v2.0). |
| 2.0 | 05/11/2026 | **REBUILD (PO confirmed 2026-11-05):** (1) Xóa toàn bộ source module MCP cũ, xây lại. (2) Clone toàn bộ logic Skill Management (workflow, version update, files, rating, subscribe, view count, export, status history, notifications). (3) **PUBLIC-only** — không có scope/sharing/cowork/system. (4) Giữ tích hợp Skill↔MCP (pin, publish validation, suggestions, reverse reference, deep-link). (5) Thêm Comments + Category/Stage (taxonomy riêng `mcp_category`/`mcp_stage`). (6) Version model = Skill model (master row = current + pending_metadata; `mcp_version` = snapshot). (7) DB drop 9 bảng cũ + `skill_mcp_dependency`, tạo lại 14 bảng (migration V103). (8) Giữ `mcp_code` + `server_key`. (9) Bỏ: scope, transport, config, credential, install spec, archive, app Windows contract, dead-letter. |

### Quy ước mức độ xác nhận

- **C — Confirmed:** đã được PO xác nhận (quyết định rebuild 2026-11-05).
- **P — Proposed:** đề xuất, chờ review.
- **O — Open:** điểm cần quyết định.

> **Lưu ý (v2.0):** Các quyết định D-1…D-25 và Q-1…Q-57 của v1.x **không còn hiệu lực** trừ khi được nêu lại ở tài liệu này. Tài liệu Functional Spec v2.0 là bản đặc tả chi tiết đi kèm.

---

# 1. Mục đích, bối cảnh, mục tiêu

## 1.1 Bối cảnh

Module MCP Management v1 (scope + transport + install spec) được xây cho hợp đồng web↔app Windows. PO quyết định (2026-11-05) **rebuild hoàn toàn** module theo model Skill Management — đơn giản hóa: MCP là record PUBLIC (không scope), file download trực tiếp trên web, không còn config/install spec. Màn MCP chưa public nên không có data thật cần giữ (DB drop + recreate).

## 1.2 Mục tiêu

1. **Web quản lý MCP** theo đúng model Skill Management: workflow duyệt, version, files, rating, subscribe, comments, category/stage.
2. **Skill pin MCP** (`skill_mcp_dependency`) + publish validation khi submit/approve skill.
3. **MCP PUBLIC**: mọi user thấy + download MCP đã APPROVED; owner/admin thấy mọi status.

## 1.3 Ranh giới

- Không còn hợp đồng web↔app Windows (install plan API bỏ).
- App Windows không phải đối tượng của module này ở v2.0.

---

# 2. Thuật ngữ (v2.0)

| Thuật ngữ | Định nghĩa |
| --- | --- |
| MCP | Model Context Protocol — record trong portal: name, description, hashtags, files, category/stage. |
| MCP Code | `MCP-0001` — ID nội bộ tự sinh, unique, không tái sử dụng sau soft delete. |
| Server Key | Slug tự sinh từ name (immutable, unique) — dùng trong frontmatter pin `mcp: key@version` + dependency row. |
| Public (visibility) | MCP APPROVED thấy được bởi mọi user; owner/admin thấy mọi status. Không có scope. |
| Version update | Owner stage metadata + files mới (`pending_metadata` + pending files); admin approve → snapshot cũ vào `mcp_version`, áp mới, version +1. |
| Version-update-pending | Master PENDING_APPROVAL + pending_metadata != null — vẫn phục vụ bản approved (visible/downloadable/rateable). |
| Category / Stage | Taxonomy riêng của MCP (`mcp_category`/`mcp_stage`), admin CRUD, bắt buộc khi tạo. |
| Dependency | Skill pin MCP ở một version number (`skill_mcp_dependency`), luôn optional. |

---

# 3. Quyền & Visibility

## 3.1 Visibility rule

`deleted_yn = 'N'` AND (creator OR admin OR status = APPROVED OR (status = PENDING_APPROVAL AND pending_metadata IS NOT NULL)).

- Owner: thấy MCP của mình mọi status.
- Admin: thấy mọi MCP mọi status.
- User khác: chỉ APPROVED (kể cả version-update-pending — phục vụ bản approved).
- Soft-deleted: không ai thấy kể cả admin.

## 3.2 Ma trận quyền

Xem chi tiết ma trận tại Functional Spec v2.0 §1.2. Tóm tắt:
- **Owner:** edit/submit/withdraw/update-version/cancel/delete MCP của mình; download mọi status.
- **Admin:** approve/reject (mọi MCP pending); delete mọi MCP; download PENDING/APPROVED; quản lý taxonomy; xóa comment bất kỳ.
- **User khác:** xem/download/comment/rate/subscribe MCP APPROVED; xóa comment của mình.
- Admin **KHÔNG** edit/submit/withdraw/update-version MCP của user khác (clone rule Skill).

---

# 4. Nghiệp vụ chính

## 4.1 Workflow

DRAFT → PENDING_APPROVAL (owner/admin submit) → APPROVED (admin) / REJECTED (admin, reason ≤500) → DRAFT (owner withdraw).
APPROVED → PENDING_APPROVAL (owner update version, gắn pending_metadata) → approve (complete version update) / reject (về APPROVED, giữ reason) / cancel (về APPROVED).

Chi tiết + notifications: Functional Spec v2.0 §6.

## 4.2 Version model (= Skill model)

- `mcp_master` row = **version hiện tại** (name/description/hashtags + live files `pending_yn='N'`).
- `mcp_version` + `mcp_version_file` = **snapshot history** (tạo khi approve version update).
- Version number tự tăng (1, 2, 3...) khi approve version update.

## 4.3 Files

- Nhiều file/MCP (multipart, ≥1 file).
- Edit (DRAFT/REJECTED): skill-style Delete/Undo + upload mới.
- Update Version: file current kế thừa (trừ phần xóa) + file mới staged (`pending_yn='Y'`).
- Download rule: creator mọi status; admin PENDING/APPROVED; khác chỉ APPROVED.
- Download All = zip in-memory.

## 4.4 Comments

Clone Skill comments: chỉ MCP APPROVED/version-update-pending; ≤1000 ký tự; rate limit 30/phút; soft delete (owner/admin); notify creator.

## 4.5 Rating & Subscribe

Clone Skill: 1.0–5.0 step 0.5, 1 user 1 rating, rate limit 10/phút, notify creator; subscribe toggle, notify subscribers khi version mới approve.

## 4.6 Category & Stage

Taxonomy riêng MCP (admin CRUD, bắt buộc khi tạo/update). Clone Skill taxonomy.

## 4.7 Tích hợp Skill↔MCP

Chi tiết tại Functional Spec v2.0 §13. Tóm tắt:
- Skill pin MCP (optional, version number ≤ current).
- Save validation: MCP tồn tại + version hợp lệ + live (ngoại lệ owner).
- Publish validation (submit/approve skill): mọi MCP phải live + version hợp lệ, không thì block.
- **Edit Skill (2026-11-06):** skill APPROVED → section MCP Dependencies read-only trong Edit (giống rule files) — thay đổi dependency chỉ qua Update Version. Backend bỏ qua `mcpDependencies` khi `wasPublished`.
- Fuzzy suggestions (0.6 name/tags + 0.4 description, threshold 0.2, top 3).
- Frontmatter `mcp: server_key@version` parser.
- Reverse reference: tab "Skills using this MCP".
- Notification deep-link `mcpId`.

---

# 5. Data Model (V103)

14 bảng — chi tiết tại Functional Spec v2.0 §15:
`mcp_master`, `mcp_version`, `mcp_version_file`, `mcp_files`, `mcp_comments`, `mcp_ratings`, `mcp_category`, `mcp_stage`, `mcp_category_mapping`, `mcp_stage_mapping`, `mcp_subscriptions`, `mcp_status_history`, `mcp_download_log`, `mcp_view_log` + `skill_mcp_dependency` (recreate).

i18n: 250 keys `app.mcp.*` (en/vi/ko).

---

# 6. API

Hợp đồng API đầy đủ tại Functional Spec v2.0 §16 (`/api/v1/mcps` + `/api/v1/admin/mcp-taxonomy`).

**Error codes:** MCP_NOT_FOUND (404), MCP_NOT_APPROVED (403), MCP_INVALID_TRANSITION (400), MCP_INVALID_RATING (400), MCP_VERSION_NOT_FOUND (404), MCP_RATE_LIMITED (429), MCP_VERSION_NOT_APPROVED (400), MCP_DELETE_FORBIDDEN (403), MCP_COMMENT_EMPTY (400), MCP_COMMENT_TOO_LONG (400), MCP_COMMENT_NOT_FOUND (404).

---

# 7. Notifications

| Event | Người nhận | Kênh |
| --- | --- | --- |
| Submit MCP | Mọi admin | In-app + `mcpId` deep-link |
| Approve/Reject MCP | Owner | In-app + `mcpId` |
| Version mới approve | Owner + subscribers | In-app + `mcpId` |
| Skill blocked (publish validation) | Owner skill | In-app |
| Comment mới | Creator MCP | In-app + email |
| Rating mới | Creator MCP | In-app + email |

Mọi notification MCP gắn `mcpId` → click mở MCP Detail Popup (`/mcps?detail={id}`).

---

# 8. Acceptance Criteria (tóm tắt)

Toàn bộ AC-MCP-001…036 tại Functional Spec v2.0. Nhóm chính:
- **Visibility:** user khác không thấy DRAFT/REJECTED (AC-001/002).
- **Workflow:** transitions + notifications (AC-009…012).
- **Version update:** snapshot/promote/reject/cancel (AC-013…016).
- **Download rule** (AC-017/018).
- **Comments** (AC-019…022).
- **Rating/Subscribe** (AC-023…025).
- **Taxonomy** (AC-026/027).
- **View/Export** (AC-028/029).
- **Skill↔MCP integration** (AC-030…034).
- **Delete** (AC-035/036).

---

# 9. Traceability

| Quyết định rebuild (2026-11-05) | FR |
| --- | --- |
| Clone Skill logic, public-only | Toàn bộ |
| Giữ Skill↔MCP integration | FR-MCP-012 |
| Thêm Comments | FR-MCP-008 |
| Thêm Category/Stage (taxonomy riêng) | FR-MCP-010 |
| Version model = Skill | FR-MCP-006 |
| DB drop + recreate (V103) | §5 |
| Giữ mcp_code + server_key | §2, FR-MCP-012 |

---

# 10. Rủi ro / ghi chú

- **Data cũ mất:** 9 bảng mcp cũ + `skill_mcp_dependency` bị drop (màn chưa public, PO chấp nhận).
- **Skill module không được vỡ:** Phase 2+3 (backend mcp + skill integration) phải cùng 1 commit.
- **i18n:** Translation Management screen hiện 250 keys mới sau V103.
