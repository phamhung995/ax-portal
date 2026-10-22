# AI-NEWS — Bug Log

> Log các bug phát hiện / xử lý trong quá trình phát triển chức năng **AI News**.
> Quy tắc: mỗi bug 1 mục, append theo thời gian (mới nhất cuối). Khi fix xong → cập nhật trạng thái + ghi chú fix, KHÔNG xóa mục (giữ lịch sử).

**Trạng thái:** `OPEN` (chưa fix) / `FIXED` (đã fix) / `WONTFIX` (chấp nhận không fix — ghi lý do) / `REGRESSION` (bug cũ quay lại)

| # | Ngày phát hiện | Ngày fix | Trạng thái | Tiêu đề | Mô tả ngắn | Root cause | Fix | SRS/TC liên quan |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-12 | 2026-10-12 | FIXED | BUG-AINEWS-001 — App không chạy được: `Schema-validation: missing table [ai_news]` | Flyway không tạo 6 bảng ai_news → Hibernate validation fail khi start | File migration đặt tên `V24__ai_news.sql` trùng version với `V24__skill_request_management.sql` đã apply lên DB shared (flyway_schema_history đã có V24) | Đổi tên file thành `V25__ai_news.sql` | AI_News_Batch.md §6 (DDL) |
| 2 | 2026-10-12 | 2026-10-12 | FIXED | BUG-AINEWS-002 — Composite-id class không override equals()/hashCode() | Hibernate WARN HHH000038/HHH000039 cho `AiNewsLike$Id`, `AiNewsCommentVote$Id` — rủi ro sai khi so sánh entity | Class `Id` nested viết tay, thiếu equals/hashCode | Thêm `@Data` (Lombok) vào 2 class `Id` | AI_News_Screen_SRS.md §6.2/§6.3 |
| 3 | 2026-10-14 | 2026-10-14 | FIXED | BUG-AINEWS-003 — Nút vote "Hữu ích" trên comment đã xóa (tombstone) vẫn bấm được | Comment top-level bị xóa (tombstone) vẫn render nút vote; bấm → gọi backend → bị reject `COMMENT_NOT_FOUND` (vote comment đã xóa) → toast lỗi | Nút vote chỉ disable khi `own`, không xét `deleted` | Thêm `c.deleted` vào điều kiện `:disabled` của nút vote top-level (`AiNewsView.vue`) | AI_News_Screen_SRS.md FR-18/FR-19 |
| 4 | 2026-10-14 | 2026-10-14 | FIXED | BUG-AINEWS-004 — Không sync selection khi back/forward / sửa tay URL `?news=` | Summary §2 ghi "Back/forward → sync selection theo hash (watch route)" nhưng code chỉ có `watch(selectedId)`, không có watcher trên `route.query.news` → bấm back/forward hay sửa tay URL không đổi tin đang chọn | Thiếu watcher theo dõi `route.query.news` | Thêm `watch(() => route.query.news, ...)` → `selectItem(id)` khi id hợp lệ & khác tin hiện tại (`AiNewsView.vue`) | ai-news_sumary.md §2 |
| 5 | 2026-10-15 | 2026-10-15 | FIXED | BUG-AINEWS-005 — Tombstone comment (top-level + reply) vẫn hiện nút Xóa | Comment đã xóa (tombstone) vẫn render nút "Xóa" cho owner/admin → bấm → gọi DELETE backend → lỗi (comment đã xóa) | `v-if` nút xóa chỉ xét `own || isAdmin`, không xét `deleted` (BUG-AINEWS-003 chỉ fix nút vote top-level, bỏ sót nút xóa cả 2 tầng) | Thêm `!c.deleted &&` / `!r.deleted &&` vào `v-if` nút xóa top-level + reply (`AiNewsView.vue`) | AI_News_Screen_SRS.md FR-19 / TC-18 |
| 6 | 2026-10-15 | 2026-10-15 | FIXED | BUG-AINEWS-006 — List tin trái không thẳng hàng dọc (regression fix v1.9) | `.ainews-list-card .ai-news-list` bị `display: grid` → item giữ max-content width, dot + title lệch ngang | Regression — fix v1.9 (`display: block`) bị mất khi refactor CSS | Đổi lại `display: block` (`AiNewsView.vue`) | AI_News_Screen_SRS.md TC-19 / v1.9 |

---

## Chi tiết (khi bug cần mô tả dài)

<!-- Template — copy khi ghi bug mới:

### BUG-AINEWS-001 — <tiêu đề>
- **Ngày phát hiện:** YYYY-MM-DD
- **Trạng thái:** OPEN
- **Mô tả:** <hành vi sai, bước tái hiện (steps to reproduce)>
- **Root cause:** <nguyên nhân gốc>
- **Fix:** <thay đổi gì, file nào (bắt buộc trong folder ai-news/ — Rule 1)>
- **Ngày fix:** YYYY-MM-DD
- **SRS/TC liên quan:** <ID requirement / test case trong AI_News_Screen_SRS.md>
-->

### BUG-AINEWS-003 — Nút vote "Hữu ích" trên comment đã xóa (tombstone) vẫn bấm được
- **Ngày phát hiện:** 2026-10-14
- **Trạng thái:** FIXED
- **Mô tả:** Comment top-level bị xóa (soft delete) được render dạng tombstone "[đã xóa]" để giữ ngữ cảnh reply con (FR-19). Tuy nhiên nút vote "Hữu ích" trên comment tombstone này vẫn active. Khi user bấm → frontend gọi `POST /comments/{id}/helpful` → backend reject vì comment đã xóa (`COMMENT_NOT_FOUND`) → toast lỗi. Nút Reply thì đã được ẩn (`v-if="!c.deleted"`), nhưng nút vote không được.
- **Root cause:** Điều kiện `:disabled` của nút vote top-level chỉ xét `c.own || voteSendingIds.has(...)`, không xét `c.deleted`.
- **Fix:** Thêm `c.deleted` vào điều kiện `:disabled` của nút vote top-level trong `AiNewsView.vue` (nút vote của reply con không cần vì reply con bị xóa sẽ bị ẩn hoàn toàn, không render).
- **Ngày fix:** 2026-10-14
- **SRS/TC liên quan:** AI_News_Screen_SRS.md FR-18 (vote) / FR-19 (xóa comment)

### BUG-AINEWS-004 — Không sync selection khi back/forward / sửa tay URL `?news=`
- **Ngày phát hiện:** 2026-10-14
- **Trạng thái:** FIXED
- **Mô tả:** `ai-news_sumary.md §2` ghi "Back/forward browser → sync selection theo hash (watch route)". Code chỉ có `watch(selectedId)` (selection → URL) nhưng không có watcher theo chiều ngược lại (URL → selection). Hệ quả: bấm nút back/forward của browser hoặc sửa tay `?news=` trên URL không làm đổi tin đang hiển thị.
- **Root cause:** Thiếu watcher trên `route.query.news`.
- **Fix:** Thêm `watch(() => route.query.news, ...)` — khi query là số hợp lệ và khác tin hiện tại → `selectItem(id)`. `selectItem` no-op khi id trùng tin đang chọn nên không xung đột với `watch(selectedId)`.
- **Ngày fix:** 2026-10-14
- **SRS/TC liên quan:** ai-news_sumary.md §2

### BUG-AINEWS-005 — Tombstone comment (top-level + reply) vẫn hiện nút Xóa
- **Ngày phát hiện:** 2026-10-15
- **Trạng thái:** FIXED
- **Mô tả:** Comment đã xóa (soft delete) render dạng tombstone "[đã xóa]" (FR-19). Nút vote đã được disable (BUG-AINEWS-003) và nút Reply đã ẩn, nhưng nút "Xóa" vẫn hiện cho owner/admin ở cả 2 tầng (top-level + reply). Bấm → gọi `DELETE /comments/{id}` → backend reject (comment đã xóa) → toast lỗi.
- **Root cause:** `v-if` của nút xóa chỉ xét `own || isAdmin`, không xét `deleted`. BUG-AINEWS-003 chỉ fix nút vote top-level, bỏ sót nút xóa.
- **Fix:** Thêm `!c.deleted &&` (top-level) và `!r.deleted &&` (reply) vào `v-if` nút xóa trong `AiNewsView.vue`.
- **Ngày fix:** 2026-10-15
- **SRS/TC liên quan:** AI_News_Screen_SRS.md FR-19 / TC-18

### BUG-AINEWS-006 — List tin trái không thẳng hàng dọc (regression fix v1.9)
- **Ngày phát hiện:** 2026-10-15
- **Trạng thái:** FIXED
- **Mô tả:** Các item trong list tin trái không cùng left — item giữ max-content width nên dot + title lệch ngang. Đây là regression của fix đã ghi trong SRS v1.9 (TC-19).
- **Root cause:** `.ainews-list-card .ai-news-list` bị `display: grid` — fix v1.9 (`display: block`) bị mất khi refactor CSS.
- **Fix:** Đổi lại `.ainews-list-card .ai-news-list { display: block }` trong `AiNewsView.vue` (khớp TC-19: "CSS `display: block`").
- **Ngày fix:** 2026-10-15
- **SRS/TC liên quan:** AI_News_Screen_SRS.md TC-19 / v1.9
