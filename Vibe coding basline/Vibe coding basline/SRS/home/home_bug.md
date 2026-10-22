# HOME — Bug Log

> Log các bug phát hiện / xử lý trong quá trình phát triển chức năng **Home**.
> Quy tắc: mỗi bug 1 mục, append theo thời gian (mới nhất cuối). Khi fix xong → cập nhật trạng thái + ghi chú fix, KHÔNG xóa mục (giữ lịch sử).

**Trạng thái:** `OPEN` (chưa fix) / `FIXED` (đã fix) / `WONTFIX` (chấp nhận không fix — ghi lý do) / `REGRESSION` (bug cũ quay lại)

| # | Ngày phát hiện | Ngày fix | Trạng thái | Tiêu đề | Mô tả ngắn | Root cause | Fix | SRS/TC liên quan |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-15 | 2026-10-15 | FIXED | BUG-HOME-001 — Timeline card AI News (Community tab) không thẳng hàng dọc | `.ai-news-list` (card AI News) bị `display: grid` → item giữ max-content width, dot + title lệch ngang (cùng regression với BUG-AINEWS-006) | Regression — fix v1.9 của AI_News_Screen_SRS (`display: block`) bị mất khi refactor CSS | Đổi lại `.ai-news-list { display: block }` (`HomeView.vue`) | AI_News_Screen_SRS.md TC-19 / Home_Screen_SRS.md §5.8 |

---

## Chi tiết (khi bug cần mô tả dài)

### BUG-HOME-001 — Timeline card AI News (Community tab) không thẳng hàng dọc
- **Ngày phát hiện:** 2026-10-15
- **Trạng thái:** FIXED
- **Mô tả:** Card AI News trong Community tab dùng chung class timeline `.ai-news-list`/`.ai-news-item` với màn AI News. CSS `.ai-news-list` trong `HomeView.vue` bị `display: grid` → item giữ max-content width, dot + title không cùng left.
- **Root cause:** Regression — fix v1.9 của `AI_News_Screen_SRS.md` (`.ai-news-list { display: block }`) bị mất khi refactor CSS (khớp BUG-AINEWS-006 ở màn AI News).
- **Fix:** Đổi lại `.ai-news-list { display: block }` trong `HomeView.vue`.
- **Ngày fix:** 2026-10-15
- **SRS/TC liên quan:** AI_News_Screen_SRS.md TC-19 / Home_Screen_SRS.md §5.8
