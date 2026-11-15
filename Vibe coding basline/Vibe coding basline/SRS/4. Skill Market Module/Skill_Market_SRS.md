# AI SKILL MANAGEMENT

## Skill Market (Skill Request) Screen – Software Requirements Specification

**Document Version:** 2.0
**Status:** Updated (2026-10-19)
**Module:** Skill Market / Skill Request Management
**Audience:** PO / BA / UX/UI / Developer / QA / DB Developer

> **v2.0 changes (2026-10-19):** module renamed Skill Forum → **Skill Market** (routes `/skill-market`); request **edit/delete** by creator; **comment edit** by author; **chronological** comment ordering (ranking removed); **icon-only** comment actions; **no length limits** on description/comments; **no image lightbox** (all files as download rows); **resolved-skill chips** on the post; **creator cannot link a skill to their own request**; **notifications** to the request creator (in-app bell + email); **optimistic UI** for vote/like; **feed auto-refresh** on mount; sidebar **Top Contributors** (was Top Requesters); **Top Voted rank colors**; detail screen **variant B** layout (flat post, composer at top of comments, timeline comments).

---

# 1. Document Purpose

This document defines the functional requirements, business rules, UI behavior, API specifications, and acceptance criteria for the **Skill Market** module — a community feed where users post **Skill Requests** (skills they need), vote on them, comment, attach files, link skills in comments, and mark skill-linked comments as resolved.

The module consists of:

- **Skill Market Feed Screen** (`/skill-market`) — ranked feed of skill request posts + composer + search/filter + sidebar stats.
- **Add Request Popup** — create a new request with optional file attachments (multipart upload).
- **Edit Request Popup** — creator edits their own request (title, description, hashtags, add/remove files).
- **Request Detail Screen** (`/skill-market/:id`) — flat post view + comments (timeline, chronological) + composer at the top of the comments + stats sidebar.
- **Comment system** — post / reply (max depth 3) / like / edit / delete / mark-resolved, with a skill-link chip on comments.

Design reference:

| Item | Reference |
|---|---|
| App Shell (Header + Sidebar) | `../5. Home module/Home_Screen_SRS.md` §4 (GNB) |
| Design system | "Prism" (CSS custom properties `--ds-*`), dark theme |
| Layout & Responsive | breakpoints: ≤1199px / ≤999px / ≤600px |
| GNB entry | Sidebar item "Skill Market" → `/skill-market` (visible to all authenticated users) |

**Naming note:** the user-facing menu is "Skill Market", but the domain entity remains **Skill Request** — frontend file names (`SkillRequestDetailView.vue`, …) and API endpoints (`/api/v1/skill-requests/...`) intentionally keep the entity-based names.

---

# 2. Scope

## 2.1 In Scope

| ID | Function |
|---|---|
| MARKET-01 | Skill request feed (ranked, cursor-paged, infinite scroll, auto-refresh on mount) |
| MARKET-02 | Feed filter chips (All / My requests) + debounced search (title / description / #hashtag) |
| MARKET-03 | Request feed card (post preview, files, resolved-skill chips, vote, comment preview) |
| MARKET-04 | Add Request Popup (title, description, hashtags, file upload, duplicate suggestions) |
| MARKET-05 | Edit Request Popup (creator only: title, description, hashtags, add/remove files) |
| MARKET-06 | Delete Request (creator only, confirm dialog) |
| MARKET-07 | File attachments (upload, all files as download rows — no image previews/lightbox) |
| MARKET-08 | Vote / unvote (toggle, optimistic UI) |
| MARKET-09 | Request Detail Screen (2-column: flat post + comments left, stats sidebar right) |
| MARKET-10 | Comments (post, reply, like/unlike, edit, delete, mark resolved, skill-link chip) |
| MARKET-11 | Comment ordering: **chronological** (oldest first) + pagination |
| MARKET-12 | Resolved-skill chips on the post (from resolved comments, with commenter) |
| MARKET-13 | Sidebar stats (Top Voted This Week / Top Contributors / Popular Hashtags) |
| MARKET-14 | Notifications to the request creator (in-app bell + email) |
| MARKET-15 | Rate limiting (create / vote / comment) |
| MARKET-16 | Screen states (loading / empty / error) |

## 2.2 Out of Scope

- Skill Management screen behavior — specified in `../1. Skill Management module/Skill_Management_Screen_Spec.md`.
- Skill Detail Popup behavior — specified in `../1. Skill Management module/Detail_Popup_SRS.md`.
- Admin moderation of requests (takedown, ban) — not in v1.0. **An admin cannot edit or delete another user's request** — only the request creator can.

---

# 3. Actors

| Actor | Description |
|---|---|
| NORMAL_USER | Can create requests, vote, comment, like, edit/delete own comments, edit/delete own requests, resolve comments on own requests |
| ADMIN_USER | Same capabilities as NORMAL_USER; additionally may delete any comment and resolve comments on any request. **Cannot** edit/delete someone else's request |
| System | Ranks the feed, enforces limits, stores attachments, sends notifications |

All skill requests are **visible to every authenticated user** — there is no scope/visibility concept (unlike Skills).

---

# 4. Skill Market Feed Screen

## 4.1 Purpose & Entry

- Route: `/skill-market` (name `skill-market`; sidebar item, all authenticated users).
- The screen is a **community feed**: posts are created by the composer, ranked by engagement × time decay, and loaded with infinite scroll.
- Detail view lives on a **separate route** (`/skill-market/:id`, name `skill-market-detail`), not a popup.
- **Auto-refresh:** the feed **always reloads page 1 on mount** — actions on the detail screen (votes, comments, resolved skills) change feed data, and the cached store would otherwise show stale numbers when the user clicks "Back to feed".

## 4.2 Screen Structure

```text
┌──────────────────────────────────────────────────────────────────────┐
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ (avatar)  [ What skill do you need? Share your idea…  ]          │ │ ← composer (opens Add Popup)
│ └──────────────────────────────────────────────────────────────────┘ │
│ [ All | My requests ]        [ 🔍 Search title, description, #tag… / ] │ ← chips + search
│                                                                      │
│ ┌──────────────────────────────────────────┐ ┌────────────────────┐ │
│ │ Request Feed Card            [⋮ creator] │ │ Top Voted This Week│ │
│ │  (avatar) Name · time  [✓ Resolved]      │ │  1. Title (v·c)    │ │ ← rank 1 amber
│ │  Title (click → detail)                  │ │  2. Title (v·c)    │ │ ← rank 2 cyan
│ │  Description (clamped 3 lines, more/less)│ │  3. Title (v·c)    │ │ ← rank 3 mint
│ │  #tag #tag                               │ │  … (10, scroll)    │ │
│ │  📄 file.pdf (1.2 MB)  [Download]        │ ├────────────────────┤ │ ← files on own row
│ │  ✓ Resolved: 🔗 Ten (knox) Skill [status]│ │ Top Contributors   │ │
│ │  👍 12   💬 5                           │ │  (avatar) Name     │ │
│ │  ── comments preview (max 2, read-only)  │ │  3 skills linked   │ │
│ │  (avatar) user · time  "comment…"        │ ├────────────────────┤ │
│ │  View all 5 comments →                   │ │ Popular Hashtags   │ │
│ └──────────────────────────────────────────┘ │  #tag 12  #tag 8   │ │
│            (spinner / "You're all caught up ✓") └────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

Layout: 2-column grid — feed (fluid) + sidebar (**360px**, gap 16px). Below `1199px` the sidebar stacks under the feed.

## 4.3 Composer

| Element | Behavior |
|---|---|
| Avatar | Logged-in user |
| Input pill | Placeholder "What skill do you need? Share your idea…" — click opens the **Add Request Popup** (§5) |

## 4.4 Toolbar (filter chips + search)

| Element | Behavior |
|---|---|
| Chip "All" | Feed of all requests (default active) |
| Chip "My requests" | Feed filtered to `createdBy = self` |
| Search box | Debounced 400ms; matches **title**, **description**, or **hashtag** (accepts both `tag` and `#tag`). Clear button (×) when non-empty |
| `/` shortcut | Pressing `/` (outside any input/textarea) focuses the search box |

Rules:

1. Changing a chip or the search keyword **resets the feed to page 1**.
2. Search is case-insensitive (server-side `lower()`).
3. Hashtag column is `jsonb`; the server casts it to text before matching (PostgreSQL has no `lower(jsonb)`).

## 4.5 Request Feed Card

| Element | Behavior |
|---|---|
| Header | Creator avatar + name + relative time ("just now" / "5 min ago" / "3 hours ago" / "2 days ago" / `DD/MM/YYYY` after 7 days) |
| Resolved badge | Green "✓ Resolved" badge next to the time when the request has ≥1 resolved comment |
| Kebab menu (⋮) | **Creator only**: Edit / Delete. Teleported to `<body>`, `position: fixed` from `getBoundingClientRect()`; closes on outside click, `Esc`, scroll, resize; `z-index: 1200` |
| Title | Clickable → navigates to the **Detail Screen** (`/skill-market/:id`) |
| Description | Sanitized HTML (whitelist: p, br, b, i, u, a[href], ul/ol/li, code, pre). Clamped to 3 lines with "more"/"less" toggle when >160 chars |
| Hashtags | Badge per tag (`#tag`), tone info |
| File attachments | **All files** (images included) as rows: `📄 fileName (size)` + **Download** button. No image grid, no thumbnails, no lightbox. Download via authenticated endpoint, original file name preserved |
| Resolved skills | When ≥1 comment is resolved: `✓ Resolved:` label + chips `🔗 Ten (knox_id) SkillName [status]` (max **3** chips, "+N more" overflow). Chip click: APPROVED skill → Skill Detail Popup; otherwise toast "This skill has not been approved yet. Please contact an admin." |
| Reaction bar | 👍 vote toggle (filled when voted) + 💬 comment count (click → detail screen) |
| Comments preview | **Read-only**: up to **2** most recent top-level comments (avatar, user, time, 2-line clamped body, resolved mark) + "View all N comments →" button → detail screen. **No** inline comment form, like, or reply on the card |

Rules:

1. Voting on the card is **optimistic**: the count and voted state flip **immediately**, roll back with an error toast if the API fails.
2. The card never allows commenting — all comment interaction happens on the Detail Screen.
3. File size format: `<1024` → `N B`; `<1MB` → `N.N KB`; else `N.N MB`.

## 4.6 Sidebar Stats (3 cards, one screen height, internal scroll)

| Card | Content |
|---|---|
| Top Voted This Week | **10** requests created in the **last 7 days**, `vote_count DESC` (ties by `comment_count DESC`, then newest). Row: rank # · title (2-line clamp) · `N votes · M comments`. **Rank colors:** #1 = warning (amber), #2 = accent (cyan), #3 = success (mint), rest = muted. Rank font 15px mono bold. Title click → detail screen |
| Top Contributors | **5** users with the most **skill links in comments** (all time). Row: avatar · name · `N skills linked` |
| Popular Hashtags | **10** hashtags by usage count on requests created in the **last 30 days**. Chip `#tag count` — **click applies the hashtag search** to the feed |

Rules:

1. The sidebar is sticky (desktop) and each card scrolls internally; on mobile the cards stack with a max-height scroll.
2. Stats refresh after each feed page load and after a request is created/edited/deleted.
3. Stats are non-critical: a failure leaves the cards empty (no error banner).

## 4.7 Feed Ranking & Paging

1. **Ranking score** = `(voteCount × 2 + commentCount × 1) × timeDecay(age)`:
   - age ≤ 2 days → decay 1.0
   - age ≤ 7 days → decay 0.5
   - age > 7 days → decay 0.1
   - Ties: `created_at DESC`, then `id DESC`.
2. **Cursor-based paging**: page size 10 (max 50). The cursor is a base64url-encoded `createdAt|id` of the last item of the previous page; the client passes `nextCursor` for the next page.
3. **Infinite scroll**: an IntersectionObserver sentinel (rootMargin 400px) triggers the next page.
4. End state: "You're all caught up ✓" when `hasMore = false`.

## 4.8 Feed Screen States

| State | Behavior |
|---|---|
| Loading (first page) | Spinner in the feed area |
| Empty | "No requests yet." / "Be the first to share the skill you need." |
| Error (no items) | "Unable to load the feed." + **Retry** button |
| Error (has items) | Inline error text + "Load more" retry button at the feed end |

---

# 5. Add Request Popup

## 5.1 Structure

```text
┌──────────────────────────────────────────────────────────────┐
│ Register new skill request                           [×]     │
├──────────────────────────────────────────────────────────────┤
│ Title *                                                    │
│ [____________________________________________]  0/100       │
│ ┌ Duplicate hint (optional) ──────────────────────────────┐ │
│ │ Similar skills: Java Spring Boot (Approved) …            │ │
│ │ Similar requests: "Need a Tibero migration guide" (12)   │ │
│ └──────────────────────────────────────────────────────────┘ │
│ Description (rich text, no length limit)                    │
│ [____________________________________________]              │
│ Attachments (images + files, max 10, each ≤ 10 MB)          │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │  ⬆  Click or drop files here                             │ │ ← upload dropzone
│ │  Files appear in the post as download links              │ │
│ └──────────────────────────────────────────────────────────┘ │
│  📄 file.pdf  1.2 MB  ×        ← pending rows (remove ×)    │
│ Hashtag                                                   │
│ [ tag1 × ] [ tag2 × ] [ + type and press Enter ]           │
│ ℹ Your request will be posted to the feed immediately.     │
├──────────────────────────────────────────────────────────────┤
│                                    [ Cancel ] [ Save ]       │
└──────────────────────────────────────────────────────────────┘
```

## 5.2 Fields & Validation

| Field | Required | Rule | Error |
|---|---|---|---|
| Title | ✓ | 1–100 chars (trimmed) | "Request title cannot be empty." / "Request title is at most 100 characters." |
| Description | – | Rich-text HTML, **no length limit** | – |
| Hashtags | – | Max **10** tags; each 1–30 chars; charset `[A-Za-z0-9_-]` only (stored without `#`) | "Hashtag is at most 30 characters." / "Hashtag contains only letters, digits, \"-\" and \"_\"." / "Maximum 10 hashtags." / "Hashtag already exists." |
| Files | – | Max **10** files; each ≤ **10 MB**; whitelist extensions (below) | "Maximum 10 attachments." / "\"name\" is larger than 10 MB." |

**Allowed extensions (client `accept` list):**

- Images: `jpg`, `jpeg`, `png`, `webp`, `gif`
- Documents & archives & code: `pdf`, `doc`, `docx`, `xls`, `xlsx`, `ppt`, `pptx`, `zip`, `7z`, `txt`, `md`, `json`, `csv`, `log`, `yaml`, `yml`, `py`, `js`, `ts`

## 5.3 Upload Behavior

1. The dropzone accepts drag & drop and click-to-browse (multi-select).
2. Pending files render as rows `📄 name (size) ×` — **no image thumbnails** (files appear in the post as download links).
3. Client-side pre-validation (count, size) before submit; server re-validates everything.
4. Submit sends a **multipart/form-data** POST: `title`, `description`, `hashtags` (JSON array string), `file[]`.
5. On success: popup closes, feed resets to page 1, sidebar stats refresh, toast "Skill request created successfully."
6. Closing with unsaved changes → "Discard changes?" confirm dialog.

## 5.4 Duplicate Suggestions (hint box)

While typing the title (debounced 400ms, min 3 chars), the popup may show a hint box with up to **5** similar Skills (name + status + creator) and **5** similar existing requests (title + vote count) for the keyword. The box is informational — it never blocks posting.

---

# 6. Edit & Delete Request (creator only)

## 6.1 Edit Request Popup

- Opened from the kebab menu (⋮) on the feed card or the detail post — **visible only to the request creator**.
- Same fields as the Add popup, pre-filled with the current values:
  - Title (1–100), Description (rich text, no limit), Hashtags (same rules).
  - **Attachments**: existing files listed with remove (×) / Undo; new files can be added (dropzone). Total stays ≤ 10, each ≤ 10 MB.
- Submit sends a **multipart PUT** `/{id}`: `title`, `description`, `hashtags`, `file[]` (new), `removeFileIds[]` (existing to delete).
- On success: toast "Skill request updated successfully.", the request reloads (detail) or the feed resets (feed).
- Backend enforces creator-only (`SKILL_REQUEST_NOT_EDITABLE` 403 otherwise).

## 6.2 Delete Request

- Kebab menu → Delete → confirm dialog: "Are you sure you want to delete \"…\"? This will permanently remove the request and all its votes, comments, and attachments."
- Backend: **creator only** (403 otherwise). Deletes the request, votes, comments, likes, and file rows (files on disk removed best-effort).
- After delete on the detail screen: toast + navigate back to the feed.

---

# 7. Request Detail Screen

## 7.1 Purpose & Entry

- Route: `/skill-market/:id` (name `skill-market-detail`). Reached from: feed card title, 💬 count, "View all comments →", notification bell deep-link.
- "Back to feed" button (top-left) → `/skill-market`.
- Invalid/non-numeric id → "Invalid request." empty state + back button.
- Navigating between two detail pages (id change) reloads the request.

## 7.2 Screen Structure (2 columns, variant B)

```text
┌──────────────────────────────────────────────────────────────────────┐
│ [← Back to feed]                                                     │
├───────────────────────────────────────────────┬──────────────────────┤
│ (avatar) Name · time   [✓ Resolved]      [⋮] │ Top Voted This Week  │
│ Title (h1, 22px, flat — no card)             │  1. …  2. …  3. …   │
│ Description (full, sanitized HTML)           ├──────────────────────┤
│ #tag #tag                                    │ Top Contributors     │
│ 📄 file.pdf (1.2 MB) [Download]              │  (avatar) Name       │
│ ✓ Resolved: 🔗 Ten (knox) Skill [status]     │  3 skills linked     │
│ 👍 12   💬 5                                 ├──────────────────────┤
│                                              │ Popular Hashtags     │
│ ── COMMENTS 5 ────────────────────────────── │  #tag 12  #tag 8    │
│ (avatar)[ Share your thoughts…  [Link skill] │                      │
│            [Post]        ← composer pill, TOP│                      │
│                                              │                      │
│ (avatar) user · time                         │                      │
│   comment body                               │                      │
│   🔗 SkillName [status]  [✓ Mark resolved]   │                      │
│   👍 2  💬  ✎  🗑      ← icon-only actions   │                      │
│   │ (reply indent 28px, left border)         │                      │
│   (avatar) reply · time                      │                      │
│   …reply body…                               │                      │
│   ── hairline between top-level comments ──  │                      │
│ (avatar) user2 · time                        │                      │
│   …                                          │                      │
│                                              │                      │
│              [ Load more comments ]          │                      │
└───────────────────────────────────────────────┴──────────────────────┘
```

| Column | Behavior |
|---|---|
| **Post (main)** | **Flat** (no card/glass border): header (avatar, name, time, Resolved badge, kebab), `h1` title 22px, full description (no clamp), hashtags, **files each on its own row** (`📄 name (size) Download`), resolved-skill chips, flat action bar (👍 vote toggle + 💬 count) |
| **Comments** | Below the post in the same column: "COMMENTS N" divider label, **composer pill at the TOP** of the comments, then the comment timeline |
| **Stats sidebar** | **360px** (synced with the feed screen), gap 16px, **border-left divider + padding-left 20px**; sticky on desktop; same 3 cards as the feed (§4.6). Below 1199px it stacks (divider removed) |

## 7.3 Detail Screen States

| State | Behavior |
|---|---|
| Loading | Skeleton shimmer (post + 3 comment rows) |
| Not found / error | "Unable to load the request." + **Retry** |
| Comments loading | 3 skeleton comment rows |
| Comments empty | "No comments yet." / "Be the first to share your thoughts." |

---

# 8. Comments

## 8.1 Comment Item (timeline style)

| Element | Behavior |
|---|---|
| Header | 26px avatar + user name + relative time |
| Body | Sanitized HTML (same whitelist as description), **no length limit** |
| Skill chip | When the comment links a Skill: chip `🔗 SkillName` + status badge. Click: if the skill is **APPROVED** → open Skill Detail Popup; otherwise toast "This skill has not been approved yet. Please contact an admin." |
| Resolved mark | "✓ Resolved" (green) when `resolvedYn = 'Y'` |
| Mark resolved | "✓ Mark resolved" button — **request creator or admin**, only on skill-linked comments that are not yet resolved |
| Actions | **Icon-only** (no text labels), in this order: **Like** (👍 + count) → **Reply** (💬, hidden at depth 3) → **Edit** (✎, own comments only) → **Delete** (🗑, own comments only; admin can delete any). Row shifted left (`margin-left: -10px`) so the first icon aligns with the comment text |
| Replies | Nested tree, **max depth 3**; reply indent `margin-left: 28px` + left border; replies sorted `created_at ASC` |
| Separators | Hairline **between top-level comments only** (not between a parent and its replies) |

## 8.2 Comment Ordering (top-level)

Top-level comments are ordered **chronologically — oldest first** (`created_at ASC`, ties by `id ASC`). There is **no** ranking by resolved status or interaction score.

Pagination is page-based (default size 10) over the chronological list; "Load more comments" appends the next page.

## 8.3 Comment Rules

| ID | Rule |
|---|---|
| BR-CMT-01 | Comment content: non-empty (trimmed); **no length limit** (rich-text HTML) |
| BR-CMT-02 | Reply depth is capped at **3** (a reply to a depth-3 comment is rejected) |
| BR-CMT-03 | A comment may link **at most 1** Skill, and the Skill must exist; linking a non-existent Skill is rejected |
| BR-CMT-04 | **The request creator cannot link a skill to their own request** (backend-enforced; the "Link skill" button is hidden in the composer for the creator) |
| BR-CMT-05 | Only the comment author (or an admin) can delete their comment (soft delete — `deleted_at` set, whole reply subtree) |
| BR-CMT-06 | **Edit**: the comment author can edit their own comment **anytime** (content only, non-empty); no length limit |
| BR-CMT-07 | Like is one-per-user-per-comment (toggle: like / unlike); 409 on double like, 404 on unlike without like |
| BR-CMT-08 | **Resolve**: only the **request creator** or an **admin** may mark a comment resolved; only skill-linked comments are resolvable; idempotent |
| BR-CMT-09 | A request shows the "✓ Resolved" badge when it has ≥1 resolved comment |
| BR-CMT-10 | `commentCount` on the request increments on create and decrements on delete (by subtree size) |
| BR-CMT-11 | After posting a comment/reply, the comment list reloads from page 1 |

## 8.4 Comment Composer (pill bar, top of the comments)

| Element | Behavior |
|---|---|
| Avatar | 26px, logged-in user |
| Editor | Rich-text editor, placeholder "Share your thoughts…", **no length limit** |
| Link skill | "Link skill" ghost button (opens the Link Skill Popup) — **hidden for the request creator** (BR-CMT-04). Selection shows a chip `🔗 Name [status]` with a remove (×) |
| Post | Disabled while empty or posting; on success clears the draft + linked skill, increments the count locally, reloads comments |
| Reply form | One at a time; opens inline under the target comment (Reply icon toggles); same editor; submits with `parentId` |
| Edit form | One at a time; opens inline under the target comment (Edit icon toggles); Save / Cancel |

## 8.5 Rate Limits

| Action | Limit |
|---|---|
| Create request | 5 per user per 60s |
| Vote | 10 per user per 60s |
| Comment / reply | 30 per user per 60s |

Exceeding a limit → HTTP 429, toast "Too many requests. Please try again later."

---

# 9. Attachments (files)

## 9.1 Storage

| Property | Value |
|---|---|
| On-disk path | `uploads/skill-requests/{requestId}/{uuid}.{ext}` (UUID prefix prevents collisions & path traversal) |
| DB record | `skill_request_files` (request_id FK CASCADE, file_name, file_path, file_size, is_image, created_at) |
| Access | Authenticated download endpoint only (original file name via `Content-Disposition`). The static resource handler for `/uploads/skill-requests/**` still exists but the UI never renders image URLs |
| Limits | ≤ 10 files per request; ≤ 10 MB per file; whitelist extensions (§5.2) |

## 9.2 Display

**All files — images included — render as plain rows** `📄 fileName (size)` + **Download** button, on both the feed card and the detail screen. No image grid, no thumbnails, no lightbox anywhere.

## 9.3 Download

- Files download through `GET /api/v1/skill-requests/{id}/files/{fileId}/download` (authenticated), returned as `application/octet-stream` with `Content-Disposition: attachment; filename="<original name>"`.

---

# 10. Notifications

When a user acts on someone else's request, the **request creator** is notified (the actor is never notified of their own action):

| Event | In-app title |
|---|---|
| Vote | "{actor} voted for your request \"{title}\"." |
| Top-level comment | "{actor} commented on your request \"{title}\"." |
| Reply to a comment | "{actor} replied to a comment on your request \"{title}\"." |

- **Channels:** in-app bell (notification row with `requestId` deep-link → detail screen) **and** email, each honoring the recipient's `UserSettings` (`inappNotify` / `emailNotify`; default both on).
- Notification failures are logged and never break the main action.
- Email subject prefix: "Skill Forum: …" (legacy string, kept).

---

# 11. Business Rules

| ID | Rule |
|---|---|
| BR-MKT-001 | All skill requests are visible to every authenticated user (no scope concept) |
| BR-MKT-002 | **Edit/delete a request: creator only** (403 otherwise); admin cannot edit/delete someone else's request |
| BR-MKT-003 | Feed ranking = `(vote×2 + comment×1) × timeDecay` (1.0 ≤2d / 0.5 ≤7d / 0.1 >7d); ties by `created_at DESC`, `id DESC` |
| BR-MKT-004 | Feed paging is cursor-based (base64url `createdAt\|id`); page size 10, max 50 |
| BR-MKT-005 | Feed **always reloads page 1 on mount** (back from detail shows fresh data) |
| BR-MKT-006 | Vote is one-per-user-per-request (toggle); 409 on double vote, 404 on unvote without vote |
| BR-MKT-007 | Search matches title OR description OR hashtag (case-insensitive; `#` prefix optional) |
| BR-MKT-008 | "My requests" filter = `createdBy = self` |
| BR-MKT-009 | Title 1–100 chars; **description: no length limit**; ≤ 10 hashtags of 1–30 chars `[A-Za-z0-9_-]` |
| BR-MKT-010 | Attachments: ≤ 10 files, ≤ 10 MB each, whitelist extensions; server re-validates client validation |
| BR-MKT-011 | **All files render as download rows** (no image grid/lightbox) |
| BR-MKT-012 | Comment ordering: **chronological, oldest first** (no ranking) |
| BR-MKT-013 | Comment depth max 3; content non-empty, **no length limit**; ≤ 1 linked Skill (must exist) |
| BR-MKT-014 | **Request creator cannot link a skill to their own request** |
| BR-MKT-015 | Delete comment: author or admin, soft delete (whole subtree) |
| BR-MKT-016 | Edit comment: author only, anytime, content only |
| BR-MKT-017 | Resolve comment: request creator or admin only; skill-linked comments only; idempotent |
| BR-MKT-018 | Request "Resolved" badge when ≥1 comment has `resolvedYn = 'Y'` |
| BR-MKT-019 | Rate limits: create 5/60s, vote 10/60s, comment 30/60s per user → 429 |
| BR-MKT-020 | Detail screen is a separate route (`/skill-market/:id`); feed cards show a read-only comment preview (max 2) + "View all N comments →" |
| BR-MKT-021 | Sidebar stats: top-10 voted (last 7 days), top-5 contributors (skill links in comments), top-10 hashtags (last 30 days); stats failure is non-critical (empty cards) |
| BR-MKT-022 | Resolved-skill chips on the post: max 3 shown + "+N more"; chip format `🔗 Ten (knox_id) SkillName [status]` |
| BR-MKT-023 | Notifications go to the **request creator only** (in-app + email per settings) on vote/comment/reply |
| BR-MKT-024 | Optimistic UI: vote (card + detail) and comment like flip immediately, roll back on error |

---

# 12. API Specification

Base path: `/api/v1/skill-requests` (entity-based; intentionally not renamed). All endpoints require a valid session (JWT). Standard envelope:

```json
{ "success": true, "data": { ... } }
```

Error responses:

```json
{ "success": false, "errorCode": "SKILL_REQUEST_NOT_FOUND", "message": "Business message." }
```

## 12.1 Feed

`GET /api/v1/skill-requests/feed?cursor=&limit=10&mine=false&title=`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 42,
        "title": "Need a Tibero migration guide",
        "description": "<p>We are migrating…</p>",
        "hashtags": ["tibero", "migration"],
        "voteCount": 12,
        "commentCount": 5,
        "creatorId": 7,
        "creatorName": "Nguyen Van A",
        "createdAt": "2026-10-18T09:30:00Z",
        "hasVoted": false,
        "isRequestCreator": false,
        "hasResolvedComment": true,
        "resolvedSkills": [
          {
            "skillId": 5, "skillName": "Tibero Migration", "skillStatus": "APPROVED",
            "commenterName": "Le Thi B", "commenterKnoxId": "ltb0123"
          }
        ],
        "resolvedSkillCount": 2,
        "topComments": [
          {
            "id": 900, "userId": 3, "userName": "Le Thi B",
            "content": "I can help with this", "createdAt": "2026-10-18T10:00:00Z",
            "linkedSkillId": 5, "linkedSkillName": "Tibero Migration", "linkedSkillStatus": "APPROVED",
            "resolvedYn": "Y", "likeCount": 2, "likedByMe": false
          }
        ],
        "files": [
          { "id": 501, "fileName": "arch.png", "fileSize": 204800, "isImage": true, "url": null },
          { "id": 502, "fileName": "plan.pdf", "fileSize": 1258291, "isImage": false, "url": null }
        ]
      }
    ],
    "nextCursor": "MjAyNi0xMC0x…|41",
    "hasMore": true
  }
}
```

| Field | Notes |
|---|---|
| `resolvedSkills` | Skills linked in resolved comments (earliest first, **max 3**); includes `commenterName` / `commenterKnoxId` of the user who wrote the resolved comment |
| `resolvedSkillCount` | Total resolved skills (may exceed `resolvedSkills.length`) |
| `topComments` | Up to 2 most recent top-level comments (read-only preview) |
| `files[].url` | Legacy field — the UI does not use it (all files download via §12.11) |
| `nextCursor` | `null` when no more pages |

## 12.2 Create (multipart)

`POST /api/v1/skill-requests` — `Content-Type: multipart/form-data`

| Part | Type | Required |
|---|---|---|
| `title` | string | ✓ |
| `description` | string (rich-text HTML) | – |
| `hashtags` | JSON array string, e.g. `["tibero","migration"]` | – |
| `file` | binary (repeatable) | – |

Success: `201` + `{ "success": true, "data": 42 }` (new request id).

## 12.3 Detail

`GET /api/v1/skill-requests/{id}` → `200` with a single feed item (same shape as §12.1 item). `404` when not found.

## 12.4 Update (creator only, multipart)

`PUT /api/v1/skill-requests/{id}` — `Content-Type: multipart/form-data`

| Part | Type | Required |
|---|---|---|
| `title` | string | ✓ |
| `description` | string | – |
| `hashtags` | JSON array string | – |
| `file` | binary (repeatable, new files) | – |
| `removeFileIds` | number (repeatable, existing files to delete) | – |

Success: `200` + `{ "success": true, "data": 42 }`. Errors: 403 `SKILL_REQUEST_NOT_EDITABLE` (not creator), 404, 400 (title/hashtag/file validation).

## 12.5 Delete (creator only)

`DELETE /api/v1/skill-requests/{id}` → `204`. Errors: 403 (not creator), 404.

## 12.6 Vote / Unvote

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/v1/skill-requests/{id}/vote` | 201 | 409 `SKILL_REQUEST_ALREADY_VOTED`, 404, 429 |
| DELETE | `/api/v1/skill-requests/{id}/vote` | 204 | 404 (not voted / not found), 429 |

## 12.7 Comments List (chronological)

`GET /api/v1/skill-requests/{id}/comments?page=1&size=10`

```json
{
  "success": true,
  "data": {
    "totalCount": 23,
    "page": 1,
    "size": 10,
    "items": [
      {
        "id": 900, "userId": 3, "userName": "Le Thi B",
        "content": "I can help", "createdAt": "2026-10-18T10:00:00Z",
        "canDelete": true, "canEdit": true, "parentId": null,
        "linkedSkillId": 5, "linkedSkillName": "Tibero Migration", "linkedSkillStatus": "APPROVED",
        "resolvedYn": "Y", "resolvedAt": "2026-10-18T11:00:00Z",
        "likeCount": 2, "likedByMe": false,
        "replies": [ { "id": 901, "…": "nested, max depth 3" } ]
      }
    ]
  }
}
```

- `items` = top-level comments only, **chronological oldest first**; replies nested (max depth 3).
- `canDelete` = true for the viewer's own comments (admins: any). `canEdit` = true for the viewer's own comments.

## 12.8 Add Comment / Reply

`POST /api/v1/skill-requests/{id}/comments`

```json
{ "content": "Thanks!", "linkedSkillId": 5, "parentId": null }
```

| Field | Notes |
|---|---|
| `content` | non-empty (trimmed), required; **no length limit** |
| `linkedSkillId` | optional; must exist; **forbidden for the request creator on their own request** |
| `parentId` | optional; reply target (depth cap 3) |

Success `201`. Errors: 400 empty/depth exceeded/creator-linking-own, 404 request not found, 429 rate limited.

## 12.9 Update Comment (author only)

`PUT /api/v1/skill-requests/{id}/comments/{commentId}` — body `{ "content": "…" }`

Success `204`. Errors: 403 `SKILL_REQUEST_NOT_EDITABLE` (not author), 400 `SKILL_REQUEST_COMMENT_EMPTY`, 404.

## 12.10 Comment Like / Unlike / Delete / Resolve

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/{id}/comments/{commentId}/like` | 201 | 409 `SKILL_REQUEST_ALREADY_LIKED` |
| DELETE | `/{id}/comments/{commentId}/like` | 204 | 404 (not liked) |
| DELETE | `/{id}/comments/{commentId}` | 204 | 403 (not author/admin), 404 |
| PUT | `/{id}/comments/{commentId}/resolve` | 204 (idempotent) | 403 (not creator/admin), 400 (not skill-linked), 404 |

## 12.11 File Download

`GET /api/v1/skill-requests/{id}/files/{fileId}/download`

- `200` + `Content-Type: application/octet-stream` + `Content-Disposition: attachment; filename="<original name>"`
- `404` when the file does not belong to the request.

## 12.12 Duplicate Suggestions

`GET /api/v1/skill-requests/suggestions?keyword=`

```json
{
  "success": true,
  "data": {
    "skills": [ { "id": 5, "name": "Tibero Migration", "status": "APPROVED", "creatorName": "Nguyen Van A" } ],
    "requests": [ { "id": 42, "title": "Need a Tibero migration guide", "voteCount": 12 } ]
  }
}
```

Max 5 items per list.

## 12.13 Sidebar Stats

`GET /api/v1/skill-requests/stats`

```json
{
  "success": true,
  "data": {
    "topVoted": [ { "id": 42, "title": "…", "voteCount": 12, "commentCount": 5 } ],
    "topContributors": [ { "userId": 7, "userName": "Nguyen Van A", "userAvatarUrl": null, "skillLinkCount": 3 } ],
    "popularHashtags": [ { "tag": "tibero", "count": 12 } ]
  }
}
```

- `topVoted`: 10, created in the last 7 days.
- `topContributors`: 5, by skill links in comments.
- `popularHashtags`: 10, from requests created in the last 30 days.

## 12.14 Error Code Catalog

| Error Code | HTTP | Message (frontend) |
|---|---|---|
| `SKILL_REQUEST_NOT_FOUND` | 404 | "Skill request not found." |
| `SKILL_REQUEST_TITLE_REQUIRED` | 400 | "Request title cannot be empty." |
| `SKILL_REQUEST_TITLE_TOO_LONG` | 400 | "Request title is at most 100 characters." |
| `SKILL_REQUEST_HASHTAG_INVALID` | 400 | "Hashtags must be a JSON array of strings." / hashtag charset errors |
| `SKILL_REQUEST_HASHTAG_TOO_LONG` | 400 | "Each hashtag is at most 30 characters." |
| `SKILL_REQUEST_HASHTAGS_TOO_MANY` | 400 | "At most 10 hashtags are allowed." |
| `SKILL_REQUEST_ALREADY_VOTED` | 409 | "You have already voted for this request." |
| `SKILL_REQUEST_NOT_EDITABLE` | 403 | "You do not have permission to perform this action." (edit/delete request, edit/delete comment, resolve) |
| `SKILL_REQUEST_COMMENT_EMPTY` | 400 | "Comment cannot be empty." |
| `SKILL_REQUEST_COMMENT_NOT_FOUND` | 404 | "Comment not found." |
| `SKILL_REQUEST_COMMENT_DEPTH_EXCEEDED` | 400 | "Replies are limited to 3 levels." |
| `SKILL_REQUEST_ALREADY_LIKED` | 409 | "You have already liked this comment." |
| `SKILL_REQUEST_LINK_INVALID` | 400 | "Linked skill not found." / "The request creator cannot link a skill to their own request." |
| `SKILL_REQUEST_RATE_LIMITED` | 429 | "Too many requests. Please try again later." |
| `SKILL_REQUEST_FILE_INVALID_TYPE` | 400 | "File type is not allowed." |
| `SKILL_REQUEST_FILE_TOO_LARGE` | 413 | "File is too large (max 10 MB)." |
| `SKILL_REQUEST_FILE_NOT_FOUND` | 404 | "File not found." |
| `SKILL_REQUEST_FILES_TOO_MANY` | 400 | "At most 10 attachments are allowed." |
| `AUTH-401` | 401 | "Your session has expired. Please log in again." → redirect to Login |
| `INTERNAL_ERROR` | 500 | "Something went wrong. Please try again." |

> Note: `SKILL_REQUEST_DESC_TOO_LONG` and `SKILL_REQUEST_COMMENT_TOO_LONG` still exist in the enum for backward compatibility but are **no longer thrown** (no length limits).

---

# 13. Data Dictionary

| Table | Columns | Description |
|---|---|---|
| `skill_requests` | `id` PK, `title` (≤100), `description` (TEXT, nullable, **no limit**), `hashtags` (jsonb), `vote_count` int default 0, `comment_count` int default 0, `created_by` FK user, `created_at` | The request post (editable/deletable by creator) |
| `skill_request_votes` | `request_id` + `user_id` composite PK, `created_at` | One vote per user per request |
| `skill_request_comments` | `id` PK, `request_id` FK, `user_id` FK, `parent_id` (self-FK, nullable), `content` (TEXT), `linked_skill_id` FK nullable, `resolved_yn` ('Y'/'N'), `resolved_at`, `resolved_by`, `like_count` int default 0, `created_at`, `deleted_at` (soft delete) | Comments + replies (tree via `parent_id`). ⚠️ Legacy DB CHECK `char_length(content) <= 1000` still exists from V24 — the app no longer enforces a limit; the constraint should be dropped in a future migration |
| `skill_request_comment_likes` | `comment_id` + `user_id` composite PK, `created_at` | One like per user per comment |
| `skill_request_files` | `id` PK, `request_id` FK (CASCADE), `file_name`, `file_path` (relative, `{requestId}/{uuid}.{ext}`), `file_size` bigint, `is_image` bool, `created_at` | Attachments (rows cascade-delete with the request) |
| `notification` (shared) | `request_id`, `comment_id` columns (V40) | Forum notification deep-link target |

---

# 14. Use Cases

## UC-MKT-001 – Browse the Feed

1. User opens `/skill-market`.
2. System **always reloads page 1** of the ranked feed + sidebar stats on mount.
3. User scrolls; the sentinel triggers the next page (cursor).
4. End of feed shows "You're all caught up ✓".

## UC-MKT-002 – Search / Filter

1. User types in the search box (debounced 400ms) or clicks a chip.
2. Feed resets to page 1 with the new filter.
3. Clicking a Popular Hashtag chip applies `#tag` as the search keyword.

## UC-MKT-003 – Post a Request with Attachments

1. User clicks the composer → Add Request Popup opens.
2. User fills title/description/hashtags, drops files (rows appear).
3. User clicks Save → multipart POST.
4. On success: popup closes, feed resets, stats refresh, toast "Skill request created successfully."
5. On validation error: inline/toast error, popup stays open.

## UC-MKT-004 – Download a File

1. User clicks **Download** next to a file row (feed card or detail).
2. System fetches the file via the authenticated download endpoint and saves it with the original file name.

## UC-MKT-005 – Edit / Delete Own Request

1. User (creator) opens the kebab menu (⋮) on their request card or detail post.
2. **Edit**: popup pre-filled; change title/description/hashtags, add/remove files → Save → toast + reload.
3. **Delete**: confirm dialog → Delete → toast; on the detail screen the user is returned to the feed.
4. Non-creators never see the kebab menu; the backend rejects with 403.

## UC-MKT-006 – Vote / Unvote

1. User clicks 👍 on a card or detail.
2. The vote flips **optimistically** (immediate UI update); on API failure it rolls back with an error toast.

## UC-MKT-007 – Open the Detail Screen

1. User clicks the title / 💬 count / "View all comments →" on a feed card, or a forum notification.
2. System navigates to `/skill-market/:id` and loads the request + first comment page.
3. "Back to feed" returns to `/skill-market` (feed reloads fresh).

## UC-MKT-008 – Comment & Reply

1. User types in the composer pill at the top of the comments (optionally links a Skill — the creator cannot) and posts.
2. Comments reload (chronological).
3. User clicks the Reply icon on a comment → inline reply form → post (depth ≤ 3).

## UC-MKT-009 – Like / Edit / Delete / Resolve a Comment

1. Like: icon toggle, optimistic update; 409 on double like.
2. Edit: own comments only, anytime; inline edit form → Save.
3. Delete: own comments only (admin: any); list reloads.
4. Resolve: request creator or admin on a skill-linked comment; the comment and request show the Resolved mark; the post gains a resolved-skill chip.

## UC-MKT-010 – Receive a Notification

1. Someone votes/comments/replies on the user's request.
2. The user gets an in-app bell notification (deep-link → detail screen) and/or an email, per their notification settings.

---

# 15. Acceptance Criteria

### AC-MKT-01 – Feed Ranking

**Given** request A (10 votes, 2 comments, 1 day old) and request B (20 votes, 0 comments, 30 days old)
**When** the feed loads
**Then** A ranks above B (score A = 22×1.0 = 22; score B = 40×0.1 = 4).

### AC-MKT-02 – Infinite Scroll

**Given** 25 requests exist
**When** the user scrolls to the sentinel
**Then** page 2 loads with `nextCursor`
**And** after page 3 the text "You're all caught up ✓" is shown.

### AC-MKT-03 – Feed Auto-Refresh

**Given** the user voted on a request from the detail screen
**When** the user clicks "Back to feed"
**Then** the feed reloads page 1 and shows the updated vote count.

### AC-MKT-04 – My Requests Filter

**Given** the user has 3 requests and the community has 20
**When** the user clicks "My requests"
**Then** only the user's 3 requests are shown.

### AC-MKT-05 – Search by Hashtag

**Given** a request with hashtags `["tibero"]`
**When** the user searches `#tibero` or `tibero`
**Then** the request is returned (case-insensitive).

### AC-MKT-06 – Create Validation

**Given** the Add Request Popup is open
**When** the user submits an empty title
**Then** "Request title cannot be empty." is shown and no request is created.

**Given** 11 files are selected
**When** the user adds them
**Then** "Maximum 10 attachments." is shown.

**Given** a 12 MB file is selected
**When** the user adds it
**Then** "\"name\" is larger than 10 MB." is shown.

### AC-MKT-07 – Attachments Render as Rows

**Given** a request with 3 images and 1 PDF
**When** its card renders
**Then** all 4 files appear as rows `📄 name (size)` + Download (no image grid, no lightbox)
**And** clicking Download saves the file with the original name.

### AC-MKT-08 – Vote Toggle (Optimistic)

**Given** the user has not voted on a request
**When** the user clicks 👍
**Then** the vote count increases by 1 and the icon is filled **immediately** (before the API returns)
**And** if the API fails, the state rolls back and an error toast is shown.

### AC-MKT-09 – Detail Screen Layout (Variant B)

**Given** the user opens `/skill-market/42`
**When** the screen renders
**Then** the post is flat (no card) in the left column with the composer pill at the top of the comments below it
**And** the stats sidebar is 360px wide with a left border divider
**And** "Back to feed" returns to `/skill-market`.

### AC-MKT-10 – Detail Loads Comments

**Given** a request with 5 comments
**When** the detail screen loads
**Then** the comments render (not empty)
**And** the "COMMENTS N" divider count matches `commentCount`.

### AC-MKT-11 – Comment Ordering (Chronological)

**Given** comments A (10:00), B (11:00), C (09:00), and C is resolved
**When** the comment list loads
**Then** the order is C → A → B (oldest first; resolved status does not affect order).

### AC-MKT-12 – Reply Depth Cap

**Given** a comment at depth 3
**When** the user attempts to reply to it
**Then** the reply is rejected with "Replies are limited to 3 levels." (the Reply icon is also hidden at depth 3).

### AC-MKT-13 – Delete Own Comment

**Given** the user authored comment X
**When** the user clicks the Delete icon
**Then** X (and its replies) disappear from the list and `commentCount` decreases.

**Given** the user did not author comment Y
**When** the user attempts to delete Y
**Then** a 403 error is shown and Y remains.

### AC-MKT-14 – Edit Own Comment

**Given** the user authored comment X
**When** the user clicks the Edit icon, changes the text, and saves
**Then** the comment content updates and "Comment updated." is shown
**And** other users do not see the Edit icon on X.

### AC-MKT-15 – Resolve Comment

**Given** the user is the request creator and a comment links an APPROVED skill
**When** the user clicks "Mark resolved"
**Then** the comment shows "✓ Resolved", the request shows the Resolved badge, and the post shows a resolved-skill chip `🔗 Ten (knox_id) SkillName [Approved]`.

**Given** the user is not the creator and not an admin
**When** the user attempts to resolve
**Then** a 403 error is shown.

### AC-MKT-16 – Creator Cannot Link Own Request

**Given** the user is the request creator
**When** the user opens the detail screen
**Then** the "Link skill" button is not shown in the composer
**And** a direct API call with `linkedSkillId` is rejected with 400 "The request creator cannot link a skill to their own request."

### AC-MKT-17 – Edit / Delete Own Request

**Given** the user created request R
**When** the user opens the kebab menu on R
**Then** Edit and Delete are available
**And** editing R updates the feed/detail and deleting R removes it (confirm dialog first).

**Given** the user did not create request S
**When** the user views S
**Then** no kebab menu is shown and API edit/delete return 403.

### AC-MKT-18 – Skill Chip on Non-Approved Skill

**Given** a comment links a skill with status PENDING_APPROVAL
**When** the user clicks the skill chip
**Then** the toast "This skill has not been approved yet. Please contact an admin." is shown and no popup opens.

### AC-MKT-19 – Rate Limit

**Given** the user has created 5 requests in the last 60 seconds
**When** the user posts a 6th
**Then** "Too many requests. Please try again later." is shown (429).

### AC-MKT-20 – Feed Card Comment Preview

**Given** a request with 5 comments
**When** its feed card renders
**Then** at most 2 comments are shown (read-only, no like/reply controls)
**And** the button reads "View all 5 comments →" and navigates to the detail screen.

### AC-MKT-21 – Screen States

**Given** the feed API fails with no items loaded
**When** the screen renders
**Then** "Unable to load the feed." + Retry is shown.

**Given** zero requests exist
**When** the screen renders
**Then** "No requests yet." / "Be the first to share the skill you need." is shown.

**Given** the request id in the URL is invalid
**When** the detail screen renders
**Then** "Invalid request." + "Back to feed" is shown.

### AC-MKT-22 – Sidebar Stats

**Given** the feed has loaded
**When** the sidebar renders
**Then** Top Voted This Week shows up to 10 rows (ranks #1 amber, #2 cyan, #3 mint), Top Contributors shows up to 5 rows (`N skills linked`), and Popular Hashtags shows clickable chips
**And** clicking a hashtag chip applies the hashtag search to the feed.

### AC-MKT-23 – Notification on Comment

**Given** user A created request R and user B comments on it
**When** B posts the comment
**Then** A receives an in-app notification "B commented on your request \"R title\"." (deep-link → detail) and an email (per A's settings)
**And** B receives no notification.

---

# 16. Frontend UX Requirements

- Feed cards, sidebar, and detail screen follow the Prism design system (dark theme, `--ds-*` tokens).
- Relative time format: "just now" / "N min ago" / "N hours ago" / "N days ago" (≤7d) / `DD/MM/YYYY` (>7d).
- Description & comment bodies render sanitized HTML only (whitelist: p, br, b, i, u, a[href], ul/ol/li, code, pre); no scripts, no event handlers, no iframes.
- **All files render as download rows** — no image tiles, no lightbox anywhere.
- Comment timeline: hairline between top-level comments only; reply indent 28px with left border; icon-only actions (Like → Reply → Edit → Delete).
- Composer is a thin pill bar at the **top** of the comments (avatar + editor + Link skill/Post buttons).
- Menus (kebab, bell) are teleported to `<body>` with `position: fixed`; close on outside click, `Esc`, scroll, resize; `z-index: 1200`.
- All interactive elements are keyboard-operable with visible focus; popups close on `Esc` (or a discard confirm when dirty).
- Optimistic UI: vote (card + detail) and comment like flip immediately, roll back on error.
- Toasts: error toasts for all failed mutations; success toasts on create/update/delete.
- Responsive: 2-column feed layout collapses below 1199px (sidebar stacks, divider removed).

---

# 17. DEV Checklists

## 17.1 Security

- [x] All `/api/v1/skill-requests/**` endpoints require a valid session (401 otherwise).
- [x] User identity always from the JWT session — never from client input.
- [x] Path traversal guard on file storage (UUID-prefixed names, normalized absolute path, prefix check).
- [x] Extension whitelist enforced server-side on upload.
- [x] File size limit (10 MB) enforced server-side.
- [x] Request edit/delete: creator check server-side.
- [x] Comment edit: author check server-side.
- [x] Comment delete: author/admin check server-side.
- [x] Comment resolve: creator/admin check server-side.
- [x] Creator-linking-own-request rejected server-side.
- [x] HTML sanitized (client-side on render) — no XSS.
- [x] Rate limiting enforced server-side (create/vote/comment).

## 17.2 Database

- [x] `skill_requests`, `skill_request_votes`, `skill_request_comments`, `skill_request_comment_likes`, `skill_request_files` tables (Flyway migrations V24, V38, V39).
- [x] `skill_request_files.request_id` FK ON DELETE CASCADE.
- [x] Indexes: `skill_request_comments(request_id)`, `skill_request_comments(parent_id)`, `skill_request_comments(linked_skill_id)`, `skill_requests(created_by)`.
- [x] `hashtags` column is `jsonb`; search casts to text — PostgreSQL has no `lower(jsonb)`.
- [x] Comment delete is soft (`deleted_at`), excluded from all read queries.
- [ ] **TODO:** drop the legacy `CHECK (char_length(content) <= 1000)` on `skill_request_comments` (comments have no length limit).

## 17.3 API

- [x] `GET /feed`, `POST` (multipart), `GET /{id}`, `PUT /{id}` (multipart, creator), `DELETE /{id}` (creator)
- [x] `POST|DELETE /{id}/vote`
- [x] `GET|POST /{id}/comments`, `PUT|DELETE /{id}/comments/{commentId}`, `PUT /{id}/comments/{commentId}/resolve`
- [x] `POST|DELETE /{id}/comments/{commentId}/like`
- [x] `GET /{id}/files/{fileId}/download`
- [x] `GET /suggestions`, `GET /stats`
- [x] Standard success/error envelope on all endpoints.

## 17.4 UX/UI Handoff

- [x] Feed screen (composer, chips, search, cards, 360px sidebar with Top Voted rank colors).
- [x] Add Request Popup (dropzone, file rows, duplicate hint).
- [x] Edit Request Popup (creator only, add/remove files).
- [x] Detail screen (variant B: flat post, composer pill at top, timeline comments, 360px sidebar with left divider).
- [x] Comment tree with skill chips, resolved marks, icon-only actions, inline reply/edit forms.
- [x] Resolved-skill chips on the post with commenter name + knox_id.
- [x] Loading / empty / error states per §4.8 / §7.3 / §15.
