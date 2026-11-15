# AI SKILL MANAGEMENT

## SRS — AI News Screen

**Software Requirements Specification**

| Mục | Nội dung |
|-----|----------|
| **Document Version** | 1.20 |
| **Status** | Updated |
| **Module** | AI News (màn hình độc lập — menu GNB ngay dưới Home) |
| **Audience** | DEV / QA / DBA |
| **Tài liệu tham chiếu** | `AI_News_Batch.md` (nguồn dữ liệu, sanitize HTML, DDL `ai_news`), `Home_Screen_SRS.md` §5.8 (card AI News), `Home_Screen_SRS.md` (GNB sidebar) |

> **v1.1:** Nội dung `AI_News_Detail_Popup_Spec.md` (BA Spec) đã được gộp vào tài liệu này (wireframe 5 trạng thái, cấu trúc popup, quy tắc hiển thị, i18n 3 ngôn ngữ, responsive/layout) — file Spec đã xóa.
> **v1.7:** AI News Detail **không còn là popup** — là **màn hình độc lập** `/ai-news` với menu GNB ngay dưới Home (layout 2-pane: list tin + detail email). File đổi tên `AI_News_Detail_Popup_SRS.md` → `AI_News_Screen_SRS.md`.
> **v1.8:** Thêm **Bình luận (threaded)** + **Like bản tin** + **Vote hữu ích trên comment** — màn hình không còn "read-only tuyệt đối" mà là **read-only nội dung email + interactive comment/like** (FR-15..FR-20). Thêm 3 bảng DB (`ai_news_comment`, `ai_news_like`, `ai_news_comment_vote`) + 5 endpoint API.
> **v1.9:** **Bỏ chức năng Like bản tin** (FR-15, bảng `ai_news_like`, endpoint `POST /like`, i18n `aiNews.like*`). Giữ **Bình luận (threaded)** + **Vote hữu ích trên comment** (FR-16..FR-20). DB còn 2 bảng (`ai_news_comment`, `ai_news_comment_vote`), API còn 4 endpoint. List tin trái: **thẳng hàng dọc** (CSS `display: block` cho `.ai-news-list` — item block-level full-width, dot cùng left).
> **v1.10:** **Khôi phục Like bản tin** (FR-15) — **nhưng KHÔNG có toast** khi like/unlike (silent toggle: chỉ đổi icon + count, không thông báo). i18n chỉ cần 2 key `aiNews.like`/`aiNews.liked` (không còn `likeRegistered`/`likeRemoved` vì không toast). Khôi phục bảng `ai_news_like` + endpoint `POST /like`. Giữ fix list thẳng hàng dọc (v1.9).
> **v1.12:** **Đối chiếu codebase thật** — route history mode `/ai-news` (không còn hash `#/`), API prefix `/api/v1`, error code `SCREAMING_SNAKE` theo enum `BusinessErrorCode`, DDL khớp Flyway `V24__phase3_consolidated.sql` (BIGINT IDENTITY, `user_id BIGINT → "user"(id)`, `TIMESTAMPTZ`), detail API trả thêm `likeCount`/`liked`, i18n per-feature (không có vue-i18n).
> **v1.14:** **Thêm Import email .mht thủ công (admin)** — button "Import" chỉ hiện với `ADMIN_USER` trên màn AI News (FR-21): popup upload 1..20 file .mht (≤20MB/file, tổng ≤40MB), metadata auto-parse từ file (title=subject, sender=from, date=Date header, source="Manual Upload"), 1 file fail không chặn file khác, dedup theo Message-ID. **Sanitizer giữ inline `style`** (lọc giá trị nguy hiểm — thay đổi chung cho cả batch IMAP, NFR-02); ảnh CID >2MB nhúng data-URI.
> **v1.15:** **Thêm Comment sidebar** (FR-22) — nút icon (MessageCircle + ChevronLeft) ở header detail card (cùng hàng title, ngoài cùng phải) mở sidebar (PrismDrawer bên phải) chứa like + comment **đúng chức năng** như khối dưới body; sidebar và khối dưới body dùng chung 1 component + 1 state → thao tác đồng bộ 2 chỗ; tự đóng khi đổi tin. Không endpoint API mới.
> **v1.16:** **Comment rich text** (FR-23) — comment form + reply dùng **RichTextEditor** (tiptap, toolbar đầy đủ như Skill Market: B/I/U/S, highlight, H1-H3, align, list, quote, code, table, link, ảnh ≤5MB auto-compress) thay textarea plain text; comment render HTML đã sanitize (backend Jsoup khi lưu + frontend `sanitizeHtml` khi render); giới hạn **1000 ký tự plain text** (không đếm thẻ HTML/ảnh); DB `ai_news_comment.content` → `TEXT` (migration V42).
> **v1.17:** **Review rounds 1-5 (2026-10-22)** — Security: SSRF guard (per-hop IP validation, Redirect.NEVER, IPv6 ULA + CGNAT), SVG XSS prevention, comment raw input cap (20k), HtmlSanitizer `url()`/`@` blocklist, MHT import server-side size limits + exception masking, MhtEmailParser recursion depth cap. Performance: N+1 batched (comments + mention notifications), CTE-scoped list aggregates, CID image count cap (20/email), archive cleanup wrapped in transaction, per-email resilience, sanitize cache (computed), detail+comments parallel fetch. Features: @mention in comments (in-app + email notification, cap 10, skip actor — temporarily disabled pending Nexus package), grid sort (newest/most liked/most commented with likeCount+commentCount in list API), comment sort (oldest/newest/likes asc/likes desc/trending), collapsible comment form (1-line → expand), sticky form in drawer, reply form at bottom of thread, comment count badge in like row, like/comment count badges on list items, bell deep-link to comment (scrollToPendingComment). Bug fixes: deleteComment idempotent, reply-to-deleted-root rejected, replyCount decrement on delete, helpfulCount GREATEST floor, scrollToPendingComment checkVisibility, timestamp Date.getTime() comparison, auto-select follows grid sort, form collapse via justSent tick, Collectors.toMap null-safe, toggle endpoints typed DTO, SKIPPED rows filtered from alert, manual worker queueing guard, bell deep-link cross-item ?comment preserved, syncHash ?comment scoping, loadComments error toast, parentId null check consistency, trending excludes deleted roots. Migration V43 (notifications.news_id + indexes).
> **v1.21:** **Reply nested depth 3** (FR-17 đổi) — bỏ flatten: reply vào comment cấp 2 giờ nằm thụt lề cấp 3 (như Skill Market); chặn reply ở cấp 3 (nút Reply không hiện; API → 400 `COMMENT_DEPTH_EXCEEDED`). Không migration DB, không i18n key mới.

---

## 1. Giới thiệu

### 1.1 Mục đích

Tài liệu này quy định yêu cầu chức năng và phi chức năng của **AI News Screen** — màn hình xem nội dung email gốc của các bản tin AI. Màn hình có **menu riêng trong GNB sidebar (ngay dưới Home)**, route `/ai-news` (history mode), layout **2-pane**: trái là list tin, phải là detail email (meta + body HTML).

### 1.2 Phạm vi

**In Scope:**

- UI màn hình (list tin + detail email, render meta + body HTML, 4 trạng thái body, 1 trạng thái 404, 1 trạng thái chưa chọn tin).
- Menu GNB "AI News" (ngay dưới Home) + route `/ai-news`.
- API `GET /api/v1/home/ai-news/{newsId}` (chi tiết 1 tin, kể cả `body`).
- **Bình luận (threaded)** — comment + reply (depth ≤3, nested) về bản tin (FR-16, FR-17).
- **Like bản tin** — 1 nút like/tin, toggle, count (FR-15) — **KHÔNG có toast** khi like/unlike (silent toggle, v1.10).
- **Vote hữu ích trên comment** — toggle, không vote comment của mình (FR-18).
- **Xóa comment** — owner hoặc admin, soft delete, tombstone "[deleted]" giữ reply (FR-19).
- **Import email .mht thủ công (admin)** — button "Import" + popup upload .mht (FR-21, v1.14).
- **Comment sidebar** — nút icon ở header detail card mở sidebar (PrismDrawer) chứa like + comment, chia sẻ state với khối dưới body (FR-22, v1.15).
- **Comment rich text** — form + reply dùng RichTextEditor (toolbar đầy đủ như Skill Market: B/I/U/S, heading, list, table, link, ảnh), render HTML sanitize, giới hạn 1000 ký tự plain text (FR-23, v1.16).
- **@mention trong comment** — gõ `@` trong RichTextEditor → dropdown autocomplete tìm user → insert mention → backend parse `data-user-id` → gửi notification (in-app bell + email) cho user được @ (cap 10, skip actor, best-effort). **Đã bật (v1.20)** — `@tiptap/extension-mention` 3.31.4 đã có trong `package.json` (v1.17 ghi "tạm disabled" do package chưa có trên Nexus — đã có từ 2026-10-30, `mention-enabled` bật ở cả 3 form comment/reply).
- **Grid sort** — sort list tin theo newest / most liked / most commented (client-side, API trả `likeCount` + `commentCount` qua CTE aggregate).
- **Comment sort** — sort comment theo oldest / newest / likes asc / likes desc / trending (tổng like thread = top-level + replies).
- **Collapsible comment form** — form thu gọn 1 dòng, click → mở rộng. Sticky trong drawer (cố định khi scroll). Reply form ở dưới cùng thread.
- **Bell deep-link** — click notification @mention trong bell → mở news + scroll tới comment, flash highlight.
- API comment + like (FR-15..FR-19, §5.2) + API import .mht admin (FR-21, §5.7) + API mention-users (§5.8).

**Out of Scope:**

- Batch import email → DB — `AI_News_Batch.md`.
- Card AI News (list 8 tin, rotating tone, empty state) — `Home_Screen_SRS.md` §5.8.
- Sanitize HTML khi import — `AI_News_Batch.md` §4.3 (client tin tưởng body đã sanitize; NFR-02 quy định lớp phòng vệ phía client).
- Phân quyền — mọi user đã đăng nhập đều xem được mọi tin (không có Part/Group filter).
- **Toast khi like/unlike bản tin** — **không có** (v1.10 — silent toggle, chỉ đổi icon + count).
- **Report vi phạm / moderation (admin) / insights / notification cho comment** — không có trong v1.10 (giữ scope gọn; có thể bổ sung ở version sau).

### 1.3 Thuật ngữ

| Thuật ngữ | Định nghĩa |
|---|---|
| Tin (news item) | 1 row trong bảng `ai_news` — 1 email đã import |
| Body | Nội dung email dạng HTML (đã sanitize khi import) |
| Sender | `from` của email (tên + địa chỉ) |
| Archived | `archive_yn = 'Y'` — tin cũ hơn 90 ngày, không còn hiện trong UI |

---

## 2. Mô tả tổng thể

### 2.1 Trigger & lifecycle

```
[Menu GNB "AI News" (sidebar, ngay dưới Home)]  hoặc  [Home → tab Community → card AI News → click title tin]
  │  → route /ai-news[?news={newsId}]
  ▼
[Màn hình AI News] ── 2-pane: list tin (trái) + detail email (phải)
  │  - mở không có ?news → tự chọn tin đầu (mới nhất)
  │  - có ?news={newsId} → chọn tin tương ứng
  ▼
[Fetch] GET /api/v1/home/ai-news/{newsId}  (lazy — chỉ fetch khi chọn tin, async, timeout 30s)
  │
  ├── 200 + body ≠ null → [Render body HTML]
  ├── 200 + body = null → [Empty state]
  ├── 404               → [Not found state]
  └── 5xx / timeout     → [Error state]
  │
  ▼
[Chọn tin khác trong list] ── thay nội dung detail, hủy fetch cũ (AbortController)
```

### 2.2 Actors

| Actor | Quyền |
|---|---|
| Mọi user đã đăng nhập (NORMAL_USER, ADMIN_USER) | Mở màn hình + xem nội dung + **like bản tin + bình luận + vote comment** — không phân quyền thêm |
| Admin (ADMIN_USER) | Như mọi user + **xóa comment của user khác** (FR-19) + **import email .mht thủ công** (FR-21) |
| User chưa đăng nhập | Không truy cập được (session guard — GNB không render) |

### 2.3 Nguyên tắc

- **Read-only nội dung email** — body email (HTML) không thể chỉnh sửa; không đổi trạng thái tin (không có "đã đọc"). **Nhưng interactive like + comment** (FR-15..FR-20) — user có thể like bản tin (silent, không toast — v1.10), bình luận, vote hữu ích trên comment.
- **Lazy fetch** — body chỉ fetch khi chọn tin (list API `GET /api/v1/home/ai-news` KHÔNG trả `body` — `Home_Screen_SRS.md` §7.4).
- **Màn hình độc lập** — có menu GNB riêng, route `/ai-news`; không phải popup/dialog.
- **Không có PUBLISHED gate** (khác skill) — mọi user đã đăng nhập đều comment được mọi tin (nhất quán "mọi user thấy toàn bộ tin").

### 2.4 Wireframe

#### 2.4.1. Trạng thái Normal (có body)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  AI News (h1)  ·  Tin AI từ email nội bộ (sub)                               │
├───────────────────────────────┬──────────────────────────────────────────────┤
│  📰 AI News (card header)      │  📜 [Notice] Release of v1.19 for AI Pro ... │  ← title = subject email (1 dòng, ellipsis)
│  ┌─ list tin (timeline) ─────┐ │  📧 Technical Support Part <...>  📅 29/09  │  ← meta row
│  │ ● [Notice] Release v1.19  │ │  ┌────────────────────────────────────────┐ │
│  │   GDCV Tech · 29/09/2026  │ │  │ To all GDCV members,                  │ │
│  │ ● [Guide] AI coding tips  │ │  │ Please check the new updates ...      │ │  ← body HTML render
│  │   GDCV DevOps · 28/09/2026│ │  │ ...                                   │ │     (đã sanitize phía
│  │ ● [Notice] ...            │ │  │ [ảnh trong email — max-width 100%]    │ │      server), scroll
│  │   ...                     │ │  │ ...                                   │ │     max-height
│  └───────────────────────────┘ │  └────────────────────────────────────────┘ │
│                                │  [♥ Like (3)]                               │  ← like bản tin (silent — không toast)
│                                │  ────────────────────────────────────────── │
│                                │  [Comment form: textarea + [Gửi]]           │  ← form bình luận
│                                │  Bình luận (3)                              │
│                                │  ● User A  29/09  [👍 2] [↪] [✕]           │  ← comment top-level
│                                │    Nội dung comment...                      │
│                                │    ○ User B  29/09  [👍 0] [✕]             │  ← reply (indent)
│                                │      Nội dung reply...                      │
└───────────────────────────────┴──────────────────────────────────────────────┘
```

#### 2.4.2. Trạng thái Loading (đang fetch body)

```
┌───────────────────────────────┬──────────────────────────────────────────────┐
│  [list tin]                   │  📜 [Subject email]                          │
│                               │  📧 Sender <...>              📅 29/09/2026  │
│                               │  Đang tải nội dung email…                     │  ← text muted
└───────────────────────────────┴──────────────────────────────────────────────┘
```

#### 2.4.3. Trạng thái Error (fetch fail / 5xx / timeout)

```
┌───────────────────────────────┬──────────────────────────────────────────────┐
│  [list tin]                   │  📜 [Subject email]                          │
│                               │  📧 Sender <...>              📅 29/09/2026  │
│                               │  ⚠ Không tải được nội dung email.            │  ← PrismAlert tone=warning
└───────────────────────────────┴──────────────────────────────────────────────┘
```

#### 2.4.4. Trạng thái Empty body (email không có nội dung)

```
┌───────────────────────────────┬──────────────────────────────────────────────┐
│  [list tin]                   │  📜 [Subject email]                          │
│                               │  📧 Sender <...>              📅 29/09/2026  │
│                               │  ℹ Email không có nội dung.                  │  ← PrismAlert tone=info
└───────────────────────────────┴──────────────────────────────────────────────┘
```

#### 2.4.5. Trạng thái 404 (tin không tồn tại / đã archive)

```
┌───────────────────────────────┬──────────────────────────────────────────────┐
│  [list tin]                   │  📜 [Subject email]                          │
│                               │  ⚠ Tin không tồn tại hoặc đã được lưu trữ.   │  ← PrismAlert tone=warning
└───────────────────────────────┴──────────────────────────────────────────────┘
```

#### 2.4.6. Trạng thái chưa chọn tin (list rỗng / không có tin)

```
┌───────────────────────────────┬──────────────────────────────────────────────┐
│  [empty state list]           │  ℹ Chọn một tin                              │
│                               │    Chọn tin trong danh sách để xem nội dung  │  ← empty state
└───────────────────────────────┴──────────────────────────────────────────────┘
```

### 2.5 Cấu trúc màn hình

| Vùng | Nội dung |
|------|----------|
| **Page header** | Title "AI News" (h1) + subtitle mô tả ngắn (i18n `home.aiNewsDesc`) |
| **List pane (trái)** | List tin dạng timeline (dot rotating tone + title dạng link + `sender · date`). Item đang chọn được highlight (title accent + dot glow). Click item → chọn tin + cập nhật query string `?news={newsId}`. **Thẳng hàng dọc** (v1.9): CSS `.ainews-list-card .ai-news-list { display: block }` — mỗi item block-level full-width, dot + title cùng left (tránh item giữ max-content width do title `nowrap` → lệch ngang). **Title ellipsis** (v1.11): title quá dài → "..." + hover hiện đủ (tooltip native `title`) — cần `.ai-news-body { min-width: 0 }` (grid item `1fr` mặc định `min-width: auto` → phình theo title `nowrap` → ellipsis không hoạt động) |
| **Detail pane (phải)** | **Header**: title = `title` của tin (subject email, **1 dòng, ellipsis "..." khi quá dài** + hover hiện đủ title qua tooltip native `title`, 16px) + **nút mở comment sidebar** (icon MessageCircle + ChevronLeft, ngoài cùng phải, disabled khi chưa chọn tin — FR-22). **Meta row**: 📧 `sender` (tên + địa chỉ email, màu accent, trái) · 📅 `createdAt` (font mono, muted, phải; định dạng theo date format Settings user — `../2. User Management Module/Settings_SRS.md` SH-03-04, mặc định DD/MM/YYYY). **Body**: `body` HTML render trong container có border + scroll dọc (NFR-05); 4 trạng thái: loading / error / empty / normal. **Like row**: nút like bản tin + count (FR-15, **không toast** — v1.10). **Comment section**: form bình luận **rich text** (FR-23, v1.16) + list comment threaded (FR-16..FR-19) — cùng component với comment sidebar (FR-22) |

> Màn hình dùng **layout 2-pane**: list `minmax(320px, 420px)` + detail `minmax(0, 1fr)`, gap 14px; mobile (< 999px) stack 1 cột (list trên, detail dưới). Không có tab. **Read-only nội dung email** (body HTML không sửa được) nhưng **interactive like + comment** (FR-15..FR-20) — có like bản tin (silent, không toast) + form bình luận + vote comment.

---

## 3. Yêu cầu chức năng

| ID | Yêu cầu |
|---|---|
| FR-01 | Menu GNB "AI News" nằm **ngay dưới Home** trong sidebar (mọi user đã đăng nhập), icon riêng (lucide `Newspaper`), active khi ở route `/ai-news` |
| FR-02 | Click title tin AI News trong card Home (tab Community) → điều hướng `/ai-news?news={newsId}` (mở màn hình, chọn tin đó) — không mở popup |
| FR-03 | Mở màn hình không có `?news` → tự chọn tin đầu (mới nhất). Có `?news={newsId}` → chọn tin tương ứng (tin không tồn tại trong list → chọn tin đầu) |
| FR-04 | Khi chọn tin, fetch `GET /api/v1/home/ai-news/{newsId}` (async, timeout 30s — axios global `timeout: 30000` trong `lib/api.ts`). Title + meta (sender, ngày) render ngay từ dữ liệu list — không chờ fetch |
| FR-05 | Chọn tin khác trong list → thay nội dung detail, hủy fetch tin cũ (AbortController). Click 2 tin liên tiếp nhanh → chỉ fetch tin mới nhất. Query string cập nhật `?news={newsId}` |
| FR-06 | Meta row hiển thị: `sender` (tên + email, icon 📧, trái, màu accent) và `createdAt` (icon 📅, phải, font mono, định dạng theo date format Settings của user — mặc định DD/MM/YYYY) |
| FR-07 | Render `body` HTML trong container có border, `overflow-y: auto` (NFR-05). CSS bắt buộc: `img { max-width: 100%; height: auto; }`, `table { max-width: 100%; }`, link `target="_blank" rel="noopener"` + màu `#ffd54a` (vàng — contrast trên nền slate, §3.1 rule 4). **Nền container = slate `#64748b` (xám xanh trung tính)** — email HTML có `background-color` inline (thường trắng) trên body/table/span → CSS ép `background-color: transparent !important` cho **mọi element** trong body để nền container áp dụng toàn bộ (đọc được cả text đen lẫn trắng). **KHÔNG** ép `background-image` — banner hero của email là ảnh nền (text trắng trên ảnh), xóa ảnh sẽ làm text trắng biến mất |
| FR-08 | Trạng thái **Loading**: hiển thị "Đang tải nội dung email…" (text muted) trong khi fetch chưa xong |
| FR-09 | Trạng thái **Error** (fetch fail / 5xx / timeout): hiển thị alert warning "Không tải được nội dung email." — màn hình không crash |
| FR-10 | Trạng thái **Empty body** (`body = NULL`): hiển thị alert info "Email không có nội dung." |
| FR-11 | Trạng thái **404** (tin không tồn tại hoặc `archive_yn = 'Y'`): hiển thị alert warning "Tin không tồn tại hoặc đã được lưu trữ." (không phân biệt lý do cho client — tránh lộ thông tin) |
| FR-12 | **Body email read-only** — không sửa được nội dung email; không share/copy/print **email**. Nhưng có **like bản tin** (FR-15, silent — không toast) + **comment** (FR-16..FR-19) |
| FR-13 | Item list đang được chọn được highlight (title màu accent + dot glow) để user biết tin nào đang hiển thị ở detail pane |
| FR-14 | Trạng thái **chưa chọn tin** (list rỗng): detail pane hiển thị empty state "Chọn một tin" |
| FR-15 | **Like bản tin** — 1 nút like/tin (icon ♥ + label + count), toggle (1 like/user/tin). **KHÔNG có toast** khi like/unlike (silent — chỉ đổi icon fill + count; v1.10). Liked → icon filled + màu accent. (Bỏ ở v1.9, khôi phục ở v1.10) |
| FR-16 | **Bình luận** — form (max 1000 ký tự plain text) + list comment trong detail pane. Mọi user đã đăng nhập đều comment được (không PUBLISHED gate). Comment mới hiện ngay (top-level sort DESC — mới nhất trước). **v1.16**: form là rich text (FR-23) |
| FR-17 | **Threaded reply** — nút "Reply" trên mỗi comment (cấp 1 + cấp 2) → form inline. Depth tối đa 3 (nested, v1.21); reply vào comment cấp 3 bị chặn (nút Reply không hiện ở cấp 3; API → 400 `COMMENT_DEPTH_EXCEEDED`). Reply hiển thị thụt lề, sort ASC |
| FR-18 | **Vote hữu ích trên comment** — nút "👍 N" trên mỗi comment + reply. Toggle. **Không vote comment của mình** (`CANNOT_VOTE_OWN_COMMENT`). Voted → accent |
| FR-19 | **Xóa comment** — owner hoặc admin. Soft delete (`deletedAt`). Top-level đã xóa mà còn reply con chưa xóa → render tombstone "[deleted]" (giữ reply con). Reply con bị xóa → ẩn bình thường |
| FR-20 | **Empty state comment** — khi tin chưa có comment: hiển thị "Chưa có bình luận nào" |
| FR-21 | **Import email .mht thủ công (admin, v1.14)** — button "Import" (icon Upload) chỉ hiện khi `auth.isAdmin`, nằm ở page header (cạnh subtitle, phải). Click → popup (PrismDialog): chọn **1..20 file `.mht`** (≤20MB/file, tổng ≤40MB — client-side guard khớp `max-request-size`) → bấm "Import" → `POST /api/v1/admin/ai-news/import`. **Metadata auto-parse từ file** (không có bước sửa): `title` = subject (qua `cleanTitle` — strip prefix `[...]`, truncate 200), `sender` = from, `createdAt` = Date header (fallback now), `source` = `"Manual Upload"`. **1 file fail không chặn file khác** — kết quả per file: `INSERTED(newsId)` / `SKIPPED_DUPLICATE` / `FAILED(reason)`; popup hiện list kết quả + toast tổng. Body sanitize như pipeline batch (NFR-02); ảnh CID ≤2MB lưu file như batch, **>2MB nhúng data-URI** vào body. **Không đụng** `ai_news_source.last_success_run_at`, **không ghi** `ai_news_batch_log`. Đóng popup sau import → list tin tự refresh (tin mới hiện đầu list) |
| FR-22 | **Comment sidebar (v1.15)** — nút icon (MessageCircle + ChevronLeft) ở **header detail card, cùng hàng title, ngoài cùng bên phải** (disabled khi chưa chọn tin; aria-label `aiNews.openComments`). Click → mở **sidebar (PrismDrawer, trượt từ phải)** chứa **hàng Like (FR-15) + comment form + list comment threaded (FR-16..FR-20)** — **đúng chức năng** như khối like + comment dưới body email. Sidebar **chia sẻ cùng state** với khối dưới body (component `AiNewsComments` dùng chung) → thao tác từ sidebar (gửi comment / reply / vote / like / xóa) cập nhật **đồng bộ cả 2 chỗ**, dùng lại endpoint API hiện có (không endpoint mới). **Tự đóng** khi user chọn tin khác (reset state comment). Đóng bằng X / Escape / click overlay. i18n thêm key `aiNews.openComments` (vi/en/ko) |
| FR-23 | **Comment rich text (v1.16)** — comment form (FR-16) + reply form (FR-17) dùng **RichTextEditor** (tiptap — component dùng chung với Skill Market) thay textarea plain text. **Toolbar đầy đủ**: B, I, U, S, highlight, H1-H3, align, bullet/numbered/task list, quote, code block, table, link, **chèn ảnh** (jpg/jpeg/png/webp ≤5MB, ảnh >500KB auto-compress, lưu data-URI base64). **Render**: comment hiển thị HTML đã sanitize — backend sanitize (Jsoup whitelist, `CommentHtmlSanitizer`) **khi lưu** + frontend `sanitizeHtml()` (whitelist khớp) **khi render** `v-html`; link mở tab mới (`target=_blank rel=noopener`); ảnh `max-width:100%`. **Giới hạn 1000 ký tự đếm PLAIN TEXT** (không đếm thẻ HTML/ảnh) — frontend counter trong editor + backend validate (`COMMENT_EMPTY`/`COMMENT_TOO_LONG` trên plain text). Nút Gửi disabled khi plain text rỗng (chỉ có ảnh → không gửi được). DB: `ai_news_comment.content` → `TEXT` (migration V42; comment cũ plain text render đúng qua cùng pipeline, không cần data migration) |
| FR-24 | **@mention trong comment (v1.17)** — gõ `@` trong RichTextEditor (comment + reply) → dropdown autocomplete tìm user (gọi `GET /api/v1/home/ai-news/mention-users?q=...`, max 10, match name/email, `enabled=true`). Chọn user → insert mention node (`<span data-type="mention" data-user-id="..." data-user-name="...">@UserName</span>`). Khi gửi comment, backend parse `data-user-id` → gửi notification (in-app bell + email) cho mỗi user được @ (cap 10 distinct, skip actor, best-effort — lỗi email không phá comment). **Đã bật (v1.20)** — `@tiptap/extension-mention` 3.31.4 đã có trong `package.json` (v1.17 ghi "tạm disabled" do package chưa có trên Nexus) |
| FR-25 | **Grid sort (v1.17)** — sort list tin theo 3 chế độ: **Newest** (mặc định, `createdAt` DESC), **Most liked** (`likeCount` DESC), **Most commented** (`commentCount` DESC). Sort client-side trên data đã fetch (API trả `likeCount` + `commentCount` qua CTE aggregate — `LEFT JOIN ai_news_like` + `LEFT JOIN ai_news_comment WHERE deleted_at IS NULL`, scoped trong CTE 50 hàng). Dropdown trong header card list. Badge ❤️ (likeCount) + 💬 (commentCount) trên mỗi item (chỉ hiện khi > 0) |
| FR-26 | **Comment sort (v1.17)** — sort comment theo 5 chế độ: **Oldest first** (mặc định), **Newest first**, **Most liked** (`helpfulCount` DESC), **Least liked** (`helpfulCount` ASC), **🔥 Trending** (tổng like thread = top-level `helpfulCount` + tổng `helpfulCount` tất cả reply; deleted roots không trend). Dropdown trong like row (cùng hàng Like + comment count). Replies sort theo `helpfulCount` trong trending mode, theo date trong các mode khác |
| FR-27 | **Collapsible comment form (v1.17)** — comment form thu gọn thành 1 dòng placeholder ("Thảo luận về bản tin này…"), click → mở rộng editor (4 dòng main pane, 3 dòng drawer). Collapse sau khi gửi thành công (via `justSent` tick — không collapse khi user tự xóa draft). **Sticky trong drawer**: form cố định `position: sticky; top: 0` khi scroll comment (dialog flex column, body là scroll container duy nhất). Reply form ở **dưới cùng** thread (sau replies list) |
| FR-28 | **Bell deep-link to comment (v1.17)** — click notification @mention trong GNB bell → điều hướng `/ai-news?news={newsId}&comment={commentId}` → mở news + scroll tới comment, flash highlight (`.is-flash` animation 2s). `scrollToPendingComment` dùng `checkVisibility()` (không dùng `offsetParent` — null trong `<dialog>`). Cross-item deep-link giữ `?comment` (chỉ strip khi `route.query.news !== target`). `syncHash` chỉ giữ `?comment` khi thuộc item hiện tại |
| FR-29 | **View tracking — đếm người đã xem (v1.18)** — khi user đọc detail tin **đủ 30s** (tab visible — timer pause khi tab ẩn, resume giữ phần thời gian còn lại), frontend gọi `POST /api/v1/home/ai-news/{newsId}/view` (idempotent per user — unique `(news_id, user_id)` trong `ai_news_view_log`; gọi lại là no-op). Backend upsert row + **recompute** cột denormalized `ai_news.view_count` từ log (không `+1` — idempotent, self-healing khi race 2 tab). Hiển thị: icon 👁 + số ở **header detail** (chỉ hiện khi > 0) + badge 👁 trên **card list** (cùng hàng ❤️/💬, chỉ hiện khi > 0). Lỗi record view → silent (không phá UI) |

### 3.1 Quy tắc hiển thị

| # | Quy tắc |
|---|---------|
| 1 | **Body email read-only** — không sửa được nội dung email (không share/copy/print email). Nhưng **interactive like + comment** (FR-15..FR-20) — user like bản tin (silent, không toast), bình luận, vote comment |
| 2 | `body` HTML render **nguyên dạng** — đã sanitize phía server khi import (`AI_News_Batch.md` §4.3: bỏ script/iframe/event handler); client KHÔNG sanitize lại, KHÔNG strip markup |
| 3 | Ảnh trong email: `max-width: 100%`, `height: auto` — không vỡ layout |
| 4 | Link trong email: **màu vàng `#ffd54a`** (contrast ~7:1 trên nền slate `#64748b` — accent cyan `#67e8f9` chỉ đạt ~1.9:1, không đọc được), mở tab mới + `rel="noopener"` |
| 5 | Table trong email: `max-width: 100%` (scroll ngang nếu quá rộng) |
| 6 | `sender` hiển thị đầy đủ "Tên <email>" — ellipsis nếu vượt meta row |
| 7 | `createdAt` hiển thị theo date format trong Settings của user (mặc định DD/MM/YYYY) |
| 8 | Title dài: list dùng ellipsis (1 dòng); detail pane hiển thị full (wrap) |
| 9 | Body rất lớn (>1MB, email kèm nhiều ảnh): render trong container scroll — không làm detail pane cao vô hạn |

### 3.2 i18n (3 ngôn ngữ)

> **Thực tế codebase:** app KHÔNG dùng vue-i18n — mỗi feature có file `i18n.ts` riêng (`frontend/src/features/ai-news/i18n.ts`) với bảng `MESSAGES: Record<'vi'|'en'|'ko', ...>`, hàm `t(key, vars)` fallback `en`; ngôn ngữ lấy từ user Settings (API `settingsApi.me()`, cache localStorage). Key dưới đây là key trong bảng đó.

| Key | VI | EN | KO |
|-----|----|----|----|
| `app.aiNews` (menu GNB) | AI News | AI News | AI 뉴스 |
| `aiNews.loading` | Đang tải nội dung email… | Loading email content… | 이메일 내용 불러오는 중… |
| `aiNews.loadFail` | Không tải được nội dung email. | Failed to load email content. | 이메일 내용을 불러오지 못했습니다. |
| `aiNews.noBody` | Email không có nội dung. | This email has no content. | 이메일에 내용이 없습니다. |
| `aiNews.notFound` | Tin không tồn tại hoặc đã được lưu trữ. | News not found or archived. | 뉴스가 없거나 보관되었습니다. |
| `aiNews.selectNews` | Chọn một tin | Select a news item | 뉴스 선택 |
| `aiNews.selectNewsDesc` | Chọn tin trong danh sách để xem nội dung email. | Select an item from the list to view the email content. | 목록에서 뉴스를 선택하여 이메일 내용을 확인하세요. |
| `aiNews.comments` | Bình luận | Comments | 댓글 |
| `aiNews.commentCount` | Bình luận ({n}) | Comments ({n}) | 댓글 ({n}) |
| `aiNews.openComments` (v1.15, aria-label nút sidebar) | Mở bình luận | Open comments | 댓글 열기 |
| `aiNews.writeComment` | Viết bình luận | Write a comment | 댓글 작성 |
| `aiNews.writeCommentPh` | Thảo luận về bản tin này… | Discuss this news… | 이 뉴스에 대해 토론하세요… |
| `aiNews.send` | Gửi | Send | 전송 |
| `aiNews.reply` | Trả lời | Reply | 답장 |
| `aiNews.writeReplyPh` | Viết trả lời… | Write a reply… | 답장 작성… |
| `aiNews.delComment` | Xóa | Delete | 삭제 |
| `aiNews.delCommentTitle` | Xóa bình luận | Delete comment | 댓글 삭제 |
| `aiNews.delCommentDesc` | Bạn có chắc muốn xóa bình luận này? | Are you sure you want to delete this comment? | 이 댓글을 정말 삭제할까요? |
| `aiNews.helpful` | Hữu ích | Helpful | 유용해요 |
| `aiNews.cannotVoteOwn` | Không thể vote bình luận của chính mình | Cannot vote on your own comment | 자신의 댓글에는 투표할 수 없습니다 |
| `aiNews.noComments` | Chưa có bình luận nào | No comments yet | 아직 댓글이 없습니다 |
| `aiNews.noCommentsDesc` | Hãy là người đầu tiên thảo luận về bản tin này. | Be the first to discuss this news. | 이 뉴스에 대해 첫 번째 토론을 시작하세요. |
| `aiNews.commentSent` | Đã gửi bình luận | Comment sent | 댓글 전송됨 |
| `aiNews.replySent` | Đã gửi trả lời | Reply sent | 답장 전송됨 |
| `aiNews.replyFlattened` | Trả lời đã gộp vào chủ đề chính | Reply flattened to main thread | 답장이 메인 스레드에 병합됨 |
| `aiNews.commentDeleted` | Đã xóa bình luận | Comment deleted | 댓글 삭제됨 |
| `aiNews.voteRegistered` | Đã đánh dấu hữu ích | Marked as helpful | 유용함 표시됨 |
| `aiNews.voteRemoved` | Đã bỏ đánh dấu hữu ích | Removed helpful mark | 유용함 표시 취소 |
| `aiNews.deletedUser` | User đã xóa | Deleted user | 삭제된 사용자 |
| `aiNews.deletedComment` | [đã xóa] | [deleted] | [삭제됨] |
| `aiNews.like` | Thích | Like | 좋아요 |
| `aiNews.liked` | Đã thích | Liked | 좋아요 |
| `aiNews.import` | Import | Import | 가져오기 |
| `aiNews.importTitle` | Import email .mht | Import .mht emails | .mht 이메일 가져오기 |
| `aiNews.importFileLabel` | File .mht | .mht files | .mht 파일 |
| `aiNews.importHint` | Chỉ nhận file .mht (email lưu từ Outlook/Knox). Tối đa 20 file, 20MB/file, tổng 40MB. | .mht files only (emails saved from Outlook/Knox). Max 20 files, 20MB each, 40MB total. | .mht 파일만 허용 (Outlook/Knox에서 저장한 이메일). 최대 20개, 각 20MB, 총 40MB. |
| `aiNews.importTotalLimit` | Tổng dung lượng vượt quá 40MB — bỏ bớt file. | Total size exceeds 40MB — remove some files. | 총 용량이 40MB를 초과합니다 — 일부 파일을 제거하세요. |
| `aiNews.importSubmit` | Import | Import | 가져오기 |
| `aiNews.importing` | Đang import… | Importing… | 가져오는 중… |
| `aiNews.importClose` | Đóng | Close | 닫기 |
| `aiNews.importInserted` | Đã import | Imported | 가져옴 |
| `aiNews.importSkippedDup` | Trùng (đã tồn tại) | Duplicate (already exists) | 중복 (이미 존재) |
| `aiNews.importFailed` | Lỗi | Failed | 실패 |
| `aiNews.importDone` | Đã import {n}/{total} tin | Imported {n}/{total} item(s) | {n}/{total}건 가져옴 |
| `aiNews.mentionSearching` (v1.17) | Đang tìm… | Searching… | 검색 중… |
| `aiNews.mentionNoUsers` (v1.17) | Không tìm thấy user | No users found | 사용자를 찾을 수 없음 |
| `aiNews.sortMostLiked` (v1.17) | Nhiều like nhất | Most liked | 좋아요 많은순 |
| `aiNews.sortLeastLiked` (v1.17) | Ít like nhất | Least liked | 좋아요 적은순 |
| `aiNews.sortTrending` (v1.17) | Thịnh hành | Trending | 트렌딩 |
| `aiNews.gridSortNewest` (v1.17) | Mới nhất | Newest | 최신순 |
| `aiNews.gridSortLikes` (v1.17) | Nhiều like nhất | Most liked | 좋아요 많은순 |
| `aiNews.gridSortComments` (v1.17) | Nhiều comment nhất | Most commented | 댓글 많은순 |

> **Lưu ý:** menu GNB hiện hardcode label "AI News" trong `AppShell.vue` (không qua i18n) — key `app.aiNews` chỉ dùng khi i18n hóa menu.

> `aiNews.notFound` là key riêng cho trạng thái 404 (wireframe §2.4.5). `aiNews.selectNews`/`selectNewsDesc` cho trạng thái chưa chọn tin (§2.4.6). Key `aiNews.comment*`/`reply*`/`vote*`/`delComment*` cho comment + vote (FR-16..FR-20). Key `aiNews.like`/`liked` cho like bản tin (FR-15) — **chỉ 2 key** (không có `likeRegistered`/`likeRemoved` vì like là silent, không toast — v1.10).

---

## 4. Yêu cầu phi chức năng

| ID | Yêu cầu |
|---|---|
| NFR-01 | **Hiệu năng:** body 1MB+ (email kèm ảnh) render xong < 1s trên máy trung bình; fetch async — không block UI; không fetch body khi chưa chọn tin |
| NFR-02 | **Bảo mật (XSS):** `body` đã sanitize phía server khi import (`AI_News_Batch.md` §4.3 — bỏ `<script>`, `<iframe>`, event handler `on*`, `javascript:` URL). Client render qua `v-html` **chỉ** với body từ API này; CSP `script-src` không cho inline script (lớp phòng vệ thứ 2). **v1.14:** sanitizer **giữ attribute `style` inline** (email Knox/Outlook dùng ~100 style inline cho layout) nhưng **lọc giá trị nguy hiểm** trong style (`expression()`, `javascript:`, `vbscript:`, `behavior:`, `-moz-binding`, `url(javascript:...)`, `@import`); `<style>` block vẫn bị bỏ. Áp dụng chung cho cả batch IMAP |
| NFR-03 | **Accessibility:** item list có `aria-current` khi đang chọn; meta row đọc được bằng screen reader (không dùng icon-only cho sender); body container có `aria-busy` khi loading |
| NFR-04 | **i18n:** mọi text UI có 3 ngôn ngữ (vi/en/ko) — xem bảng key §3.2 |
| NFR-05 | **Layout 2-pane:** list `minmax(320px, 420px)` + detail `minmax(0, 1fr)`, gap 14px, `align-items: start`. Mobile (< 999px): 1 cột (list trên, detail dưới). Chi tiết bảng giá trị: |

**Bảng giá trị Responsive & Layout:**

| Mục | Giá trị |
|---|---|
| Grid 2-pane | `display: grid; grid-template-columns: minmax(320px, 420px) minmax(0, 1fr); gap: 14px; align-items: start` |
| List pane | Reuse list timeline của card AI News Home (`.ai-news-list`); item đang chọn: title accent + dot glow |
| Detail title | 16px, line-height 1.35, **1 dòng + ellipsis "..." khi quá dài** (hover → tooltip native `title` hiện đủ) |
| Meta row | 13px, margin-bottom 10px |
| Body | font 14px, line-height 1.65, padding 20px 22px, **nền `#64748b` (slate — xám xanh trung tính)** + ép transparent `background-color` inline của email, giữ `background-image` (FR-07), `overflow-y: auto` + `max-height: calc(100svh − 320px)` (desktop) |
| Mobile (< 999px) | 1 cột; body `max-height: none` (scroll theo trang) |

---

## 5. API Specification

### 5.1 Endpoint

`GET /api/v1/home/ai-news/{newsId}`

> Toàn bộ endpoint của module nằm dưới prefix `/api/v1` (frontend axios `baseURL = '/api/v1'`; dev proxy `/api` → backend Spring Boot, context-path `/skm`).

### 5.2 Permission

Session hợp lệ (bất kỳ user đã đăng nhập — JWT Bearer). Không có phân quyền thêm.

### 5.3 Success Response (200)

```json
{
  "success": true,
  "data": {
    "newsId": 1024,
    "title": "GDC Tech Support: new AI coding guide",
    "sender": "GDC Tech Support <gdc-tech-support@internal>",
    "source": "GDC Tech Support",
    "body": "<p>...</p>",
    "createdAt": "2026-09-25T09:00:00Z",
    "likeCount": 3,
    "liked": false
  }
}
```

| Field | Type | Ghi chú |
|---|---|---|
| `newsId` | Number | PK tin (`news_id` BIGINT identity — `AI_News_Batch.md` §6.1) |
| `title` | String | Subject email (≤ 200 ký tự) |
| `sender` | String, nullable | `from` của email |
| `source` | String | Display name của mailbox nguồn |
| `body` | String (HTML), **nullable** | Nội dung email đã sanitize; `null` nếu email không có body |
| `createdAt` | ISO datetime | Ngày email gửi |
| `likeCount` | Number | Tổng like của tin (FR-15 — UI dùng để render count ngay, không cần fetch riêng) |
| `liked` | Boolean | User hiện tại đã like tin này chưa (FR-15 — icon filled/unfilled) |

> `newsId` serialize từ `news_id` (BIGINT) ra JSON **number**.

### 5.4 Error Responses

| Status | Error Code | Description |
|---|---|---|
| 401 | `AUTH-401` | Session thiếu/hết hạn |
| 404 | `NEWS_NOT_FOUND` | Tin không tồn tại HOẶC `archive_yn = 'Y'` — **không phân biệt** cho client (chống lộ thông tin tin đã archive) |

> Error code là enum `SCREAMING_SNAKE` trong `BusinessErrorCode` (backend) — frontend map `errorCode` → message qua `lib/errorMessages.ts`. |

### 5.5 Query

```sql
SELECT news_id, title, body, sender, source, created_at
FROM ai_news
WHERE news_id = :newsId AND archive_yn = 'N';
```

> Không dùng index đặc biệt — `news_id` là PK.

### 5.6 Comment + Like endpoints (FR-15..FR-19)

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/v1/home/ai-news/{newsId}/comments` | List comment tin (top-level + reply, sort top-level DESC, reply ASC) |
| `POST` | `/api/v1/home/ai-news/{newsId}/comments` | Tạo comment (body: `{ content, parentId? }`) — `parentId` null = top-level, có = reply (depth ≤3, chặn depth > 3 → 400 `COMMENT_DEPTH_EXCEEDED`). Backend parse `data-user-id` từ mention spans → gửi notification (FR-24) |
| `DELETE` | `/api/v1/home/ai-news/{newsId}/comments/{commentId}` | Xóa comment (owner hoặc admin, soft delete — idempotent: no-op nếu đã xóa) |
| `POST` | `/api/v1/home/ai-news/{newsId}/comments/{commentId}/helpful` | Toggle vote hữu ích (không vote comment của mình). Response: `{ helpfulCount: N }` (typed DTO `ToggleHelpfulResponse`) |
| `POST` | `/api/v1/home/ai-news/{newsId}/like` | Toggle like bản tin (1 like/user/tin) — **client KHÔNG hiển thị toast** (silent, v1.10). Response: `{ likeCount: N }` (typed DTO `ToggleLikeResponse`) |

### 5.7 List API (grid sort — v1.17)

`GET /api/v1/home/ai-news?limit=N&title=...&sender=...&from=...&to=...`

Response trả thêm `likeCount` + `commentCount` + `viewCount` cho mỗi item (`likeCount`/`commentCount` qua CTE-scoped LEFT JOIN aggregate; `viewCount` đọc trực tiếp cột denormalized `ai_news.view_count` — FR-29):

```json
{
  "success": true,
  "data": [
    { "newsId": 1024, "title": "...", "sender": "...", "source": "...", "createdAt": "...", "likeCount": 3, "commentCount": 5, "viewCount": 12 }
  ]
}
```

> Aggregate scoped trong CTE (WITH limited AS ...) — chỉ COUNT cho 50 hàng trả về, không scan toàn bảng.

### 5.7.1 View tracking endpoint (FR-29 — v1.18)

| Method | Endpoint | Mô tả |
|---|---|---|
| `POST` | `/api/v1/home/ai-news/{newsId}/view` | Ghi nhận 1 user đã xem tin (idempotent per user — unique `(news_id, user_id)`). Response: `{ viewCount: N }` (distinct-viewer count sau khi ghi). Tin không tồn tại/đã archive → 404 `NEWS_NOT_FOUND` |

> Frontend chỉ gọi sau 30s đọc detail (tab visible). Backend không cần chống spam — upsert idempotent, user chỉ đếm 1 lần/tin.

### 5.6.1 Error codes (comment + like endpoints)

**Permission:** Session hợp lệ (mọi user đã đăng nhập). `DELETE` comment của user khác → chỉ `ADMIN_USER` (403 cho NORMAL_USER).

**Error codes:**

| Code | Description |
|---|---|
| `NEWS_NOT_FOUND` | Tin không tồn tại |
| `COMMENT_EMPTY` | Nội dung comment rỗng |
| `COMMENT_TOO_LONG` | Comment > 1000 ký tự |
| `COMMENT_PARENT_NOT_FOUND` | Reply vào comment không tồn tại |
| `COMMENT_PARENT_DELETED` | Reply vào comment đã xóa |
| `COMMENT_NOT_FOUND` | Comment không tồn tại (xóa/vote) |
| `COMMENT_NOT_OWN` | Không phải owner/admin — không xóa được |
| `CANNOT_VOTE_OWN_COMMENT` | Không vote comment của mình |
| `COMMENT_RATE_LIMITED` | Gửi comment quá nhanh (rate limit 10 comment/phút/user — anti-spam notification, v1.19) |

### 5.8 Import .mht thủ công (admin, FR-21 — v1.14)

| Method | Endpoint | Mô tả |
|---|---|---|
| `POST` | `/api/v1/admin/ai-news/import` | Import 1..20 file .mht (multipart) — server-side enforce 20MB/file + 40MB total (mirror frontend limits). Raw exception messages masked from API response. |

**Permission:** `ROLE_ADMIN_USER` (`@Secured` trên controller) — NORMAL_USER → **403**.

**Request:** `multipart/form-data`, field `files` = 1..20 file `.mht` (≤20MB/file — `max-file-size`; tổng ≤40MB — `max-request-size`, client-side guard).

**Success Response (200):** 1 kết quả per file (thứ tự = thứ tự file upload):

```json
{
  "success": true,
  "data": [
    { "index": 0, "fileName": "20261013_[Did_You_Know]_...mht", "status": "INSERTED", "newsId": 1025, "error": null },
    { "index": 1, "fileName": "dup.mht", "status": "SKIPPED_DUPLICATE", "newsId": null, "error": null },
    { "index": 2, "fileName": "broken.mht", "status": "FAILED", "newsId": null, "error": "Import failed: Not a valid .mht file" }
  ]
}
```

| Field | Type | Ghi chú |
|---|---|---|
| `index` | Number | Vị trí 0-based của file trong batch |
| `fileName` | String | Tên file gốc |
| `status` | String | `INSERTED` \| `SKIPPED_DUPLICATE` \| `FAILED` |
| `newsId` | Number, nullable | PK tin khi `INSERTED` |
| `error` | String, nullable | Lý do khi `FAILED` |

**Processing per file** (1 transaction/file — fail không chặn file khác): parse MHT (MIME `message/rfc822` qua `jakarta.mail`) → `title` = `cleanTitle(subject)` (rỗng → `FAILED`), `sender` = from, `createdAt` = Date header (fallback now), `source` = `"Manual Upload"`, `messageId` = Message-ID (thiếu → fallback `"mht-" + sha256(from|date|subject)`) → dedup `existsByMessageId` → sanitize body (NFR-02) → INSERT → lưu CID images (≤2MB lưu file như batch; >2MB nhúng data-URI).

**Error codes (HTTP-level — lỗi toàn request):**

| Code | Description |
|---|---|
| `MHT_INVALID_FILE` | Không có file / file không phải `.mht` / >20 file |
| `MHT_EMPTY_SUBJECT` | (dự phòng — hiện tại trả về per-file `FAILED`) |

> Per-file failures KHÔNG trả HTTP error — nằm trong `data[].status = "FAILED"` (1 file lỗi không chặn file khác).

### 5.9 @mention autocomplete (FR-24 — v1.17)

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/v1/home/ai-news/mention-users?q=...` | Tìm user cho @mention autocomplete (authenticated, non-admin) |

**Response:** `[{ id: 1, name: "Alice" }]` — max 10, match name/email LIKE (escaped), `enabled=true`, sort name ASC + id tiebreaker. Chỉ trả `id` + `name` (không email/password).

**Notification flow:** Khi comment có mention spans (`data-user-id`), backend parse → gửi in-app notification (`notifications` table, `news_id` + `comment_id`) + email (best-effort). Cap 10 distinct recipients, skip actor, respect `UserSettings.inappNotify`/`emailNotify`. Migration V43: `notifications.news_id` column + indexes.

---

## 6. Database Schema

Màn hình đọc bảng `ai_news` (DDL đầy đủ trong `AI_News_Batch.md` §6.1) + **4 bảng** cho comment + like + view tracking (v1.18). **Toàn bộ DDL đã có trong Flyway migration** (backend: `V24__phase3_consolidated.sql` cho comment/like, `V49__ai_news_view_log.sql` cho view log) — các DDL bên dưới khớp đúng migration đó.

Cột `ai_news` màn hình sử dụng: `news_id` (PK), `title`, `body` (HTML, nullable), `sender`, `source`, `created_at`, `archive_yn` (filter).

### 6.1 `ai_news_comment` (comment threaded)

```sql
CREATE TABLE ai_news_comment (
    comment_id    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    news_id       BIGINT NOT NULL REFERENCES ai_news(news_id),
    user_id       BIGINT NOT NULL REFERENCES "user"(id),
    parent_id     BIGINT REFERENCES ai_news_comment(comment_id),  -- null = top-level
    content       TEXT NOT NULL,  -- v1.16: rich text HTML (FR-23; trước: VARCHAR(1000))
    helpful_count INT NOT NULL DEFAULT 0,
    reply_count   INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMP WITH TIME ZONE  -- soft delete
);
CREATE INDEX idx_ai_news_comment_news ON ai_news_comment (news_id, created_at DESC);
```

### 6.2 `ai_news_comment_vote` (vote hữu ích trên comment)

```sql
CREATE TABLE ai_news_comment_vote (
    comment_id BIGINT NOT NULL REFERENCES ai_news_comment(comment_id),
    user_id    BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);
```

### 6.3 `ai_news_like` (like bản tin, khôi phục v1.10)

```sql
CREATE TABLE ai_news_like (
    news_id    BIGINT NOT NULL REFERENCES ai_news(news_id),
    user_id    BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (news_id, user_id)
);
```

### 6.4 `ai_news_view_log` (view tracking, v1.18 — migration V49)

```sql
CREATE TABLE ai_news_view_log (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    news_id     BIGINT NOT NULL REFERENCES ai_news(news_id),
    user_id     BIGINT NOT NULL REFERENCES "user"(id),
    viewed_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_news_view_log UNIQUE (news_id, user_id)
);
-- + ALTER TABLE ai_news ADD COLUMN view_count INTEGER NOT NULL DEFAULT 0;
```

> 1 row / (news, user) — user chỉ đếm 1 lần/tin. Unique constraint `(news_id, user_id)` vừa là index cho COUNT theo `news_id` (B-tree prefix), vừa là idempotency guard cho upsert. `ai_news.view_count` là cột denormalized — recompute từ log khi ghi view (idempotent, self-healing).

> Bảng user trong project là `"user"` (tên có quote — keyword SQL), PK `id BIGINT` (không phải UUID).

---

## 7. Xử lý lỗi & Edge Case

| # | Edge case | Xử lý |
|---|---|---|
| 1 | Tin bị archive trong khi đang hiển thị (job cleanup chạy) | Lần fetch sau trả 404 → FR-11; body đã render không bị ảnh hưởng |
| 2 | `body = NULL` (email không có nội dung) | FR-10 — info note, không phải lỗi |
| 3 | Body rất lớn (>1MB, nhiều ảnh base64) | Render trong container scroll (FR-07); NFR-01; không lazy-load ảnh (v1.0) |
| 4 | Fetch timeout (>30s) | FR-09 — error state; user chọn tin khác để thử lại |
| 5 | Body chứa script (lỗ hổng sanitize phía server) | CSP `script-src` chặn inline script (NFR-02); log cảnh báo Ops nếu phát hiện |
| 6 | User click 2 tin liên tiếp nhanh | Chỉ fetch tin mới nhất — fetch cũ bị hủy (AbortController) |
| 7 | User chuyển route/màn hình khác khi đang fetch | Fetch dang dở bị hủy (AbortController, unmount) |
| 8 | Link trong body trỏ tới URL nội bộ | Mở tab mới bình thường — không block (email nội bộ, link đáng tin cậy); `rel="noopener"` bắt buộc |
| 9 | `?news={newsId}` trỏ tới tin không tồn tại trong list (đã archive) | Chọn tin đầu (mới nhất) — không crash |
| 10 | User back/forward browser trên `/ai-news?news=X` | Sync selection theo route query (watch route) |
| 11 | Reply vào comment cấp 3 (depth > 3) | Chặn — nút Reply không hiện ở cấp 3; API trực tiếp → 400 `COMMENT_DEPTH_EXCEEDED` (FR-17, v1.21) |
| 11a | Xóa comment cấp 2 còn con cấp 3 chưa xóa | Con cấp 3 bị ẩn (backend không trả về reply có parent đã xóa; frontend cũng ẩn) — khớp FR-19 "reply con bị xóa → ẩn" (v1.21) |
| 12 | Xóa comment top-level còn reply con chưa xóa | Render tombstone "[deleted]" (giữ reply con không mất ngữ cảnh) — FR-19 |
| 13 | Vote hữu ích trên comment của chính mình | Bị chặn (`CANNOT_VOTE_OWN_COMMENT`) — nút disabled + toast (FR-18) |
| 14 | Xóa comment của user khác (NORMAL_USER) | Bị chặn (`COMMENT_NOT_OWN`); admin được xóa (FR-19) |
| 15 | Like bản tin khi tin đã archive (404) | Toggle like bị chặn (`NEWS_NOT_FOUND`) — tin archive không còn trong list nên không click được; if race: API trả lỗi, UI không đổi count |
| 16 | Comment rỗng / > 1000 ký tự **plain text** | Bị chặn (`COMMENT_EMPTY` / `COMMENT_TOO_LONG`) — counter trong editor (v1.16: đếm plain text, không đếm thẻ HTML/ảnh) |
| 17 | Import file không phải .mht / corrupt (FR-21) | File đó `FAILED` (parse error) — các file khác vẫn import; file không có extension `.mht` → cả request bị chặn (`MHT_INVALID_FILE`) |
| 18 | Import email trùng Message-ID đã có trong DB (FR-21) | `SKIPPED_DUPLICATE` — không tạo row mới (dedup như batch BR-NEWS-002) |
| 19 | Import email không có subject (FR-21) | File đó `FAILED` ("Email has no subject") — admin phải sửa file rồi import lại (không có bước sửa metadata) |
| 20 | Import email cũ >90 ngày (FR-21) | Import bình thường (`INSERTED`); batch job archive ở lần chạy kế tiếp (`archive_yn='Y'` — hành vi BR-NEWS-010 hiện có) |
| 21 | NORMAL_USER gọi `POST /admin/ai-news/import` (FR-21) | 403 (`@Secured("ROLE_ADMIN_USER")`); button "Import" không render cho non-admin |
| 22 | User xem tin đủ 30s nhưng tab đang ẩn (FR-29) | Timer **true pause** khi tab ẩn — thời gian tab ẩn KHÔNG tính vào 30s (bank phần đã đọc khi ẩn, resume phần còn lại khi hiện). User đọc 10s → ẩn tab 10 phút → quay lại vẫn cần 20s đọc visible nữa mới đếm. Không đếm view khi user không thực sự đọc |
| 23 | 2 tab cùng xem 1 tin (race first-time view, FR-29) | Unique `(news_id, user_id)` chặn duplicate insert; `view_count` recompute từ log (idempotent) — không double-count |
| 24 | Record view lỗi (network/500, FR-29) | Silent — không toast, không phá UI; lần reload kế tiếp gọi lại (idempotent) |

---

## 8. Acceptance Criteria / Test Cases

| TC | Scenario | Expected |
|---|---|---|
| TC-01 | Click menu GNB "AI News" | Màn hình render; menu active; tin đầu (mới nhất) tự chọn; title + sender + ngày render ngay (từ dữ liệu list) |
| TC-02 | Fetch 200 + body HTML (kèm ảnh, link, table) | Body render nguyên dạng; ảnh scale ≤ 100% width; link mở tab mới; container scroll khi body vượt max-height |
| TC-03 | Fetch 200 + `body = null` | Info note "Email không có nội dung." |
| TC-04 | Fetch 404 (tin đã archive / không tồn tại) | Warning note "Tin không tồn tại hoặc đã được lưu trữ." |
| TC-05 | Fetch 5xx / timeout | Warning note "Không tải được nội dung email."; màn hình vẫn dùng được |
| TC-06 | Đang fetch (mô phỏng delay 2s) | Hiện "Đang tải nội dung email…" → chuyển sang body khi fetch xong |
| TC-07 | Click tin khác trong list | Detail thay nội dung; item mới được highlight; fetch tin cũ bị hủy; hash cập nhật `?news={newsId mới}` |
| TC-08 | Click title tin trong card AI News (Home) | Điều hướng `/ai-news?news={newsId}` (history mode); màn hình mở với tin đó được chọn |
| TC-09 | Body email read-only | Body HTML không sửa được; không share/copy/print email. Nhưng có like (TC-13) + comment (TC-14..TC-18) |
| TC-10 | Body chứa `<script>alert(1)</script>` (test bảo mật) | Script KHÔNG chạy (CSP + sanitize); body text vẫn render |
| TC-11 | List rỗng (không có tin) | Detail pane hiển thị empty state "Chọn một tin" |
| TC-12 | `?news={newsId}` không tồn tại trong list | Tự chọn tin đầu (mới nhất), không crash |
| TC-13 | Click nút Like bản tin | Count tăng, icon ♥ filled + accent; **KHÔNG có toast** (silent). Click lại → count giảm, icon unfill, **KHÔNG có toast** |
| TC-14 | Nhập comment + Gửi | Comment hiện trong list (top-level DESC); toast "Đã gửi bình luận" |
| TC-15 | Click "Trả lời" trên comment | Reply form inline hiện; gửi → reply thụt lề dưới comment cha (ASC) |
| TC-16 | Reply vào reply cấp 2 | Reply mới thụt lề cấp 3 (ASC) + toast "Đã gửi trả lời"; comment cấp 3 không có nút Reply (API trực tiếp → 400 `COMMENT_DEPTH_EXCEEDED`) |
| TC-17 | Click 👍 trên comment | Count tăng, accent; click lại → giảm. Comment của mình → disabled + toast |
| TC-18 | Xóa comment (owner) | Comment biến mất (soft delete). Top-level còn reply → tombstone "[deleted]" giữ reply con |
| TC-19 | List tin trái thẳng hàng dọc (v1.9) | Tất cả item cùng left, dot + title căn thẳng (CSS `display: block`) |
| TC-20 | Admin mở màn AI News → click "Import" → chọn 1 file .mht thật → Import | Popup hiện kết quả `INSERTED` + newsId; toast "Đã import 1/1 tin"; đóng popup → tin mới hiện đầu list; detail render body đúng layout (inline style giữ) + ảnh CID hiển thị (ảnh >2MB qua data-URI) |
| TC-21 | Import file .mht trùng Message-ID đã có trong DB | Kết quả `SKIPPED_DUPLICATE` — không có row mới trong `ai_news` |
| TC-22 | Import 2 file, file 2 corrupt | File 1 `INSERTED`, file 2 `FAILED` (reason parse) — file lỗi không chặn file khác |
| TC-23 | NORMAL_USER mở màn AI News | Không thấy button "Import"; gọi `POST /api/v1/admin/ai-news/import` trực tiếp → 403 |
| TC-24 | Import file không phải .mht (vd .txt) | Request bị chặn `MHT_INVALID_FILE` (400) — không import gì |
| TC-25 | Comment rich text (v1.16): gõ text + B/I/U + chèn 1 ảnh + Gửi | Comment render HTML đúng (bold/italic/underline + ảnh hiển thị ≤100% width); toast "Đã gửi bình luận"; DB lưu HTML đã sanitize (không có `<script>`/`on*`) |
| TC-26 | Comment chỉ chứa ảnh (không chữ) | Nút Gửi disabled (plain text rỗng) |
| TC-27 | Comment > 1000 ký tự **text** (kèm thẻ HTML/ảnh) | Bị chặn `COMMENT_TOO_LONG` — thẻ HTML + ảnh không đếm vào 1000 ký tự |
| TC-28 | Comment cũ plain text (trước v1.16) | Render đúng như trước qua pipeline `v-html` + `sanitizeHtml` (không cần data migration) |

---

## 9. Version History

| Version | Status | Change |
|---|---|---|
| 1.0 | Draft | Tạo SRS AI News Detail Popup: FR-01..FR-10, NFR (XSS/hiệu năng/a11y/1-popup), API `GET /api/home/ai-news/{newsId}` (200/404/401, `NEWS-201`), 8 edge case, 10 test case; NFR-06: layout ưu tiên nội dung email — dialog 980px, ẩn eyebrow, title 16px, body ~74% viewport, footer compact |
| 1.1 | Draft | Gộp nội dung `AI_News_Detail_Popup_Spec.md` (đã xóa): wireframe 5 trạng thái (§2.4), cấu trúc popup (§2.5), quy tắc hiển thị (§3.1), i18n 3 ngôn ngữ (§3.2), bảng giá trị responsive/layout (NFR-06) |
| 1.2 | Draft | FR-04 + NFR-06: body email render trên **nền slate `#64748b` (xám xanh trung tính)** — ép `background-color: transparent !important` cho mọi element (email có background-color inline trắng), **giữ `background-image`** (banner hero ảnh nền có text trắng) — đọc được cả text đen lẫn trắng (ban đầu `#808080`, đổi sang slate `#64748b` theo đánh giá contrast) |
| 1.3 | Draft | Review cross-doc: (1) 4 chỗ `max-height: 55vh` cũ (wireframe §2.4.1, §2.5, FR-04, TC-02) → `calc(100svh − 235px)` theo NFR-06 (khớp demo); (2) §5.3 `newsId` example `"AN0001"` → `1024` (Number — khớp DDL `news_id` BIGSERIAL `AI_News_Batch.md` §6.1) + note demo mock `AN-xxx` là demo-only |
| 1.4 | Draft | Review demo: (1) §3.1 rule 4 + FR-04 — link trong email **màu vàng `#ffd54a`** (contrast ~7:1 trên nền slate `#64748b`; accent cyan chỉ ~1.9:1) thay vì "màu accent" (khớp demo `app.css`); (2) FR-02 + edge case #7 — ghi chú demo không implement timeout/AbortController (mock fetch local file; production bắt buộc) |
| 1.5 | Draft | Review vòng 7 (L4): §2.5 meta row — `createdAt` bổ sung tham chiếu date format Settings user (`../2. User Management Module/Settings_SRS.md` SH-03-04, mặc định DD/MM/YYYY) — nhất quán Home SRS §11 |
| 1.6 | Draft | Review toàn bộ SRS (vòng 10): gộp 2 dòng "1.0" trùng (NFR-06 layout) thành 1 dòng |
| 1.7 | Draft | **Popup → màn hình độc lập**: AI News Detail không còn là popup — là màn hình `#/ai-news` với **menu GNB ngay dưới Home** (icon riêng, active state), layout **2-pane** (list tin trái `minmax(320px,420px)` + detail email phải `minmax(0,1fr)`, gap 14px, mobile 1 cột). FR: thêm FR-01 (menu GNB), FR-02 (click title Home → navigate), FR-03 (mặc định chọn tin đầu), FR-05 (chọn tin khác → hủy fetch cũ), FR-13 (highlight item đang chọn), FR-14 (empty state chưa chọn tin); bỏ FR-09 cũ (4 cách đóng popup). NFR: bỏ "1 popup tại 1 thời điểm" + focus trap dialog; thêm NFR-05 layout 2-pane. i18n thêm `app.aiNews` + `aiNews.selectNews`/`selectNewsDesc`. Edge case +2 (hash không tồn tại, back/forward). TC: 10 → 12. File đổi tên `AI_News_Detail_Popup_SRS.md` → `AI_News_Screen_SRS.md` |
| 1.8 | Draft | **Thêm Bình luận (threaded) + Like bản tin + Vote hữu ích trên comment**: màn hình không còn "read-only tuyệt đối" — **read-only nội dung email + interactive comment/like**. FR-12 sửa (body email read-only, nhưng có like/comment); thêm FR-15 (like bản tin), FR-16 (comment), FR-17 (threaded reply depth ≤2), FR-18 (vote hữu ích), FR-19 (xóa comment + tombstone), FR-20 (empty state comment). §2.3 bỏ "read-only tuyệt đối" → "read-only nội dung email + interactive"; §2.5 detail pane thêm like row + comment section. §3.1 rule 1 sửa. §3.2 i18n thêm ~26 key (vi/en/ko). §5.6 thêm 5 endpoint comment/like + error codes. §6 thêm 3 bảng DB (`ai_news_comment`, `ai_news_like`, `ai_news_comment_vote`). §7 edge case +6 (11-16). §8 TC-09 sửa + TC-13..TC-18. Out of scope: report/moderation/insights/notification (v1.8) |
| 1.9 | Draft | **Bỏ chức năng Like bản tin** (FR-15): xóa nút like + count khỏi detail pane. FR-15 đánh dấu "ĐÃ BỎ" (giữ numbering để FR-16..FR-20 không đổi). §1.2 In Scope bỏ like + thêm vào Out of Scope. §2.2 Actors bỏ "like". §2.3 Nguyên tắc bỏ "like". §2.4.1 wireframe bỏ dòng like. §2.5 detail pane bỏ like row + **thêm ghi chú list thẳng hàng dọc**. §3.1 rule 1 bỏ like. §3.2 i18n bỏ 4 key `aiNews.like*`. §5.6 bỏ endpoint `POST /like` (còn 4 endpoint). §6 bỏ bảng `ai_news_like` (còn 2 bảng, đánh số lại 6.2). §7 edge case #15 bỏ like. §8 TC-13 đổi thành "list thẳng hàng dọc". **Fix list tin trái thẳng hàng dọc**: CSS `.ainews-list-card .ai-news-list { display: block }` + `.ai-news-item { align-items: start }` — item block-level full-width, dot + title cùng left (trước đó `display: grid` + title `nowrap` → item giữ max-content width, lệch ngang). |
| 1.10 | Draft | **Khôi phục Like bản tin** (FR-15) — **KHÔNG có toast** khi like/unlike (silent toggle): §1.2 In Scope thêm like + Out of Scope thêm "toast khi like". §2.2 Actors thêm "like". §2.3 Nguyên tắc: "interactive like + comment". §2.4.1 wireframe thêm dòng like row. §2.5 detail pane thêm like row. FR-12 sửa (có like + comment). FR-15 khôi phục (ghi rõ "KHÔNG có toast"). §3.1 rule 1 sửa. §3.2 i18n thêm 2 key `aiNews.like`/`aiNews.liked` (không có `likeRegistered`/`likeRemoved` vì silent). §5.6 thêm endpoint `POST /like` (5 endpoint). §6 thêm bảng `ai_news_like` (6.3 — 3 bảng). §7 edge case #15 khôi phục. §8 TC-13 khôi phục (like, verify không toast) + thêm TC-19 (list thẳng hàng dọc — thay thế nội dung TC-13 cũ v1.9). Giữ fix list thẳng hàng dọc (v1.9). |
| 1.11 | Draft | **Title 1 dòng + ellipsis (detail + list)**: (1) Detail title — không còn wrap nhiều dòng, `1 dòng, ellipsis "..." khi quá dài` + hover hiện đủ (tooltip native `title`); §2.4.1 wireframe, §2.5 detail pane, NFR-06 bảng layout cập nhật. (2) **Title list tin** — ellipsis "..." khi quá dài + hover hiện đủ; root cause: grid item `.ai-news-body` (cột `1fr`) mặc định `min-width: auto` → phình theo min-content của title `nowrap` → ellipsis không cắt; fix `.ai-news-body { min-width: 0 }` (base class — áp dụng cả card Home lẫn màn AI News). §2.5 list pane thêm ghi chú. |
| 1.13 | Updated | **Đối chiếu codebase thật + data thật (DEV/PROD) — tiếp theo v1.12**: (1) **Timeout** — FR-04 + §2.1 + edge case #4: "timeout 10s" → **30s** (axios global `timeout: 30000` trong `lib/api.ts` — không có timeout riêng cho detail fetch trong code); (2) **TC-08** — `#/ai-news?news={newsId}` → **`/ai-news?news={newsId}`** (history mode — khớp router thật `createWebHistory`; v1.12 đã đổi route nhưng TC-08 còn sót hash). |
| 1.14 | Updated | **Thêm Import email .mht thủ công (admin)**: FR-21 — button "Import" chỉ `ADMIN_USER` trên page header; popup upload 1..20 file .mht (≤20MB/file, tổng ≤40MB); metadata auto-parse từ file (title=subject qua `cleanTitle`, sender=from, date=Date header, source="Manual Upload") — không có bước sửa metadata; 1 file fail không chặn file khác; dedup Message-ID (fallback `mht-`+sha256); không đụng `last_success_run_at`/`ai_news_batch_log`. §1.2 In Scope + §2.2 Actors cập nhật. §3.2 i18n +12 key `aiNews.import*` (vi/en/ko). §5.7 mới: `POST /api/v1/admin/ai-news/import` (multipart `files[]`, response per-file INSERTED/SKIPPED_DUPLICATE/FAILED, error codes `MHT_INVALID_FILE`/`MHT_EMPTY_SUBJECT`). §7 edge case +5 (17-21). §8 +TC-20..TC-24. **NFR-02 cập nhật**: sanitizer giữ attribute `style` inline (lọc giá trị nguy hiểm: `expression()`, `javascript:`, `vbscript:`, `behavior:`, `-moz-binding`, `@import`) — thay đổi chung cho cả batch IMAP; ảnh CID >2MB nhúng data-URI vào body (không nâng cap image store). |
| 1.15 | Updated | **Thêm Comment sidebar** (FR-22): nút icon (MessageCircle + ChevronLeft) ở header detail card (cùng hàng title, ngoài cùng phải, disabled khi chưa chọn tin) mở sidebar (PrismDrawer bên phải, 768px, ẩn nhãn "PRISM / DRAWER") chứa like + comment **đúng chức năng** như khối dưới body — dùng chung component `AiNewsComments` + 1 state → thao tác đồng bộ 2 chỗ; tự đóng khi đổi tin; không endpoint API mới. §2.5 detail pane + §3.2 i18n +1 key `aiNews.openComments` (vi/en/ko). |
| 1.16 | Updated | **Comment rich text** (FR-23): comment form + reply dùng **RichTextEditor** (tiptap, toolbar đầy đủ như Skill Market: B/I/U/S, highlight, H1-H3, align, list, quote, code, table, link, ảnh ≤5MB auto-compress) thay textarea plain text. Render HTML sanitize 2 lớp: backend `CommentHtmlSanitizer` (Jsoup whitelist, không giữ `style`) khi lưu + frontend `sanitizeHtml()` khi render `v-html`; link `target=_blank rel=noopener`; ảnh `max-width:100%`. Giới hạn 1000 ký tự **đếm plain text** (không đếm thẻ HTML/ảnh) — backend validate trên plain text; nút Gửi disabled khi plain text rỗng. DB `ai_news_comment.content` `VARCHAR(1000)` → `TEXT` (migration V42; comment cũ render đúng, không data migration). §2.5 + §6.1 + edge case #16 cập nhật. |
| 1.17 | Updated | **Review rounds 1-5 (2026-10-22)**: Security (SSRF guard, SVG XSS, comment cap 20k, HtmlSanitizer url()/@, MHT size limits + exception mask, MhtEmailParser recursion cap). Performance (N+1 batched comments + mentions, CTE-scoped list aggregates, CID count cap 20/email, archive transaction, per-email resilience, sanitize cache, detail+comments parallel). Features: @mention (FR-24, temporarily disabled pending Nexus), grid sort (FR-25), comment sort (FR-26, trending), collapsible form (FR-27), bell deep-link (FR-28). Bug fixes: deleteComment idempotent, reply-to-deleted-root, replyCount decrement, helpfulCount floor, scrollToPendingComment checkVisibility, timestamp Date.getTime(), auto-select grid sort, form collapse justSent, Collectors.toMap null-safe, toggle typed DTO, SKIPPED alert fix, manual worker guard, bell cross-item ?comment, syncHash scoping, loadComments error toast, parentId null check, trending excludes deleted. Migration V43 (notifications.news_id). §1.2 + §3.2 i18n +9 key + §5.6-5.9 API + FR-24..FR-28. |
| 1.18 | Updated | **View tracking — đếm người đã xem** (FR-29): frontend gọi `POST /api/v1/home/ai-news/{newsId}/view` sau 30s đọc detail (tab visible — pause khi ẩn); backend upsert `ai_news_view_log` (unique `(news_id, user_id)` — idempotent per user) + recompute `ai_news.view_count` (không `+1` — self-healing khi race). Hiển thị 👁 ở header detail + badge list. §5.7.1 endpoint mới + list API trả thêm `viewCount`. §6.4 bảng `ai_news_view_log` (migration V49). §7 edge case +3 (22-24). Test: `AiNewsServiceImplViewTest` (unit) + `AiNewsViewTrackingIntegrationTest` (H2 e2e idempotency). |
| 1.19 | Updated | **Review round 6 (2026-10-28)**: (1) **Fix race-loser 500** — `recordView` upsert chạy trong transaction riêng `REQUIRES_NEW` (chứa PostgreSQL transaction abort khi 2 tab cùng fire first-time view — unique constraint violation không còn poison transaction ngoài, recompute + count vẫn chạy). (2) **Rate limit comment** — `POST /{newsId}/comments` throttle 10 comment/phút/user (in-memory `RateLimiter`, pattern skill comment) → error `COMMENT_RATE_LIMITED` (429) — anti-spam notification (mỗi comment tới 10 notification + email). §5.6.1 +1 error code. (3) **Xóa dead code** — endpoint `GET /sources` + repo `findDistinctActiveSources` + frontend wrapper `aiNewsApi.sources()` (không UI nào dùng). (4) **Archive cleanup self-healing** — image sweep chạy trên TOÀN bộ archived ids (không chỉ ids mới archive) — orphan files từ crash giữa 2 bước được dọn ở lần chạy kế tiếp. (5) **Bell poll visibility-aware** — `loadUnread` skip khi tab ẩn + refresh 1 lần khi tab hiện lại (AppShell). (6) **View timer true pause** — thời gian tab ẩn KHÔNG tính vào 30s (bank phần đã đọc khi ẩn, resume phần còn lại khi hiện — khớp FR-29 + edge case #22; code cũ dùng deadline absolute nên user đọc 10s + ẩn tab 10 phút vẫn bị đếm). |
| 1.20 | Updated | **Review rounds 2-7 + verify merge-readiness (2026-10-30):** (1) **@mention BẬT** — `@tiptap/extension-mention` 3.31.4 đã có trong `package.json` (v1.17 ghi "tạm disabled" do package chưa có trên Nexus) — `mention-enabled` bật ở cả 3 form comment/reply; §1.2 + FR-24 cập nhật. (2) **Refactor cấu trúc** — tách `AiNewsCommentService`/`Impl` (comment/vote/like + @mention helpers) khỏi `AiNewsServiceImpl` + composable `useAiNewsComments()` (state + mutations comment) khỏi `AiNewsView.vue`; comment mutations patch local state từ response (không re-fetch list). (3) **Notification** — `EventNotificationService` (in-app + email theo `UserSettings`, skip actor/disabled, email best-effort) + `NotificationEmailSender` `@Async` (SMTP không còn trong request/transaction). (4) **Perf/security** — image fetch parallel (pool 4, timeout 5s), SSRF guard unwrap IPv4-mapped IPv6, CSS blacklist mở rộng (phishing overlay), `pg_trgm` GIN index (V54 — đã verify extension 1.6 + 2 index trên DB live), sanitize cache persistent bounded 2000, drawer `compact` (read-only). (5) **Verify merge vào dev (2026-10-30)** — dev đã merge vào `phase_2/tq.hop` (commit `abc2b25`, đã push) → merge vào dev clean; 242 backend tests pass + `vue-tsc` pass; quy tắc Flyway: V50–V53 trên DB live đã bị chiếm bởi MCP + system (out-of-band) → mọi migration mới bắt đầu từ V55 (chi tiết trong `AI_News_Batch.md` §12.4 v1.24). |
| 1.21 | Updated | **Reply nested depth 3 (2026-11-02)** — FR-17 đổi: bỏ flatten (trước: reply vào comment cấp 2 → gộp về root + toast "đã gộp") → **nested depth 3** như Skill Market: reply vào comment cấp 2 nằm thụt lề cấp 3; **chặn reply ở cấp 3** (nút Reply không hiện; API → 400 `COMMENT_DEPTH_EXCEEDED`). Backend: `AiNewsCommentServiceImpl` bỏ khối flatten, thêm `MAX_COMMENT_DEPTH = 3` + `depthOf()`/`threadRoot()` (root vẫn phải alive), `findBoundedByNewsId` CTE thêm tầng 3 (cap 50/parent/tầng), backstop filter giữ depth-3. Frontend: `AiNewsComments.vue` nút Reply + reply form cho depth-2, nest depth-3 (không nút Reply ở cấp 3), trending tính like depth-3, CSS indent tầng 3; `useAiNewsComments.sendReply` increment `replyCount` parent trực tiếp (bỏ `willFlattened`). Không migration DB, không i18n key mới (key `aiNews.replyFlattened` thành unused — giữ nguyên trong DB). Edge case #11 + TC-16 + §5.6 cập nhật. Test: +4 unit test (`replyToDepth2KeepsParent`, `replyToDepth3Blocked`, `replyToDeletedDepth2Blocked`, `depth3UnderDeletedParentHidden`). Review fix: (a) CTE `ranked_replies3` partition theo **root** (JOIN parent depth-2) thay vì parent trực tiếp — cap 50/depth-2-parent có thể orphan depth-3 (reply depth-2 thứ 51 có con depth-3 không được fetch nhưng `replyCount > 0`); (b) `comments()` ẩn reply có parent trực tiếp đã xóa (FR-19) — depth-3 dưới depth-2 đã xóa không còn bị trả về; frontend cũng ẩn depth-3 khi parent depth-2 deleted; edge case #11a mới. |
