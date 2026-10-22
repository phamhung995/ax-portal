# AI SKILL MANAGEMENT

## Home Screen & Global Navigation Bar (GNB) – Software Requirements Specification

**Document Version:** 1.24
**Status:** Updated
**Module:** Home / Global Navigation
**Audience:** PO / BA / UX/UI / Developer / QA / DB Developer

---

# 1. Document Purpose

This document defines the functional requirements, business rules, UI behavior, API specifications, and acceptance criteria for:

- **Global Navigation Bar (GNB)** — the persistent app-shell navigation (Header + Sidebar) shared by all post-login screens.
- **Home Screen** — the default landing screen displayed after a successful Login.

The Home Screen is a read-only dashboard that gives the logged-in user an at-a-glance summary of their Skill activity and a fast entry point to the Skill Management screen.

Design reference:

| Item | Reference |
|---|---|
| App Shell (Header + Sidebar) | `../../neon-design-system/docs/COMPONENTS.md` (component contracts) + demo `views/AppShell.vue` |
| Home layout | `../../neon-design-system/docs/COMPONENTS.md` §PrismGrid (KPI/dashboard grid, `responsive` = 3 → 1 cột) + demo `views/Home.vue` |
| Layout & Responsive | demo `src/app.css` (breakpoints: ≤1199px / ≤600px) |
| Login → Home redirect | `../2. User Management module/Login_User Register before login_SRS.md` §58 (Login Success → Home) |

---

# 2. Scope

## 2.1 In Scope

| ID | Function |
|---|---|
| HOME-01 | Global Navigation Bar (Header + Sidebar) |
| HOME-02 | Role-based navigation item visibility |
| HOME-03 | User menu (My Settings, Logout) |
| HOME-04 | Notification bell (unread count + notification list) |
| HOME-05 | Home screen landing after Login |
| HOME-06 | Home tabs (Chart / Community) |
| HOME-07 | Greeting & user summary + inline quick actions |
| HOME-08 | KPI cards (6 cards, single row) |
| HOME-09 | Community skill cards (Hot / Trending / New) |
| HOME-10 | New Request card (8 latest BUG/ENHANCEMENT requests) + AI News |
| HOME-11 | Chart tab (checkbox toggles + 9 charts) |
| HOME-12 | Admin Review Queue banner (compact, single line) |
| HOME-13 | Admin Feature Requests banner (compact, single line) |
| HOME-14 | Home screen states (loading / empty / error) |

## 2.2 Out of Scope

The following items are not included in this document:

- Logout management (session invalidation, server-side token revocation) — out of scope per `Login_User Register before login_SRS.md` §2.2. Only the UI trigger and redirect are specified here.
- Full Notification Center (notification settings, archive, history beyond the latest 10) — to be specified in a separate Notification SRS.
- Skill Management screen behavior — specified in `Skill_Management_Screen_Spec.md`.
- Add Skill Popup behavior — specified in `Add_Popup_SRS.md`.
- Skill Detail Popup behavior — specified in `Detail_Popup_SRS.md`.
- AI News Screen behavior — specified in `AI_News_Screen_SRS.md`.
- Role assignment / user management — specified in `User Management Screen_SRS.md`.

---

# 3. Actors

| Actor | Description |
|---|---|
| NORMAL_USER | Sees Home with personal KPIs, recent skills, and discovery list |
| ADMIN_USER | Sees Home with personal KPIs + Admin Review Queue banner |
| System | Computes KPIs, enforces visibility rules, serves Home data |

Both roles use the **same GNB layout**; only the visibility of navigation items and the Review Queue banner differ.

---

# 4. Global Navigation Bar (GNB)

## 4.1 App Shell Structure

The GNB is the persistent app shell rendered on **every post-login screen** (Home, Skill Management, User Management, Settings). It is never rebuilt per page.

```text
┌──────────────────────────────────────────────────────────────┐
│ HEADER (sticky, 56px)                                        │
│ [☰] [AI SKILL MANAGEMENT]        [breadcrumb]  🔔  (avatar) │
├──────────┬───────────────────────────────────────────────────┤
│ SIDEBAR  │                                                   │
│ 240px    │  MAIN CONTENT                                     │
│          │  [ Page Header ]                                  │
│ [Home]   │  [ Screen content ]                               │
│ [Skill   │                                                   │
│  Mgmt]   │                                                   │
│ [User    │                                                   │
│  Mgmt]   │  ← ADMIN_USER only                                │
└──────────┴───────────────────────────────────────────────────┘
```

| Element | Behavior |
|---|---|
| Header | Sticky top, always visible while scrolling |
| Sidebar | Fixed 240px on desktop (≥lg), 256px on wide (≥xl) |
| Main | Independent scroll area, fluid up to `container.max.2xl` |

## 4.2 Header

| Element | Position | Behavior |
|---|---|---|
| Menu toggle (☰) | Left | Visible below `lg`. Opens/closes the Sidebar drawer |
| Brand "AI SKILL MANAGEMENT" | Left, after menu toggle | Click → navigate to **Home** |
| Breadcrumb | Center-left | Visible on `lg` and above. Reflects current screen |
| Notification bell (🔔) | Right | Unread count badge. Click → notification dropdown (§4.7) |
| User menu (avatar + User Name) | Right, last | Click → user dropdown (§4.6) |

### 4.2.1 Brand Logo (AppLogo) — spec tái sinh

Logo hệ thống **"AI SKILL MANAGEMENT"** — hiển thị ở **2 nơi**: GNB Header (cạnh brand text) và Login/Auth screen (trên brand text). **Mọi màn hình phải dùng CÙNG một logo** (component `AppLogo`).

**Motif:** Hexagon (khung lục giác — tech/futuristic) + Neural network (mạng nơ-ron — AI). Gradient neon **cyan → violet** đồng bộ Design System Prism.

**Tham số bắt buộc (copy đúng để gen cùng 1 logo):**

| Thuộc tính | Giá trị |
|---|---|
| ViewBox | `0 0 40 40` |
| Size GNB Header | `22px` |
| Size Auth screen | `30px` (trong box 46px) |
| Gradient khung (`appLogoFrame`) | linear 135deg `#67e8f9` (cyan) → `#a58aef` (violet) |
| Gradient edge (`appLogoEdge`) | linear `#67e8f9` → `#a58aef` (trục ngang) |
| Hexagon | path `M20 3 L34.7 11.5 V28.5 L20 37 L5.3 28.5 V11.5 Z`, stroke `url(#appLogoFrame)`, `stroke-width: 2.2`, `stroke-linejoin: round`, `fill: none` |
| Neural edges | path `M13 20 L20 12.5 M13 20 L20 27.5 M20 12.5 L27 20 M20 27.5 L27 20`, stroke `url(#appLogoEdge)`, `stroke-width: 1.6`, `stroke-linecap: round` |
| Node trái | circle `(13,20) r=2.6` fill `#67e8f9` |
| Node phải | circle `(27,20) r=2.6` fill `#a58aef` |
| Node trên | circle `(20,12.5) r=2.2` fill `#8be9f6` |
| Node dưới | circle `(20,27.5) r=2.2` fill `#b99bf2` |
| Node trung tâm | circle `(20,20) r=1.7` fill `#eafcff` |
| Glow | SVG filter `feGaussianBlur stdDeviation=1.4` + `feMerge` (blur + SourceGraphic) áp lên nhóm neural |
| CSS container | `.app-logo { display: block; flex-shrink: 0; filter: drop-shadow(0 0 6px rgb(103 232 249 / .45)) }` |
| CSS hover (GNB) | `.gnb-brand:hover .app-logo { filter: drop-shadow(0 0 10px rgb(103 232 249 / .7)) }` (glow tăng khi hover brand) |

**SVG source (canonical — copy nguyên để tái sinh logo giống hệt):**

```svg
<svg class="app-logo" width="22" height="22" viewBox="0 0 40 40" fill="none" role="img" aria-hidden="true">
  <defs>
    <linearGradient id="appLogoFrame" x1="6" y1="6" x2="34" y2="34" gradientUnits="userSpaceOnUse">
      <stop stop-color="#67e8f9" />
      <stop offset="1" stop-color="#a58aef" />
    </linearGradient>
    <linearGradient id="appLogoEdge" x1="13" y1="20" x2="27" y2="20" gradientUnits="userSpaceOnUse">
      <stop stop-color="#67e8f9" />
      <stop offset="1" stop-color="#a58aef" />
    </linearGradient>
    <filter id="appLogoGlow" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="1.4" result="b" />
      <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
    </filter>
  </defs>
  <!-- Hexagon frame (tech) -->
  <path d="M20 3 L34.7 11.5 V28.5 L20 37 L5.3 28.5 V11.5 Z"
        stroke="url(#appLogoFrame)" stroke-width="2.2" stroke-linejoin="round" fill="none" />
  <!-- Neural network (AI) -->
  <g filter="url(#appLogoGlow)">
    <path d="M13 20 L20 12.5 M13 20 L20 27.5 M20 12.5 L27 20 M20 27.5 L27 20"
          stroke="url(#appLogoEdge)" stroke-width="1.6" stroke-linecap="round" />
    <circle cx="13" cy="20" r="2.6" fill="#67e8f9" />
    <circle cx="27" cy="20" r="2.6" fill="#a58aef" />
    <circle cx="20" cy="12.5" r="2.2" fill="#8be9f6" />
    <circle cx="20" cy="27.5" r="2.2" fill="#b99bf2" />
    <circle cx="20" cy="20" r="1.7" fill="#eafcff" />
  </g>
</svg>
```

> **Lưu ý khi tái sinh:** Nếu render logo nhiều lần trên cùng 1 trang (VD GNB + Auth), phải **đổi `id`** của `linearGradient`/`filter` (VD `appLogoFrame-2`) để tránh xung đột SVG `id` global. Auth screen dùng `width/height="30"`; GNB dùng `22`.

## 4.3 Sidebar Navigation Items

| # | Label | Icon | Route | Visible To |
|---|---|---|---|---|
| 1 | Home | 🏠 | `/home` | All |
| 2 | Skill Management | 📚 | `/skills` | All |
| 3 | User Management | 👥 | `/users` | ADMIN_USER only |

### Navigation Item States

| State | Visual |
|---|---|
| Default | Secondary text, transparent background |
| Hover | Subtle background tint |
| Active (current route) | Primary text + primary background tint + left accent bar |
| Focus | Visible focus ring (keyboard navigation) |
| Disabled | Muted, non-clickable (reserved for future items) |

## 4.4 Role-Based Visibility

```text
NORMAL_USER GNB:
┌──────────────────────┐
│ Home                 │
│ Skill Management     │
└──────────────────────┘

ADMIN_USER GNB:
┌──────────────────────┐
│ Home                 │
│ Skill Management     │
│ User Management      │
└──────────────────────┘
```

Rules:

1. The **User Management** item is rendered **only** when `ROLE = ADMIN_USER`.
2. A NORMAL_USER must never see the User Management item, even temporarily.
3. If a NORMAL_USER navigates directly to `/users` (typed URL or stale bookmark), the system must:
   - Reject the request at the API level (403).
   - Display an Access Denied state in Main content.
   - Not highlight any sidebar item.
4. Role is read from the authenticated session (JWT claims), never from client input.

## 4.5 Active State & Routing Rules

| Rule | Description |
|---|---|
| Active item | Exactly one sidebar item is active at a time, matching the current route |
| Home is default | After Login, the first screen is Home and the Home item is active |
| Brand click | Always navigates to Home |
| Re-click active item | No-op (stays on the same screen, no reload) |
| Unauthenticated access | Any post-login route without a valid session redirects to Login |
| Popup screens | Opening a popup (Detail/Add/Edit) does **not** change the active sidebar item |
| Settings screen | Settings is reached from the user menu (avatar → My Settings), not from the Sidebar; no sidebar item is active on the Settings screen |

## 4.6 User Menu

Clicking the avatar/User Name opens a dropdown:

```text
┌─────────────────────────────┐
│ (avatar) Nguyen Nhung       │
│ Part X · Group 1            │
├─────────────────────────────┤
│ ⚙️  My Settings             │
├─────────────────────────────┤
│ 🚪  Logout                  │
└─────────────────────────────┘
```

| Item | Behavior |
|---|---|
| Header (name + Part · Group) | Read-only display from session data (`PART` / `GROUP` — hidden when NULL; `USER_ID` is a DB-generated UUID and is NOT displayed) |
| My Settings | Navigate to Settings screen |
| Logout | Clear the local session (token) and redirect to Login. Detailed logout management is out of scope (§2.2) |

The dropdown closes on outside click or `Esc`.

## 4.7 Notification Bell

| Element | Behavior |
|---|---|
| Bell icon | Always visible on the Header |
| Unread badge | Shows unread count; displays `99+` when count > 99; hidden when 0 |
| Click | Opens a dropdown panel with the latest 10 notifications |

Notification panel:

```text
┌───────────────────────────────────────────┐
│ Notifications              [Mark all read]│
├───────────────────────────────────────────┤
│ ● Skill 'Java Spring Boot' has a new     │
│   version — 2 hours ago                  │
│ ○ Skill 'Python Data' was rated 5★ — 1d │
│ ... (latest 10)                           │
└───────────────────────────────────────────┘
```

Rules:

1. Unread notifications are marked with a dot (●); read ones are muted (○).
2. Clicking a notification marks it as read. If the notification references a Skill, the Skill Detail Popup opens.
3. "Mark all read" marks all unread notifications as read and clears the badge.
4. The badge refreshes when the panel is opened and after any read action.
5. The full Notification Center (settings, archive, pagination) is out of scope (§2.2).

## 4.8 GNB Responsive Behavior

Per demo `src/app.css` (responsive breakpoints):

| Breakpoint | Sidebar | Header |
|---|---|---|
| Mobile (`<md`, <900px) | Off-canvas drawer, opened by ☰ | Condensed (☰ + brand) |
| Tablet (`md…lg`, 900–1199px) | Collapsible (toggle) | Full |
| Desktop (`≥lg`, ≥1200px) | Fixed 240px | Full + breadcrumb |
| Wide (`≥xl`, ≥1536px) | Fixed 256px | Full + breadcrumb |

- Opening the mobile drawer shows a dimmed overlay; clicking the overlay or the selected item closes it.
- Navigation items meet the 40×40px minimum touch target below `lg`.

---

# 5. Home Screen

## 5.1 Purpose & Entry

- Home is the **default landing screen after a successful Login** (see Login SRS §58).
- Home is **read-only**: no data is created or modified from Home except opening popups and navigating.
- Home data is always scoped to the **logged-in user** (and, for the discovery list, to Skills visible to that user).

## 5.2 Screen Structure

Home is organized into **two tabs** (PrismTabs): **Chart** (default) and **Community**.

### Tab 1 — Chart

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Home                                                                 │
├──────────────────────────────────────────────────────────────────────┤
│ [ Chart | Community ]                                                 │
│                                                                      │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ ☑ Skill theo trạng thái  ☑ Chỉ số cá nhân      ☑ Skill theo creator│ │
│ │ ☑ Views theo creator     ☑ Activity 6 tuần     ☑ Activity coverage│ │  ← checkbox ẩn/hiện từng chart
│ │ ☑ Tỷ lệ published        ☑ Views sparkline     ☑ Skill update/tuần│ │
│ └──────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                   │
│ │ Status donut (i)│ │ Radar      (i)│ │ Stacked bar(i)│  ← 3-column grid  │
│ │ (skill status) │ │ (personal    │ │ (skill by    │    (i) = info icon,  │
│ │              │ │  metrics)    │ │  creator)    │    click → popover   │
│ └──────────────┘ └──────────────┘ └──────────────┘    chart description │
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                   │
│ │ Bar          │ │ Area         │ │ Heatmap      │                   │
│ │ (views/creator)│ │ (activity 6w)│ │ (coverage)   │                   │
│ └──────────────┘ └──────────────┘ └──────────────┘                   │
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                   │
│ │ Gauge        │ │ Sparkline    │ │ Line (update)│                   │
│ │ (published)  │ │ (views 6w)   │ │              │                   │
│ └──────────────┘ └──────────────┘ └──────────────┘                   │
└──────────────────────────────────────────────────────────────────────┘
```

- Each chart is a Card; unchecking a checkbox hides its card (grid reflows).
- 9 charts (row-major order): **Status donut** (skill status), **Personal metrics** (radar), **Skill by creator** (stacked bar) · Views by creator (bar), Activity 6 weeks (area), Activity coverage (heatmap) · Published ratio (gauge), Views sparkline, Skill updated by week (line).
- Row 1 groups **Skill status + Personal metrics + Skill by creator & status** on the same horizontal row.
- Personal metrics (radar) SVG reserves a viewBox padding so axis labels (e.g. "Average Rating", "Subscribed", "Published") are not clipped at the chart edge.
- **Chart info icon (i)**: each chart card header has an info icon `(i)` at the **top-right** (title left, icon right). Clicking it opens a **popover** (dismissible by outside click / Esc) with a one-paragraph description of what that chart shows — one dedicated text per chart (i18n 3 languages: vi/en/ko). The icon is rendered together with its card, so toggling the chart checkbox hides the icon as well.

### Tab 2 — Community

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Home                                                                 │
├──────────────────────────────────────────────────────────────────────┤
│ [ Chart | Community ]                                                 │
│                                                                      │
│ Hello, Nguyen Nhung                        [ + Add Skill ][ Skill Mgmt ] │
│ Part X · Group 1 · Admin User   ← quick actions OUTSIDE-RIGHT of greeting │
│                                                                      │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ ⚠ You have 4 skills awaiting review.                    [Review Now]│ │ ← ADMIN_USER only,
│ └──────────────────────────────────────────────────────────────────┘ │   compact single line
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ ℹ Feature Requests: 3                               [Review Now]│ │ ← ADMIN_USER only,
│ └──────────────────────────────────────────────────────────────────┘ │   requests awaiting review
│                                                                      │
│ ┌────────┐ ┌────────┐ ┌───────────┐ ┌────────┐ ┌────────┐ ┌────────┐ │
│ │My      │ │Publis- │ │Pending    │ │Total   │ │Avg     │ │Subscri-│ │ ← 6 KPI cards, ONE row
│ │Skills  │ │hed     │ │Approval   │ │Views   │ │Rating  │ │bed     │ │   (6 cols → 3 → 1)
│ │   12   │ │   8    │ │    1      │ │  342   │ │ 4.6(5) │ │   3    │ │
│ └────────┘ └────────┘ └───────────┘ └────────┘ └────────┘ └────────┘ │
│                                                                      │
│ ┌─────────────────────────┐ ┌─────────────────────────┐ ┌───────────┐│
│ │ Skill Hot          (top)│ │ Skill mới nổi       (mid)│ │ Skill mới ││
│ │ View nhiều nhất         │ │ Xem nhiều nhất 2 tuần    │ │ 10 skill  ││
│ │ 1. Java Spring Boot     │ │ 1. Python Data Analysis │ │ gần nhất  ││
│ │ 2. Python Data Analysis │ │ 2. Java Spring Boot     │ │ (scroll)  ││
│ │ ... (5 rows)            │ │ ... (5 rows)            │ │ ...       ││
│ └─────────────────────────┘ └─────────────────────────┘ └───────────┘│
│                                                                      │
│ ┌─────────────────────────┐ ┌─────────────────────────────────────────┐│
│ │ New Request             │ │ AI News                                 ││
│ │ Xem tất cả              │ │ ● SDS AI news …                         ││
│ │ ● FR-012 Sửa lỗi filter │ │ ● GDC Tech Support guide …              ││
│ │ ○ FR-013 Export báo cáo │ │ ● AI news from Part X …                 ││
│ │ ... (8 requests)        │ │ ...                                     ││
│ └─────────────────────────┘ └─────────────────────────────────────────┘│
│  (cột 1 — thẳng với Hot Skills)  (cột 2–3 — thẳng với Trending + New)  │
└──────────────────────────────────────────────────────────────────────┘
```

Layout order (Community tab):

```text
[ Page Header ("Home") ]
[ Tabs: Chart | Community ]
[ Greeting + user summary  ──────────────  [ + Add Skill ][ Skill Management ] ]
[ Admin Review banner (ADMIN_USER only, compact single line) ]
[ Admin Feature Requests banner (ADMIN_USER only, when pendingRequestReviews > 0) ]
[ KPI Row (6 cards, one row: 6 cols → 3 → 1) ]
[ Three-column community row: Skill Hot | Skill mới nổi | Skill mới ]
[ Two-card row aligned to the 3-col community grid: New Request | AI News (spans 2 cols) ]
```

### Tab 1 — Chart (structure)

```text
[ Page Header ("Home") ]
[ Tabs: Chart | Community ]
[ Chart filter bar: 9 checkboxes (one per chart) ]
[ 3-column chart grid (9 cards, each toggleable by checkbox) ]
  Row 1: Status donut | Personal metrics (radar) | Skill by creator (stacked bar)
  Row 2: Views by creator (bar) | Activity 6 weeks (area) | Activity coverage (heatmap)
  Row 3: Published ratio (gauge) | Views sparkline | Skill updated by week (line)
  Each card header: title (left) + info icon (i) (right) → click = popover with chart description
```

## 5.3 Greeting, User Summary & Inline Quick Actions

| Element | Source | Format |
|---|---|---|
| Greeting | Session `USER_NAME` | `Hello, {USER_NAME}` |
| Part · Group | Session `PART` / `GROUP` | Displayed after `·` as `Part X · Group 1`; each part hidden when NULL; both NULL → only Role shown |
| Role | Session `ROLE` | Display label: "Normal User" / "Admin User" |

The greeting row is a **flex row** with the greeting text on the left and the **quick actions on the far right** (same row, vertically centered). Quick actions are no longer a standalone block at the bottom of the screen.

## 5.4 Quick Actions (inline, right of greeting)

| Button | Variant | Behavior |
|---|---|---|
| + Add Skill | Primary | Opens Add Skill Popup (per `Add_Popup_SRS.md`) |
| Skill Management | Secondary | Navigates to Skill Management (default view) |

Rules:

1. Both buttons sit on the **same row as the greeting**, aligned to the **far right** of the content area.
2. On narrow screens (`≤600px`) the greeting row stacks: greeting on top, quick actions below (full width).

## 5.5 Admin Review Queue Banner (compact)

| Property | Value |
|---|---|
| Visible to | ADMIN_USER only |
| Condition | `pendingReviews > 0` |
| Text | `You have {N} skills awaiting review.` |
| Layout | **Compact single line**: icon + title on the left, `Review Now` button on the right (reduced vertical padding/font vs. the standard Alert) |
| Action | `Review Now` button → Skill Management with `Status = PENDING_APPROVAL` filter (no Creator filter) |
| Hidden when | NORMAL_USER, or `pendingReviews = 0` |

`pendingReviews` = count of Skills with `STATUS = PENDING_APPROVAL` across **all creators** (system-wide).

## 5.5a Admin Feature Requests Banner (compact)

| Property | Value |
|---|---|
| Visible to | ADMIN_USER only |
| Condition | `pendingRequestReviews > 0` |
| Text | `Feature Requests: {N}` |
| Layout | **Compact single line** (same style as §5.5): icon + title on the left, `Review Now` button on the right |
| Action | `Review Now` button → Skill Management with hash query `?requests=open` (auto-opens Request List Popup) |
| Hidden when | NORMAL_USER, or `pendingRequestReviews = 0` |

`pendingRequestReviews` = count of Feature Requests with `STATUS = SUBMITTED` or `UNDER_REVIEW` across **all requesters** (system-wide). See `Request_Feature_SRS.md` (BR-RF-027, SR-RF-041/042).

## 5.6 KPI Cards

| # | KPI | Definition | Clickable | Click Target |
|---|---|---|---:|---|
| 1 | My Skills | Count of Skills created by the logged-in user (all statuses) | ✓ | Skill Management, Creator = self |
| 2 | Published | Count of my Skills with `STATUS = APPROVED` (code enum — "Published" is the UI label for `APPROVED`) | ✓ | Skill Management, Creator = self + Status = APPROVED |
| 3 | Pending Approval | Count of my Skills with `STATUS = PENDING_APPROVAL` | ✓ | Skill Management, Creator = self + Status = PENDING_APPROVAL |
| 4 | Total Views | Sum of `view_count` across my Skills | ✓ | Skill Management, Creator = self |
| 5 | Average Rating | Average of the average rating of my Skills that have ≥1 rating | ✓ | Skill Management, Creator = self |
| 6 | Subscribed | Count of Skills the user is subscribed to | ✕ | — (display only) |

Rules:

1. KPI values are computed **server-side** for the logged-in user. The client never computes them from raw lists.
2. All counts are integers; a count of 0 is displayed as `0` (not blank, not `-`).
3. **Average Rating** display:
   - Has rated Skills → `★ 4.6 (5)` where `4.6` = average of my Skills' average ratings (rounded to 1 decimal) and `5` = number of my Skills that have at least one rating.
   - No rated Skill → `No rating` (consistent with Skill Management rating display rule).
4. The **Subscribed** card is display-only in v1.0 because Skill Management has no "subscribed only" filter. *(Status: Recommended — PO to confirm whether a Subscribed filter should be added to Skill Management in a future version.)*
5. KPI cards use the Card component; value uses the KPI value typography, label uses the secondary text style.
6. Max 6 KPI cards (Dashboard pattern rule: do not overload with >7 KPIs).
7. **Layout**: the 6 KPI cards render in a **single row** on desktop (6-column grid), collapsing to 3 columns on tablet (`≤1199px`) and 1 column on mobile (`≤600px`). Card padding and value font are compact so all 6 fit on one line.

## 5.7 Community Skill Cards (Hot / Trending / New)

Three cards in a 3-column row (stack on mobile). All rows are clickable → Skill Detail Popup (View Count +1 on success).

| Card | Selection | Row content |
|---|---|---|
| **Skill Hot** | 5 Skills with the highest `view_count` (visible to user) | Rank # · Name · Creator · 👁 views · ★ rating |
| **Skill mới nổi** (Trending) | 5 Skills with the highest `view_count` among Skills updated within the last 14 days (visible to user) | Rank # · Name · Creator · 👁 views · ★ rating |
| **Skill mới** (New) | 10 Skills by `created_at DESC` (visible to user); **scrollable** list | Name · Status Badge · Creator · Created date · 👁 views · ★ rating |

Rules:

1. All three lists are scoped to Skills **visible to the logged-in user** (Visibility Rule, enforced server-side).
2. **Skill mới** is the only scrollable card (max 10 items, internal vertical scroll).
3. Each card shows an empty state when it has no items.
4. Ties are broken by `skill_id` (stable ordering).

## 5.8 New Request & AI News

Two cards aligned to the 3-column grid of the community row above (same tracks + 14px gap, vertical spacing 14px — equal to horizontal gap): **New Request** spans column 1 (aligned with Hot Skills), **AI News** spans columns 2–3 (aligned with Trending + New Skills); stack on mobile.

| Card | Content |
|---|---|
| **New Request** | List of the **8 most recent Feature Requests with `CATEGORY = BUG` or `ENHANCEMENT`** (all requesters — no per-user filter), `created_at DESC`, ties by `request_id`. Each item: title (link) + status badge + category + `requester · date`. Click item → **Request Detail Popup** (see `../1. Skill Management module/Request_Feature_SRS.md`). "Xem tất cả" → Skill Management with `?requests=open` (auto-opens Request List Popup) |
| **AI News** | Timeline of the 8 newest AI news items (all users see all items — no per-user filter). Each item shows the email **sender** in full (`"Tên <email>"` — name + address, as returned by the API) + **date** (per user's date format — `../2. User Management module/Settings_SRS.md` SH-03-04) instead of a category. Title is a **link** — click opens a popup with the full email content (body HTML). Newest first |

Rules:

1. AI News items are **not** filtered by user — every user sees all items (no category/part/group classification).
2. Both cards show an empty state when they have no items — **New Request**: "No requests yet" / "No bug or enhancement requests yet." (i18n `home.noNewRequests`/`noNewRequestsDesc`); **AI News**: "No AI news" / "No AI news yet." (i18n `home.noAiNews`/`noAiNewsDesc`) — 3 ngôn ngữ vi/en/ko.
3. AI News data is populated by the batch job `AI-NEWS-IMPORT` (email → DB, body stored as HTML) — see `AI_News_Batch.md`.
4. Dot tones differ per card: **AI News** uses **rotating tones** (accent/violet/success/warning/teal/rose) for visual variety — decorative, not semantic; **New Request** uses **semantic tones by Request status** — SUBMITTED: accent, UNDER_REVIEW: warning, APPROVED: success, REJECTED: danger, FULFILLED: teal, CLOSED: neutral.
5. Click item title → **AI News Screen** (AI News) hoặc **Request Detail Popup** (New Request). AI News: điều hướng `/ai-news?news={newsId}` (history mode — không dùng hash) — xem `AI_News_Screen_SRS.md` (hiển thị sender, ngày, body HTML; empty body → info note).
6. Card headers: **AI News** has a short descriptive subtitle on the right of the title (i18n 3 languages, e.g. en "AI news from internal emails · shows sender (name + email)"); **New Request** has a "View All" link on the right of the title → Skill Management `?requests=open` (see card table).
7. **New Request** is scoped to `CATEGORY = BUG` / `ENHANCEMENT` only (bug reports and skill improvement requests) — `NEW_SKILL` / `OTHER` requests are not shown; max 8 items.

## 5.9 Home Screen States

| State | Behavior |
|---|---|
| Loading | KPI cards render as Skeleton cards; community/news cards render as Skeleton rows; Quick actions disabled |
| Loaded | Data rendered; each region (KPI, community cards, New Request, AI News, charts) loads **independently** — one failed region does not blank the whole screen |
| Empty | Per-region empty state text (§5.7, §5.8) |
| Error (per region) | Region shows Error State with a **Retry** button; other regions remain usable |
| Error (all regions) | Full-screen Error State with Retry |

---

# 6. Business Rules

| ID | Rule |
|---|---|
| BR-HOME-001 | Home is the default landing screen after a successful Login |
| BR-HOME-002 | Home is read-only; no data is created or modified from Home |
| BR-HOME-003 | All KPI values are scoped to the logged-in user and computed server-side |
| BR-HOME-004 | `My Skills` counts all statuses of the user's own Skills |
| BR-HOME-005 | `Total Views` = SUM(`view_count`) of the user's own Skills |
| BR-HOME-006 | `Average Rating` = average of the average rating of the user's Skills that have ≥1 rating; no rated Skill → `No rating` |
| BR-HOME-007 | `Subscribed` = count of active subscriptions of the user |
| BR-HOME-008 | KPI card click navigates to Skill Management with the pre-applied filter defined in §5.6 |
| BR-HOME-009 | The Subscribed KPI card is not clickable in v1.0 |
| BR-HOME-010 | Skill Hot = 5 visible Skills with the highest `view_count`, ties broken by `skill_id` |
| BR-HOME-011 | Skill mới nổi (Trending) = 5 visible Skills with the highest `view_count` among Skills updated within the last 14 days |
| BR-HOME-012 | Skill mới (New) = 10 visible Skills by `created_at DESC`, shown in a scrollable list |
| BR-HOME-013 | The Visibility Rule (Status + Scope) is enforced server-side for all community skill cards |
| BR-HOME-014 | Opening a Skill from Home (community cards) follows the Skill Detail rules, including View Count +1 on success |
| BR-HOME-015 | The Admin Review Queue banner is visible only to ADMIN_USER when `pendingReviews > 0`; it renders as a compact single-line alert |
| BR-HOME-016 | `pendingReviews` counts PENDING_APPROVAL Skills across all creators (system-wide) |
| BR-HOME-017 | Home has two tabs: **Chart** (default) and **Community**; tab content is scoped to the logged-in user |
| BR-HOME-018 | Each Chart-tab checkbox toggles the visibility of its chart card; all 9 charts are visible by default |
| BR-HOME-019 | AI News is not filtered by user — all items are shown to every user, sorted newest first; each item displays the email `sender` (no category) |
| BR-HOME-020 | Quick actions (+ Add Skill, Skill Management) are rendered on the greeting row, aligned to the far right |
| BR-HOME-021 | The New Request card shows the 8 most recent Feature Requests with `CATEGORY = BUG` or `ENHANCEMENT` (all requesters), `created_at DESC`, ties by `request_id`; clicking an item opens the Request Detail Popup; "View All" navigates to Skill Management with `?requests=open` |
| BR-HOME-022 | The Admin Feature Requests banner is visible only to ADMIN_USER when `pendingRequestReviews > 0` (system-wide `SUBMITTED` / `UNDER_REVIEW` requests); `Review Now` navigates to Skill Management with `?requests=open` |
| BR-GNB-001 | The GNB is rendered on every post-login screen and is never rebuilt per page |
| BR-GNB-002 | The User Management nav item is visible only to ADMIN_USER |
| BR-GNB-003 | Exactly one sidebar item is active at a time, matching the current route (exception: on the Settings screen, no sidebar item is active) |
| BR-GNB-004 | Brand click always navigates to Home |
| BR-GNB-005 | Re-clicking the active item is a no-op |
| BR-GNB-006 | Unauthenticated access to any post-login route redirects to Login |
| BR-GNB-007 | A NORMAL_USER accessing `/users` directly receives 403 and an Access Denied state |
| BR-GNB-008 | Opening a popup does not change the active sidebar item |
| BR-GNB-009 | The notification badge shows the unread count, capped at `99+` |
| BR-GNB-010 | Logout clears the local session and redirects to Login (detailed logout management is out of scope) |
| BR-GNB-011 | Settings is reachable only via the user menu (avatar → My Settings); the Sidebar has no Settings entry |

---

# 7. API Specification

All Home APIs require a valid session (JWT). All responses follow the standard envelope:

```json
{ "success": true, "data": { ... } }
```

Error responses:

```json
{ "success": false, "errorCode": "INTERNAL_ERROR", "message": "Business message." }
```

> **Thực tế codebase:** backend không có error code `HOME-001` — lỗi không do nghiệp vụ (DB, serialization…) được trả về `INTERNAL_ERROR` (HTTP 500, generic handler `GlobalExceptionHandler`); lỗi nghiệp vụ dùng enum `BusinessErrorCode` (SCREAMING_SNAKE). Catalog §7.7 giữ ID `ERR-HOME-00x` làm **message ID phía frontend** (map theo region), không phải `errorCode` backend.

## 7.1 Home Summary (KPIs)

### Endpoint

`GET /api/v1/home/summary`

### Success Response

```json
{
  "success": true,
  "data": {
    "mySkills": 12,
    "myPublished": 8,
    "myPendingApproval": 1,
    "myDraft": 3,
    "myTotalViews": 342,
    "myAverageRating": 4.6,
    "myRatedSkillCount": 5,
    "subscribedSkills": 3,
    "myOpenRequests": 2,
    "pendingReviews": 4,
    "pendingRequestReviews": 3
  }
}
```

| Field | Type | Description |
|---|---|---|
| mySkills | Integer | Count of own Skills (all statuses) |
| myPublished | Integer | Count of own `APPROVED` Skills (UI label "Published") |
| myPendingApproval | Integer | Count of own PENDING_APPROVAL Skills |
| myDraft | Integer | Count of own DRAFT Skills (informational — no KPI card) |
| myTotalViews | Integer | Sum of view_count of own Skills |
| myAverageRating | Decimal (1 dp) | Average of own Skills' average ratings (Skills with ≥1 rating). `null` when no rated Skill |
| myRatedSkillCount | Integer | Number of own Skills with ≥1 rating |
| subscribedSkills | Integer | Count of active subscriptions |
| myOpenRequests | Integer | Count of own open Feature Requests (`SUBMITTED` / `UNDER_REVIEW` / `REJECTED`) — informational (no KPI card; requests are surfaced in the New Request card) |
| pendingReviews | Integer | System-wide PENDING_APPROVAL count. **Returned only for ADMIN_USER; omitted for NORMAL_USER** |
| pendingRequestReviews | Integer | System-wide count of Feature Requests `SUBMITTED` / `UNDER_REVIEW`. **Returned only for ADMIN_USER; omitted for NORMAL_USER** |

### Error Codes

| Code | Description |
|---|---|
| INTERNAL_ERROR | Home summary could not be loaded (HTTP 500 — generic handler; không có code `HOME-001` trong `BusinessErrorCode`) |
| AUTH-401 | Session missing or expired → redirect to Login |

## 7.2 Community Skill Cards (Hot / Trending / New)

### Endpoint

`GET /api/v1/home/community-skills`

### Success Response

```json
{
  "success": true,
  "data": {
    "hot": [
      { "skillId": "SK0001", "skillName": "Java Spring Boot", "creatorName": "Tran Minh Quan", "viewCount": 45, "ratingScore": 4.7, "ratingCount": 3 }
    ],
    "trending": [
      { "skillId": "SK0002", "skillName": "Python Data Analysis", "creatorName": "Le Van A", "viewCount": 32, "ratingScore": 4.5, "ratingCount": 2 }
    ],
    "new": [
      { "skillId": "SK0003", "skillName": "Document Summarizer", "creatorName": "Pham Thi B", "status": "APPROVED", "createdAt": "2026-09-26T10:00:00", "viewCount": 5, "ratingScore": null, "ratingCount": 0 }
    ]
  }
}
```

### Rules

- `hot`: 5 visible Skills, `view_count DESC`, ties broken by `skill_id`.
- `trending`: 5 visible Skills updated within the last 14 days, `view_count DESC`, ties broken by `skill_id`.
- `new`: 10 visible Skills, `created_at DESC`, ties broken by `skill_id`.
- Visibility Rule (Status + Scope) enforced server-side for all three lists.
- `ratingScore` is `null` when the Skill has no rating → UI displays `No rating`.

## 7.3 New Request (Latest BUG / ENHANCEMENT Requests)

### Endpoint

`GET /api/v1/home/new-requests?limit=8`

### Success Response

```json
{
  "success": true,
  "data": [
    {
      "requestId": "FR-012",
      "title": "Fix status filter not resetting when scope changes",
      "category": "BUG",
      "status": "SUBMITTED",
      "requesterName": "Nguyen Nhung",
      "createdAt": "2026-09-28T11:00:00"
    }
  ]
}
```

### Rules

- 8 most recent Feature Requests with `CATEGORY = BUG` or `ENHANCEMENT` (all requesters — no per-user filter), `created_at DESC`, ties broken by `request_id`.
- Clicking an item opens the Request Detail Popup (read-only for non-owners/non-admins — see `Request_Feature_SRS.md`).

> **Thực tế codebase (v1.24):** module Feature Request **chưa implement** — endpoint hiện trả về list rỗng (`[]`), card New Request hiển thị empty state; `myOpenRequests` và `pendingRequestReviews` hardcode `0` trong `HomeServiceImpl` cho tới khi module ship. Spec trên là mục tiêu khi module hoàn tất.

## 7.4 AI News

### Endpoint

`GET /api/v1/home/ai-news?limit=8`

### Success Response

```json
{
  "success": true,
  "data": [
    {
      "newsId": 1024,
      "title": "GDC Tech Support: new AI coding guide",
      "sender": "GDC Tech Support <gdc-tech-support@internal>",
      "source": "GDC Tech Support",
      "createdAt": "2026-09-25T09:00:00"
    }
  ]
}
```

### Rules

- `sender` = `from` của email (người gửi) — hiển thị trên card AI News thay vì category.
- **List API KHÔNG trả `body`** (body HTML có thể rất lớn — email kèm ảnh) — màn hình chi tiết fetch `body` qua `GET /api/v1/home/ai-news/{newsId}` (chi tiết: `AI_News_Screen_SRS.md` §5).
- Không filter theo user — mọi user nhận cùng một danh sách.
- Ordered by `created_at DESC`.

## 7.5 Notification Unread Count

### Endpoint

`GET /api/v1/notifications/unread-count`

### Success Response

```json
{
  "success": true,
  "data": { "unreadCount": 2 }
}
```

## 7.6 Notification List

### Endpoint

`GET /api/v1/notifications?limit=10`

### Success Response

```json
{
  "success": true,
  "data": [
    {
      "notificationId": "NT0001",
      "title": "Skill 'Java Spring Boot' has a new version",
      "skillId": "SK0001",
      "createdAt": "2026-09-21T09:00:00",
      "isRead": false
    }
  ]
}
```

- Latest 10 notifications, `createdAt DESC`.
- `skillId` is `null` when the notification has no linked Skill.

### Mark All Read

`POST /api/v1/notifications/read-all`

```json
{ "success": true }
```

> **Dependency note:** The notification data model and generation rules (subscription new-version events) are specified in the separate Notification SRS. This document consumes the API contract above only.

## 7.7 Error Message Catalog

All user-facing error messages are in **English** and identified by a stable ID.
The frontend maps `errorCode` → message ID.

| ID | Message | Error Code(s) | Where |
|---|---|---|---|
| ERR-HOME-001 | "Unable to load home summary. Please try again." | `HOME-001` | Home summary (KPI) region |
| ERR-HOME-002 | "Unable to load community skills. Please try again." | (region API failure) | Community skill cards (Hot / Trending / New) region |
| ERR-HOME-003 | "Unable to load new requests. Please try again." | (region API failure) | New Request region |
| ERR-HOME-004 | "Unable to load AI news. Please try again." | (region API failure) | AI News region |
| ERR-HOME-005 | "Your session has expired. Please log in again." | `AUTH-401` | All Home APIs → redirect to Login |
| ERR-HOME-006 | "You do not have permission to access this screen." | (HTTP 403) | `/users` direct access as NORMAL_USER |
| ERR-HOME-007 | "No bug or enhancement requests yet." | — (empty state) | New Request |
| ERR-HOME-008 | "No AI news." | — (empty state) | AI News |
| ERR-HOME-009 | "Unable to load notifications. Please try again." | (notifications API failure) | Notification bell |
| ERR-HOME-010 | "No notifications." | — (empty state) | Notification panel |
| ERR-NEWS-001 | "News not found or archived." | `NEWS_NOT_FOUND` | AI News Screen (404 — tin không tồn tại hoặc `archive_yn = 'Y'`) |
| ERR-NEWS-002 | "The import job is already running." | `NEWS_JOB_RUNNING` | Admin manual run `POST /api/v1/admin/ai-news/run` (job đang chạy — lock) |
| ERR-NEWS-003 | "The import job failed." | `NEWS_JOB_FAILED` | Admin manual run — job status `FAILED` (chi tiết trong `ai_news_batch_log.error_message`) |

> AI News Screen messages (loading / load fail / empty body) are specified in `AI_News_Screen_SRS.md` §3.2 (i18n `aiNews.*`).

---

# 8. Data Dictionary

Home data is derived from existing tables, plus an `ai_news` table for the AI News feed:

| Home Element | Source |
|---|---|
| My Skills / Published / Pending Approval | `skills_master` (creator = user, status) |
| Total Views | `skills_master.view_count` (creator = user) |
| Average Rating | `skill_ratings` aggregated per Skill (creator = user) |
| Subscribed | `skill_subscriptions` (subscriber = user) |
| Skill Hot / Trending / New | `skills_master` + `skill_ratings` + visibility rules |
| New Request | Feature Request table (`Request_Feature_SRS.md`) — `category IN (BUG, ENHANCEMENT)`, `created_at DESC` |
| AI News | `ai_news` (sender, source, created_at) |
| myOpenRequests / pendingRequestReviews | Feature Request table (`Request_Feature_SRS.md`) |
| pendingReviews | `skills_master` (status = PENDING_APPROVAL, all creators) |
| User summary (name, org, role) | `USER_MASTER` (session) |
| Notifications | Notification tables (separate SRS) |

### `ai_news` (new table)

| Column | Type | Description |
|---|---|---|
| news_id | PK | Unique identifier |
| message_id | String | Email `Message-ID` (dedup, unique) |
| title | String | News title (email subject) |
| body | HTML (TEXT) | Email content, sanitized HTML |
| sender | String | Email `from` (name + address) |
| source | String | Display source (e.g. "GDC Tech Support") |
| created_at | DateTime | Publication date |

> Data is written by the batch job `AI-NEWS-IMPORT` (email → DB). Full DDL (incl. `message_id`, `body`, `archive_yn`, `imported_at`) + `ai_news_source` + `ai_news_batch_log` are in `AI_News_Batch.md` §6.

---

# 9. Use Cases

## UC-HOME-001 – Open Home After Login

1. User logs in successfully.
2. System redirects to Home (Chart tab active by default).
3. System identifies the user and role from the session.
4. System calls `GET /api/v1/home/summary`, `GET /api/v1/home/community-skills`, `GET /api/v1/home/new-requests`, `GET /api/v1/home/ai-news` in parallel.
5. System calls `GET /api/v1/notifications/unread-count` for the GNB badge.
6. System renders tabs, greeting + inline quick actions, KPIs, community cards, New Request, and AI News.

---

## UC-HOME-002 – View KPIs

1. User opens the Community tab.
2. System displays the 6 KPI cards in a single row with server-computed values.
3. Average Rating displays `No rating` when the user has no rated Skill.

---

## UC-HOME-003 – KPI Card Click

1. User clicks a clickable KPI card.
2. System navigates to Skill Management.
3. System pre-applies the filter defined in §5.6 (Creator = self, and Status where applicable).
4. Skill Management displays the filtered result.

---

## UC-HOME-004 – Open a Skill from Home

1. User clicks a Skill row (Skill Hot / Skill mới nổi / Skill mới).
2. System opens the Skill Detail Popup.
3. On success, View Count increases by 1.

---

## UC-HOME-004a – Open a Request from New Request Card

1. User opens the Community tab.
2. System displays the New Request card with the 8 most recent BUG/ENHANCEMENT requests.
3. User clicks a request item.
4. System opens the Request Detail Popup (read-only for non-owners/non-admins).
5. User clicks "View All".
6. Skill Management opens with `?requests=open` — Request List Popup auto-opens.

---

## UC-HOME-005 – Admin Review Queue

1. ADMIN_USER opens Home (Community tab).
2. System returns `pendingReviews = N`.
3. If N > 0, the compact single-line banner is displayed.
4. User clicks Review Now.
5. Skill Management opens with Status = PENDING_APPROVAL filter.

---

## UC-HOME-005a – Feature Requests Admin Banner

1. ADMIN_USER opens Home (Community tab).
2. System returns `pendingRequestReviews = K` (system-wide open requests).
3. If K > 0, the Feature Requests banner is displayed.
4. User clicks the banner's Review Now.
5. Skill Management opens with `?requests=open` — Request List Popup auto-opens.

---

## UC-HOME-006 – Toggle Charts (Chart Tab)

1. User is on the Chart tab.
2. User unchecks a chart checkbox.
3. The corresponding chart card is hidden; the grid reflows.
4. Re-checking the checkbox shows the chart again.

---

## UC-GNB-001 – Navigate via Sidebar

1. User clicks a sidebar item.
2. System navigates to the target screen.
3. The clicked item becomes active; the previous item deactivates.
4. Main content reloads for the new screen.

---

## UC-GNB-002 – Open Settings from User Menu

1. User clicks the avatar/User Name.
2. System opens the user dropdown.
3. User clicks My Settings.
4. System navigates to the Settings screen.
5. The dropdown closes.

---

## UC-GNB-003 – Logout

1. User opens the user dropdown.
2. User clicks Logout.
3. System clears the local session (token).
4. System redirects to the Login screen.
5. The GNB is no longer rendered.

---

## UC-GNB-004 – View Notifications

1. User clicks the notification bell.
2. System calls `GET /api/v1/notifications?limit=10`.
3. System displays the latest 10 notifications with read/unread markers.
4. User clicks a notification.
5. System marks it as read.
6. If the notification has a linked Skill, the Skill Detail Popup opens.
7. The unread badge updates.

---

# 10. Acceptance Criteria

## GNB

### AC-GNB-01 – GNB Persistence

**Given** the user is logged in
**When** the user is on any post-login screen (Home, Skill Management, User Management, Settings)
**Then** the GNB (Header + Sidebar) is displayed with the same layout.

---

### AC-GNB-02 – Active Item

**Given** the user is on Skill Management
**When** the GNB is rendered
**Then** the Skill Management item is active
**And** all other items are in the default state.

---

### AC-GNB-03 – Role-Based Visibility

**Given** the user is a NORMAL_USER
**When** the GNB is rendered
**Then** the User Management item is not displayed.

**Given** the user is an ADMIN_USER
**When** the GNB is rendered
**Then** the User Management item is displayed.

---

### AC-GNB-04 – Brand Click

**Given** the user is on any post-login screen
**When** the user clicks the brand
**Then** the Home screen is displayed
**And** the Home item becomes active.

---

### AC-GNB-05 – Direct Access to /users as Normal User

**Given** the user is a NORMAL_USER
**When** the user navigates directly to `/users`
**Then** the API returns 403
**And** an Access Denied state is displayed in Main content
**And** no sidebar item is active.

---

### AC-GNB-06 – Unauthenticated Access

**Given** the user has no valid session
**When** the user opens any post-login route
**Then** the Login screen is displayed.

---

### AC-GNB-07 – User Menu

**Given** the user is logged in
**When** the user clicks the avatar/User Name
**Then** the dropdown displays the user's name, Part · Group, My Settings, and Logout.

---

### AC-GNB-08 – Logout

**Given** the user is logged in
**When** the user clicks Logout
**Then** the local session is cleared
**And** the Login screen is displayed.

---

### AC-GNB-09 – Responsive Sidebar

**Given** the viewport is below `lg` (1200px)
**When** the GNB is rendered
**Then** the sidebar is not fixed
**And** the ☰ toggle opens the off-canvas drawer.

**Given** the viewport is `lg` or above
**When** the GNB is rendered
**Then** the sidebar is fixed (240px, 256px on wide).

---

### AC-GNB-10 – Notification Badge

**Given** the user has 2 unread notifications
**When** the GNB is rendered
**Then** the bell displays a badge with `2`.

**Given** the user has 120 unread notifications
**When** the GNB is rendered
**Then** the badge displays `99+`.

**Given** the user has 0 unread notifications
**When** the GNB is rendered
**Then** no badge is displayed.

---

### AC-GNB-11 – Notification Read

**Given** the notification panel is open
**When** the user clicks an unread notification
**Then** the notification is marked as read
**And** the unread badge count decreases
**And** if the notification has a linked Skill, the Skill Detail Popup opens.

---

## Home

### AC-HOME-01 – Landing After Login

**Given** the user logs in successfully
**When** the Login API returns success
**Then** the Home screen is displayed
**And** the Home sidebar item is active.

---

### AC-HOME-02 – Greeting

**Given** the logged-in user is "Nguyen Nhung" in Part X / Group 1
**When** Home is rendered
**Then** the greeting displays `Hello, Nguyen Nhung`
**And** the summary displays `Part X · Group 1 · Normal User`.

---

### AC-HOME-03 – KPI Values

**Given** the user has 12 Skills (8 PUBLISHED, 1 PENDING_APPROVAL), total views 342, 5 rated Skills averaging 4.6, and 3 active subscriptions
**When** Home is rendered
**Then** the KPI cards display: My Skills `12`, Published `8`, Pending Approval `1`, Total Views `342`, Average Rating `★ 4.6 (5)`, Subscribed `3`.

---

### AC-HOME-04 – No Rating

**Given** the user has no Skill with a rating
**When** Home is rendered
**Then** the Average Rating card displays `No rating`.

---

### AC-HOME-05 – Zero Count

**Given** the user has 0 Skills
**When** Home is rendered
**Then** the My Skills card displays `0`.

---

### AC-HOME-06 – KPI Click Navigation

**Given** the user is on Home
**When** the user clicks the Pending Approval KPI card
**Then** Skill Management is displayed
**And** the search is pre-filtered by Creator = self and Status = PENDING_APPROVAL.

---

### AC-HOME-07 – Subscribed Card Not Clickable

**Given** the user is on Home
**When** the user clicks the Subscribed KPI card
**Then** no navigation occurs.

---

### AC-HOME-08 – Tabs

**Given** the user is on Home
**When** Home is rendered
**Then** two tabs are displayed: `Chart` (active by default) and `Community`
**And** the Chart tab shows the chart filter bar + chart grid
**And** the Community tab shows greeting, KPIs, community cards, New Request, and AI News.

---

### AC-HOME-09 – Chart Checkbox Toggle

**Given** the user is on the Chart tab with all 9 charts visible
**When** the user unchecks a chart checkbox
**Then** the corresponding chart card is hidden and the grid reflows
**And** re-checking the checkbox shows the chart again.

---

### AC-HOME-10 – Skill Hot

**Given** the user has visible Skills with view counts
**When** the Community tab is rendered
**Then** Skill Hot displays the 5 visible Skills with the highest `view_count`
**And** each row shows rank, name, creator, views, and rating.

---

### AC-HOME-11 – Skill mới nổi (Trending)

**Given** the user has visible Skills updated within the last 14 days
**When** the Community tab is rendered
**Then** Skill mới nổi displays the 5 visible Skills with the highest `view_count` among Skills updated within the last 14 days.

---

### AC-HOME-12 – Skill mới (New)

**Given** the user has visible Skills
**When** the Community tab is rendered
**Then** Skill mới displays up to 10 visible Skills by `created_at DESC`
**And** the list is scrollable
**And** each row shows name, status badge, creator, created date, views, and rating.

---

### AC-HOME-13 – Community Card Visibility

**Given** a Skill that is not visible to the user (Scope rule)
**When** the Community tab is rendered
**Then** the Skill is NOT displayed in Skill Hot, Skill mới nổi, or Skill mới.

---

### AC-HOME-14 – Row Click Opens Detail

**Given** the user is on the Community tab
**When** the user clicks a Skill row (community cards)
**Then** the Skill Detail Popup opens
**And** on success the View Count increases by 1.

---

### AC-HOME-15 – AI News

**Given** the user belongs to Part X and Group 1
**When** the Community tab is rendered
**Then** AI News displays ALL items (no per-user filter)
**And** each item shows the email `sender` (no category)
**And** items are sorted newest first.

---

### AC-HOME-15a – AI News: click title điều hướng màn hình AI News

**Given** the Community tab is rendered and the AI News list has items
**When** the user clicks an item title
**Then** the app navigates to `/ai-news?news={newsId}` (history mode) — the AI News Screen opens with that item selected, showing the email `sender`, date, and the email body (HTML, sanitized) in a scrollable container
**And** the menu GNB "AI News" (ngay dưới Home) is active
**And** if the email has no body → the detail pane shows an info note (empty state).

---

### AC-HOME-16 – Admin Banner

**Given** the user is an ADMIN_USER with 4 system-wide PENDING_APPROVAL Skills
**When** the Community tab is rendered
**Then** the compact single-line banner displays `You have 4 skills awaiting review.`
**And** the `Review Now` button is on the right of the same line
**And** clicking Review Now opens Skill Management filtered by Status = PENDING_APPROVAL.

---

### AC-HOME-17 – Banner Hidden for Normal User

**Given** the user is a NORMAL_USER
**When** the Community tab is rendered
**Then** the Admin Review Queue banner is not displayed
**And** the `pendingReviews` field is not returned by the summary API.

---

### AC-HOME-18 – Quick Actions Inline

**Given** the user is on the Community tab
**When** Home is rendered
**Then** the `+ Add Skill` and `Skill Management` buttons are on the greeting row, aligned to the far right
**And** clicking `+ Add Skill` opens the Add Skill Popup
**And** clicking `Skill Management` navigates to Skill Management.

---

### AC-HOME-19 – Per-Region Error

**Given** the community-skills API fails
**When** the Community tab is rendered
**Then** the community cards region displays an Error State with a Retry button
**And** the KPI and New Request regions remain usable.

---

### AC-HOME-20 – Loading State

**Given** Home is loading
**When** the APIs have not returned
**Then** KPI cards and cards display Skeleton states
**And** Quick action buttons are disabled.

---

### AC-HOME-21 – New Request Card & Feature Requests Banner

**Given** there are 8 recent Feature Requests with `CATEGORY = BUG` or `ENHANCEMENT`
**When** the Community tab is rendered
**Then** the New Request card displays the 8 requests, newest first (`created_at DESC`)
**And** each item shows title, status badge, category, requester and date
**And** clicking an item opens the Request Detail Popup
**And** clicking "View All" opens Skill Management with the Request List Popup auto-opened (`?requests=open`).

**Given** there are no BUG/ENHANCEMENT Feature Requests
**When** the Community tab is rendered
**Then** the New Request card displays the empty state "No bug or enhancement requests yet."

**Given** the user is an ADMIN_USER with 3 system-wide requests in SUBMITTED / UNDER_REVIEW
**When** the Community tab is rendered
**Then** the Feature Requests banner displays `Feature Requests: 3`
**And** clicking Review Now opens Skill Management with the Request List Popup auto-opened.

**Given** the user is a NORMAL_USER
**When** the Community tab is rendered
**Then** the Feature Requests banner is not displayed
**And** the `pendingRequestReviews` field is not returned by the summary API.

---

# 11. Frontend UX Requirements

- Home is the landing screen after a successful Login.
- GNB is persistent across all post-login screens; it is not rebuilt per page.
- Exactly one sidebar item is active at a time.
- Brand click always navigates to Home.
- User Management item is hidden for NORMAL_USER.
- Home has two tabs: **Chart** (default) and **Community**.
- KPI cards display value + label; zero counts display `0`.
- Average Rating follows the `★ 4.6 (5)` / `No rating` display rule.
- Dates display per the user's date format setting (`../2. User Management module/Settings_SRS.md` SH-03-04 — enum `DD/MM/YYYY` / `MM/DD/YYYY` / `YYYY-MM-DD`, default `DD/MM/YYYY`, applied system-wide after Save).
- Skill Names longer than the card width are truncated with `...` + tooltip.
- Status is displayed with the Status Badge component.
- KPI grid: 6 columns on desktop (single row), 3 on tablet, 1 on mobile.
- The three community cards sit side by side on desktop and stack on mobile; Skill mới is the scrollable one.
- New Request and AI News sit side by side on desktop and stack on mobile.
- Quick actions (+ Add Skill, Skill Management) are on the greeting row, aligned to the far right.
- The Admin Review banner is a compact single-line alert (icon + title left, Review Now right).
- Each Home region (KPI, community cards, New Request, AI News, charts) loads independently with its own loading/empty/error state.
- Error states offer a Retry button.
- Quick action buttons are disabled while Home data is loading.
- Notification badge is capped at `99+`.
- The notification panel shows the latest 10 notifications with read/unread markers.
- All GNB interactive elements meet the 40×40px touch target below `lg`.
- Keyboard navigation: sidebar items and user menu are reachable and operable by keyboard with visible focus. Link-style actions (New Request "View All", AI News item titles — rendered as `<a>` without `href` in the demo) MUST be keyboard-operable in production: use `<button>` styled as a link, or `<a role="link" tabindex="0">` + Enter/Space handler, with visible focus ring.

---

# 12. DEV Checklists

## 12.1 Security

- [ ] All Home APIs require a valid session (401 otherwise).
- [ ] Home data is scoped to the logged-in user from the session — never from client input.
- [ ] Visibility Rule (Status + Scope) is enforced server-side for community skill cards.
- [ ] `pendingReviews` + `pendingRequestReviews` are returned only for ADMIN_USER.
- [ ] `/users` route returns 403 for NORMAL_USER at the API level.
- [ ] No Skill data of unauthorized users is ever returned by Home APIs.
- [ ] Technical errors are not exposed; business messages only.

## 12.2 Database

- [ ] `ai_news` table created — full DDL (9 cột: news_id, message_id, title, body (HTML), sender, source, created_at, archive_yn, imported_at + index) trong `AI_News_Batch.md` §6.1 (bảng do batch job sở hữu, Home chỉ đọc).
- [ ] Summary queries use indexed columns (creator, status, updated_at, view_count).
- [ ] Community-card queries apply visibility rules in the database layer; New Request queries filter `category IN (BUG, ENHANCEMENT)` in the database layer.
- [ ] Notification tables are owned by the Notification SRS, not this module.

## 12.3 API

- [ ] `GET /api/v1/home/summary`
- [ ] `GET /api/v1/home/community-skills`
- [ ] `GET /api/v1/home/new-requests`
- [ ] `GET /api/v1/home/ai-news`
- [ ] `GET /api/v1/notifications/unread-count`
- [ ] `GET /api/v1/notifications`
- [ ] `POST /api/v1/notifications/read-all`
- [ ] Standard success/error envelope used for all endpoints.

## 12.4 UX/UI Handoff

- [ ] GNB (Header + Sidebar) in all themes (Light/Dark)
- [ ] Sidebar item states: default / hover / active / focus
- [ ] User dropdown (name, Part · Group, My Settings, Logout)
- [ ] Notification bell + badge + panel
- [ ] Home tabs (Chart / Community)
- [ ] Chart filter bar (9 checkboxes) + 3-column chart grid
- [ ] Greeting & user summary with inline quick actions (far right)
- [ ] Compact single-line Admin Review Queue banner
- [ ] Compact single-line Admin Feature Requests banner (ADMIN_USER only)
- [ ] 6 KPI cards in a single row (value, label, skeleton, clickable affordance)
- [ ] Community cards: Skill Hot / Skill mới nổi / Skill mới (rows, empty state, scroll for Skill mới)
- [ ] New Request card (8 latest BUG/ENHANCEMENT requests, rows, empty state)
- [ ] AI News card (rows, empty state)
- [ ] Per-region loading / empty / error states
- [ ] Responsive behavior (mobile drawer, tablet collapse, desktop fixed)

---

# 13. QA Test Scope

## GNB

- GNB present on all 4 post-login screens
- Active item per route
- Brand click → Home
- User Management hidden for NORMAL_USER / visible for ADMIN_USER
- Direct `/users` as NORMAL_USER → 403 + Access Denied
- Unauthenticated route → Login redirect
- User menu: My Settings navigation, Logout
- Notification badge: 0 / 2 / 120 (99+)
- Notification panel: latest 10, read marker, click → read + Detail, mark all read
- Responsive: drawer (<md), collapsible (md…lg), fixed (≥lg), wide (≥xl)

## Home

- Landing after successful Login (Chart tab active by default)
- Tabs: Chart / Community switch correctly
- Chart tab: 9 checkboxes toggle each chart card; grid reflows
- Greeting with name / Part · Group / role
- Quick actions inline on greeting row (far right); Add Skill opens popup; Skill Management navigates
- KPI values: correct counts, zero counts, No rating case; 6 cards in a single row
- KPI click → Skill Management with pre-applied filter (each clickable card)
- Subscribed card not clickable
- Skill Hot: top 5 by view_count, ordering, visibility rule, empty state
- Skill mới nổi: top 5 by view_count within 14 days, visibility rule, empty state
- Skill mới: top 10 by created_at DESC, scrollable, visibility rule, empty state
- New Request: 8 latest BUG/ENHANCEMENT requests by created_at DESC, no per-user filter, item click → Request Detail Popup, "View All" → `?requests=open`, empty state
- AI News: all items for every user (no per-user filter), newest first, empty state
- Row click → Detail Popup + View Count +1
- Admin banner: compact single line, shown/hidden by role and count, Review Now navigation
- Admin Feature Requests banner: shown/hidden by role and `pendingRequestReviews`, Review Now → `?requests=open`
- `pendingReviews` + `pendingRequestReviews` omitted for NORMAL_USER
- Per-region error + retry; full error state
- Loading skeletons; quick actions disabled while loading

---

# 14. Version History

| Version | Status | Change |
|---|---|---|
| 1.0 | Baseline | Create Home Screen & GNB SRS |
| 1.1 | Updated | Removed Settings from Sidebar — Settings reachable only via user menu (avatar → My Settings) |
| 1.2 | Updated | Home reworked to 2 tabs (Chart / Community). Chart tab = 9 checkbox-toggled charts. Community tab = greeting + inline quick actions (far right), compact single-line Admin Review banner, 6 KPI cards in one row, community cards (Skill Hot / Skill mới nổi / Skill mới), Recent Updates + AI News. Replaced My Recent / Recently Updated lists and bottom quick-actions block. New `ai_news` table + `GET /api/v1/home/community-skills`, `GET /api/v1/home/recent-updates`, `GET /api/v1/home/ai-news` APIs. |
| 1.3 | Updated | Fixed tab name spelling: "Comuniti" → "Community" (i18n `home.tabCommunity` vi/en; ko already correct). |
| 1.4 | Updated | AI News: bỏ lọc theo category/part/group — mọi user thấy toàn bộ tin; `ai_news` bỏ cột `category`/`part`/`group`, thêm `sender` + `body` (HTML); API `GET /api/v1/home/ai-news` trả `sender` + `body` (HTML), không filter theo user; card AI News hiển thị sender thay vì category; timeline dot nhiều màu (rotating tone); **click title tin → popup xem nội dung email** (sender, ngày, body HTML) + endpoint `GET /api/v1/home/ai-news/{newsId}`. |
| 1.5 | Updated | AI News Detail Popup tách thành tài liệu riêng (`AI_News_Detail_Popup_Spec.md` + `AI_News_Detail_Popup_SRS.md`): §2.2 Out of Scope tham chiếu SRS popup; §5.8 rule 5 rút gọn thành tham chiếu; §7.4 list API **bỏ field `body`** (body có thể rất lớn — popup fetch riêng qua `GET /api/v1/home/ai-news/{newsId}`); §8 bảng `ai_news` bổ sung cột `message_id`. |
| 1.6 | Updated | Gộp 5 file BA Spec vào SRS (Spec đã xóa) — tham chiếu Out of Scope §2.2 giữ nguyên (đã trỏ SRS). |
| 1.7 | Updated | §5.8: 2 card Recent Updates / AI News **căn cột theo lưới 3 cột của hàng community phía trên** (Recent Updates thẳng cột với Hot Skills, AI News thẳng cột với Trending + New Skills; khoảng cách dọc = 14px — bằng gap ngang; mobile vẫn stack 1 cột) — khớp demo `.activity-ai-grid { grid-template-columns: repeat(3, minmax(0,1fr)); gap: 14px }` + `.ai-news-card { grid-column: span 2 }` + `.community-grid { margin-bottom: 14px }`. |
| 1.8 | Updated | Review cross-doc: §7.4 example `newsId` `"AN0001"` → `1024` (Number — khớp DDL `news_id` BIGSERIAL `AI_News_Batch.md` §6.1; demo mock `AN-xxx` là demo-only). |
| 1.9 | Updated | Review vòng 5: §11 date format tham chiếu Settings SH-03-04 (không hardcode DD/MM/YYYY); §5.8 card AI News description = sender + date (theo date format user); AC-HOME-15a 4 cách đóng popup (FR-09); §12.2 checklist `ai_news` tham chiếu DDL đầy đủ 9 cột (Batch §6.1). |
| 1.10 | Updated | Review vòng 6: (1) §5.8 card AI News — `sender` hiển thị **FULL** (`"Tên <email>"`, không tách tên) — khớp demo + API §7.4; (2) §5.8 rule 6 — card headers: AI News có subtitle i18n 3 ngôn ngữ, Recent Updates có link "Xem tất cả"; (3) demo fix theo SRS: Recent Updates áp Visibility Rule (`visible.value` — e2e B3a). |
| 1.11 | Updated | Review vòng 7 (L2): §5.8 rule 6 chuyển sang tiếng Anh (nhất quán rules 1-5) + cập nhật example subtitle khớp i18n vòng 6 ("... shows sender (name + email)"). |
| 1.12 | Updated | Review vòng 8: (1) **M2** — §5.8 rule 2 quy định text empty state của 2 card (i18n `home.noRecent`/`noAiNews` + 3 ngôn ngữ); (2) **M3** — §11 keyboard navigation: link-style actions (Recent Updates "View All", AI News titles — `<a>` không `href` trong demo) bắt buộc keyboard-operable trong production (`<button>` style link hoặc `<a role="link" tabindex="0">` + Enter/Space); (3) demo fix theo SRS: Recent Updates có empty state `PrismEmptyState` (e2e B3b code review). |
| 1.13 | Updated | Review vòng 9 (N1): §5.8 rule 4 tách tone theo card — **AI News** rotating tones (decorative); **Recent Updates** semantic tones theo status (DRAFT: neutral, PENDING_APPROVAL: warning, PUBLISHED: accent, REJECTED: danger) — khớp demo `Home.vue` (trước đó rule 4 ghi chung "rotating, not semantic" cho cả 2 card, mâu thuẫn demo Recent Updates). |
| 1.14 | Updated | Đồng bộ model Email (Login SRS v1.6): greeting/dropdown chuyển từ `ORGANIZATION` + User ID hiển thị sang **`Part · Group`** (session `PART`/`GROUP`, hidden khi NULL; `USER_ID` là UUID DB sinh — không hiển thị cho user). Chỗ sửa: §4.6 dropdown wireframe + header row, §5.2 wireframe greeting, §5.3 bảng Greeting, AC-GNB-07, AC-HOME-02, §12 checklist. Tab Chart/Community + 9 chart + AI News giữ nguyên. |
| 1.15 | Updated | Áp dụng cấu trúc SRS2 v1.1: **bỏ 2 tab (Chart / Community) + 9 chart** — Home là **single page** (greeting + quick actions, Admin Review banner, 6 KPI cards, community cards Hot/Trending/New, Recent Updates + AI News — card AI News giữ nguyên). |
| 1.16 | Updated | **Đưa lại 2 tab (Chart / Community) + 9 chart** (revert v1.15) — lấy cấu trúc tab + chart từ `SRS2/Home_Screen_SRS.md` v1.13; **giữ nguyên model Part · Group** (v1.14). Home trở lại 2 tab: Chart (default, 9 checkbox-toggled charts) + Community (greeting, 6 KPI, community cards, Recent Updates + AI News). |
| 1.17 | Updated | Tab Chart: (1) **sắp xếp 3 chart cùng hàng ngang đầu** — hàng 1 = Skill status (donut) + Personal metrics (radar) + Skill by creator & status (stacked bar); cập nhật wireframe §5.2 (checkbox + grid) + structure block; (2) **fix radar label bị cắt** — `PrismRadarChart` mở rộng viewBox thêm padding 50px để đủ chỗ hiển thị label trục (vd "Average Rating", "Subscribed", "Published") — sửa ở design system component dùng chung. Khớp demo `Home.vue`. |
| 1.18 | Updated | Tab Chart: thêm **info icon (i)** ở góc trên phải header của từng chart card — click → **popover** giải thích ý nghĩa chart (mỗi chart 1 text riêng, i18n 3 ngôn ngữ vi/en/ko; popover đóng khi click ngoài). Icon render cùng card nên ẩn/hiện theo checkbox. Chỗ sửa: §5.2 wireframe + bullet + structure block; demo: component `ChartInfoTip.vue` (PrismPopover + icon Info), i18n `home.chartInfo.*`, e2e G1a2 (4 checks). |
| 1.19 | Updated | Gộp từ `SRSPhase2/` vào `..` (thư mục Phase2 đã xóa): thêm lại **§7.7 Error Message Catalog** (ERR-HOME-001~010 theo region mới + ERR-NEWS-001~003 cho `NEWS-201`/`NEWS-101`/`NEWS-102`); đồng bộ `0. Error_Codes_Messages.md` + `sitemap.md`. Nội dung yêu cầu (1.0–1.18) không đổi. |
| 1.20 | Updated | **Thêm KPI thứ 7 "My Requests"** (đồng bộ `Request_Feature_SRS.md` BR-RF-026/SR-RF-040 + demo): KPI = số request **đang mở** của user (SUBMITTED/UNDER_REVIEW/REJECTED), click → Skill Management `?requests=open` (auto-mở Request List Popup); grid 6 → **7 cột** (7 → 3 → 1); max KPI 6 → 7. **Thêm Admin Feature Requests banner** (§5.5a, compact single line — đồng bộ BR-RF-027/SR-RF-041/042 + demo): ADMIN_USER only, `pendingRequestReviews > 0` (system-wide SUBMITTED/UNDER_REVIEW), Review Now → `?requests=open`. API `GET /api/v1/home/summary` thêm `myOpenRequests` (mọi user) + `pendingRequestReviews` (ADMIN only). Chỗ sửa: §2.1 (HOME-08/13/14), §5.2 wireframe + layout, §5.5a mới, §5.6 (KPI #7 + rules 6/7/8), §6 (BR-HOME-021/022), §7.1, §8, UC-HOME-002/005a, AC-HOME-03/21, §11, §12, §13. **Sửa design reference §1 + §4.8**: `design-system/patterns/pattern-library.md` + `layout-responsive.md` (không tồn tại) → `../../neon-design-system/docs/COMPONENTS.md` (PrismGrid) + demo `AppShell.vue` / `Home.vue` / `app.css`. |
| 1.21 | Updated | **Fix radar "Chỉ số cá nhân" label bị cắt/đè chữ** — implement đúng yêu cầu đã ghi ở v1.17 (trước đó component chưa được sửa): `PrismRadarChart.vue` (design system) mở rộng viewBox `size` → `size + 100` (padding 50px mỗi bên), tâm chart dịch `+50px` theo cả 2 trục; label trục (vd "Average Rating", "Subscribed", "Published") hiển thị đầy đủ, không bị cắt mép SVG. Chỗ sửa: `neon-design-system/src/components/PrismRadarChart.vue` (script `cx`/`cy` + template `viewBox`). Demo `Home.vue` không đổi. |
| 1.22 | Updated | **Bỏ KPI "My Requests" (KPI #7) + đổi card "Recent Updates" thành "New Request"**: (1) §5.6 — 6 KPI cards (bỏ KPI #7 My Requests, grid 7 → **6 cột** 6 → 3 → 1, max KPI 7 → 6); `myOpenRequests` giữ trong API (informational). (2) §5.8 — card **New Request**: 8 request mới nhất `CATEGORY = BUG` / `ENHANCEMENT` (mọi requester, không filter theo user), `created_at DESC` ties by `request_id`; mỗi item = title (link) + status badge + category + `requester · date`; click item → Request Detail Popup; "Xem tất cả" → Skill Management `?requests=open`; dot tone semantic theo request status (SUBMITTED: accent, UNDER_REVIEW: warning, APPROVED: success, REJECTED: danger, FULFILLED: teal, CLOSED: neutral); empty state i18n `home.noNewRequests`/`noNewRequestsDesc` (3 ngôn ngữ). (3) API §7.3 `GET /api/v1/home/recent-updates` → `GET /api/v1/home/new-requests?limit=8`. Chỗ sửa: §2.1 (HOME-08/10), §5.2 wireframe + layout, §5.6, §5.8, §6 (BR-HOME-013/014/021), §7.1/7.3, §8, UC-HOME-001/002/004/005a (+UC-HOME-004a mới), AC-HOME-03/08/14/19/21, §11, §12, §13. Demo: `Home.vue` (bỏ KPI #7, card New Request + mount `RequestDetailPopup`), `store.js` (`recentRequests(limit=8)`), `app.css` (`.new-req-*`, bỏ `.kpi-grid-7`), i18n `home.newRequests`/`noNewRequests`/`noNewRequestsDesc` (vi/en/ko). |
| 1.23 | Updated | **AI News Detail: popup → màn hình độc lập `#/ai-news`** (đồng bộ `AI_News_Screen_SRS.md` v1.7 — file `AI_News_Detail_Popup_SRS.md` đã đổi tên): click title tin trong card AI News (Community tab) giờ **điều hướng `#/ai-news?news={newsId}`** (không mở popup); màn hình AI News có **menu GNB riêng ngay dưới Home** (icon Newspaper, active state), layout 2-pane (list tin + detail email). Chỗ sửa: §2.2 Out of Scope (tham chiếu `AI_News_Screen_SRS.md`), §5.8 rule 5 (click title → điều hướng màn hình), §7.4 (màn hình chi tiết fetch body), §7.7 ERR-NEWS-001 (AI News Screen), AC-HOME-15a (navigate thay vì mở/đóng popup). |
| 1.24 | Updated | **Đối chiếu codebase thật + data thật (DEV/PROD)**: (1) **API prefix** — mọi endpoint Home/Notification sửa `/api/home/*` → **`/api/v1/home/*`**, `/api/notifications*` → **`/api/v1/notifications*`** (khớp `@RequestMapping` thật + axios `baseURL: '/api/v1'`); (2) **Skill status** — `PUBLISHED` → **`APPROVED`** (enum thật `Skill.java`: DRAFT/PENDING_APPROVAL/APPROVED/REJECTED — "Published" là UI label) ở §5.6 KPI #2, §7.1 field `myPublished`, §7.2 example; (3) **§7.1 thêm field `myDraft`** (11 field — khớp `HomeSummary` record thật); (4) **Error code** — `HOME-001` không tồn tại trong code → §7 ghi chú + §7.1 Error Codes đổi sang `INTERNAL_ERROR` (generic handler), catalog §7.7 giữ làm frontend message ID; (5) **§7.3 ghi chú** module Feature Request chưa implement (endpoint trả `[]`, `myOpenRequests`/`pendingRequestReviews` = 0); (6) **§5.8 rule 5 + AC-HOME-15a** — `#/ai-news?news={newsId}` → **`/ai-news?news={newsId}`** (router thật dùng `createWebHistory`, không hash). |
