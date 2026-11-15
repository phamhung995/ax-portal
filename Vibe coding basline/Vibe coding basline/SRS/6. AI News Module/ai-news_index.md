# AI-NEWS — Index

> **File index tổng quát** của chức năng **AI News** (theo Rule 3 — `tq.hop/RULE.md`).
> AI đọc file này TRƯỚC để định vị file liên quan khi phát triển / fix bug.
> Chi tiết nghiệp vụ / luồng / logic → xem [`ai-news_sumary.md`](./ai-news_sumary.md).

## Công dụng

Màn hình **AI News** — màn hình độc lập (route `#/ai-news`, menu GNB ngay dưới Home) xem nội dung email gốc của các bản tin AI. Layout **2-pane**: trái = list tin (timeline), phải = detail email (meta + body HTML) + **like bản tin** + **bình luận threaded** + **vote hữu ích trên comment**.

## Trạng thái

| Mục | Giá trị |
|---|---|
| SRS version | 1.20 (Updated) |
| Scope chính | Màn hình AI News + comment/like/vote (FR-01..FR-29) + @mention + grid sort + comment sort + collapsible form + bell deep-link + view tracking |
| Route | `/ai-news[?news={newsId}]` (createWebHistory — không dùng hash; tài liệu SRS ghi `#/ai-news` theo demo cũ) |
| Trạng thái code | **Đã implement Phase 2 + review rounds 1-7, SẴN SÀNG merge vào dev (verify 2026-10-30: 0 conflict, 242 tests pass)** — frontend AiNewsView.vue + AiNewsComments.vue + composable useAiNewsComments + RichTextEditor mention + backend package ainews (12 endpoints) + migrations V24/V42/V43/V48/V49/V53/V54 |

## Cấu trúc file (trong folder `ai-news/`)

| File | Nội dung |
|---|---|
| `AI_News_Screen_SRS.md` | SRS màn hình: 2-pane layout, 6 trạng thái, FR-01..FR-28 (comment/like/vote/mention/grid-sort/comment-sort/collapsible-form/bell-deep-link), NFR, API (detail + comment + like + mention-users + import), DB (3 bảng comment/like/vote + V43 notifications), edge cases, TC |
| `AI_News_Batch.md` | Batch job `AI-NEWS-IMPORT` (email → DB): nguồn dữ liệu, sanitize HTML, DDL bảng `ai_news` + `ai_news_source` + `ai_news_batch_log`, cleanup 90 ngày, quy tắc Flyway V55+ (v1.24 — verify merge-readiness 2026-10-30) |
| `ai-news_bug.md` | Bug log của chức năng AI News (mỗi bug 1 mục, append, không xóa mục đã fix) |

## Mapping chi tiết

- Nghiệp vụ, luồng, logic chi tiết → [`ai-news_sumary.md`](./ai-news_sumary.md)
- Bug log → [`ai-news_bug.md`](./ai-news_bug.md)
- Tài liệu liên quan (ngoài folder — chỉ tham chiếu, KHÔNG sửa khi làm AI News):
  - `../5. Home module/Home_Screen_SRS.md` §5.8 (card AI News trên Home — list 8 tin, click title → điều hướng sang đây)
  - `../5. Home module/Home_Screen_SRS.md` §7.4 (list API `GET /api/home/ai-news` — KHÔNG trả body)
  - `../2. User Management Module/Settings_SRS.md` SH-03-04 (date format)

## TODO / mở

- ~~**@tiptap/extension-mention** — package chưa có trên Nexus nội bộ → @mention tạm disabled.~~ → **Đã bật (2026-10-30):** package `@tiptap/extension-mention` 3.31.4 đã có trong `package.json`, `mention-enabled` bật ở cả 3 form comment/reply trong `AiNewsComments.vue`.
- Report vi phạm / moderation admin / insights — out of scope, có thể bổ sung version sau.
- Lazy-load ảnh trong body — không có trong v1.0.
- ~~Image fetch trong DB transaction (batch + MHT import) — refactor lớn, deferred.~~ → **Đã fix (round 2A, 2026-10-28):** insert ngắn → fetch ảnh ngoài transaction → update body transaction ngắn thứ 2 (cả 2 pipeline).
- ~~Rate limiting trên comment endpoint — chưa có.~~ → **Đã fix (round 4E, 2026-10-28):** throttle 10 comment/phút/user (in-memory `RateLimiter`) → `COMMENT_RATE_LIMITED` (429).
- Notifications table purge + bell poll 30s với hidden tab — **poll đã fix** (skip khi `document.hidden` + refresh 1 lần khi tab hiện lại); **purge/retention job vẫn chưa có** (cần migration + scheduler, user quyết định).
- `stripHtml` per-keystroke (touch shared RichTextEditor) — deferred (vẫn tồn tại: `hasCommentText`/`hasReplyText` computed trong `AiNewsComments.vue`; nút Send đã có `isOverLimit` từ editor nên chỉ còn phần empty-check).
- ~~`pg_trgm` GIN index cho search — khi data lớn.~~ → **Đã làm (V54, 2026-10-30):** `CREATE EXTENSION pg_trgm` + GIN index `ai_news.title`/`sender`; đã verify extension 1.6 + 2 index tồn tại trên DB live.
- ~~Structural refactor (AiNewsView ~1345 dòng, AiNewsCommentService split) — deferred.~~ → **Đã làm (batch 7, 2026-10-30):** tách `AiNewsCommentService`/`Impl` + composable `useAiNewsComments()`.
- ~~**Dead code:** `GET /api/v1/home/ai-news/sources` + `findDistinctActiveSources` — endpoint backend + frontend api wrapper tồn tại nhưng không có UI nào gọi (chưa xóa).~~ → **Đã xóa (round 4I, 2026-10-28).** (Filter `source` trong search API giữ lại — endpoint search hợp lệ.)

## Merge vào dev — trạng thái (verify 2026-10-30)

- **SẴN SÀNG merge:** dev đã merge vào `phase_2/tq.hop` (commit `abc2b25`, đã push) → merge vào dev clean; 242 backend tests pass + `vue-tsc` pass trên cây merge.
- **Quy tắc Flyway (verify trên DB live):** V50–V53 trên `flyway_schema_history` đã bị chiếm bởi MCP + system (out-of-band, không có file trong repo) → **mọi migration mới bắt đầu từ V55**. File `V53__ai_news_source_gdc_portal_login.sql` trong repo bị skip trên DB live — harmless (row `gdcv.cag.ax` đã ACTIVE).
- **Sau merge:** migration V55 drop cột `imap_host`/`imap_port` (DB live vẫn giữ 2 cột — re-add out-of-band; entity đã bỏ mapping).
- **Trước deploy:** set env `AI_NEWS_SECRET_GDC` trên server (password mailbox — đang chờ rotate, user hoãn) + verify server reach `http://kor5.samsung.net`.
