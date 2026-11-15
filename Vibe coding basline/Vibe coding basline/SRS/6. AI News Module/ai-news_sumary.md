# AI-NEWS — Summary (chi tiết)

> **File summary chi tiết** của chức năng **AI News** (theo Rule 3 — `tq.hop/RULE.md`).
> Index tổng quát → [`ai-news_index.md`](./ai-news_index.md). Nguồn gốc: `AI_News_Screen_SRS.md` (v1.17) + `AI_News_Batch.md`.

## 1. Nghiệp vụ tổng quan

AI News là **màn hình độc lập** (không phải popup) xem nội dung email gốc của các bản tin AI:
- Menu GNB riêng trong sidebar, **ngay dưới Home**, icon riêng, active khi ở route `#/ai-news`.
- Route: `#/ai-news[?news={newsId}]`.
- Layout **2-pane**: trái = list tin dạng timeline, phải = detail email (title + meta + body HTML) + like + comment.
- **Read-only nội dung email** (body HTML không sửa được) nhưng **interactive**: like bản tin (silent, không toast) + bình luận threaded + vote hữu ích trên comment + **@mention** (v1.17, tạm disabled) + **grid sort** + **comment sort** (oldest/newest/likes/trending).
- **Không phân quyền** — mọi user đã đăng nhập đều xem/comment/like mọi tin (không Part/Group filter, không PUBLISHED gate).

## 2. Luồng chính

```
[Menu GNB "AI News"]  hoặc  [Home → Community → card AI News → click title]
  → route #/ai-news[?news={newsId}]
  → mở không có ?news → tự chọn tin đầu (mới nhất); có ?news → chọn tin đó (không tồn tại → chọn tin đầu)
  → fetch GET /api/home/ai-news/{newsId} (lazy, async, timeout 10s)
      ├── 200 + body ≠ null → render body HTML
      ├── 200 + body = null → empty state
      ├── 404               → not found state (tin không tồn tại HOẶC đã archive — không phân biệt)
      └── 5xx / timeout     → error state
  → chọn tin khác → thay detail, hủy fetch cũ (AbortController)
```

- Title + meta (sender, ngày) render **ngay từ dữ liệu list** — không chờ fetch body.
- Back/forward browser → sync selection theo hash (watch route).

## 3. Cấu trúc màn hình (2-pane)

| Vùng | Nội dung |
|---|---|
| Page header | Title "AI News" + subtitle (i18n `home.aiNewsDesc`) |
| List pane (trái) | Timeline: dot rotating tone + title (link, **1 dòng ellipsis** + tooltip) + `sender · date`. Item đang chọn: title accent + dot glow. Click → chọn tin + cập nhật hash. **Thẳng hàng dọc**: `.ainews-list-card .ai-news-list { display: block }`; `.ai-news-body { min-width: 0 }` (fix ellipsis) |
| Detail pane (phải) | Title (16px, **1 dòng + ellipsis** + tooltip) → meta row (📧 sender trái, 📅 createdAt phải, mono, theo date format Settings) → body container (border, scroll, nền slate `#64748b`) → like row → comment section |

**Body HTML render rules:**
- Render **nguyên dạng** (đã sanitize phía server khi import — client KHÔNG sanitize lại).
- `img { max-width: 100%; height: auto }`, `table { max-width: 100% }`.
- Link: **màu vàng `#ffd54a`** (contrast ~7:1 trên nền slate), `target="_blank" rel="noopener"`.
- Nền container slate `#64748b`; ép `background-color: transparent !important` cho mọi element trong body (email có bg inline trắng) — **KHÔNG** ép `background-image` (banner hero có text trắng trên ảnh).
- `max-height: calc(100svh − 320px)` desktop; mobile (< 999px) 1 cột, `max-height: none`.

**6 trạng thái:** Normal / Loading ("Đang tải nội dung email…") / Error (alert warning) / Empty body (alert info) / 404 (alert warning) / Chưa chọn tin (empty state "Chọn một tin").

## 4. Interactive features (FR-15..FR-28)

| Feature | Quy tắc |
|---|---|
| **Like bản tin** (FR-15) | 1 nút like/tin (♥ + label + count), toggle 1 like/user/tin. **KHÔNG có toast** (silent — chỉ đổi icon fill + count). Bỏ ở v1.9, khôi phục v1.10 |
| **Comment** (FR-16) | Rich text form (FR-23) max 1000 ký tự plain text. Mọi user đã đăng nhập comment được. Sort: oldest/newest/likes/trending (FR-26) |
| **Threaded reply** (FR-17) | Nút Reply → form inline ở **dưới cùng** thread (sau replies). Depth tối đa 2; reply vào comment cấp 2 → **flatten về root** + toast info. Reply thụt lề |
| **Vote hữu ích** (FR-18) | Nút 👍 N trên comment + reply, toggle. **Không vote comment của mình** (`CANNOT_VOTE_OWN_COMMENT`). `helpfulCount` floored at 0 (GREATEST) |
| **Xóa comment** (FR-19) | Owner hoặc admin. Soft delete (`deleted_at`) — **idempotent** (no-op nếu đã xóa). Top-level đã xóa còn reply con → tombstone "[deleted]". Reply deleted → `replyCount` decrement (GREATEST floor) |
| **Empty comment** (FR-20) | "Chưa có bình luận nào" |
| **@mention** (FR-24, v1.17) | Gõ `@` → dropdown tìm user → insert mention → backend parse `data-user-id` → notification (bell + email, cap 10, skip actor). **Tạm disabled** (Nexus package) |
| **Grid sort** (FR-25, v1.17) | Sort list tin: newest / most liked / most commented. API trả `likeCount` + `commentCount` (CTE aggregate). Badge ❤️ + 💬 trên item |
| **Comment sort** (FR-26, v1.17) | Sort comment: oldest / newest / likes asc / likes desc / 🔥 trending (tổng like thread). Deleted roots không trend |
| **Collapsible form** (FR-27, v1.17) | Form thu gọn 1 dòng → click expand. Sticky trong drawer. Collapse sau send (via `justSent` tick) |
| **Bell deep-link** (FR-28, v1.17) | Click @mention notification → mở news + scroll tới comment + flash. `checkVisibility()` (không `offsetParent`). Cross-item giữ `?comment` |

## 5. API

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/home/ai-news` | List tin (limit, filters). Trả `likeCount` + `commentCount` (CTE aggregate, v1.17) |
| GET | `/api/v1/home/ai-news/{newsId}` | Chi tiết 1 tin (kể cả `body` HTML, nullable). 404 `NEWS_NOT_FOUND` |
| GET | `/api/v1/home/ai-news/{newsId}/comments` | List comment (top-level + reply) |
| POST | `/api/v1/home/ai-news/{newsId}/comments` | Tạo comment `{ content, parentId? }` — parse mention → notify (FR-24) |
| DELETE | `/api/v1/home/ai-news/{newsId}/comments/{commentId}` | Xóa (owner/admin, soft delete, idempotent) |
| POST | `/api/v1/home/ai-news/{newsId}/comments/{commentId}/helpful` | Toggle vote (typed `ToggleHelpfulResponse`) |
| POST | `/api/v1/home/ai-news/{newsId}/like` | Toggle like — typed `ToggleLikeResponse`, **client KHÔNG toast** |
| GET | `/api/v1/home/ai-news/mention-users?q=...` | @mention autocomplete (max 10, v1.17) |
| POST | `/api/v1/admin/ai-news/import` | Import .mht (admin, server-side size limits, exception mask) |
| POST | `/api/v1/admin/ai-news/run` | Chạy batch job thủ công (admin, `ADMIN_USER`) |
| GET | `/api/v1/admin/ai-news/logs?limit=20` | Tra log batch job (admin) |

> **Dead code:** `GET /api/v1/home/ai-news/sources` (distinct source options) tồn tại trong backend nhưng không có UI nào gọi — chưa xóa.

Error codes: `NEWS_NOT_FOUND`, `NO_SESSION`, `COMMENT_EMPTY`, `COMMENT_TOO_LONG`, `COMMENT_PARENT_NOT_FOUND`, `COMMENT_PARENT_DELETED`, `COMMENT_NOT_FOUND`, `COMMENT_NOT_OWN`, `CANNOT_VOTE_OWN_COMMENT`.

> List API `GET /api/home/ai-news` (trên Home) **KHÔNG trả body** — body chỉ fetch qua detail endpoint khi chọn tin.

## 6. Database

- **`ai_news`** — bảng gốc, DDL đầy đủ trong `AI_News_Batch.md` §6.1 (9 cột: news_id PK, message_id unique, title, body HTML, sender, source, created_at, archive_yn, imported_at). Màn hình dùng: news_id, title, body, sender, source, created_at, archive_yn (filter).
- **`ai_news_comment`** — comment threaded: comment_id PK, news_id FK, user_id FK, parent_id (null = top-level), content TEXT (v1.16 rich text HTML), helpful_count (GREATEST floor 0), reply_count (GREATEST floor 0, decrement on delete), created_at, deleted_at (soft delete).
- **`ai_news_comment_vote`** — vote hữu ích: PK (comment_id, user_id).
- **`ai_news_like`** — like bản tin: PK (news_id, user_id).
- **`notifications`** — v1.17: thêm cột `news_id` (migration V43) + index `idx_notifications_news` + `idx_notifications_user_created` cho @mention deep-link.

## 7. Batch job (nguồn dữ liệu)

`AI-NEWS-IMPORT` (chi tiết trong `AI_News_Batch.md` v1.23): email → DB, sanitize HTML (bỏ script/iframe/event handler/`javascript:` URL), dedup bằng `Message-ID` (`knox-{mailId}`), cleanup tin > 90 ngày (`archive_yn = 'Y'` + xóa ảnh, có archive sweep độc lập 03:30), log vào `ai_news_batch_log`. Admin có thể manual run (`POST /api/v1/admin/ai-news/run` — lock khi job đang chạy `NEWS_JOB_RUNNING` 409, fail/timeout `NEWS_JOB_FAILED` 500, 10-minute cap cho cả cron lẫn manual). Knox portal reader (web mail API — login tự động bằng user/password, 401/403 → re-login 1 lần) đọc inbox qua list + detail API, body HTML từ detail (ảnh remote URL → lưu disk, SSRF guard).

## 8. Edge cases chính

- Tin bị archive khi đang hiển thị → fetch sau trả 404.
- Body > 1MB (nhiều ảnh) → container scroll, không lazy-load (v1.0).
- Click 2 tin nhanh → chỉ fetch tin mới nhất (AbortController).
- Body chứa script (lỗ hổng sanitize) → CSP `script-src` chặn inline script.
- Link nội bộ trong body → mở tab mới bình thường, `rel="noopener"` bắt buộc.
- `?news={newsId}` không tồn tại → chọn tin đầu, không crash.
- Reply depth > 2 → flatten về root + toast.
- Like tin đã archive → bị chặn (`NEWS_NOT_FOUND`).

## 9. i18n

3 ngôn ngữ (vi/en/ko), prefix `aiNews.*` (~30 key: loading, loadFail, noBody, notFound, selectNews, comment*, reply*, vote*, delComment*, like/liked). Menu GNB: `app.aiNews`.

## 10. Điểm cần nhớ khi fix/extend

- **Menu GNB "AI News" nằm ngay dưới Home** — nếu task AI News bắt buộc sửa GNB (app shell).
- Card AI News trên **Home** (list 8 tin) thuộc folder `home/` — không sửa khi làm màn hình AI News.
- Like là **silent** — không bao giờ thêm toast cho like/unlike (quyết định v1.10).
- 404 không phân biệt "không tồn tại" vs "đã archive" (chống lộ thông tin).
- `newsId` là Number (BIGSERIAL) — demo mock dùng string `AN-xxx` là demo-only, không phải API contract.
- Nền body = slate `#64748b` + link vàng `#ffd54a` — đổi màu phải giữ contrast.

## 11. Trạng thái implement Phase 2 + Review rounds 1-5 (2026-10-22)

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| `AiNewsView.vue` 2-pane | ✅ | list timeline + detail (body/like/comments/votes), 6 body states, AbortController, grid sort, comment sort, collapsible form, bell deep-link |
| `AiNewsComments.vue` | ✅ | shared component (main pane + drawer), mention-enabled, sanitize cache, reply form at bottom, sort dropdown, comment count badge |
| `RichTextEditor.vue` | ✅ | mention opt-in (mentionEnabled prop) — **temporarily disabled** (@tiptap/extension-mention not on Nexus) |
| Backend package `ainews` (9 public + 3 admin = 12 endpoints) | ✅ | list/detail/comments/POST/DELETE/helpful/like/mention-users (public) + admin-import/admin-run/admin-logs — prefix `/api/v1/home/ai-news` + `/api/v1/admin/ai-news` |
| V24 migration (6 bảng) | ✅ | ai_news, ai_news_source, ai_news_batch_log, ai_news_comment, ai_news_comment_vote, ai_news_like — trong `V24__phase3_consolidated.sql` |
| V42 migration | ✅ | ai_news_comment.content VARCHAR(1000) → TEXT |
| V43 migration | ✅ | notifications.news_id + idx_notifications_news + idx_notifications_user_created |
| Error handler + codes | ✅ | NEWS_NOT_FOUND + 8 code comment/vote + MHT_INVALID_FILE/MHT_EMPTY_SUBJECT |
| GNB sidebar + route `/ai-news` | ✅ | ngoại lệ Rule 1 đã approve |
| i18n 3 ngôn ngữ | ✅ | `ai-news/i18n.ts` ~50 key (gốc + mention + sort + grid sort) |
| Security fixes | ✅ | SSRF guard, SVG XSS, comment cap 20k, HtmlSanitizer url()/@, MHT size limits + exception mask, MhtEmailParser recursion cap |
| Performance fixes | ✅ | N+1 batched (comments + mentions), CTE-scoped aggregates, CID count cap, archive transaction, sanitize cache, detail+comments parallel |
| `npm run build` | ✅ pass | 2026-10-22 (201 backend tests, vue-tsc pass) |
| **@tiptap/extension-mention** | ⚠️ disabled | Package chưa có trên Nexus nội bộ → @mention tạm disabled, uncomment khi có |
| Batch job `AI-NEWS-IMPORT` | ✅ | Implemented + hardened (archive transaction, per-email resilience, SKIPPED alert fix, manual worker guard, IMAP CID cap) |
