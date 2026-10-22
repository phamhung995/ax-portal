# AI-NEWS — Summary (chi tiết)

> **File summary chi tiết** của chức năng **AI News** (theo Rule 3 — `tq.hop/RULE.md`).
> Index tổng quát → [`ai-news_index.md`](./ai-news_index.md). Nguồn gốc: `AI_News_Screen_SRS.md` (v1.11) + `AI_News_Batch.md`.

## 1. Nghiệp vụ tổng quan

AI News là **màn hình độc lập** (không phải popup) xem nội dung email gốc của các bản tin AI:
- Menu GNB riêng trong sidebar, **ngay dưới Home**, icon riêng, active khi ở route `#/ai-news`.
- Route: `#/ai-news[?news={newsId}]`.
- Layout **2-pane**: trái = list tin dạng timeline, phải = detail email (title + meta + body HTML) + like + comment.
- **Read-only nội dung email** (body HTML không sửa được) nhưng **interactive**: like bản tin (silent, không toast) + bình luận threaded + vote hữu ích trên comment.
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

## 4. Interactive features (FR-15..FR-20)

| Feature | Quy tắc |
|---|---|
| **Like bản tin** (FR-15) | 1 nút like/tin (♥ + label + count), toggle 1 like/user/tin. **KHÔNG có toast** (silent — chỉ đổi icon fill + count). Bỏ ở v1.9, khôi phục v1.10 |
| **Comment** (FR-16) | Textarea max 1000 ký tự, counter đỏ ≥900. Mọi user đã đăng nhập comment được. Top-level sort DESC (mới nhất trước) |
| **Threaded reply** (FR-17) | Nút Reply → form inline. Depth tối đa 2; reply vào comment cấp 2 → **flatten về root** + toast info. Reply thụt lề, sort ASC |
| **Vote hữu ích** (FR-18) | Nút 👍 N trên comment + reply, toggle. **Không vote comment của mình** (`CANNOT_VOTE_OWN_COMMENT`) |
| **Xóa comment** (FR-19) | Owner hoặc admin. Soft delete (`deleted_at`). Top-level đã xóa còn reply con → tombstone "[deleted]" giữ reply con. Reply con bị xóa → ẩn |
| **Empty comment** (FR-20) | "Chưa có bình luận nào" |

## 5. API

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/home/ai-news/{newsId}` | Chi tiết 1 tin (kể cả `body` HTML, nullable). 404 `NEWS_NOT_FOUND` nếu không tồn tại HOẶC `archive_yn = 'Y'` |
| GET | `/api/home/ai-news/{newsId}/comments` | List comment (top-level DESC, reply ASC) |
| POST | `/api/home/ai-news/{newsId}/comments` | Tạo comment `{ content, parentId? }` — null = top-level, có = reply (depth ≤2, flatten) |
| DELETE | `/api/home/ai-news/{newsId}/comments/{commentId}` | Xóa (owner hoặc admin, soft delete) |
| POST | `/api/home/ai-news/{newsId}/comments/{commentId}/helpful` | Toggle vote (không vote comment mình) |
| POST | `/api/home/ai-news/{newsId}/like` | Toggle like — **client KHÔNG toast** |

Error codes: `NEWS_NOT_FOUND`, `NO_SESSION`, `COMMENT_EMPTY`, `COMMENT_TOO_LONG`, `COMMENT_PARENT_NOT_FOUND`, `COMMENT_PARENT_DELETED`, `COMMENT_NOT_FOUND`, `COMMENT_NOT_OWN`, `CANNOT_VOTE_OWN_COMMENT`.

> List API `GET /api/home/ai-news` (trên Home) **KHÔNG trả body** — body chỉ fetch qua detail endpoint khi chọn tin.

## 6. Database

- **`ai_news`** — bảng gốc, DDL đầy đủ trong `AI_News_Batch.md` §6.1 (9 cột: news_id PK, message_id unique, title, body HTML, sender, source, created_at, archive_yn, imported_at). Màn hình dùng: news_id, title, body, sender, source, created_at, archive_yn (filter).
- **`ai_news_comment`** — comment threaded: comment_id PK, news_id FK, user_id FK, parent_id (null = top-level), content VARCHAR(1000), helpful_count, reply_count, created_at, deleted_at (soft delete).
- **`ai_news_comment_vote`** — vote hữu ích: PK (comment_id, user_id).
- **`ai_news_like`** — like bản tin: PK (news_id, user_id).

## 7. Batch job (nguồn dữ liệu)

`AI-NEWS-IMPORT` (chi tiết trong `AI_News_Batch.md`): email → DB, sanitize HTML (bỏ script/iframe/event handler/`javascript:` URL), dedup bằng `Message-ID`, cleanup tin > 90 ngày (`archive_yn = 'Y'`), log vào `ai_news_batch_log`. Admin có thể manual run (`POST /api/admin/ai-news/run` — lock khi job đang chạy `NEWS-101`, fail `NEWS-102`).

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

- **Menu GNB "AI News" nằm ngay dưới Home** — nếu task AI News bắt buộc sửa GNB (app shell) → ghi ngoại lệ trong `tq.hop/context_sumary.md` (Rule 1).
- Card AI News trên **Home** (list 8 tin) thuộc folder `home/` — không sửa khi làm màn hình AI News.
- Like là **silent** — không bao giờ thêm toast cho like/unlike (quyết định v1.10).
- 404 không phân biệt "không tồn tại" vs "đã archive" (chống lộ thông tin).
- `newsId` là Number (BIGSERIAL) — demo mock dùng string `AN-xxx` là demo-only, không phải API contract.
- Nền body = slate `#64748b` + link vàng `#ffd54a` — đổi màu phải giữ contrast.

## 11. Trạng thái implement Phase 2 (2026-10-12)

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| `AiNewsView.vue` 2-pane | ✅ | list timeline + detail (body/like/comments/votes), 6 body states, AbortController khi đổi tin |
| Backend package `ainews` (7 endpoints) | ✅ | list/detail/comments/POST comment/DELETE comment/helpful/like — prefix `/api/v1/home/ai-news` |
| V25 migration (6 bảng) | ✅ | ai_news, ai_news_source, ai_news_batch_log, ai_news_comment, ai_news_comment_vote, ai_news_like |
| Error handler `AiNewsExceptionHandler` | ✅ | `@RestControllerAdvice` scoped package `ainews` — không đụng GlobalExceptionHandler |
| Error codes | ✅ | NEWS_NOT_FOUND + 8 code comment/vote trong `BusinessErrorCode` |
| GNB sidebar item + route `/ai-news` | ✅ | ngoại lệ Rule 1 đã approve (AppShell.vue + router) |
| i18n 3 ngôn ngữ | ✅ | `ai-news/i18n.ts` ~30 key |
| `npm run build` | ✅ pass | 2026-10-12 |
| **Batch job `AI-NEWS-IMPORT`** | ❌ **chưa implement** | SRS `AI_News_Batch.md` — email→DB, sanitize, dedup, cleanup 90 ngày, admin manual run. Chưa có data trong `ai_news` → màn hình hiện empty state. |
| Backend compile | ⚠️ chưa validate | Không có maven/docker local — cần CI/máy khác để compile. |
