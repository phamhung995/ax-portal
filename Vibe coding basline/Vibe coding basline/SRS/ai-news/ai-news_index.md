# AI-NEWS — Index

> **File index tổng quát** của chức năng **AI News** (theo Rule 3 — `tq.hop/RULE.md`).
> AI đọc file này TRƯỚC để định vị file liên quan khi phát triển / fix bug.
> Chi tiết nghiệp vụ / luồng / logic → xem [`ai-news_sumary.md`](./ai-news_sumary.md).

## Công dụng

Màn hình **AI News** — màn hình độc lập (route `#/ai-news`, menu GNB ngay dưới Home) xem nội dung email gốc của các bản tin AI. Layout **2-pane**: trái = list tin (timeline), phải = detail email (meta + body HTML) + **like bản tin** + **bình luận threaded** + **vote hữu ích trên comment**.

## Trạng thái

| Mục | Giá trị |
|---|---|
| SRS version | 1.11 (Draft) |
| Scope chính | Màn hình AI News + comment/like/vote (FR-01..FR-20) |
| Route | `/ai-news[?news={newsId}]` (createWebHistory — không dùng hash; tài liệu SRS ghi `#/ai-news` theo demo cũ) |
| Trạng thái code | **Đã implement Phase 2** (frontend `AiNewsView.vue` 2-pane + backend package `ainews` 7 endpoints + V25 migration 6 bảng) |

## Cấu trúc file (trong folder `ai-news/`)

| File | Nội dung |
|---|---|
| `AI_News_Screen_SRS.md` | SRS màn hình: 2-pane layout, 6 trạng thái (normal/loading/error/empty body/404/chưa chọn tin), FR-01..FR-20, NFR, API (detail + comment + like), DB (3 bảng comment/like/vote), edge cases, TC |
| `AI_News_Batch.md` | Batch job `AI-NEWS-IMPORT` (email → DB): nguồn dữ liệu, sanitize HTML, DDL bảng `ai_news` + `ai_news_source` + `ai_news_batch_log`, cleanup 90 ngày |
| `ai-news_bug.md` | Bug log của chức năng AI News (mỗi bug 1 mục, append, không xóa mục đã fix) |

## Mapping chi tiết

- Nghiệp vụ, luồng, logic chi tiết → [`ai-news_sumary.md`](./ai-news_sumary.md)
- Bug log → [`ai-news_bug.md`](./ai-news_bug.md)
- Tài liệu liên quan (ngoài folder — chỉ tham chiếu, KHÔNG sửa khi làm AI News):
  - `../home/Home_Screen_SRS.md` §5.8 (card AI News trên Home — list 8 tin, click title → điều hướng sang đây)
  - `../home/Home_Screen_SRS.md` §7.4 (list API `GET /api/home/ai-news` — KHÔNG trả body)
  - `../2. User Management module/Settings_SRS.md` SH-03-04 (date format)

## TODO / mở

- Report vi phạm / moderation admin / insights / notification cho comment — out of scope v1.10, có thể bổ sung version sau.
- Lazy-load ảnh trong body — không có trong v1.0.
