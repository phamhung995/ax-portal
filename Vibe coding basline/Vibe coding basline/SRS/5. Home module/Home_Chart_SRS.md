# AI SKILL MANAGEMENT

## Home — Chart Tab – Software Requirements Specification

**Document Version:** 1.1
**Status:** New
**Module:** Home / Chart Tab
**Audience:** PO / BA / UX/UI / Developer / QA / DB Developer

---

# 1. Document Purpose

This document defines the functional requirements, business rules, UI behavior, API specifications, and acceptance criteria for the **Chart Tab** of the Home screen.

The Chart Tab is an **admin-only** analytics view that compares organizational units (Groups or Parts) across 5 metrics over a selectable time range. It replaces the previous Chart tab (9 toggleable charts, SRS v1.25 §5.2), which was removed on 2026-10-20 for re-implementation.

Design reference:

| Item | Reference |
|---|---|
| Layout (KPI tiles + 3 mini bar charts + radar grid) | HTML demo `demo-chart-tab/variant-C-small-multiples.html` (variant C — small multiples) |
| App Shell / Tabs | `Home_Screen_SRS.md` §4 (GNB) + PrismTabs |
| Chart components | `PrismBarChart` (vertical bars), `PrismRadarChart` (design system — read-only) |

---

# 2. Scope

## 2.1 In Scope

| ID | Function |
|---|---|
| CHART-01 | Chart tab visibility (ADMIN_USER only; Community is the default tab for all users) |
| CHART-02 | Filter bar: time range (1 month / 3 months / 6 months / 1 year) + dimension (Group / Part) + group selector (Part mode) |
| CHART-03 | 5 KPI tiles — system-wide totals of the 5 metrics |
| CHART-04 | 3 mini bar charts (vertical columns): Skill approved / Average rating / Total views |
| CHART-05 | Radar chart per Group/Part (5 axes, normalized) |
| CHART-06 | Backend endpoints: `GET /api/v1/home/chart-stats`, `GET /api/v1/home/chart-dimensions` |
| CHART-07 | Home screen states (loading / empty / error) for the Chart tab |

## 2.2 Out of Scope

- Trend over time (weekly/monthly breakdown) — the tab shows **one aggregate value per entity per selected range**, not a time series.
- Non-admin access (the tab is not rendered for NORMAL_USER).
- Persisting user filter preferences (filters reset to defaults on each visit: 1 year + Group).

---

# 3. Actors

| Actor | Description |
|---|---|
| ADMIN_USER | Sees the Chart tab and all its data |
| NORMAL_USER | Does NOT see the Chart tab (Community only) |
| System | Buckets users by organization, computes the 5 metrics per bucket |

---

# 4. UI Design

## 4.1 Tab Visibility (CHART-01)

- Home has two tabs: **Community** (default, all users) and **Chart** (ADMIN_USER only).
- NORMAL_USER sees only the Community tab; the Chart tab item is not rendered.
- ADMIN_USER sees both tabs; Community is active by default.

## 4.2 Filter Bar (CHART-02)

```text
┌──────────────────────────────────────────────────────────────────────────┐
│ Thời gian: (1 tháng | 3 tháng | 6 tháng | 1 năm)   Loại: (Group | Part)  │
│ [Group selector — Part mode only: ▼ Group A]                             │
└──────────────────────────────────────────────────────────────────────────┘
```

| Control | Type | Options | Behavior |
|---|---|---|---|
| Time range | Segmented buttons (radio) | `1 tháng` / `3 tháng` / `6 tháng` / `1 năm` (default) | Selects the look-back window: last 1 / 3 / 6 months or last 1 year. Changing it re-loads the data. |
| Dimension | Segmented buttons (toggle) | `Group` (default) / `Part` | Group → data per each Group. Part → shows a **Group selector** dropdown; data per each Part of the selected Group. |
| Group selector | Dropdown | All Groups (from `chart-dimensions`) | Visible **only in Part mode**. Selecting a Group re-loads the data for that Group's Parts. |

Rules:

1. Every filter change triggers a fresh `chart-stats` call (no client-side caching between ranges).
2. Part mode without a valid Group selection is not possible — the selector always has a default (first Group alphabetically).
3. Filters are local to the Chart tab; leaving the tab does not persist them.

## 4.3 KPI Tiles — System Totals (CHART-03)

A row of **5 KPI tiles** (5 cols → 3 → 2 on narrow screens), each showing the **total across all displayed entities** (all Groups, or all Parts of the selected Group) for the selected range:

| # | Tile | Value |
|---|---|---|
| 1 | Skill approved | SUM of `approvedSkills` |
| 2 | Điểm vote TB | Average of the entities' `avgRating` (entities with `null` excluded; all null → `No rating`), with a ★ suffix when a rating exists |
| 3 | Tổng views | SUM of `totalViews` |
| 4 | User hoạt động | SUM of `activeUsers` |
| 5 | User mới | SUM of `newUsers` |

Each tile shows a sub-line: `tổng {N} group` / `tổng {N} part` (count of displayed entities).

## 4.4 Mini Bar Charts (CHART-04)

A row of **3 mini charts** (3 cols → 1 on narrow screens). Each is a **vertical column chart** (one column per entity, real value on top of the column, entity name below):

| # | Chart | Columns | Title suffix |
|---|---|---|---|
| 1 | Skill approved | `approvedSkills` per entity | `· tổng {sum}` |
| 2 | Điểm vote TB | `avgRating` per entity (1 decimal) | `· Max 5★` |
| 3 | Tổng views | `totalViews` per entity | `· tổng {sum}` |

Rules:

1. Column height is proportional to the value (tallest column = 100%); zero values render a minimal stub.
2. Column color is consistent per entity across all charts and radars (fixed palette by entity index).
3. Long entity names are truncated with `...` + tooltip.
4. Empty data (no entities) → the chart area shows the empty state (§4.7).

## 4.5 Radar Charts (CHART-05)

A grid of **one radar chart per entity** — **3 per row**, an odd last row is **centered**, and all cells in a row have **equal height** (2 per row ≤1199px, 1 per row ≤600px). Each cell is a **profile card**: the radar is the **dominant element** — it fills the **full cell width** — with the **compact secondary list** of the 5 real values (thin % bar vs the leading entity) **below** the radar, wrapped in a centered row (variant F, de-emphasized: 10px text, 3px bars). Each radar has **5 axes = 5 metrics**, normalized to 0–100%:

| Axis | Normalization |
|---|---|
| Skill approved | `entity.approvedSkills / SUM(all entities' approvedSkills)` |
| Điểm vote TB | `entity.avgRating / 5` (scale of 5; null → 0) |
| Tổng views | `entity.totalViews / SUM(all entities' totalViews)` |
| User hoạt động | `entity.activeUsers / SUM(all entities' activeUsers)` |
| User mới | `entity.newUsers / SUM(all entities' newUsers)` |

Rules:

1. Each radar is titled with the entity name; the polygon color matches the entity's color in the mini charts.
2. A metric with total 0 across all entities → all entities get 0 on that axis (no division by zero).
3. The radar shows relative shape (profile); the 5 values next to it show the real numbers with a % bar vs the leading entity per metric (bar min width 3%).

## 4.6 Layout Order (Chart tab)

```text
[ Page Header ("Home") ]
[ Tabs: Community | Chart ]
[ Filter bar: Time range | Dimension | Group selector (Part mode) ]
[ 5 KPI tiles (one row) ]
[ 3 mini bar charts (one row) ]
[ Radar grid (one radar per entity) ]
```

## 4.7 States (CHART-07)

| State | Behavior |
|---|---|
| Loading | KPI tiles + charts render as Skeleton; filter bar disabled |
| Loaded | Data rendered |
| Empty (no entities / no org data) | Single empty state: "No organization data yet." (i18n 3 languages) |
| Error | Error state with a **Retry** button (re-calls `chart-stats` with the current filters) |

---

# 5. Business Rules

| ID | Rule |
|---|---|
| BR-CHART-001 | The Chart tab is visible to ADMIN_USER only; Community is the default tab for all users |
| BR-CHART-002 | All 5 metrics are computed **server-side** over the selected look-back window (1m / 3m / 6m / 1y) |
| BR-CHART-003 | **Skill approved** = count of not-deleted Skills with `approval_status = 'APPROVED'` and `updated_at` within the window, created by the bucket's users (the approval action refreshes `updated_at`) |
| BR-CHART-004 | **Điểm vote TB** = average of the per-skill average ratings of the Skills counted in BR-CHART-003 (skills without any rating are excluded); `null` when no such Skill has a rating; rounded to 1 decimal |
| BR-CHART-005 | **Tổng views** = count of `skill_view_log` rows with `viewed_at` within the window for Skills created by the bucket's users (all statuses, not deleted) |
| BR-CHART-006 | **User hoạt động** = count of distinct users of the bucket with ≥1 `login_management` row `login_result = 'SUCCESS'` and `login_at` within the window |
| BR-CHART-007 | **User mới** = count of users of the bucket whose `created_at` is within the window |
| BR-CHART-008 | **Bucketing** — a user belongs to a bucket via `organization_master` rows with `employed = 'Y'`, joined on `knox_id`: Group dimension → bucket = `group_name`; Part dimension (group G) → only users having ≥1 org row with `group_name = G` and `employed = 'Y'`, bucket = `part`. A user with several rows is counted in **each** matching bucket (intentional double-count). Users with no employed org row (or a blank dimension value) are **excluded** from the chart — every user is expected to exist in the org chart. |
| BR-CHART-009 | Only buckets with at least one user are returned by `chart-stats` |
| BR-CHART-010 | KPI tile totals = SUM over returned entities (Điểm vote TB tile = average of non-null entity ratings) |
| BR-CHART-011 | Radar normalization: count metrics → share of the total; rating → value / 5 |
| BR-CHART-012 | The Chart tab is read-only; no data is created or modified from it |

---

# 6. API Specification

All endpoints require a valid session (JWT) and **ADMIN_USER** (HTTP 403 otherwise). Standard envelope `{ success, data | errorCode, message }`.

## 6.1 Chart Dimensions (selector data)

### Endpoint

`GET /api/v1/home/chart-dimensions`

### Success Response

```json
{
  "success": true,
  "data": {
    "groups": ["A", "B", "C"],
    "partsByGroup": { "A": ["A1", "A2"], "B": ["B1", "B2", "B3"], "C": ["C1"] }
  }
}
```

| Field | Description |
|---|---|
| groups | Distinct non-blank `group_name` values (org rows `employed = 'Y'`), ordered A→Z |
| partsByGroup | Distinct non-blank `part` values per group (same filter), ordered A→Z |

## 6.2 Chart Stats

### Endpoint

`GET /api/v1/home/chart-stats?period={1m|3m|6m|1y}&dimension={group|part}&group={name}`

| Param | Required | Description |
|---|---|---|
| period | yes | `1m` / `3m` / `6m` / `1y` (anything else → 400 `BAD_REQUEST`) |
| dimension | yes | `group` / `part` (anything else → 400 `BAD_REQUEST`) |
| group | when `dimension=part` | Group name (blank/missing → 400 `BAD_REQUEST`) |

### Success Response

```json
{
  "success": true,
  "data": {
    "period": "1y",
    "dimension": "group",
    "group": null,
    "entities": [
      { "name": "A", "approvedSkills": 24, "avgRating": 4.2, "totalViews": 1240, "activeUsers": 18, "newUsers": 6 },
      { "name": "B", "approvedSkills": 31, "avgRating": 4.5, "totalViews": 1860, "activeUsers": 24, "newUsers": 9 }
    ]
  }
}
```

| Field | Type | Description |
|---|---|---|
| entities[].name | String | Group or Part name |
| entities[].approvedSkills | Integer | BR-CHART-003 |
| entities[].avgRating | Decimal (1 dp) / null | BR-CHART-004; `null` when no rated Skill |
| entities[].totalViews | Integer | BR-CHART-005 |
| entities[].activeUsers | Integer | BR-CHART-006 |
| entities[].newUsers | Integer | BR-CHART-007 |

Entities are ordered by name A→Z.

### Error Codes

| Code | Description |
|---|---|
| BAD_REQUEST | Unknown `period` / `dimension`, or missing `group` in Part mode (HTTP 400 — `IllegalArgumentException` handler) |
| INTERNAL_ERROR | Chart stats could not be loaded (HTTP 500 — generic handler) |
| AUTH-401 | Session missing or expired → redirect to Login |
| HTTP 403 | Non-admin caller |

## 6.3 Error Message Catalog

| ID | Message | Where |
|---|---|---|
| ERR-CHART-001 | "Unable to load chart data. Please try again." | Chart tab error state |
| ERR-CHART-002 | "No organization data yet." | Chart tab empty state |

---

# 7. Data Dictionary

No new tables. Data is derived from:

| Metric / Data | Source |
|---|---|
| Skill approved / Điểm vote TB | `skills_master` (approval_status, updated_at, created_by, deleted_yn) + `skill_ratings` |
| Tổng views | `skill_view_log` (viewed_at, skill_id) |
| User hoạt động | `login_management` (login_at, login_result, user_id) |
| User mới | `"user".created_at` |
| Bucketing (Group / Part) | `organization_master` (knox_id, part, group_name, employed) joined to `"user".knox_id` |

---

# 8. Use Cases

## UC-CHART-001 — View Chart Tab (Admin)

1. ADMIN_USER opens Home → Community tab is active by default.
2. User clicks the Chart tab.
3. System calls `GET /api/v1/home/chart-dimensions` (selector data) and `GET /api/v1/home/chart-stats?period=1y&dimension=group` (defaults) in parallel.
4. System renders the filter bar, 5 KPI tiles, 3 mini bar charts, and the radar grid.

## UC-CHART-002 — Change Time Range

1. User selects `3 tháng`.
2. System re-calls `chart-stats` with `period=3m` (dimension unchanged).
3. KPI tiles, mini charts, and radars re-render.

## UC-CHART-003 — Switch to Part Mode

1. User selects `Part`.
2. The Group selector appears (default: first Group).
3. System re-calls `chart-stats` with `dimension=part&group={default}`.
4. Data renders per Part of that Group. Selecting another Group re-loads.

---

# 9. Acceptance Criteria

### AC-CHART-01 — Tab Visibility

**Given** the user is a NORMAL_USER
**When** Home is rendered
**Then** only the Community tab is displayed (active)
**And** the Chart tab is not rendered.

**Given** the user is an ADMIN_USER
**When** Home is rendered
**Then** both tabs are displayed
**And** Community is active by default.

---

### AC-CHART-02 — Defaults

**Given** an ADMIN_USER opens the Chart tab
**When** the tab loads
**Then** the filter bar shows `1 năm` selected and `Group` selected
**And** the Group selector is hidden (Group mode).

---

### AC-CHART-03 — Group Mode Data

**Given** Groups A, B, C exist in `organization_master` (employed='Y')
**When** the Chart tab loads in Group mode, period 1 year
**Then** the KPI tiles show the system totals
**And** each mini chart has one column per Group
**And** the radar grid has one radar per Group.

---

### AC-CHART-04 — Part Mode Data

**Given** Group B has Parts B1, B2, B3
**When** the user selects `Part` and Group B
**Then** the data shows one column/radar per Part (B1, B2, B3)
**And** metrics are computed only from users of Group B.

---

### AC-CHART-05 — KPI Tiles

**Given** entities A (views 1240), B (views 1860), C (views 640)
**When** the Chart tab renders in Group mode
**Then** the Tổng views tile shows `3740`
**And** the sub-line shows `tổng 3 group`.

---

### AC-CHART-06 — Rating Tile & Chart

**Given** entity A has `avgRating = 4.2` and entity B has `avgRating = null`
**When** the Chart tab renders
**Then** the Điểm vote TB tile shows the average of non-null ratings (4.2)
**And** the rating mini chart shows a column for A (4.2) and a zero stub for B
**And** the tile shows `No rating` when all entities have null.

---

### AC-CHART-07 — Radar Normalization

**Given** total views = 200 and entity A1 = 50
**When** the radar for A1 renders
**Then** the Views axis of A1's radar extends to 25% (50/200)
**And** the rating axis of a radar extends to `avgRating / 5` (e.g. 4.5 → 90%).

---

### AC-CHART-08 — Error & Retry

**Given** the `chart-stats` API fails
**When** the Chart tab renders
**Then** an Error state with a Retry button is displayed
**And** clicking Retry re-calls the API with the current filters.

---

### AC-CHART-09 — Non-admin Direct Call

**Given** a NORMAL_USER session
**When** `GET /api/v1/home/chart-stats` is called directly
**Then** the API returns HTTP 403.

---

# 10. i18n (vi / en / ko)

All UI strings use the existing Home i18n module (`features/home/i18n.ts`), keys prefixed `home.chart.*` (the old v1.25 chart keys were removed on 2026-10-20 and re-added for this design).
