# AI SKILL MANAGEMENT

## Skill Forum (Skill Request) Screen – Software Requirements Specification

**Document Version:** 1.0
**Status:** New
**Module:** Skill Forum / Skill Request Management
**Audience:** PO / BA / UX/UI / Developer / QA / DB Developer

---

# 1. Document Purpose

This document defines the functional requirements, business rules, UI behavior, API specifications, and acceptance criteria for the **Skill Forum** module — a community feed where users post **Skill Requests** (skills they need), vote on them, comment, attach files/images, and mark skill-linked comments as resolved.

The module consists of:

- **Skill Forum Feed Screen** (`/skill-requests`) — ranked feed of skill request posts + composer + search/filter + sidebar stats.
- **Add Request Popup** — create a new request with optional file/image attachments (multipart upload).
- **Request Detail Screen** (`/skill-requests/:id`) — full post view + comments sidebar (scrollable, ranked) + pinned comment composer.
- **Comment system** — post / reply (max depth 3) / like / delete / mark-resolved, with a skill-link chip on comments.

Design reference:

| Item | Reference |
|---|---|
| App Shell (Header + Sidebar) | `../home/Home_Screen_SRS.md` §4 (GNB) |
| Design system | "Prism" (CSS custom properties `--ds-*`), dark theme |
| Layout & Responsive | breakpoints: ≤1199px / ≤999px / ≤600px |
| GNB entry | Sidebar item "Skill Forum" → `/skill-requests` (visible to all authenticated users) |

---

# 2. Scope

## 2.1 In Scope

| ID | Function |
|---|---|
| FORUM-01 | Skill request feed (ranked, cursor-paged, infinite scroll) |
| FORUM-02 | Feed filter chips (All / My requests) + debounced search (title / description / #hashtag) |
| FORUM-03 | Request feed card (post preview, attachments, vote, comment preview) |
| FORUM-04 | Add Request Popup (title, description, hashtags, file/image upload, duplicate suggestions) |
| FORUM-05 | File/image attachments (upload, image grid + lightbox, non-image download links) |
| FORUM-06 | Vote / unvote (toggle) |
| FORUM-07 | Request Detail Screen (2-column: post left, comments sidebar right) |
| FORUM-08 | Comments (post, reply, like/unlike, delete, mark resolved, skill-link chip) |
| FORUM-09 | Comment ranking (resolved → interaction → newest) + pagination |
| FORUM-10 | Sidebar stats (Top Voted / Top Requesters / Popular Hashtags) |
| FORUM-11 | Rate limiting (create / vote / comment) |
| FORUM-12 | Screen states (loading / empty / error) |

## 2.2 Out of Scope

- Editing or deleting a **skill request post** — not supported. A posted request is immutable; **nobody can delete a skill request, not even an admin**. The detail screen is view + interact (vote/comment) only.
- Skill Management screen behavior — specified in `../1. Skill Management module/Skill_Management_Screen_Spec.md`.
- Skill Detail Popup behavior — specified in `../1. Skill Management module/Detail_Popup_SRS.md`.
- Notification generation on new comments/votes — separate Notification SRS.
- Admin moderation of requests (takedown, ban) — not in v1.0.

---

# 3. Actors

| Actor | Description |
|---|---|
| NORMAL_USER | Can create requests, vote, comment, like, delete own comments, resolve comments on own requests |
| ADMIN_USER | Same capabilities as NORMAL_USER (no extra moderation powers on requests in v1.0); may resolve comments on any request |
| System | Ranks feed and comments, enforces limits, stores attachments |

All skill requests are **visible to every authenticated user** — there is no scope/visibility concept (unlike Skills).

---

# 4. Skill Forum Feed Screen

## 4.1 Purpose & Entry

- Route: `/skill-requests` (sidebar item, all authenticated users).
- The screen is a **community feed**: posts are created by the composer, ranked by engagement × time decay, and loaded with infinite scroll.
- Detail view lives on a **separate route** (`/skill-requests/:id` — Pattern A), not a popup.

## 4.2 Screen Structure

```text
┌──────────────────────────────────────────────────────────────────────┐
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ (avatar)  [ What skill do you need? Share your idea…  ]          │ │ ← composer (opens Add Popup)
│ └──────────────────────────────────────────────────────────────────┘ │
│ [ All | My requests ]        [ 🔍 Search title, description, #tag… / ] │ ← chips + search
│                                                                      │
│ ┌──────────────────────────────────────────┐ ┌────────────────────┐ │
│ │ Request Feed Card                        │ │ Top Voted This Week│ │
│ │  (avatar) Name · time  [✓ Resolved]      │ │  1. Title (v·c)    │ │
│ │  Title (click → detail)                  │ │  2. Title (v·c)    │ │
│ │  Description (clamped 3 lines, more/less)│ │  … (10, scroll)    │ │
│ │  #tag #tag                               │ ├────────────────────┤ │
│ │  [img][img][img]   (image grid, lightbox)│ │ Top Requesters     │ │
│ │  📄 file.pdf (1.2 MB)  [Download]        │ │  (avatar) Name     │ │
│ │  👍 12   💬 5                     (vote) │ │  3 requests · 1 res│ │
│ │  ── comments preview (max 2, read-only)  │ ├────────────────────┤ │
│ │  (avatar) user · time  "comment…"        │ │ Popular Hashtags   │ │
│ │  View all 5 comments →                   │ │  #tag 12  #tag 8   │ │
│ └──────────────────────────────────────────┘ └────────────────────┘ │
│            (spinner / "You're all caught up ✓")                     │
└──────────────────────────────────────────────────────────────────────┘
```

Layout: 2-column grid — feed (fluid) + sidebar (300px). Below `1199px` the sidebar stacks under the feed.

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
| Title | Clickable → navigates to the **Detail Screen** (`/skill-requests/:id`) |
| Description | Sanitized HTML (whitelist: p, br, b, i, u, a[href], ul/ol/li, code, pre). Clamped to 3 lines with "more"/"less" toggle when >160 chars |
| Hashtags | Badge per tag (`#tag`), tone info |
| Image attachments | Grid (auto-fill ≥150px tiles, 4:3 cover). Click → **Image Lightbox** (←/→ navigation, `Esc` closes, counter "2/5", caption = file name). Last tile shows "N photos" count when >1 |
| Non-image attachments | Row per file: `📄 fileName (size)` + **Download** button (right next to the name). Download via authenticated endpoint, original file name preserved |
| Reaction bar | 👍 vote toggle (filled when voted) + 💬 comment count (click → detail screen) |
| Comments preview | **Read-only**: up to **2** most recent top-level comments (avatar, user, time, 2-line clamped body, resolved mark) + "View all N comments →" button → detail screen. **No** inline comment form, like, or reply on the card |

Rules:

1. Voting on the card updates the count and voted state **locally** (optimistic UI on success; toast on error).
2. The card never allows commenting — all comment interaction happens on the Detail Screen.
3. File size format: `<1024` → `N B`; `<1MB` → `N.N KB`; else `N.N MB`.

## 4.6 Sidebar Stats (3 cards, one screen height, internal scroll)

| Card | Content |
|---|---|
| Top Voted This Week | 10 requests, `vote_count DESC` (ties by `created_at DESC`). Row: rank # (top-2 highlighted) · title (2-line clamp) · `N votes · M comments` |
| Top Requesters | 10 users by request count. Row: avatar · name · `N requests · M resolved` |
| Popular Hashtags | 10 hashtags by usage count. Chip `#tag count` — **click applies the hashtag search** to the feed |

Rules:

1. The sidebar is sticky (desktop) and each card scrolls internally; on mobile the cards stack with a max-height scroll.
2. Stats refresh after feed page loads and after a new request is created (votes/comments changed).
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
│ New Skill Request                                    [×]      │
├──────────────────────────────────────────────────────────────┤
│ Title *                                                    │
│ [____________________________________________]  0/100       │
│ Description                                                 │
│ [____________________________________________]  0/2000      │
│ [____________________________________________]              │
│ Hashtags (max 10)                                           │
│ [ tag1 ] [ tag2 ] [ + add tag ]                             │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │  ⬆  Drop files here or click to browse                   │ │ ← upload dropzone
│ │  Images, PDF, Office, archives, code — max 10 files,     │ │
│ │  10 MB each                                              │ │
│ └──────────────────────────────────────────────────────────┘ │
│  [img] [img] [📄 file.pdf 1.2MB ×]   ← previews (remove ×)  │
│ ┌ Duplicate hint (optional) ──────────────────────────────┐ │
│ │ Similar skills: Java Spring Boot (Approved) …            │ │
│ │ Similar requests: "Need a Tibero migration guide" (12)   │ │
│ └──────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────┤
│                                    [ Cancel ] [ Post ]       │
└──────────────────────────────────────────────────────────────┘
```

## 5.2 Fields & Validation

| Field | Required | Rule | Error |
|---|---|---|---|
| Title | ✓ | 1–100 chars (trimmed) | "Request title cannot be empty." / "Request title is at most 100 characters." |
| Description | – | ≤ 2000 chars | "Description is at most 2000 characters." |
| Hashtags | – | Max **10** tags; each 1–30 chars; charset `[A-Za-z0-9_-]` only (stored without `#`) | "Hashtags may only contain letters, numbers, underscore and hyphen." / "Each hashtag is at most 30 characters." / "At most 10 hashtags are allowed." |
| Files | – | Max **10** files; each ≤ **10 MB**; whitelist extensions (below) | "At most 10 attachments are allowed." / "File is too large (max 10 MB)." / "File type is not allowed." |

**Allowed extensions:**

- Images: `jpg`, `jpeg`, `png`, `webp`, `gif`
- Documents & archives & code: `pdf`, `doc`, `docx`, `xls`, `xlsx`, `ppt`, `pptx`, `zip`, `7z`, `txt`, `md`, `json`, `csv`, `log`, `yaml`, `yml`, `py`, `js`, `ts`

## 5.3 Upload Behavior

1. The dropzone accepts drag & drop and click-to-browse (multi-select).
2. Previews render immediately: images as thumbnails, other files as rows with size; each has a remove (×) button.
3. Client-side pre-validation (count, size, extension) before submit; server re-validates everything.
4. Submit sends a **multipart/form-data** POST: `title`, `description`, `hashtags` (JSON array string), `file[]`.
5. On success: popup closes, feed resets to page 1, sidebar stats refresh, toast "Request posted."

## 5.4 Duplicate Suggestions (hint box)

While typing the title (debounced), the popup may show a hint box with up to **5** similar Skills (name + status) and **5** similar existing requests (title + vote count) for the keyword. The box is informational — it never blocks posting. Clicking a similar skill opens the Skill Detail Popup.

---

# 6. Request Detail Screen

## 6.1 Purpose & Entry

- Route: `/skill-requests/:id` (name `skill-request-detail`). Reached from: feed card title, 💬 count, "View all comments →".
- "Back to feed" button (top-left) → `/skill-requests`.
- Invalid/non-numeric id → "Invalid request." empty state + back button.
- Navigating between two detail pages (id change) reloads the request.

## 6.2 Screen Structure (2 columns)

```text
┌──────────────────────────────────────────────────────────────────────┐
│ [← Back to feed]                                                     │
├───────────────────────────────────────────────┬──────────────────────┤
│ (avatar) Name · time        [✓ Resolved]      │ Comments      (5)    │
│ Title (h1)                                  │ ┌──────────────────┐ │
│ Description (full, sanitized HTML)          │ │ (scroll area,    │ │
│ #tag #tag                                   │ │  internal scroll)│ │
│ [img][img][img]  (grid → lightbox)          │ │ ranked comments  │ │
│ 📄 file.pdf (1.2 MB) [Download]             │ │ + reply trees    │ │
│ ─────────────────────────────────────────    │ │ [Load more]      │ │
│ 👍 12   💬 5                                 │ └──────────────────┘ │
│                                             │ ┌──────────────────┐ │
│                                             │ │ [Share your      │ │ ← pinned composer
│                                             │ │  thoughts…]      │ │
│                                             │ │ [Link a skill]   │ │
│                                             │ │           [Post] │ │
│                                             │ └──────────────────┘ │
└───────────────────────────────────────────────┴──────────────────────┘
```

| Column | Behavior |
|---|---|
| **Post (main)** | Same content as the feed card but full description (no clamp), title as `h1` (not a link) |
| **Comments sidebar** | Fixed height (`calc(100vh - header - gaps)`, min 480px, max 820px), sticky on desktop; **internal vertical scroll** (`overflow-y: auto`); composer pinned at the bottom (never scrolls away) |

Below `999px` the columns stack (post on top, sidebar below with a fixed 480px height).

## 6.3 Detail Screen States

| State | Behavior |
|---|---|
| Loading | "Loading request…" |
| Not found / error | "Unable to load the request." + **Retry** |
| Comments loading | "Loading comments…" in the sidebar |
| Comments empty | "No comments yet. Be the first to share your thoughts." |

---

# 7. Comments

## 7.1 Comment Item

| Element | Behavior |
|---|---|
| Header | Avatar + user name + relative time |
| Body | Sanitized HTML (same whitelist as description) |
| Skill chip | When the comment links a Skill: chip `🔗 SkillName [status badge]`. Click: if the skill is **APPROVED** → open Skill Detail Popup; otherwise toast "This skill has not been approved yet. Please contact an admin." |
| Resolved mark | "✓ Resolved" (green) when `resolvedYn = 'Y'` |
| Actions | 👍 like toggle · 💬 Reply · 🗑 Delete (own comments only) · ✓ Resolve (request creator or admin, only on skill-linked comments) |
| Replies | Nested tree, **max depth 3**; replies sorted `created_at ASC` |

## 7.2 Comment Ranking (top-level, in the sidebar)

Top-level comments are ranked server-side, in this order:

1. **Resolved first** (`resolvedYn = 'Y'` before others)
2. **Interaction score descending** = `likeCount + reply subtree size` (all descendant replies, any depth)
3. **Newest first** (`created_at DESC`, ties by `id DESC`)

Pagination is page-based (default size 10) over the ranked list; "Load more comments" appends the next page.

## 7.3 Comment Rules

| ID | Rule |
|---|---|
| BR-CMT-01 | Comment content: 1–1000 chars (trimmed, non-empty) |
| BR-CMT-02 | Reply depth is capped at **3** (a reply to a depth-3 comment is rejected) |
| BR-CMT-03 | A comment may link **at most 1** Skill, and the Skill must exist; linking a non-existent Skill is rejected |
| BR-CMT-04 | Only the comment author can delete their comment (soft delete — `deleted_at` set) |
| BR-CMT-05 | Like is one-per-user-per-comment (toggle: like / unlike); liking is idempotent-safe (409 on double like) |
| BR-CMT-06 | **Resolve**: only the **request creator** or an **admin** may mark a comment resolved; only skill-linked comments are resolvable; idempotent (re-resolve = no-op) |
| BR-CMT-07 | A request shows the "✓ Resolved" badge when it has ≥1 resolved comment |
| BR-CMT-08 | `commentCount` on the request increments on create and decrements on delete |
| BR-CMT-09 | After posting a comment/reply, the sidebar reloads (ranked list) and scrolls to the top |

## 7.4 Comment Composer (pinned, sidebar bottom)

| Element | Behavior |
|---|---|
| Textarea | Placeholder "Share your thoughts…", max 1000 chars with counter |
| Link a skill | Opens the Link Skill Popup (skill picker); selection shows a chip `🔗 Name [status]` with a remove (×) |
| Post | Disabled while empty or posting; on success clears the draft + linked skill, reloads comments |
| Reply form | One at a time; opens inline under the target comment (Reply button toggles); same max length; submits with `parentId` |

## 7.5 Rate Limits

| Action | Limit |
|---|---|
| Create request | 5 per user per 60s |
| Vote | 10 per user per 60s |
| Comment / reply | 30 per user per 60s |

Exceeding a limit → HTTP 429, toast "Too many requests. Please try again later."

---

# 8. Attachments (files & images)

## 8.1 Storage

| Property | Value |
|---|---|
| On-disk path | `uploads/skill-requests/{requestId}/{uuid}.{ext}` (UUID prefix prevents collisions & path traversal) |
| DB record | `skill_request_files` (request_id FK CASCADE, file_name, file_path, file_size, is_image, created_at) |
| Image public URL | `/uploads/skill-requests/{requestId}/{uuid}.{ext}` (served by static resource handler, `permitAll`) |
| Non-image access | Authenticated download endpoint (original file name via `Content-Disposition`) |
| Limits | ≤ 10 files per request; ≤ 10 MB per file; whitelist extensions (§5.2) |

## 8.2 Display

| Type | Feed card | Detail screen |
|---|---|---|
| Image | Grid tile → Lightbox | Grid tile → Lightbox |
| Non-image | `📄 name (size)` + Download | `📄 name (size)` + Download |

## 8.3 Lightbox

- Full-screen modal over a dimmed backdrop; click backdrop or `Esc` closes.
- `←` / `→` keys and on-screen arrows navigate between images of the post.
- Counter `i/N` + caption (file name).
- **The lightbox must always be closable** (backdrop click, `Esc`, and close button all work).

## 8.4 Download

- Non-image files download through `GET /api/v1/skill-requests/{id}/files/{fileId}/download` (authenticated), returned as `application/octet-stream` with `Content-Disposition: attachment; filename="<original name>"`.
- Images are also downloadable through the same endpoint (the public URL is for display only).

---

# 9. Business Rules

| ID | Rule |
|---|---|
| BR-FRM-001 | All skill requests are visible to every authenticated user (no scope concept) |
| BR-FRM-002 | A posted request is **immutable** — no edit, no delete (including by admin) |
| BR-FRM-003 | Feed ranking = `(vote×2 + comment×1) × timeDecay` (1.0 ≤2d / 0.5 ≤7d / 0.1 >7d); ties by `created_at DESC`, `id DESC` |
| BR-FRM-004 | Feed paging is cursor-based (base64url `createdAt\|id`); page size 10, max 50 |
| BR-FRM-005 | Vote is one-per-user-per-request (toggle); `voteCount` updates accordingly; 409 on double vote, 404 on unvote without vote |
| BR-FRM-006 | Search matches title OR description OR hashtag (case-insensitive; `#` prefix optional) |
| BR-FRM-007 | "My requests" filter = `createdBy = self` |
| BR-FRM-008 | Title 1–100 chars; description ≤ 2000 chars; ≤ 10 hashtags of 1–30 chars `[A-Za-z0-9_-]` |
| BR-FRM-009 | Attachments: ≤ 10 files, ≤ 10 MB each, whitelist extensions; server re-validates client validation |
| BR-FRM-010 | Image = extension in `jpg, jpeg, png, webp, gif` → renders in grid; everything else → download link |
| BR-FRM-011 | Comment ranking: resolved → interaction (likes + reply subtree size) desc → newest first |
| BR-FRM-012 | Comment depth max 3; content 1–1000 chars; ≤ 1 linked Skill (must exist) |
| BR-FRM-013 | Delete comment: author only, soft delete |
| BR-FRM-014 | Resolve comment: request creator or admin only; skill-linked comments only; idempotent |
| BR-FRM-015 | Request "Resolved" badge when ≥1 comment has `resolvedYn = 'Y'` |
| BR-FRM-016 | Rate limits: create 5/60s, vote 10/60s, comment 30/60s per user → 429 |
| BR-FRM-017 | Detail screen is a separate route (`/skill-requests/:id`); feed cards show a read-only comment preview (max 2) + "View all N comments →" |
| BR-FRM-018 | Sidebar stats: top-10 voted, top-10 requesters, top-10 hashtags; stats failure is non-critical (empty cards) |

---

# 10. API Specification

Base path: `/api/v1/skill-requests`. All endpoints require a valid session (JWT) unless noted. Standard envelope:

```json
{ "success": true, "data": { ... } }
```

Error responses:

```json
{ "success": false, "errorCode": "SKILL_REQUEST_NOT_FOUND", "message": "Business message." }
```

## 10.1 Feed

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
        "topComments": [
          {
            "id": 900, "userId": 3, "userName": "Le Thi B",
            "content": "I can help with this", "createdAt": "2026-10-18T10:00:00Z",
            "linkedSkillId": 5, "linkedSkillName": "Tibero Migration", "linkedSkillStatus": "APPROVED",
            "resolvedYn": "Y", "likeCount": 2, "likedByMe": false
          }
        ],
        "files": [
          { "id": 501, "fileName": "arch.png", "fileSize": 204800, "isImage": true, "url": "/uploads/skill-requests/42/9f1c…arch.png" },
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
| `topComments` | Up to 2 most recent top-level comments (read-only preview) |
| `files[].url` | Non-null only for images (public URL); non-image files download via §10.9 |
| `nextCursor` | `null` when no more pages |

## 10.2 Create (multipart)

`POST /api/v1/skill-requests` — `Content-Type: multipart/form-data`

| Part | Type | Required |
|---|---|---|
| `title` | string | ✓ |
| `description` | string | – |
| `hashtags` | JSON array string, e.g. `["tibero","migration"]` | – |
| `file` | binary (repeatable) | – |

Success: `201` + `{ "success": true, "data": 42 }` (new request id).

## 10.3 Detail

`GET /api/v1/skill-requests/{id}` → `200` with a single feed item (same shape as §10.1 item). `404` when not found.

## 10.4 Vote / Unvote

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/v1/skill-requests/{id}/vote` | 201 | 409 `SKILL_REQUEST_ALREADY_VOTED`, 404 |
| DELETE | `/api/v1/skill-requests/{id}/vote` | 204 | 404 (not voted / not found) |

## 10.5 Comments List (ranked)

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
        "canDelete": false, "parentId": null,
        "linkedSkillId": 5, "linkedSkillName": "Tibero Migration", "linkedSkillStatus": "APPROVED",
        "resolvedYn": "Y", "resolvedAt": "2026-10-18T11:00:00Z",
        "likeCount": 2, "likedByMe": false,
        "replies": [ { "id": 901, "…": "nested, max depth 3" } ]
      }
    ]
  }
}
```

- `items` = top-level comments only, ranked per BR-FRM-011; replies nested (max depth 3).
- `canDelete` = true only for the viewer's own comments.

## 10.6 Add Comment / Reply

`POST /api/v1/skill-requests/{id}/comments`

```json
{ "content": "Thanks!", "linkedSkillId": 5, "parentId": null }
```

| Field | Notes |
|---|---|
| `content` | 1–1000 chars, required |
| `linkedSkillId` | optional; must exist |
| `parentId` | optional; reply target (depth cap 3) |

Success `201`. Errors: 400 empty/too long/depth exceeded, 404 request not found, 429 rate limited.

## 10.7 Comment Like / Unlike

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/v1/skill-requests/{id}/comments/{commentId}/like` | 201 | 409 `SKILL_REQUEST_ALREADY_LIKED` |
| DELETE | `/api/v1/skill-requests/{id}/comments/{commentId}/like` | 204 | 404 (not liked) |

## 10.8 Delete / Resolve Comment

| Method | Path | Success | Errors |
|---|---|---|---|
| DELETE | `/api/v1/skill-requests/{id}/comments/{commentId}` | 204 | 403 (not author), 404 |
| PUT | `/api/v1/skill-requests/{id}/comments/{commentId}/resolve` | 204 (idempotent) | 403 (not creator/admin), 400 (not skill-linked), 404 |

## 10.9 File Download

`GET /api/v1/skill-requests/{id}/files/{fileId}/download`

- `200` + `Content-Type: application/octet-stream` + `Content-Disposition: attachment; filename="<original name>"`
- `404` when the file does not belong to the request.

## 10.10 Duplicate Suggestions

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

## 10.11 Sidebar Stats

`GET /api/v1/skill-requests/stats`

```json
{
  "success": true,
  "data": {
    "topVoted": [ { "id": 42, "title": "…", "voteCount": 12, "commentCount": 5 } ],
    "topRequesters": [ { "userId": 7, "userName": "Nguyen Van A", "requestCount": 3, "resolvedCount": 1 } ],
    "popularHashtags": [ { "tag": "tibero", "count": 12 } ]
  }
}
```

## 10.12 Error Code Catalog

| Error Code | HTTP | Message (frontend) |
|---|---|---|
| `SKILL_REQUEST_NOT_FOUND` | 404 | "Skill request not found." |
| `SKILL_REQUEST_TITLE_REQUIRED` | 400 | "Request title cannot be empty." |
| `SKILL_REQUEST_TITLE_TOO_LONG` | 400 | "Request title is at most 100 characters." |
| `SKILL_REQUEST_DESC_TOO_LONG` | 400 | "Description is at most 2000 characters." |
| `SKILL_REQUEST_HASHTAG_INVALID` | 400 | "Hashtags may only contain letters, numbers, underscore and hyphen." |
| `SKILL_REQUEST_HASHTAG_TOO_LONG` | 400 | "Each hashtag is at most 30 characters." |
| `SKILL_REQUEST_HASHTAGS_TOO_MANY` | 400 | "At most 10 hashtags are allowed." |
| `SKILL_REQUEST_ALREADY_VOTED` | 409 | "You have already voted for this request." |
| `SKILL_REQUEST_COMMENT_EMPTY` | 400 | "Comment cannot be empty." |
| `SKILL_REQUEST_COMMENT_TOO_LONG` | 400 | "Comment is at most 1000 characters." |
| `SKILL_REQUEST_COMMENT_NOT_FOUND` | 404 | "Comment not found." |
| `SKILL_REQUEST_COMMENT_DEPTH_EXCEEDED` | 400 | "Replies are limited to 3 levels." |
| `SKILL_REQUEST_ALREADY_LIKED` | 409 | "You have already liked this comment." |
| `SKILL_REQUEST_LINK_INVALID` | 400 | "The linked skill does not exist or is not approved yet." |
| `SKILL_REQUEST_NOT_EDITABLE` | 403 | "You do not have permission to perform this action." |
| `SKILL_REQUEST_RATE_LIMITED` | 429 | "Too many requests. Please try again later." |
| `SKILL_REQUEST_FILE_INVALID_TYPE` | 400 | "File type is not allowed." |
| `SKILL_REQUEST_FILE_TOO_LARGE` | 413 | "File is too large (max 10 MB)." |
| `SKILL_REQUEST_FILE_NOT_FOUND` | 404 | "File not found." |
| `SKILL_REQUEST_FILES_TOO_MANY` | 400 | "At most 10 attachments are allowed." |
| `AUTH-401` | 401 | "Your session has expired. Please log in again." → redirect to Login |
| `INTERNAL_ERROR` | 500 | "Something went wrong. Please try again." |

---

# 11. Data Dictionary

| Table | Columns | Description |
|---|---|---|
| `skill_requests` | `id` PK, `title` (≤100), `description` (TEXT, nullable), `hashtags` (jsonb), `vote_count` int default 0, `comment_count` int default 0, `created_by` FK user, `created_at` | The request post (immutable) |
| `skill_request_votes` | `request_id` + `user_id` composite PK, `created_at` | One vote per user per request |
| `skill_request_comments` | `id` PK, `request_id` FK, `user_id` FK, `parent_id` (self-FK, nullable), `content` (≤1000), `linked_skill_id` FK nullable, `resolved_yn` ('Y'/'N'), `resolved_at`, `resolved_by`, `like_count` int default 0, `created_at`, `deleted_at` (soft delete) | Comments + replies (tree via `parent_id`) |
| `skill_request_comment_likes` | `comment_id` + `user_id` composite PK, `created_at` | One like per user per comment |
| `skill_request_files` | `id` PK, `request_id` FK (CASCADE), `file_name`, `file_path` (relative, `{requestId}/{uuid}.{ext}`), `file_size` bigint, `is_image` bool, `created_at` | Attachments (rows cascade-delete with the request) |

---

# 12. Use Cases

## UC-FRM-001 – Browse the Feed

1. User opens `/skill-requests`.
2. System loads page 1 of the ranked feed + sidebar stats in parallel.
3. User scrolls; the sentinel triggers the next page (cursor).
4. End of feed shows "You're all caught up ✓".

## UC-FRM-002 – Search / Filter

1. User types in the search box (debounced 400ms) or clicks a chip.
2. Feed resets to page 1 with the new filter.
3. Clicking a Popular Hashtag chip applies `#tag` as the search keyword.

## UC-FRM-003 – Post a Request with Attachments

1. User clicks the composer → Add Request Popup opens.
2. User fills title/description/hashtags, drops files (previews appear).
3. User clicks Post → multipart POST.
4. On success: popup closes, feed resets, stats refresh, toast "Request posted."
5. On validation error: inline/toast error, popup stays open.

## UC-FRM-004 – View an Image in the Lightbox

1. User clicks an image tile (feed card or detail).
2. Lightbox opens at that image; `←`/`→` navigate, `Esc`/backdrop/close-button close.
3. Counter and caption (file name) are shown.

## UC-FRM-005 – Download a Non-Image File

1. User clicks **Download** next to a file row.
2. System fetches the file via the authenticated download endpoint and saves it with the original file name.

## UC-FRM-006 – Vote / Unvote

1. User clicks 👍 on a card or detail.
2. Vote toggles; count updates locally on success; 409/404 → toast.

## UC-FRM-007 – Open the Detail Screen

1. User clicks the title / 💬 count / "View all comments →" on a feed card.
2. System navigates to `/skill-requests/:id` and loads the request + first comment page.
3. "Back to feed" returns to `/skill-requests`.

## UC-FRM-008 – Comment & Reply

1. User types in the pinned composer (optionally links a Skill) and posts.
2. Comments reload (ranked) and the sidebar scrolls to the top.
3. User clicks Reply on a comment → inline reply form → post (depth ≤ 3).

## UC-FRM-009 – Like / Delete / Resolve a Comment

1. Like: toggle; 409 on double like.
2. Delete: own comments only; list reloads.
3. Resolve: request creator or admin on a skill-linked comment; the comment and request show the Resolved mark; resolved comments rank first.

---

# 13. Acceptance Criteria

### AC-FRM-01 – Feed Ranking

**Given** request A (10 votes, 2 comments, 1 day old) and request B (20 votes, 0 comments, 30 days old)
**When** the feed loads
**Then** A ranks above B (score A = 22×1.0 = 22; score B = 40×0.1 = 4).

### AC-FRM-02 – Infinite Scroll

**Given** 25 requests exist
**When** the user scrolls to the sentinel
**Then** page 2 loads with `nextCursor`
**And** after page 3 the text "You're all caught up ✓" is shown.

### AC-FRM-03 – My Requests Filter

**Given** the user has 3 requests and the community has 20
**When** the user clicks "My requests"
**Then** only the user's 3 requests are shown.

### AC-FRM-04 – Search by Hashtag

**Given** a request with hashtags `["tibero"]`
**When** the user searches `#tibero` or `tibero`
**Then** the request is returned (case-insensitive).

### AC-FRM-05 – Create Validation

**Given** the Add Request Popup is open
**When** the user submits an empty title
**Then** "Request title cannot be empty." is shown and no request is created.

**Given** 11 files are selected
**When** the user submits
**Then** "At most 10 attachments are allowed." is shown.

**Given** a 12 MB file is selected
**When** the user submits
**Then** "File is too large (max 10 MB)." is shown.

**Given** a `.exe` file is selected
**When** the user submits
**Then** "File type is not allowed." is shown.

### AC-FRM-06 – Attachments Render

**Given** a request with 3 images and 1 PDF
**When** its card renders
**Then** the 3 images appear in the grid (click → lightbox)
**And** the PDF appears as `📄 plan.pdf (1.2 MB)` with a Download button next to the name
**And** clicking Download saves the file with the original name.

### AC-FRM-07 – Lightbox Closes

**Given** the lightbox is open
**When** the user presses `Esc`, clicks the backdrop, or clicks the close button
**Then** the lightbox closes (all three methods work).

### AC-FRM-08 – Vote Toggle

**Given** the user has not voted on a request
**When** the user clicks 👍
**Then** the vote count increases by 1 and the icon is filled
**And** clicking again removes the vote (count decreases).

**Given** the user has already voted
**When** the user votes again (e.g. two tabs)
**Then** a 409 toast "You have already voted for this request." is shown.

### AC-FRM-09 – Detail Screen Layout

**Given** the user opens `/skill-requests/42`
**When** the screen renders
**Then** the post is in the left column and the comments sidebar in the right column
**And** the sidebar has internal scroll with the composer pinned at the bottom
**And** "Back to feed" returns to `/skill-requests`.

### AC-FRM-10 – Detail Loads Comments

**Given** a request with 5 comments
**When** the detail screen loads
**Then** the sidebar shows the comments (not empty)
**And** the header count matches `commentCount`.

### AC-FRM-11 – Comment Ranking

**Given** comments: R (resolved, 0 likes), A (0 likes, 4 replies), B (3 likes, 0 replies), C (1 like, 0 replies)
**When** the comment list loads
**Then** the order is R → A (score 4) → B (score 3) → C (score 1).

**Given** two comments with equal interaction scores
**When** the list loads
**Then** the newer one comes first.

### AC-FRM-12 – Reply Depth Cap

**Given** a comment at depth 3
**When** the user attempts to reply to it
**Then** the reply is rejected with "Replies are limited to 3 levels."

### AC-FRM-13 – Delete Own Comment

**Given** the user authored comment X
**When** the user deletes X
**Then** X disappears from the list and `commentCount` decreases by 1.

**Given** the user did not author comment Y
**When** the user attempts to delete Y
**Then** a 403 error is shown and Y remains.

### AC-FRM-14 – Resolve Comment

**Given** the user is the request creator and a comment links an APPROVED skill
**When** the user clicks Resolve
**Then** the comment shows "✓ Resolved", the request shows the Resolved badge, and the comment ranks first.

**Given** the user is not the creator and not an admin
**When** the user attempts to resolve
**Then** a 403 error is shown.

### AC-FRM-15 – Skill Chip on Non-Approved Skill

**Given** a comment links a skill with status PENDING_APPROVAL
**When** the user clicks the skill chip
**Then** the toast "This skill has not been approved yet. Please contact an admin." is shown and no popup opens.

### AC-FRM-16 – Rate Limit

**Given** the user has created 5 requests in the last 60 seconds
**When** the user posts a 6th
**Then** "Too many requests. Please try again later." is shown (429).

### AC-FRM-17 – Feed Card Comment Preview

**Given** a request with 5 comments
**When** its feed card renders
**Then** at most 2 comments are shown (read-only, no like/reply controls)
**And** the button reads "View all 5 comments →" and navigates to the detail screen.

### AC-FRM-18 – No Request Deletion

**Given** any user (including an admin)
**When** they view a request (card or detail)
**Then** no edit or delete control is available for the request itself.

### AC-FRM-19 – Screen States

**Given** the feed API fails with no items loaded
**When** the screen renders
**Then** "Unable to load the feed." + Retry is shown.

**Given** zero requests exist
**When** the screen renders
**Then** "No requests yet." / "Be the first to share the skill you need." is shown.

**Given** the request id in the URL is invalid
**When** the detail screen renders
**Then** "Invalid request." + "Back to feed" is shown.

### AC-FRM-20 – Sidebar Stats

**Given** the feed has loaded
**When** the sidebar renders
**Then** Top Voted shows 10 rows (rank, title, votes · comments), Top Requesters shows 10 rows, and Popular Hashtags shows clickable chips
**And** clicking a hashtag chip applies the hashtag search to the feed.

---

# 14. Frontend UX Requirements

- Feed cards, sidebar, and detail screen follow the Prism design system (dark theme, `--ds-*` tokens).
- Relative time format: "just now" / "N min ago" / "N hours ago" / "N days ago" (≤7d) / `DD/MM/YYYY` (>7d).
- Description & comment bodies render sanitized HTML only (whitelist: p, br, b, i, u, a[href], ul/ol/li, code, pre); no scripts, no event handlers, no iframes.
- Image tiles: 4:3, `object-fit: cover`, lazy-loaded; grid `auto-fill minmax(150px, 1fr)`.
- The comments sidebar scrolls internally; the composer never scrolls out of view.
- All interactive elements are keyboard-operable with visible focus; the lightbox and popups close on `Esc`.
- Optimistic UI: vote/like toggle updates locally on success, toast on error (no full reload).
- Toasts: error toasts for all failed mutations; success toast on post creation.
- Responsive: 2-column feed layout collapses below 1199px; detail 2-column collapses below 999px.

---

# 15. DEV Checklists

## 15.1 Security

- [ ] All `/api/v1/skill-requests/**` endpoints require a valid session (401 otherwise); only `/uploads/skill-requests/**` is public (static images).
- [ ] User identity always from the JWT session — never from client input.
- [ ] Path traversal guard on file storage (UUID-prefixed names, normalized absolute path, prefix check).
- [ ] Extension whitelist enforced server-side on upload.
- [ ] File size limit (10 MB) enforced server-side.
- [ ] Comment delete: author check server-side.
- [ ] Comment resolve: creator/admin check server-side.
- [ ] HTML sanitized (server-side on create, client-side on render) — no XSS.
- [ ] Rate limiting enforced server-side (create/vote/comment).

## 15.2 Database

- [ ] `skill_requests`, `skill_request_votes`, `skill_request_comments`, `skill_request_comment_likes`, `skill_request_files` tables (Flyway migration).
- [ ] `skill_request_files.request_id` FK ON DELETE CASCADE.
- [ ] Indexes: `skill_request_comments(request_id, deleted_at)`, `skill_request_votes(user_id)`, `skill_request_files(request_id)`.
- [ ] `hashtags` column is `jsonb`; search casts to text (`lower(text(hashtags))`) — PostgreSQL has no `lower(jsonb)`.
- [ ] Comment delete is soft (`deleted_at`), excluded from all read queries.

## 15.3 API

- [ ] `GET /feed`, `POST` (multipart), `GET /{id}`, `POST|DELETE /{id}/vote`
- [ ] `GET|POST /{id}/comments`, `DELETE /{id}/comments/{commentId}`, `PUT /{id}/comments/{commentId}/resolve`
- [ ] `POST|DELETE /{id}/comments/{commentId}/like`
- [ ] `GET /{id}/files/{fileId}/download`
- [ ] `GET /suggestions`, `GET /stats`
- [ ] Standard success/error envelope on all endpoints.

## 15.4 UX/UI Handoff

- [ ] Feed screen (composer, chips, search, cards, sidebar) in the dark Prism theme.
- [ ] Add Request Popup (dropzone, previews, duplicate hint).
- [ ] Detail screen (2-column, sticky sidebar, pinned composer).
- [ ] Image Lightbox (nav, counter, caption, closable).
- [ ] Comment tree with skill chips, resolved marks, reply forms.
- [ ] Loading / empty / error states per §4.8 / §6.3 / §13.
