# AI SKILL MANAGEMENT

## 5. Home Module — Software Requirements Specification

**Document Version:** 1.26
**Status:** Updated
**Module:** 5. Home Module (Home Screen + Global Navigation Bar)
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
| Login → Home redirect | `../2. User Management Module/Login_User Register before login_SRS.md` §58 (Login Success → Home) |
| Chart tab (admin analytics) | `Home_Chart_SRS.md` (đồng folder — spec riêng cho tab Chart) |

---

# 2. Scope

## 2.1 In Scope

| ID | Function |
|---|---|
| HOME-01 | Global Navigation Bar (Header + Sidebar) |
| HOME-02 | Role-based navigation item visibility |
| HOME-03 | User menu (My Settings, VOC, Logout) |
| HOME-04 | Notification bell (unread count + notification list) |
| HOME-05 | Home screen landing after Login |
| HOME-06 | Home tabs (Community default / Chart admin-only) |
| HOME-07 | Greeting & user summary + inline quick actions |
| HOME-08 | KPI cards (6 cards, single row) |
| HOME-09 | Community skill cards (Most Viewer / Trending Skills / New Skills — APPROVED only) |
| HOME-10 | New Request card (5 latest Skill Market requests) + AI News (5 items) |
| HOME-11 | Chart tab (admin analytics — spec riêng trong `Home_Chart_SRS.md`) |
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
| 1 | Home | LayoutGrid | `/home` | All |
| 2 | AI News | Newspaper | `/ai-news` | All |
| 3 | Skill Management | Sparkles | `/skills` | All |
| 4 | Skill Market | Lightbulb | `/skill-market` | All |
| 5 | User Management | Users | `/users` | ADMIN_USER only |
| 6 | Organization Management | Building2 | `/organizations` | ADMIN_USER only |
| 7 | VOC Management | Inbox | `/feedback` | ADMIN_USER only |

> Sidebar là **sticky** (desktop ≥1200px): `position: sticky; top: var(--nx-app-header-height); height: calc(100vh - var(--nx-app-header-height)); overflow-y: auto` — không cuộn theo nội dung main. Mobile ≤1199px vẫn là off-canvas drawer.

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
│ AI News              │
│ Skill Management     │
│ Skill Market         │
└──────────────────────┘

ADMIN_USER GNB:
┌──────────────────────┐
│ Home                 │
│ AI News              │
│ Skill Management     │
│ Skill Market         │
│ User Management      │
│ Organization Mgmt    │
│ VOC Management       │
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
├─────────────────────────────┤
│ ⚙️  My Settings             │
│ 📥  VOC                     │
├─────────────────────────────┤
│ 🚪  Logout                  │
└─────────────────────────────┘
```

| Item | Behavior |
|---|---|
| Header (avatar + name) | Read-only display from session data |
| My Settings | Navigate to Settings screen |
| VOC | Opens the My VOC popup (VOC module) |
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

Home is organized into **two tabs** (PrismTabs): **Community** (default, all users) and **Chart** (**ADMIN_USER only** — spec riêng trong `Home_Chart_SRS.md`).

### Tab 1 — Community (default)

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Home                                                                 │
├──────────────────────────────────────────────────────────────────────┤
│ [ Community | Chart (admin only) ]                                   │
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
│ │ 👁 Most Viewer    [View] │ │ 📈 Trending Skills       │ │ ✨ New Skills││
│ │ 1. Java Spring Boot     │ │ Xem nhiều nhất 2 tuần    │ │ [View All] ││
│ │ 2. Python Data Analysis │ │ 1. Python Data Analysis │ │ 1. ...    ││
│ │ ... (5 rows, APPROVED)  │ │ ... (5 rows, APPROVED)  │ │ (5 rows,  ││
│ └─────────────────────────┘ └─────────────────────────┘ │  APPROVED)││
│                                                          └───────────┘│
│                                                                      │
│ ┌─────────────────────────┐ ┌─────────────────────────────────────────┐│
│ │ 👍 New Request [View All]│ │ 📰 AI News [View All]                   ││
│ │ 1. Title — creator·date │ │ ● Title — sender · date                 ││
│ │   👍 votes · 💬 comments │ │ ● Title — sender · date                 ││
│ │ ... (5 rows)            │ │ ... (5 items, timeline)                 ││
│ └─────────────────────────┘ └─────────────────────────────────────────┘│
│  (cột 1 — thẳng với Most Viewer) (cột 2–3 — thẳng với Trending + New)  │
└──────────────────────────────────────────────────────────────────────┘
```

Layout order (Community tab):

```text
[ Page Header ("Home") ]
[ Tabs: Community (default) | Chart (admin only) ]
[ Greeting + user summary  ──────────────  [ + Add Skill ][ Skill Management ] ]
[ Admin Review banner (ADMIN_USER only, compact single line) ]
[ Admin Feature Requests banner (ADMIN_USER only, when pendingRequestReviews > 0) ]
[ KPI Row (6 cards, one row: 6 cols → 3 → 1) ]
[ Three-column community row: Most Viewer | Trending Skills | New Skills ]
[ Two-card row aligned to the 3-col community grid: New Request | AI News (spans 2 cols) ]
```

### Tab 2 — Chart (ADMIN_USER only)

Tab phân tích theo tổ chức (Group/Part) — **chỉ hiển thị cho ADMIN_USER**. Layout: filter bar (period 1m/3m/6m/1y · Group/Part · Group combobox khi chọn Part) → 5 KPI tiles (system totals) → 3 mini column charts → radar grid (1 profile card per entity, 3/row, odd row centered). Chi tiết đầy đủ (5 metrics, normalization radar, API, states) trong **`Home_Chart_SRS.md`** — tài liệu này KHÔNG lặp lại.

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

## 5.7 Community Skill Cards (Most Viewer / Trending Skills / New Skills)

Three cards in a 3-column row (stack on mobile). All rows are clickable → Skill Detail (navigate to Skills page with `?detail={skillId}` — View Count +1 on the skills side). **All three cards list APPROVED skills only** (user decision 2026-10-21 — applies to every role, including ADMIN_USER).

| Card | Title icon | Selection | Row content | Header right |
|---|---|---|---|---|
| **Most Viewer** | Eye | 5 APPROVED Skills with the highest `view_count` (visible to user) | Rank # · Name · Creator · 👁 views · ★ rating | — |
| **Trending Skills** | TrendingUp | 5 APPROVED Skills with the highest `view_count` among Skills updated within the last 14 days (visible to user) | Rank # · Name · Creator · 👁 views · ★ rating | Subtitle "Most viewed in 2 weeks" (i18n `home.card.trendingSub`) |
| **New Skills** | Sparkles | 5 APPROVED Skills by `created_at DESC` (visible to user) | Rank # · Name · Creator · Created date · 👁 views · ★ rating | "View All" link → Skills page pre-filtered `Status = APPROVED` (auto-search) |

Rules:

1. All three lists are scoped to Skills **visible to the logged-in user** (Visibility Rule, enforced server-side) **AND `approval_status = 'APPROVED'`**.
2. Each card shows at most 5 rows (no internal scroll).
3. Each card shows an empty state when it has no items.
4. Ties are broken by `skill_id` (stable ordering).
5. Card titles carry a lucide icon (accent color) before the label (`.card-title-icon`).

## 5.8 New Request & AI News

Two cards aligned to the 3-column grid of the community row above (same tracks + 14px gap, vertical spacing 14px — equal to horizontal gap): **New Request** spans column 1 (aligned with Most Viewer), **AI News** spans columns 2–3 (aligned with Trending + New Skills); stack on mobile.

| Card | Title icon | Content |
|---|---|---|
| **New Request** | ThumbsUp | List of the **5 most recently created Skill Market requests** (all users — no per-user filter), `created_at DESC`, ties by `request_id`. Each item: **rank #** + **title** (link) + `creator · date` + **👍 votes** + **💬 comments**. Click item → Skill Market request detail (`/skill-market/{id}`). "View All" link → Skill Market screen (`/skill-market`) — **visible to all users** (route is not admin-only) |
| **AI News** | Newspaper | Timeline of the **5 newest** AI news items (all users see all items — no per-user filter). Each item: dot (rotating tone) + **title** (link) + `sender · date` (sender = full `"Tên <email>"` as returned by the API; date per user's date format — `../2. User Management Module/Settings_SRS.md` SH-03-04). Click title → AI News Screen. "View All" link → AI News screen (`/ai-news`) |

Rules:

1. AI News items are **not** filtered by user — every user sees all items (no category/part/group classification).
2. Both cards show an empty state when they have no items — **New Request**: "No requests yet" / "No skill requests yet." (i18n `home.noNewRequests`/`noNewRequestsDesc`); **AI News**: "No AI news" / "No AI news yet." (i18n `home.noAiNews`/`noAiNewsDesc`) — 3 ngôn ngữ vi/en/ko.
3. AI News data is populated by the batch job `AI-NEWS-IMPORT` (email → DB, body stored as HTML) — see `AI_News_Batch.md`.
4. **New Request** data comes from the **Skill Market module** (`skill_request` table) via `GET /api/v1/skill-requests/recent?limit=5` — NOT from the Feedback/VOC module (v1.25).
5. Dot tones: **AI News** uses **rotating tones** (accent/violet/success/warning/teal/rose) for visual variety — decorative, not semantic. **New Request** rows use the same rank-badge layout as the community skill cards (no dot tones).
6. Click item → **AI News Screen** (AI News: điều hướng `/ai-news?news={newsId}` — xem `AI_News_Screen_SRS.md`) hoặc **Skill Market request detail** (New Request: điều hướng `/skill-market/{id}`).
7. Card headers: both cards have a "View All" link on the right of the title (New Request → `/skill-market`, AI News → `/ai-news`). Card titles carry a lucide icon before the label (`.card-title-icon`).

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
| BR-HOME-010 | Most Viewer = 5 visible **APPROVED** Skills with the highest `view_count`, ties broken by `skill_id` |
| BR-HOME-011 | Trending Skills = 5 visible **APPROVED** Skills with the highest `view_count` among Skills updated within the last 14 days |
| BR-HOME-012 | New Skills = 5 visible **APPROVED** Skills by `created_at DESC` |
| BR-HOME-013 | The Visibility Rule (Status + Scope) is enforced server-side for all community skill cards; all three cards additionally filter `approval_status = 'APPROVED'` |
| BR-HOME-014 | Opening a Skill from Home (community cards) navigates to the Skills page with `?detail={skillId}`; View Count +1 happens on the skills side |
| BR-HOME-015 | The Admin Review Queue banner is visible only to ADMIN_USER when `pendingReviews > 0`; it renders as a compact single-line alert |
| BR-HOME-016 | `pendingReviews` counts PENDING_APPROVAL Skills across all creators (system-wide) |
| BR-HOME-017 | Home has two tabs: **Community** (default, all users) and **Chart** (**ADMIN_USER only**); NORMAL_USER never sees the Chart tab |
| BR-HOME-018 | The Chart tab (filter bar, 5 KPI tiles, 3 mini column charts, radar grid) is specified in `Home_Chart_SRS.md` |
| BR-HOME-019 | AI News is not filtered by user — all items are shown to every user, sorted newest first; each item displays the email `sender` (no category); the card shows the 5 newest items |
| BR-HOME-020 | Quick actions (+ Add Skill, Skill Management) are rendered on the greeting row, aligned to the far right |
| BR-HOME-021 | The New Request card shows the **5 most recently created Skill Market requests** (all users), `created_at DESC`, ties by `request_id`; each item shows rank, title, creator, date, vote count and comment count; clicking an item navigates to the request detail (`/skill-market/{id}`); "View All" (all users) navigates to the Skill Market screen (`/skill-market`) |
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
| myOpenRequests | Integer | Count of own open Feature Requests (`SUBMITTED` / `UNDER_REVIEW` / `REJECTED`) — informational only (no KPI card; the New Request card is backed by the `feedback` table, not Feature Requests) |
| pendingReviews | Integer | System-wide PENDING_APPROVAL count. **Returned only for ADMIN_USER; omitted for NORMAL_USER** |
| pendingRequestReviews | Integer | System-wide count of Feature Requests `SUBMITTED` / `UNDER_REVIEW`. **Returned only for ADMIN_USER; omitted for NORMAL_USER** |

### Error Codes

| Code | Description |
|---|---|
| INTERNAL_ERROR | Home summary could not be loaded (HTTP 500 — generic handler; không có code `HOME-001` trong `BusinessErrorCode`) |
| AUTH-401 | Session missing or expired → redirect to Login |

## 7.2 Community Skill Cards (Most Viewer / Trending / New)

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

- `hot` (Most Viewer): 5 visible **APPROVED** Skills, `view_count DESC`, ties broken by `skill_id`.
- `trending` (Trending Skills): 5 visible **APPROVED** Skills updated within the last 14 days, `view_count DESC`, ties broken by `skill_id`.
- `new` (New Skills): 5 visible **APPROVED** Skills, `created_at DESC`, ties broken by `skill_id`.
- Visibility Rule (Status + Scope) enforced server-side for all three lists, **plus `approval_status = 'APPROVED'` on every list**.
- `ratingScore` is `null` when the Skill has no rating → UI displays `No rating`.

## 7.3 New Request (Latest Skill Market Requests)

### Endpoint

`GET /api/v1/skill-requests/recent?limit=5`

> **Note (v1.26):** the New Request card no longer reads the Feedback/VOC table (v1.25). It reads the **Skill Market** `skill_request` table via the Skill Market module endpoint (owned by `Skill_Market_SRS.md`). The old `GET /api/v1/home/new-requests` endpoint is removed.

### Success Response

```json
{
  "success": true,
  "data": [
    {
      "id": 42,
      "title": "Cần skill tổng hợp báo cáo tự động",
      "creatorName": "Nguyen Nhung",
      "createdAt": "2026-10-14T11:00:00",
      "voteCount": 3,
      "commentCount": 2
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| id | Long | Skill request `id` (PK) |
| title | String | Request title |
| creatorName | String | Name of the user who created the request |
| createdAt | DateTime | Creation time |
| voteCount | Integer | Number of votes (👍) on the request |
| commentCount | Integer | Number of comments (💬) on the request |

### Rules

- 5 most recently created Skill Market requests, all users (no per-user filter), `created_at DESC`, ties broken by `request_id`.
- Clicking an item navigates to the request detail (`/skill-market/{id}`); "View All" navigates to `/skill-market`.
- Item shape is the same `SkillRequestFeedItem` used by the Skill Market feed.

## 7.4 AI News

### Endpoint

`GET /api/v1/home/ai-news?limit=5`

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
- The Home card requests `limit=5` (5 newest items).

## 7.4a Chart Tab (ADMIN_USER only)

The Chart tab's two endpoints are specified in `Home_Chart_SRS.md` §6:

- `GET /api/v1/home/chart-dimensions` → `{ groups, partsByGroup }` (selector data).
- `GET /api/v1/home/chart-stats?period=&dimension=&group=` → `{ period, dimension, group, entities[] }` (5 metrics per Group/Part).

Both are `@PreAuthorize("hasAuthority('ROLE_ADMIN_USER')")`. This document does not repeat their contract.

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
| ERR-HOME-007 | "No bug, suggestion, or question feedback yet." | — (empty state) | New Request |
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
| Most Viewer / Trending / New Skills | `skills_master` (APPROVED only) + `skill_ratings` + visibility rules |
| New Request | `skill_request` table (Skill Market module) — 5 newest, `created_at DESC` |
| AI News | `ai_news` (sender, source, created_at) |
| Chart tab (admin) | `skills_master`, `skill_ratings`, `skill_view_log`, `login_management`, `user`, `organization_master` — see `Home_Chart_SRS.md` §8 |
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
2. System redirects to Home (Community tab active by default; Chart tab rendered only for ADMIN_USER).
3. System identifies the user and role from the session.
4. System calls `GET /api/v1/home/summary`, `GET /api/v1/home/community-skills`, `GET /api/v1/skill-requests/recent?limit=5`, `GET /api/v1/home/ai-news?limit=5` in parallel.
5. System calls `GET /api/v1/notifications/unread-count` for the GNB badge.
6. System renders tabs, greeting + inline quick actions, KPIs, community cards, New Request, and AI News.
7. For ADMIN_USER only: System also calls `GET /api/v1/home/chart-dimensions` + `GET /api/v1/home/chart-stats` when the Chart tab is opened (see `Home_Chart_SRS.md`).

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
2. System displays the New Request card with the 5 most recently created Skill Market requests.
3. User clicks a request item.
4. System navigates to the Skill Market request detail (`/skill-market/{id}`).
5. User clicks "View All" (visible to all users).
6. The Skill Market screen (`/skill-market`) opens.

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

## UC-HOME-006 – View Chart Tab (ADMIN_USER only)

1. ADMIN_USER opens the Chart tab.
2. System loads selector data (`chart-dimensions`) and stats (`chart-stats`) for the default filters (period 1y, dimension Group).
3. System renders the filter bar, 5 KPI tiles, 3 mini column charts, and the radar grid.
4. Changing period / dimension / group re-fetches `chart-stats`.
5. Full behavior is specified in `Home_Chart_SRS.md`.

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
**Then** the `Community` tab is displayed and active by default
**And** the `Chart` tab is displayed **only for ADMIN_USER**
**And** for a NORMAL_USER, only the `Community` tab exists.

---

### AC-HOME-09 – Chart Tab (admin)

**Given** the user is an ADMIN_USER on the Chart tab
**When** the tab is rendered
**Then** the filter bar, 5 KPI tiles, 3 mini column charts, and radar grid are displayed
**And** changing period / dimension / group re-fetches the stats
**And** the full behavior is covered by `Home_Chart_SRS.md` acceptance criteria.

---

### AC-HOME-10 – Most Viewer

**Given** the user has visible APPROVED Skills with view counts
**When** the Community tab is rendered
**Then** Most Viewer displays the 5 visible APPROVED Skills with the highest `view_count`
**And** each row shows rank, name, creator, views, and rating.

---

### AC-HOME-11 – Trending Skills

**Given** the user has visible APPROVED Skills updated within the last 14 days
**When** the Community tab is rendered
**Then** Trending Skills displays the 5 visible APPROVED Skills with the highest `view_count` among Skills updated within the last 14 days.

---

### AC-HOME-12 – New Skills

**Given** the user has visible APPROVED Skills
**When** the Community tab is rendered
**Then** New Skills displays up to 5 visible APPROVED Skills by `created_at DESC`
**And** each row shows rank, name, creator, created date, views, and rating
**And** the "View All" link navigates to Skills pre-filtered by `Status = APPROVED`.

---

### AC-HOME-13 – Community Card Visibility

**Given** a Skill that is not visible to the user (Scope rule) OR is not APPROVED
**When** the Community tab is rendered
**Then** the Skill is NOT displayed in Most Viewer, Trending Skills, or New Skills.

---

### AC-HOME-14 – Row Click Opens Detail

**Given** the user is on the Community tab
**When** the user clicks a Skill row (community cards)
**Then** the Skills page opens with the Skill detail (`?detail={skillId}`)
**And** on success the View Count increases by 1.

---

### AC-HOME-15 – AI News

**Given** the user belongs to Part X and Group 1
**When** the Community tab is rendered
**Then** AI News displays the 5 newest items (no per-user filter)
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

**Given** there are 5 recently created Skill Market requests
**When** the Community tab is rendered
**Then** the New Request card displays the 5 requests, newest first (`created_at DESC`)
**And** each item shows rank, title, creator, date, vote count and comment count
**And** clicking an item navigates to the request detail (`/skill-market/{id}`)
**And** clicking "View All" (all users) navigates to the Skill Market screen (`/skill-market`).

**Given** there are no Skill Market requests
**When** the Community tab is rendered
**Then** the New Request card displays the empty state "No skill requests yet."

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
- Home has two tabs: **Community** (default, all users) and **Chart** (ADMIN_USER only).
- KPI cards display value + label; zero counts display `0`.
- Average Rating follows the `★ 4.6 (5)` / `No rating` display rule.
- Dates display per the user's date format setting (`../2. User Management Module/Settings_SRS.md` SH-03-04 — enum `DD/MM/YYYY` / `MM/DD/YYYY` / `YYYY-MM-DD`, default `DD/MM/YYYY`, applied system-wide after Save).
- Skill Names longer than the card width are truncated with `...` + tooltip.
- KPI grid: 6 columns on desktop (single row), 3 on tablet, 1 on mobile.
- The three community cards sit side by side on desktop and stack on mobile; each card shows at most 5 rows (no scroll).
- New Request and AI News sit side by side on desktop and stack on mobile.
- Community card titles and bottom-card titles carry a lucide icon (accent color) before the label.
- New Request items show rank + title + creator + date + 👍 votes + 💬 comments; the "View All" link is visible to **all users** (targets `/skill-market`).
- Quick actions (+ Add Skill, Skill Management) are on the greeting row, aligned to the far right.
- The Admin Review banner is a compact single-line alert (icon + title left, Review Now right).
- The Sidebar is sticky on desktop (does not scroll with main content); off-canvas drawer on mobile.
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
- [ ] Community-card queries apply visibility rules + `approval_status = 'APPROVED'` in the database layer; New Request reads the `skill_request` table (Skill Market module) sorted `created_at DESC` (ties by `id`).
- [ ] Chart tab queries (admin) — see `Home_Chart_SRS.md` §12.
- [ ] Notification tables are owned by the Notification SRS, not this module.

## 12.3 API

- [ ] `GET /api/v1/home/summary`
- [ ] `GET /api/v1/home/community-skills`
- [ ] `GET /api/v1/skill-requests/recent` (Skill Market module — New Request card)
- [ ] `GET /api/v1/home/ai-news`
- [ ] `GET /api/v1/home/chart-dimensions` (ADMIN only — `Home_Chart_SRS.md`)
- [ ] `GET /api/v1/home/chart-stats` (ADMIN only — `Home_Chart_SRS.md`)
- [ ] `GET /api/v1/notifications/unread-count`
- [ ] `GET /api/v1/notifications`
- [ ] `POST /api/v1/notifications/read-all`
- [ ] Standard success/error envelope used for all endpoints.

## 12.4 UX/UI Handoff

- [ ] GNB (Header + Sidebar) in all themes (Light/Dark)
- [ ] Sidebar item states: default / hover / active / focus
- [ ] Sidebar sticky on desktop (does not scroll with main content)
- [ ] User dropdown (name, My Settings, VOC, Logout)
- [ ] Notification bell + badge + panel
- [ ] Home tabs (Community default / Chart admin-only)
- [ ] Chart tab per `Home_Chart_SRS.md` (filter bar, KPI tiles, mini charts, radar grid)
- [ ] Greeting & user summary with inline quick actions (far right)
- [ ] Compact single-line Admin Review Queue banner
- [ ] Compact single-line Admin Feature Requests banner (ADMIN_USER only)
- [ ] 6 KPI cards in a single row (value, label, skeleton, clickable affordance)
- [ ] Community cards: Most Viewer / Trending Skills / New Skills (APPROVED only, 5 rows each, title icons, empty state)
- [ ] New Request card (5 latest Skill Market requests — rank + title + creator + date + votes + comments, "View All" all users)
- [ ] AI News card (5 items, timeline with rotating dot tones, "View All")
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

- Landing after successful Login (Community tab active by default)
- Tabs: Community (default) / Chart (admin-only) — NORMAL_USER sees only Community
- Chart tab: filter bar, 5 KPI tiles, 3 mini charts, radar grid (per `Home_Chart_SRS.md` QA scope)
- Greeting with name / Part · Group / role
- Quick actions inline on greeting row (far right); Add Skill opens popup; Skill Management navigates
- KPI values: correct counts, zero counts, No rating case; 6 cards in a single row
- KPI click → Skill Management with pre-applied filter (each clickable card)
- Subscribed card not clickable
- Most Viewer: top 5 APPROVED by view_count, ordering, visibility rule, empty state
- Trending Skills: top 5 APPROVED by view_count within 14 days, visibility rule, empty state
- New Skills: top 5 APPROVED by created_at DESC, visibility rule, empty state, View All → `/skills?status=APPROVED`
- Non-APPROVED skills never appear in the 3 community cards (test as ADMIN_USER too)
- New Request: 5 latest Skill Market requests by created_at DESC, no per-user filter, item shows rank + title + creator + date + votes + comments, item click → `/skill-market/{id}`, "View All" → `/skill-market` (all users), empty state
- AI News: 5 newest items for every user (no per-user filter), newest first, empty state
- Row click → Skills page detail + View Count +1
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
| 1.25 | Updated | **Card New Request đổi nguồn data từ Feature Request module (chưa implement) sang bảng `feedback` (VOC)**: (1) §5.8 — card **New Request**: 8 feedback mới nhất **mọi type** (`BUG` / `SUGGESTION` / `QUESTION`, mọi user, không filter theo user), `created_at DESC` ties by `feedback_id`; mỗi item = **content preview** (1 dòng, truncate `...` + tooltip, link) + **type badge** + **status badge** + `user · date`; click item → **Feedback Detail Popup** — **admin** mở `FeedbackDetailPopup` (data `GET /api/v1/admin/feedback/{id}`, có actions Mark In Review / Resolve / Ignore), **non-admin** (kể cả owner) mở `MyFeedbackPopup` read-only (data `GET /api/v1/feedback/{id}`); **"View All" → màn Feedback Management `/feedback` — chỉ render cho ADMIN_USER** (route admin-only); dot tone semantic theo feedback status (NEW: accent, IN_REVIEW: warning, RESOLVED: success, IGNORED: neutral); empty state "No feedback yet" / "No bug, suggestion, or question feedback yet." (i18n keys giữ `home.noNewRequests`/`noNewRequestsDesc`, 3 ngôn ngữ — **text code hiện vẫn là "No requests yet" / "No bug or enhancement requests yet." — cần cập nhật khi implement**); list API **không trả `screenshot`** (base64 multi-MB — chỉ fetch trong detail). (2) API §7.3 — shape response đổi sang feedback fields (`feedbackId`, `content`, `type`, `status`, `userName`, `createdAt`); xoá note "Feature Request module chưa implement" cho card (endpoint hiện trả `[]` — cần implement đọc bảng `feedback` theo spec). (3) §7.7 ERR-HOME-007 message cập nhật theo empty state mới. (4) §7.1 `myOpenRequests` — sửa description: **informational only**, card New Request KHÔNG còn lấy từ Feature Request (đã chuyển sang `feedback` table). (5) `pendingRequestReviews` (§7.1) + Admin Feature Requests banner (BR-HOME-022) **giữ nguyên** — vẫn gắn Feature Request module. Chỗ sửa: §2.1 (HOME-10), §5.2 wireframe, §5.8 (card table + rules 2/4/5/6/7), §6 (BR-HOME-021), §7.1, §7.3, §7.7, §8, UC-HOME-004a, AC-HOME-21, §11, §12.2, §13. |
| 1.26 | Updated | **Đồng bộ codebase thật (2026-10-21) + đổi tên module "5. Home module"**: (1) **Tab** — **Community = default (mọi user)**, **Chart = ADMIN_USER only** (spec riêng `Home_Chart_SRS.md` v1.1 — §7.4a tham chiếu 2 endpoint `GET /api/v1/home/chart-dimensions` + `GET /api/v1/home/chart-stats`); (2) **§5.7** — 3 card skill (Most Viewer / Trending Skills / New Skills) **chỉ skill APPROVED**, top 5, có icon trong title (Eye / TrendingUp / Sparkles); (3) **§5.8** — card **New Request** đổi nguồn data từ `feedback` (VOC) sang **Skill Market requests** (`GET /api/v1/skill-requests/recent?limit=5`, 5 request mới nhất mọi user, rank + title + `creator · date · 👍votes · 💬comments`; click → `/skill-market/:id`; "View All" → `/skill-market` — mọi user); card **AI News** giảm 8 → **5** items; (4) **§4.3** — sidebar **7 items** (thêm Skill Market + Organization Management) + sidebar **sticky** desktop ≥1200px; §4.6 user menu thêm item **VOC** (mở My VOC popup); (5) §6 BR rules, §7.2-7.4 API, §8 Data Dictionary cập nhật; UC/AC/checklist/QA scope cập nhật tương ứng. |
