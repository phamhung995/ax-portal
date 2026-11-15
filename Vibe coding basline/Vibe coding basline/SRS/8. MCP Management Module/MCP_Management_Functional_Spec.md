# Đặc tả chi tiết chức năng — MCP Management Module

## Kiểm soát tài liệu

| Thuộc tính | Giá trị |
| --- | --- |
| Dự án | AX Portal (skill-management) |
| Module | MCP Management + tích hợp Skill–MCP |
| Phiên bản tài liệu | 2.0 — **REBUILD: module MCP xây lại hoàn toàn theo model Skill Management (public-only)** |
| Ngày lập | 27/10/2026 |
| Ngày cập nhật | 05/11/2026 (v2.0 — rebuild toàn bộ module: xóa kiến trúc cũ (scope/transport/install spec), clone logic Skill Management, PUBLIC-only, thêm Comments + Category/Stage, DB drop + recreate V103) |
| Ngôn ngữ tài liệu | Tiếng Việt |
| Người lập | Business Analyst |
| Người review / phê duyệt | Product Owner, BA — chờ xác nhận |
| Tài liệu nguồn | `SRS/8. MCP Management Module/MCP_Management_SRS.md` v2.0 |
| Mục đích | Đặc tả chi tiết chức năng của module MCP Management sau rebuild (clone Skill Management, public-only) |

### Change Log

| Phiên bản | Ngày | Thay đổi |
| --- | --- | --- |
| 1.0–1.51 | 27/10–05/11/2026 | Kiến trúc cũ: scope PRIVATE/PART/GROUP/PUBLIC, transport HTTP/STDIO, install spec, config template, credential fields, archive, app Windows contract. **TẤT CẢ ĐÃ BỎ** (v2.0). |
| 2.0 | 05/11/2026 | **REBUILD (PO confirmed 2026-11-05):** (1) Xóa toàn bộ source module MCP cũ (backend `mcp/` + frontend `features/mcp/` + tests), xây lại. (2) **Clone toàn bộ logic Skill Management**: workflow DRAFT/PENDING_APPROVAL/APPROVED/REJECTED, version update (pending metadata), files (skill-style: Delete/Undo + upload mới), rating, subscribe, view count, Excel export, status history, notifications. (3) **PUBLIC-only — không có scope/sharing/cowork/system**: mọi user thấy MCP khi APPROVED; owner + admin thấy mọi status. (4) **Giữ tích hợp Skill↔MCP**: skill pin MCP (`skill_mcp_dependency`), publish validation khi submit/approve skill, section MCP Dependencies trong Add/Edit/UpdateVersion Skill, fuzzy suggestions, Browse All MCPs, notification deep-link `mcpId`, tab "Skills using this MCP" (reverse reference). (5) **Thêm 2 feature clone từ Skill**: Comments (tab trong MCP Detail) + Category/Stage (**taxonomy riêng** `mcp_category`/`mcp_stage` — admin CRUD). (6) **Model version = model Skill**: `mcp_master` row = version hiện tại + `pending_metadata`; bảng `mcp_version` chỉ là snapshot history. (7) **DB: drop 9 bảng mcp cũ + `skill_mcp_dependency`, tạo lại 14 bảng mới** (migration V103). (8) Giữ `mcp_code` (MCP-0001) + `server_key` (immutable, dùng trong frontmatter pin + dependency). (9) Bỏ hoàn toàn: scope, transport, config template, credential fields, install spec, archive, app Windows contract, dead-letter notification. Chi tiết: toàn bộ tài liệu v2.0. |

### Quy ước

- **Mã yêu cầu:** `FR-MCP-xxx` (bố cục lại từ đầu ở v2.0 — mã cũ 1.0–1.51 không còn hiệu lực).
- **AC:** `AC-MCP-xxx` (bố cục lại từ đầu ở v2.0).
- **API path:** dùng `{id}` numeric (id `mcp_master`); `mcp_code` chỉ là field trong response.
- **i18n:** mọi label UI dùng i18n key `app.mcp.*` (250 keys en/vi/ko — migration V103).
- **Terminology:** không còn "Scope" cho MCP (public-only). "Category"/"Stage" = taxonomy riêng của MCP (khác `skill_category`/`process_stage`).

---

# 1. Tổng quan kiến trúc (v2.0)

## 1.1 Nguyên tắc thiết kế

MCP Management là **bản clone của Skill Management** với các khác biệt:

| Khía cạnh | Skill Management | MCP Management (v2.0) |
| --- | --- | --- |
| Visibility | Scope PRIVATE/PART/GROUP/PUBLIC + share/cowork | **PUBLIC-only** — APPROVED thì mọi người thấy |
| Workflow | DRAFT/PENDING/APPROVED/REJECTED | **Cùng hệt** |
| Version model | Master row = current + pending_metadata; `skill_version` = snapshot | **Cùng hệt** (`mcp_master` + `mcp_version`) |
| Files | Nhiều file, Delete/Undo + upload mới, pending files | **Cùng hệt** (`mcp_files.pending_yn`) |
| Category/Stage | `skill_category`/`process_stage` (chung) | **Taxonomy riêng** `mcp_category`/`mcp_stage` |
| Comments | Có | **Có** (clone) |
| Rating/Subscribe/View/Export | Có | **Có** (clone) |
| MCP dependency | — | **MCP pin được bởi Skill** (chiều ngược) |
| mcp_code / server_key | — | **Giữ** (MCP-0001 / slug immutable) |
| Transport / config / install spec / archive | (kiến trúc cũ) | **BỎ HOÀN TOÀN** |

## 1.2 Quyền (ma trận)

| Thao tác | Owner | Admin | User khác |
| --- | --- | --- | --- |
| Xem MCP | Mọi status | Mọi status | Chỉ APPROVED (hoặc version-update-pending — phục vụ bản approved) |
| Edit (DRAFT/REJECTED) | ✓ | ✗ | ✗ |
| Submit for Approval (DRAFT/REJECTED → PENDING) | ✓ | ✓ | ✗ |
| Approve (PENDING → APPROVED) | ✗ | ✓ | ✗ |
| Reject (PENDING → REJECTED, reason ≤500) | ✗ | ✓ | ✗ |
| Withdraw (PENDING → DRAFT) | ✓ | ✗ | ✗ |
| Update Version (APPROVED → PENDING + pending metadata) | ✓ | ✗ | ✗ |
| Cancel version update | ✓ | ✗ | ✗ |
| Delete (soft) | ✓ (mọi status) | ✓ (mọi status) | ✗ |
| Download file | Mọi status | PENDING/APPROVED | Chỉ APPROVED |
| Comment | ✓ (MCP APPROVED hoặc version-update-pending) | ✓ | ✓ (cùng rule) |
| Xóa comment | Comment của mình | Mọi comment | Comment của mình |
| Rate (1.0–5.0, step 0.5) | ✓ (APPROVED) | ✓ | ✓ |
| Subscribe/Unsubscribe | ✓ (APPROVED) | ✓ | ✓ |
| Quản lý taxonomy (admin CRUD) | ✗ | ✓ | ✗ |

**Visibility rule (backend `McpMasterRepository.visibleTo`):**
`deleted_yn = 'N'` AND (creator OR admin OR status = APPROVED OR (status = PENDING_APPROVAL AND pending_metadata IS NOT NULL)).

**Download rule (clone Skill):** creator mọi status; admin PENDING/APPROVED; user khác chỉ APPROVED (version-update-pending phục vụ bản live).

---

# 2. FR-MCP-001 — Search & Grid (MCP Management screen)

**Mô tả:** Màn hình MCP Management — search panel + grid, clone `SkillManagementView` (bỏ filter Scope/System/Share; thêm filter Category/Stage).

**Search criteria:** keyword (name/description/hashtags), creator (SearchUserPopup), status (multi), category (multi), stage (multi), sort (createdDate / viewCount / avgRating), pagination.

**Grid columns:** checkbox, mcp_name, description (truncate + tooltip), creator_name, created_date, status, categories, stages, view_count, rating ("★ 4.5 (12)"/"No rating"), subscribe (bell toggle — chỉ khi APPROVED + không phải creator).

**Rules:**
- Không auto-search on mount — empty state "Enter search criteria..." (đồng nhất Skill Management).
- Bulk delete: nút "Delete (n)" → check-delete API → dialog liệt kê MCP không xóa được (all-or-nothing) → confirm → `DELETE /mcps?ids=...`.
- Double-click / Enter → mở Detail Popup.

**AC-MCP-001:** User nhập criteria + Search → grid trả kết quả đúng visibility (owner/admin: mọi status; khác: chỉ APPROVED + version-update-pending).
**AC-MCP-002:** User khác KHÔNG thấy MCP DRAFT/REJECTED của người khác trong search.

---

# 3. FR-MCP-002 — MCP Detail Popup

**Mô tả:** Clone `SkillDetailPopup` (bỏ Share/Cowork/Scope; thêm **tab Dependent Skills**).

**Header:** tên MCP (18px bold, truncate); Status badge + Category/Stage chips; meta "Creator: X · Created · Updated"; dòng "Code: MCP-0001 · Server key: tavily-search [copy] · Version: N (current) · Views".

**Files + Hashtags:** trên tabs — mỗi file "📄 name (size)" + Download (theo quyền); nút **Download All** (zip). Hashtag chips.

**Tabs:**
1. **Information** — Description (rich text), Rating (5 sao, editable khi APPROVED), Subscribe button, Rejection reason (khi REJECTED), Pending version info (khi version-update-pending).
2. **Versions** — list snapshot `mcp_version` (version, name, description, hashtags, creator, date, files + download).
3. **Comments** — list comment (newest first) + input (khi có quyền) + delete (owner/admin).
4. **Skills using this MCP** — reverse reference: lazy load `GET /mcps/{id}/dependent-skills`; mỗi skill = tên (link → Skill Detail) + `(v{n})` + `— creatorName`.

**Footer buttons (ma trận):**
- **Edit** — owner + DRAFT/REJECTED → mở Edit MCP Popup.
- **Submit for Approval** — owner/admin + DRAFT/REJECTED.
- **Approve / Reject** — admin + PENDING (Reject: prompt reason ≤500, bắt buộc).
- **Withdraw** — owner + PENDING (không phải version-update-pending).
- **Update Version** — owner + APPROVED (hoặc version-update-pending → mở lại form).
- **Cancel Update** — owner + version-update-pending.
- **Download / Download All** — theo download rule §1.2.
- **Delete** — owner/admin.

**Deep-link:** notification có `mcpId` → `router.push({ name: 'mcps', query: { detail: mcpId } })` → tự mở Detail Popup.

**AC-MCP-003:** Click notification MCP → Detail Popup mở đúng MCP.
**AC-MCP-004:** Tab "Skills using this MCP" list đúng các skill đang pin MCP (tên + version + creator).

---

# 4. FR-MCP-003 — Add MCP Popup

**Mô tả:** Clone `AddSkillPopup` (bỏ Scope/System/Cowork/McpDependencySection).

**Fields:**
1. **MCP Name** `*` (1–200 ký tự, unique).
2. **Category** `*` (taxonomy `mcp_category`).
3. **Stage** `*` (taxonomy `mcp_stage`).
4. **Description** `*` (rich text — bắt buộc, rule v1.50).
5. **Hashtag** (chip, không bắt buộc).
6. **Files** `*` (≥1 file, skill-style dropzone).

**Server key** tự sinh từ name (slugify, immutable, unique) — KHÔNG hiển thị trong form. **mcp_code** tự sinh (MCP-0001...) — KHÔNG hiển thị trong form.

**Postconditions:** master tạo **DRAFT** (không có auto-approve — public-only, mọi MCP phải qua admin approval) + status history NULL→DRAFT + toast.

**AC-MCP-005:** Save Draft → MCP DRAFT, chỉ owner/admin thấy.
**AC-MCP-006:** Trùng name → error; thiếu description/files/category/stage → validate client-side.

---

# 5. FR-MCP-004 — Edit MCP (DRAFT/REJECTED)

**Mô tả:** Clone `EditSkillPopup`. Chỉ owner, chỉ DRAFT/REJECTED.

**Fields:** như Add MCP + section Files kiểu Skill: file hiện tại với **Delete/Undo** (toggle, gạch ngang) + upload file mới (thêm vào). API `PUT /mcps/{id}` (multipart) + `removeFileIds`.

**AC-MCP-007:** Edit + save → metadata + files cập nhật, status giữ DRAFT/REJECTED.
**AC-MCP-008:** MCP APPROVED không có nút Edit (thay đổi phải qua Update Version).

---

# 6. FR-MCP-005 — Workflow (Submit / Approve / Reject / Withdraw)

**Mô tả:** Clone `SkillWorkflowService` (bỏ pending_reason SHARE/SCOPE, cowork).

**Transitions:**

| Từ | Về | Ai | Điều kiện |
| --- | --- | --- | --- |
| DRAFT / REJECTED | PENDING_APPROVAL | Owner / Admin | — |
| PENDING_APPROVAL | APPROVED | Admin | — |
| PENDING_APPROVAL | REJECTED | Admin | reason ≤500, không blank |
| PENDING_APPROVAL | DRAFT | Owner (withdraw) | Không phải version-update-pending |
| APPROVED | PENDING_APPROVAL | Owner (version update) | Gắn pending_metadata |

**Version-update reject/withdraw:** effective target = **APPROVED** (bản cũ vẫn live); reject giữ `rejectionReason` trên row APPROVED.

**Status history:** mọi transition ghi `mcp_status_history` (from, to, actor, reason).

**Notifications:**
- Submit → in-app **mọi admin** ("MCP 'X' by {owner} has been submitted for approval." + `mcpId`).
- Approve/Reject → in-app **owner** ("Your MCP 'X' has been approved." / "Your MCP 'X' was rejected. Reason: {reason}" + `mcpId`).

**AC-MCP-009:** Submit DRAFT → PENDING + notify toàn bộ admin.
**AC-MCP-010:** Admin reject không reason → 400.
**AC-MCP-011:** User thường không thể submit/approve/reject/withdraw MCP của người khác.
**AC-MCP-012:** Withdraw PENDING (không phải version update) → DRAFT.

---

# 7. FR-MCP-006 — Update Version (model Skill)

**Mô tả:** Clone `SkillVersionUpdateService`. Owner + APPROVED (hoặc đang version-update-pending → chỉnh lại).

**Form (UpdateVersionPopup):** Update Info `*` (≤500, bắt buộc), Name `*`, Category `*`, Stage `*`, Description `*` (rich text), Hashtag, Files (skill-style: file current với Delete/Undo + upload mới — file mới lưu `pending_yn='Y'`).

**Flow:**
1. Submit → master: status PENDING_APPROVAL + `pending_metadata` (name, description, hashtags, categoryIds, stageIds) + `pending_remove_file_ids`; file mới = pending.
2. **Trong khi pending:** master vẫn phục vụ bản approved (visible/downloadable/rateable như APPROVED — `isVersionUpdatePending()`); owner/admin xem được pending files.
3. **Admin Approve** → `completeVersionUpdate()`:
   - Snapshot current → `mcp_version` + `mcp_version_file` (mỗi live file 1 row);
   - Áp pending metadata (name/description/hashtags/category/stage);
   - Promote pending files (`pending_yn` Y→N);
   - Xóa file rows bị remove (disk không xóa);
   - `version` +1, clear pending_metadata/pending_remove_file_ids/rejection_reason;
   - Notify **owner + subscribers** ("MCP 'X' has a new approved version." + `mcpId`).
4. **Admin Reject** → master về APPROVED (bản cũ live), giữ rejectionReason, pending files xóa (disk + row).
5. **Owner Cancel** → master về APPROVED, pending files xóa (disk + row).

**AC-MCP-013:** Approve version update → version +1, snapshot đúng old state, metadata mới áp dụng.
**AC-MCP-014:** Reject version update → master APPROVED, version không tăng, rejectionReason hiện.
**AC-MCP-015:** Cancel version update → master APPROVED, pending files xóa trên disk + DB.
**AC-MCP-016:** Trong khi version-update-pending, user khác vẫn download được bản approved hiện tại.

---

# 8. FR-MCP-007 — Files & Download

**Mô tả:** Clone Skill file storage (disk + zip in-memory).

- `mcp_files`: id, mcpId, fileName, filePath, fileSize, **pendingYn** ('N' = live, 'Y' = staged cho pending version), createdAt.
- Upload: multipart nhiều file (1 lần/file), validate ≥1 file.
- Delete file: `DELETE /mcps/{id}/files/{fileId}` (chỉ khi DRAFT/REJECTED — owner).
- **Download rule** (§1.2): creator mọi status; admin PENDING/APPROVED; khác chỉ APPROVED.
- Endpoints: download 1 file, download-all (zip in-memory, dedupe name) — cho current version, pending version (owner/admin), và version snapshot (`/versions/{n}/files/...`).
- Download log: `mcp_download_log`.

**AC-MCP-017:** User thường download MCP DRAFT của người khác → 403.
**AC-MCP-018:** Download All → zip chứa đúng toàn bộ live files.

---

# 9. FR-MCP-008 — Comments (feature mới, clone Skill)

**Mô tả:** Comment trong MCP Detail (tab Comments).

**Rules:**
- Chỉ comment được MCP **APPROVED** hoặc **version-update-pending** (403 `MCP_NOT_APPROVED` ngược lại).
- Content: không blank (400 `MCP_COMMENT_EMPTY`), ≤1000 ký tự (400 `MCP_COMMENT_TOO_LONG`).
- Rate limit: 30 comment/phút/user (429 `MCP_RATE_LIMITED`).
- Soft delete (`deletedAt`): owner của comment hoặc admin (403 `MCP_DELETE_FORBIDDEN`); comment không tồn tại/sai MCP/đã xóa → 404 `MCP_COMMENT_NOT_FOUND`.
- List: newest first, phân trang, `canDelete` flag theo viewer.
- **Notification:** comment mới → in-app + email **creator MCP** (không notify người comment chính mình).

**AC-MCP-019:** Comment MCP DRAFT → 403.
**AC-MCP-020:** Comment >1000 ký tự → 400.
**AC-MCP-021:** Creator nhận notification khi có comment mới (không nhận khi tự comment).
**AC-MCP-022:** User lạ không xóa được comment của người khác; admin xóa được.

---

# 10. FR-MCP-009 — Rating & Subscribe (clone Skill)

**Mô tả:** Clone `SkillRatingService` + subscribe.

**Rating:**
- 1.0–5.0, step 0.5 (400 `MCP_INVALID_RATING` nếu sai step/ngoài range).
- 1 user 1 rating (upsert). Chỉ MCP APPROVED hoặc version-update-pending (403 `MCP_NOT_APPROVED`).
- Rate limit: 10 rating/phút/user (429).
- Notification: rating mới → in-app + email creator.
- `GET /mcps/{id}/rating` → {averageRating, totalRatings}; `GET /mcps/{id}/rating/me` → rating của viewer.

**Subscribe:**
- Toggle `mcp_subscriptions` (PK mcpId+userId). Chỉ khi APPROVED (hoặc version-update-pending).
- Subscribers nhận notification khi version mới được approve (FR-MCP-006).

**AC-MCP-023:** Rating 4.3 → 400 (sai step).
**AC-MCP-024:** Rating MCP DRAFT → 403.
**AC-MCP-025:** Subscribe → nhận notification khi version mới approve.

---

# 11. FR-MCP-010 — Category & Stage (taxonomy riêng MCP)

**Mô tả:** Clone Skill taxonomy, **taxonomy riêng** cho MCP: bảng `mcp_category` / `mcp_stage` + mapping `mcp_category_mapping` / `mcp_stage_mapping`.

- **Admin CRUD** (`AdminMcpTaxonomyController`, path `/admin/mcp-taxonomy`): create/update/delete category & stage (id, name, sortOrder, description).
- **Public read:** `GET /mcps/taxonomy` → {categories[], stages[]} (kèm `mcpCount`).
- Skill form Add/Edit/UpdateVersion MCP: Category `*` + Stage `*` (bắt buộc).
- Search filter: Category/Stage multi.
- Version update: category/stage nằm trong pending_metadata (áp khi approve).

**AC-MCP-026:** Admin tạo category → hiện trong form Add MCP + filter search.
**AC-MCP-027:** Add MCP không chọn Category/Stage → validate fail.

---

# 12. FR-MCP-011 — View Count & Excel Export (clone Skill)

- **View count:** `POST /mcps/{id}/view` khi mở Detail (log `mcp_view_log`).
- **Excel export:** `GET /mcps/export` (blob, JWT qua axios) — không chọn row → export trang hiện tại; có chọn → row chọn (param `ids`). Style `ExcelExportStyle` (reuse từ skill package).

**AC-MCP-028:** Mở Detail → view count tăng 1.
**AC-MCP-029:** Export có chọn row → file Excel chỉ chứa row chọn.

---

# 13. FR-MCP-012 — Tích hợp Skill↔MCP (GIỮ NGUYÊN)

**Mô tả:** Skill pin MCP qua `skill_mcp_dependency` (UQ skill_id+mcp_id).

**Dependency row:** skillId, mcpId, mcpCode, mcpServerKey, mcpVersionNumber, required (luôn FALSE — mọi dep optional), source (SKILL_MD / MANUAL).

**Save validation (`McpDependencyService.saveDependencies`):**
- mcpId null/trùng → 400 `MCP_NOT_FOUND`.
- MCP không tồn tại/soft-deleted → 400 `MCP_NOT_FOUND`.
- Pinned version > current version MCP → 400 `MCP_VERSION_NOT_FOUND`.
- MCP không live (không APPROVED, không version-update-pending) → 400 `MCP_VERSION_NOT_APPROVED` — **ngoại lệ: skill creator sở hữu MCP đó** (owner pin được MCP DRAFT/PENDING của mình).
- Replace-all: bulk delete theo skillId rồi insert.

**Publish validation (`McpPublishValidationService.validate(skillId)`)** — chạy khi skill **submit** (DRAFT/REJECTED→PENDING) và khi admin **approve skill**:
- MCP deleted/missing → blocker `MCP_DELETED`.
- MCP không live hoặc pinned version > current → blocker `NOT_APPROVED`.
- Có blocker → 400 + `blockers[]` (mcpId, mcpCode, mcpVersion, reason), skill giữ status, notify owner.

**UI (Skill side):**
- Section "MCP Dependencies" trong Add/Edit/UpdateVersion Skill: combobox + 3 fuzzy suggestions + chip (tên link → MCP Detail) + "Browse all MCPs" + "Add new MCP".
- **Fuzzy suggestions (`McpSuggestionService`):** weighted `0.6 × overlap(name+hashtags) + 0.4 × overlap(description)`, Jaccard/overlap tokenizer Unicode-aware + tiếng Việt + stopword, threshold 0.2, top 3; chỉ MCP APPROVED (hoặc version-update-pending) + visible.
- **Frontmatter parser:** đọc `mcp:` trong `skill.md` (entry `server_key@<integer>`) → resolve → dependencies (source SKILL_MD).
- **Skill Detail:** section "🔗 MCP Dependencies (n)" — tên (link), version pin, status badge, size, [Download] (zip all files của MCP), Download All.
- **MCP Detail:** tab "Skills using this MCP" (reverse reference, FR-MCP-002).
- **Notification deep-link:** mọi notification MCP gắn `mcpId` → click mở MCP Detail.

**AC-MCP-030:** Skill pin MCP DRAFT của user khác → 400.
**AC-MCP-031:** Skill pin MCP DRAFT của chính mình → OK.
**AC-MCP-032:** Submit skill có MCP chưa APPROVED → 400 + blockers[].
**AC-MCP-033:** Admin approve skill re-validate MCP (MCP đổi status giữa submit và approve → block).
**AC-MCP-034:** Fuzzy suggest trả top 3 MCP liên quan (threshold 0.2).

---

# 14. FR-MCP-013 — Delete (soft)

- `deleted_yn = 'Y'` — **không ai thấy kể cả admin** (mọi query lọc `deleted_yn='N'`).
- `mcp_code` + `server_key` không tái sử dụng.
- Check-delete: `GET /mcps/check-delete?ids=...` → liệt kê MCP không xóa được + lý do (all-or-nothing).
- MCP đang được skill pin: skill vẫn hoạt động (dependency row giữ; detail MCP hiển thị "No longer exists" — file đã download trước đó không ảnh hưởng).

**AC-MCP-035:** Delete MCP → biến mất khỏi mọi search/detail kể cả admin.
**AC-MCP-036:** Check-delete trả lý do cho từng MCP không xóa được.

---

# 15. Data Model (V103)

**14 bảng** (drop 9 bảng cũ + `skill_mcp_dependency` cũ, recreate):

| Bảng | Nội dung |
| --- | --- |
| `mcp_master` | id, mcp_code (UQ), mcp_name (UQ), server_key (UQ), description, status, rejection_reason, hashtags (jsonb), view_count, version, pending_metadata (jsonb), pending_remove_file_ids (jsonb), deleted_yn, created_by, created_at, updated_at |
| `mcp_version` | snapshot history: id, mcp_id, version, name, description, hashtags, created_by, created_at |
| `mcp_version_file` | id, mcp_version_id, file_name, file_path, file_size |
| `mcp_files` | id, mcp_id, file_name, file_path, file_size, **pending_yn**, created_at |
| `mcp_comments` | id, mcp_id, user_id, content (≤1000), created_at, deleted_at |
| `mcp_ratings` | id, mcp_id, user_id, rating (1.0–5.0), created_at; UQ(mcp_id, user_id) |
| `mcp_category` / `mcp_stage` | id, name, sort_order, description |
| `mcp_category_mapping` / `mcp_stage_mapping` | mcp_id + category_id / stage_id |
| `mcp_subscriptions` | mcp_id + user_id (PK), created_at |
| `mcp_status_history` | id, mcp_id, from_status, to_status, actor_id, reason, created_at |
| `mcp_download_log` / `mcp_view_log` | audit |
| `skill_mcp_dependency` | id, skill_id, mcp_id, mcp_code, mcp_server_key, mcp_version_number, required, source; UQ(skill_id, mcp_id) |

**i18n:** 250 keys `app.mcp.*` (en/vi/ko) — V103 (xóa bộ keys cũ, insert mới clone từ `app.skills.*`).

---

# 16. API Summary (`/api/v1/mcps`)

| Method | Path | Mô tả |
| --- | --- | --- |
| POST | `/` (multipart) | Create (DRAFT) |
| GET | `?keyword&creatorId&status&categoryIds&stageIds&sortBy&page&size` | Search |
| GET | `/taxonomy` | Category + Stage list |
| GET | `/suggest?skillName&skillDescription&skillHashtags` | Fuzzy top 3 |
| GET | `/{id}` | Detail |
| GET | `/{id}/dependent-skills` | Reverse reference |
| GET | `/check-delete?ids=` | Pre-delete check |
| DELETE | `?ids=` | Bulk soft delete |
| POST | `/{id}/view` | Record view |
| GET | `/export?ids=` | Excel export (blob) |
| PUT | `/{id}` (multipart) | Update (DRAFT/REJECTED) + removeFileIds |
| DELETE | `/{id}/files/{fileId}` | Delete file |
| PUT | `/{id}/status` | Workflow transition (toStatus, rejectionReason) |
| POST | `/{id}/versions` (multipart) | Update Version (staged) |
| POST | `/{id}/versions/cancel` | Cancel version update |
| GET | `/{id}/versions` | Version history |
| GET | `/{id}/files/{fileId}/download` | Download current file |
| GET | `/{id}/files/download-all` | Download all (zip) |
| GET | `/{id}/pending-versions/files/{fileId}/download` | Pending file (owner/admin) |
| GET | `/{id}/pending-versions/files/download-all` | Pending all (owner/admin) |
| GET | `/{id}/versions/{version}/files/{fileId}/download` | Snapshot file |
| GET | `/{id}/versions/{version}/files/download-all` | Snapshot all |
| GET/DELETE | `/{id}/subscription` | Subscribe / Unsubscribe |
| GET | `/{id}/rating` | Average + count |
| GET | `/{id}/rating/me` | My rating |
| PUT | `/{id}/rating` | Upsert rating |
| GET | `/{id}/comments?page&size` | Comment list |
| POST | `/{id}/comments` | Add comment |
| DELETE | `/{id}/comments/{commentId}` | Delete comment |

**Admin taxonomy:** `/api/v1/admin/mcp-taxonomy` (CRUD category + stage).

**Error codes (BusinessErrorCode):** MCP_NOT_FOUND (404), MCP_NOT_APPROVED (403), MCP_INVALID_TRANSITION (400), MCP_INVALID_RATING (400), MCP_VERSION_NOT_FOUND (404), MCP_RATE_LIMITED (429), MCP_VERSION_NOT_APPROVED (400), MCP_DELETE_FORBIDDEN (403), MCP_COMMENT_EMPTY (400), MCP_COMMENT_TOO_LONG (400), MCP_COMMENT_NOT_FOUND (404).

---

# 17. Frontend (`frontend/src/features/mcp/`)

| File | Nguồn | Ghi chú |
| --- | --- | --- |
| `api.ts` | clone `skills/api.ts` | Bỏ share/systems; thêm dependentSkills, suggest |
| `types.ts` | clone | McpListItem, McpDetailResponse, McpSearchResult, McpVersionItem, McpTaxonomyItem, McpSuggestionResponse... |
| `store.ts` | clone | Pinia `useMcpListStore` |
| `views/McpManagementView.vue` | clone SkillManagementView | Bỏ filter Scope/System/Share; thêm Category/Stage |
| `views/AddMcpPopup.vue` | clone AddSkillPopup | Name*/Category*/Stage*/Desc*/Files* |
| `views/EditMcpPopup.vue` | clone EditSkillPopup | + skill-style files |
| `views/UpdateVersionPopup.vue` | clone (skill) | Update Info* + desc required |
| `views/McpDetailPopup.vue` | clone SkillDetailPopup | + tab Dependent Skills; bỏ Share/Cowork |
| `views/ManageMcpTaxonomyPopup.vue` | clone ManageTaxonomyPopup | Taxonomy MCP riêng |
| `views/McpDependencySection.vue` | giữ (tích hợp) | Dùng trong skill popups |
| `views/BrowseAllMcpPopup.vue` | giữ (tích hợp) | Browse MCP từ skill popups |

**Route:** `/mcps` giữ; nav item giữ; deep-link `?detail={id}` giữ.

---

# 18. Tests (Phase 5)

6 test classes mới (clone pattern skill tests):
- `McpWorkflowServiceTest` (16 tests) — mọi transition + version update lifecycle.
- `McpVersionUpdateServiceTest` (3) — snapshot + promote + notify; discard.
- `McpRatingServiceTest` (6) — invalid step/range, not approved, upsert.
- `McpCommentServiceTest` (14) — rate limit, not approved, empty/too long, save+notify, delete rules.
- `McpPublishValidationServiceTest` (7) — blockers NOT_APPROVED / MCP_DELETED.
- `McpDependencyServiceTest` (8) — save validation + owner exception.

**Verify:** `mvn test` → BUILD SUCCESS (493 tests, 0 failures).

---

# 19. Điểm khác biệt so với v1.x (tóm tắt cho reviewer)

1. **Không còn scope** — mọi MCP PUBLIC; visibility = APPROVED (owner/admin: mọi status).
2. **Không còn transport/config/credential/install spec** — MCP chỉ là record + files (download trực tiếp trên web).
3. **Không còn archive** — chỉ soft delete.
4. **Không còn app Windows contract** (install plan API) — download file trực tiếp.
5. **Version model = Skill** (master row = current) thay vì "current_version pointer + version row là source of truth".
6. **Thêm Comments + Category/Stage** (taxonomy riêng).
7. **Giữ nguyên:** tích hợp Skill↔MCP (pin, publish validation, suggestions, reverse reference, deep-link), mcp_code + server_key.
