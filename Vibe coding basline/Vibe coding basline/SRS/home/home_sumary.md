# HOME — Summary (chi tiết)

> **File summary chi tiết** của chức năng **Home** (theo Rule 3 — `tq.hop/RULE.md`).
> Index tổng quát → [`home_index.md`](./home_index.md). Nguồn gốc: `Home_Screen_SRS.md` (v1.23).

## 1. Nghiệp vụ tổng quan

Home là **landing screen mặc định sau Login**, read-only dashboard cho user đã đăng nhập. Có 2 phần:

1. **GNB (Global Navigation Bar)** — app shell (Header + Sidebar) dùng chung cho TẤT CẢ màn hình sau login (Home, Skill Management, User Management, Settings). Không rebuild per page.
2. **Home Screen** — 2 tab: **Chart** (mặc định) và **Community**.

## 2. GNB

### Header (sticky, 56px)
- Menu toggle ☰ (hiện khi < lg) — mở/đóng sidebar drawer.
- Brand "AI SKILL MANAGEMENT" + logo `AppLogo` (SVG hexagon + neural network, gradient cyan→violet; spec đầy đủ trong SRS §4.2.1 — mọi màn hình dùng CÙNG logo, render nhiều lần trên 1 trang phải đổi SVG `id`).
- Breadcrumb (≥ lg).
- Notification bell 🔔: badge unread (cap `99+`), click → panel 10 tin mới nhất, "Mark all read".
- User menu (avatar + tên): dropdown My Settings / Logout.

### Sidebar (240px desktop, 256px wide)
| Item | Route | Visible |
|---|---|---|
| Home | `/home` | All |
| AI News | `#/ai-news` | All (ngay dưới Home — từ SRS ai-news) |
| Skill Management | `/skills` | All |
| User Management | `/users` | ADMIN_USER only |

- Active item: đúng 1 item khớp route hiện tại (ngoại lệ: Settings — không item active).
- Brand click → luôn về Home. Re-click active item → no-op.
- NORMAL_USER gõ thẳng `/users` → 403 + Access Denied, không item active.
- Role đọc từ session JWT, không từ client input.
- Responsive: mobile off-canvas drawer + overlay; tablet collapsible; desktop/wide fixed.

## 3. Home — Tab Chart (mặc định)

- Filter bar: **9 checkbox** (1/chart) — uncheck → ẩn card, grid reflow; mặc định tất cả hiện.
- 3-column grid, 9 charts (row-major):
  1. Status donut (skill theo trạng thái)
  2. Personal metrics (radar — viewBox có padding để label không bị cắt)
  3. Skill by creator (stacked bar)
  4. Views by creator (bar)
  5. Activity 6 weeks (area)
  6. Activity coverage (heatmap)
  7. Published ratio (gauge)
  8. Views sparkline
  9. Skill updated by week (line)
- Mỗi card header: title (trái) + **info icon (i)** (phải) → popover mô tả chart (i18n vi/en/ko, dismiss bằng outside click/Esc).

## 4. Home — Tab Community

Layout order:

```
[ Page Header "Home" ]
[ Tabs: Chart | Community ]
[ Greeting + user summary  ────  [ + Add Skill ][ Skill Management ] ]  (quick actions far-right cùng dòng)
[ Admin Review Queue banner (ADMIN, pendingReviews > 0, compact 1 dòng) ]
[ Admin Feature Requests banner (ADMIN, pendingRequestReviews > 0, compact 1 dòng) ]
[ KPI Row — 6 cards 1 dòng (6 col → 3 → 1) ]
[ 3 cột: Skill Hot | Skill mới nổi | Skill mới ]
[ 2 card: New Request (col 1) | AI News (col 2-3) ]
```

### Greeting
`Hello, {USER_NAME}` + `Part X · Group 1 · {Role}` (Part/Group ẩn khi NULL). Quick actions: **+ Add Skill** (primary → Add Skill Popup), **Skill Management** (secondary → điều hướng).

### KPI cards (6, server-side computed)
| KPI | Định nghĩa | Click → |
|---|---|---|
| My Skills | Số skill của user (mọi status) | Skill Mgmt, Creator = self |
| Published | Skill PUBLISHED của user | + Status = PUBLISHED |
| Pending Approval | Skill PENDING_APPROVAL của user | + Status = PENDING_APPROVAL |
| Total Views | SUM view_count skill của user | Creator = self |
| Average Rating | Trung bình rating của skill có ≥1 rating | Creator = self. Format `★ 4.6 (5)`; không có → `No rating` |
| Subscribed | Số skill đang subscribe | **KHÔNG click** (v1.0) |

### Community skill cards (3 cột, mọi row click → Skill Detail Popup + View Count +1)
| Card | Chọn |
|---|---|
| Skill Hot | 5 skill visible có view_count cao nhất (tie → skill_id) |
| Skill mới nổi | 5 skill visible update trong 14 ngày, view_count cao nhất |
| Skill mới | 10 skill visible `created_at DESC`, **scrollable** |

Visibility Rule (Status + Scope) enforce **server-side**.

### New Request card
- 8 Feature Request mới nhất có `CATEGORY IN (BUG, ENHANCEMENT)` (mọi requester), `created_at DESC`.
- Item: title (link) + status badge + category + requester · date. Click → Request Detail Popup.
- "View All" → Skill Management `?requests=open` (auto-open Request List Popup).
- Dot tone theo status request (semantic).

### AI News card
- 8 tin mới nhất (mọi user thấy tất cả — không filter user).
- Item: title (link) + `sender` (tên + email) + date. Dot tone **rotating** (trang trí).
- Click title → điều hướng `#/ai-news?news={newsId}` (màn hình AI News — xem `../ai-news/ai-news_sumary.md`).

### Admin banners
- **Review Queue**: `pendingReviews > 0` (system-wide PENDING_APPROVAL) → "You have N skills awaiting review." + Review Now → Skill Mgmt filter Status = PENDING_APPROVAL.
- **Feature Requests**: `pendingRequestReviews > 0` (system-wide SUBMITTED/UNDER_REVIEW) → "Feature Requests: N" + Review Now → `?requests=open`.

## 5. Luồng chính (use cases)

1. **Login → Home**: login thành công → redirect Home, tab Chart active, fetch song song 4 API (summary, community-skills, new-requests, ai-news) + unread-count.
2. **KPI click** → Skill Management với filter pre-applied.
3. **Skill row click** → Skill Detail Popup, View Count +1.
4. **Request item click** → Request Detail Popup (read-only cho non-owner/non-admin).
5. **Chart checkbox toggle** → ẩn/hiện chart card.
6. **GNB**: sidebar navigate, brand → Home, user menu → Settings/Logout, notification bell → panel.

## 6. API (đều cần session JWT, envelope `{ success, data | errorCode, message }`)

| Endpoint | Mô tả |
|---|---|
| `GET /api/home/summary` | KPI + `pendingReviews`/`pendingRequestReviews` (chỉ ADMIN) |
| `GET /api/home/community-skills` | hot / trending / new (3 list) |
| `GET /api/home/new-requests?limit=8` | 8 request BUG/ENHANCEMENT mới nhất |
| `GET /api/home/ai-news?limit=8` | 8 tin mới nhất — **KHÔNG trả `body`** (body lớn) |
| `GET /api/notifications/unread-count` | Badge GNB |
| `GET /api/notifications?limit=10` | Panel notification |
| `POST /api/notifications/read-all` | Mark all read |

Error codes chính: `HOME-001` (summary fail), `AUTH-401` (session hết hạn → redirect Login), HTTP 403 (`/users` cho NORMAL_USER). Error message catalog: ERR-HOME-001..010.

## 7. Data

- Home đọc bảng có sẵn: `skills_master`, `skill_ratings`, `skill_subscriptions`, Feature Request table, `USER_MASTER`, notification tables.
- **`ai_news`** — bảng do batch job `AI-NEWS-IMPORT` sở hữu (DDL trong `../ai-news/AI_News_Batch.md` §6.1); Home chỉ đọc.

## 8. Trạng thái màn hình

- Loading: Skeleton (KPI + cards), quick actions disabled.
- Loaded: mỗi region (KPI, community, New Request, AI News, charts) load **độc lập** — 1 region fail không blank cả màn hình.
- Error per region: Error State + Retry. All regions fail → full-screen Error + Retry.
- Empty: text riêng từng region (i18n 3 ngôn ngữ).

## 9. Business rules chính (trích)

- BR-HOME-001: Home = landing mặc định sau Login.
- BR-HOME-002: Home read-only (không tạo/sửa data).
- BR-HOME-003: KPI scoped theo user, computed server-side.
- BR-HOME-010..012: định nghĩa Hot/Trending/New.
- BR-HOME-015/016/022: admin banner chỉ ADMIN, count system-wide.
- BR-HOME-019: AI News không filter user.
- BR-GNB-001..011: GNB persistence, role visibility, active item, brand click, 403, notification badge.

## 10. Điểm cần nhớ khi fix/extend

- **Không sửa GNB chung khi làm Home** — GNB thuộc app shell; nếu task Home bắt buộc động vào GNB → ghi ngoại lệ trong `tq.hop/context_sumary.md` (Rule 1). **Ngoại lệ Phase 2 đã được user approve** (2026-10-12): được sửa `AppShell.vue` + `router/index.ts` cho GNB (AI News sidebar item, breadcrumb).
- Card AI News trong Home là **card list** (8 tin, không body) — logic màn hình chi tiết thuộc folder `ai-news/`.
- Date hiển thị theo date format Settings user (mặc định DD/MM/YYYY).
- i18n 3 ngôn ngữ (vi/en/ko) cho mọi text UI.
- Keyboard: link-style actions phải operable bằng keyboard (button-as-link + focus ring).

## 11. Trạng thái implement Phase 2 (2026-10-12)

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| `HomeView.vue` 2 tabs (Chart/Community) | ✅ | 9 checkbox + 9 charts, greeting, 6 KPI, 3 community cards, New Request, AI News card, 2 admin banner |
| Backend `GET /home/summary` | ✅ | thêm `myOpenRequests`, `pendingRequestReviews` |
| Backend `GET /home/community-skills` | ✅ | hot/trending/new, visibility server-side |
| Backend `GET /home/new-requests` | ✅ | **trả empty list** (chưa có Feature Request module — quyết định user) |
| GNB: AI News sidebar item + breadcrumb | ✅ | trong `AppShell.vue` (ngoại lệ Rule 1 đã approve) |
| Route `/ai-news` | ✅ | trong `router/index.ts` |
| `npm run build` (type-check + vite) | ✅ pass | 2026-10-12 |
| **Chart data** | ⚠️ **placeholder** | 9 charts hiện derive từ `summary` + `community-skills` (không có endpoint riêng cho chart series theo SRS). Cần backend chart endpoints khi có data thật — ghi nhận để phase sau. |
| Backend compile | ⚠️ chưa validate | Không có maven/docker local — cần CI/máy khác để compile. |
