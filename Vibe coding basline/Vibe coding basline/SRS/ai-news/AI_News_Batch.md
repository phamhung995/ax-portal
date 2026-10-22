# AI SKILL MANAGEMENT

## AI News Batch Job — Batch Import AI News From Email To Database

**Document Version:** 1.18
**Status:** Draft
**Module:** AI News (Home — Community tab)
**Audience:** PO / BA / DEV / DBA / Ops / QA

---

# 1. Mục đích

Tài liệu này mô tả **batch job `AI-NEWS-IMPORT`** — job định kỳ:

1. **Đọc email** từ các mailbox nguồn (IMAP) bằng **Skill đọc email của Trường** (tham chiếu §3).
2. **Parse & sanitize** email: title, body (HTML), sender, date.
3. **Lưu vào bảng `ai_news`** (PostgreSQL) — bảng đã định nghĩa trong `Home_Screen_SRS.md` §8.
4. **Hiển thị lên card "AI News"** trên Home (tab Community) qua API `GET /api/home/ai-news` (đã định nghĩa trong `Home_Screen_SRS.md` §7.4).

Batch job là **duy nhất** nguồn ghi dữ liệu vào `ai_news` (không có UI tạo tin). Frontend chỉ đọc. **Không phân loại category** — mọi user thấy toàn bộ tin.

```text
┌──────────────┐   IMAP    ┌────────────────────────────┐
│ Mailbox      │◄──────────│  AI-NEWS-IMPORT (batch)     │
│ - SDS AI     │  fetch    │  ┌──────────────────────┐  │
│ - GDC Tech   │           │  │ Skill đọc email      │  │
│ - Part X/Y   │           │  │ (của Trường)         │  │
│ - Group 1/2  │           │  └──────────────────────┘  │
└──────────────┘           │  parse → sanitize → dedup  │
                           └─────────────┬──────────────┘
                                         │ INSERT (idempotent)
                                         ▼
                           ┌────────────────────────────┐
                           │ PostgreSQL: ai_news        │
                           │ ai_news_source             │
                           │ ai_news_batch_log          │
                           └─────────────┬──────────────┘
                                         │ read
                                         ▼
                           ┌────────────────────────────┐
                           │ GET /api/home/ai-news      │
                           └─────────────┬──────────────┘
                                         │
                                         ▼
                           ┌────────────────────────────┐
                           │ Home → tab Community →      │
                           │ card "AI News" (timeline)  │
                           └────────────────────────────┘
```

---

# 2. Phạm vi

## 2.1 In Scope

| ID | Chức năng |
|---|---|
| NEWS-01 | Đọc email mới từ các mailbox nguồn qua IMAP (dùng Skill đọc email của Trường) |
| NEWS-02 | Parse email: title, body, date, sender |
| NEWS-03 | Sanitize body HTML (loại script/iframe, giữ markup hiển thị) + lưu sender |
| NEWS-04 | Deduplication (không import trùng) |
| NEWS-05 | Ghi vào `ai_news` + log mỗi lần chạy vào `ai_news_batch_log` |
| NEWS-06 | Schedule định kỳ (cron) + chạy thủ công (admin) |
| NEWS-07 | Error handling, retry, cảnh báo khi job fail |

## 2.2 Out of Scope

- Hiển thị UI card AI News — đã quy định trong `Home_Screen_SRS.md` §5.8, §7.4.
- API `GET /api/home/ai-news` — đã quy định trong `Home_Screen_SRS.md` §7.4.
- Quản lý cấu hình mailbox qua UI (v1.0 cấu hình trong DB `ai_news_source`, sửa trực tiếp DB / config file).
- Dịch thuật nội dung email (giữ nguyên ngôn ngữ gốc).
- Xóa tin cũ khỏi `ai_news` (chỉ archive sau 90 ngày — xem BR-NEWS-010).

---

# 3. Nguồn dữ liệu: Email & Skill đọc email

## 3.1 Skill đọc email (tham chiếu)

Batch job **không tự viết logic IMAP** mà gọi **Skill đọc email của Trường** — skill AI có khả năng đọc email từ mailbox qua IMAP.

Giao diện mong đợi của skill (input/output) mà batch job sử dụng:

| | Mô tả |
|---|---|
| **Input** | `host` (IMAP server), `port` (993), `username`, `password` (từ secret store), `folder` (mặc định `INBOX`), `since` (timestamp — chỉ lấy email mới hơn), `limit` (max email/lần) |
| **Output** | Array email: `{ messageId, subject, from, to, date, bodyText, bodyHtml }` |
| **Yêu cầu** | Chỉ đọc (IMAP `READONLY`), không gửi/xóa/đánh dấu. Timeout 60s/mailbox. **Trả email theo date tăng dần (cũ trước)** — bắt buộc để cơ chế limit hoạt động đúng (BR-NEWS-009: phần vượt limit phải luôn mới hơn mốc `last_success_run_at`) |

> **Ghi chú triển khai:** Skill đọc email của Trường được gọi như một bước trong pipeline (skill → trả về array email → batch job xử lý tiếp). Nếu skill không khả dụng ở thời điểm chạy job, job fail theo §8 (không có fallback IMAP riêng).

## 3.2 Mailbox nguồn

| # | Mailbox (ví dụ) | Nội dung |
|---|---|---|
| 1 | `sds-ai-news@internal` | Tin AI của SDS/GDC (announcements, roadmap, workshop) |
| 2 | `gdc-tech-support@internal` | Hướng dẫn mới về AI từ GDC Technical Support |
| 3 | `part-x-ai@internal` | Tin AI của Part X |
| 4 | `part-y-ai@internal` | Tin AI của Part Y |
| 5 | `group1-ai@internal` | Tin AI của Group 1 |
| 6 | `group2-ai@internal` | Tin AI của Group 2 |

- Mailbox ↔ tên hiển thị được khai báo trong bảng **`ai_news_source`** (§6.2) — thêm/bớt mailbox không cần sửa code.
- Không phân loại category — mọi user thấy toàn bộ tin từ mọi mailbox.
- Tên mailbox thực tế (hostname, tên user) lấy từ config/secret store, không hardcode.

## 3.3 Quy tắc chọn email

Một email được import khi **đúng cả 3**:

1. `date > last_success_run_at` của source đó (lần chạy thành công gần nhất). **Source mới (`last_success_run_at = NULL`) → dùng `now() - 2d` làm mốc** (trùng rule 2 — tránh `date > NULL` luôn false làm lần chạy đầu không import được email nào). Mốc có thể đã bị lùi 1s khi lần trước chạm limit (BR-NEWS-009) — email trùng mốc bị fetch lại, dedup `message_id` chống trùng (bước 4c).
2. `date >= now() - 2d` (bỏ qua email quá cũ — chống import ồ ạt sau downtime).
3. `subject` khác rỗng và email không phải `bounce`/`auto-reply` (header `Auto-Submitted: auto-replied` bị loại).

---

# 4. Quy trình batch job

## 4.1 Schedule

| Property | Value |
|---|---|
| Cron mặc định | `0 0,12 * * *` (hàng ngày 00:00 và 12:00 giờ máy chủ — 2 lần/ngày) |
| Thời gian chạy tối đa | 10 phút |
| Chồng job | **Không** — nếu job trước chưa xong thì bỏ lượt (skip + log) |
| Chạy thủ công | Admin gọi `POST /api/v1/admin/ai-news/run` (permission `ADMIN_USER`) — dùng khi cần import gấp hoặc re-run sau sự cố |

## 4.2 Flow chính

```text
[Start]
  │
  ▼
1. Lock job (chống chạy song song) ── fail ──► log SKIP, exit
  │
  ▼
2. Đọc danh sách source từ ai_news_source (status = ACTIVE)
  │
  ▼
3. Với MỖI source:
  │   ├─ 3a. Gọi Skill đọc email của Trường
  │   │      (host, port, user, pass, since = last_success_run_at ?? (now() - 2d), limit = 50)
  │   ├─ 3b. Fail (timeout/auth/network) → log lỗi source đó, TIẾP TỤC source khác
  │   │
  │   ▼
  │   4. Với MỖI email nhận được:
  │   │   ├─ 4a. Loại auto-reply / subject rỗng (§3.3)
  │   │   ├─ 4b. Parse: title = subject (truncate 200 ký tự)
  │   │   │        body = bodyHtml (sanitize — giữ HTML, bỏ script/iframe)
  │   │   │        sender = from của email
  │   │   │        created_at = date của email
  │   │   ├─ 4c. Dedup: message_id đã tồn tại trong ai_news → bỏ qua
  │   │   └─ 4d. INSERT vào ai_news (batch, 1 transaction/source)
  │   │
  │   ▼
  │   5. Source OK → UPDATE ai_news_source.last_success_run_at:
  │   │      fetch trả < limit (50) → now()
  │   │      fetch trả ĐÚNG limit → MAX(created_at) của email ĐÃ FETCH trong lượt đó − 1s
  │   │      (KHÔNG phải chỉ email đã import — xem BR-NEWS-009; email trùng mốc
  │   │       được fetch lại lần sau, dedup chống trùng)
  │
  ▼
6. Cleanup (BR-NEWS-010): UPDATE ai_news SET archive_yn = 'Y'
  │   WHERE created_at < now() - 90d AND archive_yn = 'N'
  │   └─ fail → log + alert Ops, job status KHÔNG đổi (import đã xong — archive best-effort)
  │
  ▼
7. Ghi ai_news_batch_log (run_id, status, total fetched/inserted/skipped/failed/archived, duration)
  │
  ▼
8. Có source fail → gửi cảnh báo (email/log alert) nhưng job status = PARTIAL
  │
  ▼
[End]
```

**Nguyên tắc:** lỗi của 1 source **không** làm fail cả job (các source khác vẫn import). `last_success_run_at` chỉ được update khi source đó import thành công — email của source fail sẽ được lấy lại ở lần chạy sau.

## 4.3 Parse & sanitize

| Field `ai_news` | Nguồn |
|---|---|
| `title` | `subject` của email (truncate 200 ký tự, bỏ prefix `[ANN]`, `[GUIDE]`… nếu có) |
| `body` | `bodyHtml` của email — sanitize (loại `<script>`, `<iframe>`, event handler `on*`, `javascript:` URL) rồi lưu nguyên dạng HTML |
| `sender` | `from` của email (tên + địa chỉ, vd "GDC Tech <gdc-tech-support@internal>") |
| `source` | Tên hiển thị: `ai_news_source.display_name` (vd "GDC Technical Support") |
| `created_at` | `date` của email |
| `message_id` | `Message-ID` header (dedup) |

- **Không phân loại category** — không có bước classify, mọi user thấy toàn bộ tin.
- Sanitize HTML là bắt buộc (chống XSS khi render lại body) — giữ markup hiển thị (đoạn, bảng, link, ảnh) nhưng bỏ mọi script.
- Email có `Message-ID` rỗng → dùng `SHA256(from + date + subject)` làm `message_id` thay thế. Nếu hash trùng `message_id` đã tồn tại (collision — vd 2 email cùng from+date+subject) → append `-1`, `-2`, … cho tới khi unique; **KHÔNG** làm fail transaction.

---

# 5. Business Rules

| ID | Rule |
|---|---|
| BR-NEWS-001 | Batch job là nguồn ghi duy nhất của `ai_news`; API public chỉ đọc |
| BR-NEWS-002 | Mỗi email chỉ import 1 lần (unique `message_id`) — job chạy lại là idempotent |
| BR-NEWS-003 | Chỉ import email có `date > last_success_run_at` của source và `date >= now() - 2d`. Source mới (`last_success_run_at = NULL`) → mốc = `now() - 2d` (§3.3 rule 1) |
| BR-NEWS-004 | Auto-reply / bounce / subject rỗng bị loại |
| BR-NEWS-005 | Không phân loại category — mọi user thấy toàn bộ tin; `source` lấy từ cấu hình source (mailbox) |
| BR-NEWS-006 | Lỗi 1 source không làm fail các source khác; job status = `PARTIAL` nếu có source fail |
| BR-NEWS-007 | `last_success_run_at` chỉ update khi source import thành công |
| BR-NEWS-008 | Không chạy 2 job song song (lock); job trùng bị skip + log |
| BR-NEWS-009 | Max 50 email/source/lần chạy; phần vượt (nếu có) được lấy ở lần chạy sau — cơ chế: khi fetch chạm limit, `last_success_run_at` chỉ tiến tới `MAX(created_at)` của **toàn bộ email đã FETCH trong lượt đó** (email mới nhất trong 50 — KHÔNG phải chỉ email đã insert) − 1s (không phải `now()`); email có date trùng mốc được fetch lại, dedup chống trùng. **Lý do dùng "đã fetch" thay vì "đã import":** nếu 50 email fetch về mà nhiều/trùng (dedup skip), email mới nhất có thể đã import trước đó — dùng mốc "đã import" sẽ làm mốc lùi lại hoặc (nếu toàn trùng) rỗng → fallback `now()` → **mất email** phía sau cửa sổ 50. Dùng mốc "đã fetch" (luôn ≥ "đã import") đảm bảo không bỏ sót email chưa fetch. Yêu cầu: skill đọc email trả về theo **thứ tự date tăng dần** (cũ trước — §3.1) để phần vượt limit luôn mới hơn mốc |
| BR-NEWS-010 | Tin cũ hơn 90 ngày được `archive_yn = 'Y'` (không xóa) — job cleanup chạy cùng cron, sau import |
| BR-NEWS-011 | Credential mailbox (password) chỉ đọc từ secret store — không log, không lưu DB |
| BR-NEWS-012 | Job status `FAILED` hoặc `PARTIAL` liên tiếp 3 lần → cảnh báo Ops (email + log alert) — khớp §8 Monitoring |

---

# 6. Data Model

## 6.1 `ai_news` (đã định nghĩa trong `Home_Screen_SRS.md` §8 — bổ sung cột)

```sql
CREATE TABLE ai_news (
    news_id        BIGSERIAL PRIMARY KEY,
    message_id     VARCHAR(320) NOT NULL UNIQUE,   -- Message-ID email (dedup)
    title          VARCHAR(200) NOT NULL,
    body           TEXT,                           -- nội dung email (HTML, đã sanitize)
    sender         VARCHAR(320),                   -- from của email (tên + địa chỉ)
    source         VARCHAR(100) NOT NULL,          -- display name (vd 'GDC Technical Support')
    created_at     TIMESTAMP    NOT NULL,          -- ngày email gửi
    archive_yn     CHAR(1)      NOT NULL DEFAULT 'N',
    imported_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_news_created ON ai_news (created_at DESC);
```

> **Lưu ý:** `body` lưu HTML đã sanitize (§4.3) — frontend render theo `AI_News_Screen_SRS.md` NFR-02 (render `v-html` + CSP `script-src` chặn inline script làm lớp phòng vệ thứ 2; client KHÔNG sanitize lại).

## 6.2 `ai_news_source` (mới — cấu hình mailbox)

```sql
CREATE TABLE ai_news_source (
    source_id             BIGSERIAL PRIMARY KEY,
    mailbox               VARCHAR(200) NOT NULL UNIQUE,  -- user IMAP
    imap_host             VARCHAR(200) NOT NULL,
    imap_port             SMALLINT     NOT NULL DEFAULT 993,
    secret_ref            VARCHAR(200) NOT NULL,         -- ref tới secret store (KHÔNG lưu password)
    display_name          VARCHAR(100) NOT NULL,         -- hiện thị trong UI (vd 'GDC Technical Support')
    status                VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    last_success_run_at   TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT now()
);
```

## 6.3 `ai_news_batch_log` (mới — log mỗi lần chạy)

```sql
CREATE TABLE ai_news_batch_log (
    run_id            BIGSERIAL PRIMARY KEY,
    started_at        TIMESTAMP NOT NULL,
    finished_at       TIMESTAMP,
    status            VARCHAR(10) NOT NULL CHECK (status IN ('RUNNING','SUCCESS','PARTIAL','FAILED','SKIPPED')),
    total_fetched     INT NOT NULL DEFAULT 0,
    total_inserted    INT NOT NULL DEFAULT 0,
    total_skipped     INT NOT NULL DEFAULT 0,   -- dedup + filter
    total_failed      INT NOT NULL DEFAULT 0,   -- source fail
    total_archived    INT NOT NULL DEFAULT 0,   -- tin archive 90 ngày (bước cleanup)
    error_message     TEXT,
    trigger_type      VARCHAR(10) NOT NULL DEFAULT 'CRON' CHECK (trigger_type IN ('CRON','MANUAL'))
);
```

---

# 7. API

## 7.1 Chạy thủ công (admin)

### Endpoint

`POST /api/v1/admin/ai-news/run`

### Permission

`ADMIN_USER` (JWT). NORMAL_USER → 403.

### Success Response

```json
{
  "success": true,
  "data": {
    "runId": 1024,
    "status": "SUCCESS",
    "totalInserted": 3,
    "totalSkipped": 1,
    "totalFailed": 0,
    "totalArchived": 2
  }
}
```

### Error Codes

| Code | Description |
|---|---|
| NEWS-101 | Job đang chạy (lock bị giữ) — không chạy chồng |
| NEWS-102 | Job status `FAILED` (chi tiết trong `ai_news_batch_log.error_message`) |
| AUTH-401 | Session thiếu/hết hạn |
| AUTH-403 | Không phải ADMIN_USER |

## 7.2 Tra log (admin, hỗ trợ debug)

`GET /api/v1/admin/ai-news/logs?limit=20` → list `ai_news_batch_log` mới nhất.

## 7.3 Đọc tin cho UI

Không thay đổi — dùng `GET /api/home/ai-news` theo `Home_Screen_SRS.md` §7.4. Query trả toàn bộ tin `archive_yn = 'N'` (không filter theo user), `created_at DESC`, limit 8.

## 7.4 Chi tiết tin (popup xem email)

`GET /api/home/ai-news/{newsId}` → 1 tin đầy đủ (kể cả `body` HTML) cho màn hình chi tiết. Tin không tồn tại hoặc `archive_yn = 'Y'` → 404.

> Chi tiết endpoint (request/response, error codes, NFR, test case) — xem `AI_News_Screen_SRS.md` §5, §7, §8.

> **v1.16 — Comment + Like (khôi phục Like, không toast):** Màn hình AI News có **Like bản tin** (FR-15 — **KHÔNG có toast** khi like/unlike, silent toggle) + **Bình luận (threaded)** + **Vote hữu ích trên comment** (FR-16..FR-20 `AI_News_Screen_SRS.md`). 5 endpoint comment/like (§5.6 `AI_News_Screen_SRS.md`) + 3 bảng DB: `ai_news_comment`, `ai_news_comment_vote`, `ai_news_like` (§6.1-6.3 `AI_News_Screen_SRS.md`). Batch job **không ghi** vào 3 bảng này — chỉ ghi `ai_news` (BR-NEWS-001 giữ nguyên: batch là nguồn ghi duy nhất của `ai_news`).

---

# 8. Error Handling & Monitoring

| Sự cố | Xử lý |
|---|---|
| Skill đọc email timeout / lỗi network (1 source) | Log lỗi source, tiếp tục source khác, job = `PARTIAL` |
| Lỗi auth mailbox (sai password) | Như trên + cảnh báo Ops (cần đổi secret) |
| DB down | Job = `FAILED`, retry tự động 1 lần sau 5 phút |
| Job `FAILED`/`PARTIAL` 3 lần liên tiếp | Cảnh báo Ops (email + log alert) — BR-NEWS-012 |
| Job chạy quá 10 phút | Kill + log `FAILED` (timeout) |
| Email lỗi encoding / body rỗng | Import với `body = NULL`, title vẫn lấy từ subject; nếu subject cũng rỗng → skip |
| Cleanup archive fail (bước 6) | Log + alert Ops; job status **không đổi** (import đã thành công — archive chạy lại ở lượt sau) |
| Chạy trùng (cron + manual cùng lúc) | Lock → job sau = `SKIPPED` |

**Monitoring:**

- Dashboard log: `ai_news_batch_log` (status, inserted, duration theo ngày).
- Alert: job `FAILED`/`PARTIAL` liên tiếp 3 lần (BR-NEWS-012); `last_success_run_at` của **source ACTIVE** nào đó > 14 giờ (chu kỳ 12h + biên độ 2h) — source INACTIVE không alert (tắt có chủ đích).

---

# 9. Use Cases

## UC-NEWS-001 – Chạy định kỳ

1. Cron `0 0,12 * * *` kích hoạt job (00:00 và 12:00 hàng ngày).
2. Job lấy lock, đọc danh sách source ACTIVE.
3. Với mỗi source: gọi Skill đọc email của Trường → parse → sanitize → dedup → INSERT.
4. Update `last_success_run_at` cho source thành công (logic 2 nhánh theo limit — xem flow §4.2 bước 5).
5. Cleanup: archive tin cũ hơn 90 ngày (`archive_yn = 'Y'`).
6. Ghi `ai_news_batch_log`.
7. User mở Home → tab Community → card AI News hiển thị tin mới (qua `GET /api/home/ai-news`).

## UC-NEWS-002 – Chạy thủ công (admin)

1. Admin gọi `POST /api/v1/admin/ai-news/run`.
2. Nếu job đang chạy → trả `NEWS-101`.
3. Ngược lại chạy như UC-NEWS-001, `trigger_type = MANUAL`, trả kết quả ngay (synchronous, max 10 phút). ⚠️ Client/gateway có thể timeout trước khi job xong (HTTP timeout thường 60s–5min) — job phía server vẫn chạy tới cùng, kết quả tra qua `GET /api/v1/admin/ai-news/logs`. Nếu production gặp timeout thường xuyên → cân nhắc đổi async (trả `runId` ngay + poll logs) — ghi nhận, chưa đổi thiết kế v1.0.

## UC-NEWS-003 – Re-run sau sự cố

1. Source X fail do network trong 1 ngày (vẫn nằm trong cửa sổ 2 ngày).
2. Lần chạy thành công tiếp theo: `since = last_success_run_at` cũ → lấy lại toàn bộ email 1 ngày đó.
3. Email đã import (lần trước) bị dedup bỏ qua; email mới được insert.

---

# 10. Acceptance Criteria

### AC-NEWS-01 – Import thành công

**Given** mailbox GDC Tech Support có 3 email mới từ lần chạy trước
**When** job chạy
**Then** 3 row mới xuất hiện trong `ai_news` với `source = 'GDC Technical Support'`, `sender` = địa chỉ gửi, `body` = HTML đã sanitize
**And** `ai_news_batch_log` ghi `total_inserted = 3`, `status = 'SUCCESS'`.

---

### AC-NEWS-02 – Idempotent (chạy lại không trùng)

**Given** job đã import email có `Message-ID: <abc@internal>`
**When** job chạy lại và skill trả lại email đó
**Then** không có row mới (unique `message_id`)
**And** `total_skipped` tăng.

---

### AC-NEWS-03 – Filter email cũ

**Given** email có `date` cách đây 3 ngày (vượt cửa sổ 2 ngày)
**When** job chạy
**Then** email đó KHÔNG được import.

---

### AC-NEWS-04 – Loại auto-reply

**Given** email có header `Auto-Submitted: auto-replied`
**When** job chạy
**Then** email đó KHÔNG được import.

---

### AC-NEWS-05 – Lỗi 1 source không chặn source khác

**Given** mailbox Part X lỗi auth, các mailbox khác bình thường
**When** job chạy
**Then** các source khác vẫn import thành công
**And** job status = `PARTIAL`
**And** `last_success_run_at` của Part X KHÔNG thay đổi (email sẽ lấy lại lần sau).

---

### AC-NEWS-06 – Mọi user thấy toàn bộ tin

**Given** user bất kỳ (Part X/Group 1 hay Part Y/Group 2)
**When** user mở Home → tab Community
**Then** card AI News hiển thị toàn bộ tin `archive_yn = 'N'` (không filter theo part/group)
**And** mỗi tin hiển thị `sender` (người gửi email) thay vì category.

---

### AC-NEWS-07 – Chạy trùng bị skip

**Given** job cron đang chạy
**When** admin gọi `POST /api/v1/admin/ai-news/run`
**Then** API trả `NEWS-101`
**And** không có 2 job chạy song song.

---

### AC-NEWS-08 – Permission

**Given** user là NORMAL_USER
**When** gọi `POST /api/v1/admin/ai-news/run`
**Then** API trả 403.

---

### AC-NEWS-09 – Archive

**Given** tin có `created_at` cách đây 91 ngày
**When** job cleanup chạy
**Then** `archive_yn = 'Y'`
**And** tin không còn hiện trong card AI News
**And** row vẫn tồn tại trong DB (không xóa).

---

# 11. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-NEWS-01 | Job chạy xong trong 10 phút với ≤ 6 source × 50 email |
| NFR-NEWS-02 | Credential mailbox chỉ trong secret store; log KHÔNG chứa password/body email |
| NFR-NEWS-03 | Job idempotent — chạy lại an toàn, không tạo dữ liệu trùng |
| NFR-NEWS-04 | IMAP chỉ đọc (READONLY) — job không gửi, xóa, đánh dấu email |
| NFR-NEWS-05 | `ai_news` query cho UI (limit 8, đã index) < 20ms |
| NFR-NEWS-06 | Mọi lần chạy (kể cả skip/fail) đều có log trong `ai_news_batch_log` |

---

# 12. DEV Checklists

## 12.1 Security

- [ ] IMAP credential đọc từ secret store, không hardcode, không log.
- [ ] IMAP connection READONLY.
- [ ] `POST /api/v1/admin/ai-news/run` + `GET /api/v1/admin/ai-news/logs` yêu cầu `ADMIN_USER` (403 cho NORMAL_USER).
- [ ] Body email không ghi ra log (chỉ log subject + message_id khi debug).

## 12.2 Database

- [ ] `ai_news` (bổ sung `message_id`, `body` (HTML), `sender`, `archive_yn`, `imported_at` + index).
- [ ] `ai_news_source` + seed 6 mailbox nguồn.
- [ ] `ai_news_batch_log`.
- [ ] Query UI dùng index `idx_ai_news_created`.

## 12.3 Integration

- [ ] Skill đọc email của Trường: xác nhận giao diện input/output (§3.1) + timeout 60s.
- [ ] Cron `0 0,12 * * *` (00:00 + 12:00 hàng ngày) + lock chống chạy chồng.
- [ ] Alert khi job `FAILED`/`PARTIAL` 3 lần liên tiếp (BR-NEWS-012).

## 12.4 Ops

- [ ] Thủ tục đổi secret khi mailbox đổi password.
- [ ] Thủ tục thêm/bớt mailbox (INSERT/UPDATE `ai_news_source`, không cần deploy).
- [ ] Dashboard log job.
- [ ] **Cấu hình env trước khi job chạy thật (v1.17 — đối chiếu codebase):** 6 mailbox seed trong `V27__seed_ai_news_sources.sql` dùng `imap_host = 'imap.internal'` (placeholder) — cần set env `AI_NEWS_DEFAULT_IMAP_HOST` (IMAP host thật, fallback cho source còn placeholder) + 6 secret env `AI_NEWS_SECRET_SDS` / `AI_NEWS_SECRET_GDC` / `AI_NEWS_SECRET_PART_X` / `AI_NEWS_SECRET_PART_Y` / `AI_NEWS_SECRET_GROUP1` / `AI_NEWS_SECRET_GROUP2` (map `secret_ref` → password, BR-NEWS-011). Chưa cấu hình → job trả `PARTIAL`/`FAILED` (mọi source fail auth/network).

---

# 13. QA Test Scope

- Import thành công từ từng mailbox nguồn
- Body lưu dạng HTML đã sanitize (không còn script/iframe)
- Idempotent: chạy 2 lần liên tiếp → lần 2 `total_inserted = 0`
- Cửa sổ 2 ngày: email 1 ngày (import) / 3 ngày (không import)
- Auto-reply/bounce bị loại
- Dedup theo `Message-ID` + fallback hash khi thiếu
- 1 source fail → `PARTIAL`, source khác OK, `last_success_run_at` không đổi
- Re-run sau downtime: lấy lại email bị bỏ, không trùng
- Mailbox có >50 email trong 1 chu kỳ → lần 1 import 50, lần 2 import phần còn lại (không mất email, không trùng — BR-NEWS-009)
- **Fetch chạm limit 50 mà toàn email TRÙNG (đã import trước đó)** → `last_success_run_at` vẫn tiến tới `MAX(created_at)` của 50 email đã fetch − 1s (KHÔNG phải `now()`), email mới hơn phía sau vẫn được lấy ở lần chạy sau (BR-NEWS-009 — chống mất email)
- Chạy trùng (cron + manual) → `NEWS-101`
- Permission: NORMAL_USER → 403
- UI: mọi user thấy toàn bộ tin; mỗi tin hiển thị sender (người gửi email)
- UI: click title tin → điều hướng màn hình AI News `/ai-news?news={newsId}` (history mode) hiện nội dung email (sender, ngày, body HTML); email không có body → info note (chi tiết test case: `AI_News_Screen_SRS.md` §8)
- Archive 90 ngày: tin biến mất khỏi UI, row còn trong DB
- Log: mọi lần chạy có record trong `ai_news_batch_log`

---

# 14. Version History

| Version | Status | Change |
|---|---|---|
| 1.0 | Draft | Tạo tài liệu batch job AI-NEWS-IMPORT: đọc email qua Skill đọc email của Trường, parse/classify/dedup, ghi `ai_news` + `ai_news_source` + `ai_news_batch_log`, cron 07:00, admin manual run, error handling & test scope |
| 1.1 | Draft | Đổi schedule: cron `0 0,12 * * *` — chạy 00:00 và 12:00 hàng ngày (2 lần/ngày, trước 07:00); alert stale source điều chỉnh > 14 giờ |
| 1.2 | Draft | Bỏ phân loại category (không còn cột `category`/`part`/`group` ở `ai_news` + `ai_news_source`); `body` lưu HTML đã sanitize (TEXT) thay vì text strip; thêm cột `sender`; API UI trả toàn bộ tin (không filter theo user) — Home hiển thị sender; thêm endpoint `GET /api/home/ai-news/{newsId}` cho popup xem nội dung email; cửa sổ import email cũ: 7 ngày → **2 ngày** (`date >= now() - 2d`) |
| 1.3 | Draft | Tách popup xem email thành tài liệu riêng: §7.4 + §13 QA tham chiếu `AI_News_Detail_Popup_SRS.md` (Spec + SRS mới). **v1.3.1:** Spec popup đã gộp vào SRS (file Spec xóa) — tham chiếu chỉ còn `AI_News_Detail_Popup_SRS.md`. |
| 1.4 | Draft | Review cross-doc: §6.1 note render body — bỏ "sandbox/iframe hoặc sanitizer phía client" (mâu thuẫn `AI_News_Detail_Popup_SRS.md` NFR-02) → tham chiếu NFR-02 (render `v-html` + CSP, client không sanitize lại) |
| 1.5 | Draft | Review logic: (1) **first-run** — source mới `last_success_run_at = NULL` → mốc import = `now() - 2d` (§3.3 rule 1, BR-NEWS-003, flow §4.2 bước 3a `since = last_success_run_at ?? (now() - 2d)`) — tránh `date > NULL` luôn false; (2) **cleanup archive 90 ngày** đưa vào flow §4.2 (bước 6, sau import) + DDL `ai_news_batch_log` thêm cột `total_archived` + §8: cleanup fail → log + alert, job status không đổi (archive best-effort) |
| 1.6 | Draft | Review logic (vòng 3): (1) **R1 — fix data loss**: fetch chạm limit 50 → `last_success_run_at` chỉ tiến tới `MIN(created_at)` email đã import (không `now()`) — phần vượt limit lấy lại lần sau (flow bước 5 + BR-NEWS-009 + QA test scope); (2) **R2**: §7.1 response manual run thêm `totalArchived`; (3) **R3**: fallback `message_id` SHA256 collision → append `-1/-2`…, không fail transaction (§4.3); (4) **R4**: UC-NEWS-002 ghi chú manual run synchronous có thể client timeout — tra kết quả qua logs, option async ghi nhận |
| 1.7 | Draft | Review logic (vòng 4): (1) **S1 — fix boundary bug**: mốc khi chạm limit sửa `MIN(created_at)` → **`MAX(created_at) − 1s`** (MIN làm mỗi lượt chỉ tiến 1 email; MAX thuần mất email cùng mốc) — flow bước 5 + BR-NEWS-009 + §3.3 rule 1; BR-NEWS-009 + §3.1 yêu cầu skill trả email **theo date tăng dần** (cũ trước) để phần vượt limit luôn mới hơn mốc; (2) **S3**: UC-NEWS-001 bước 4 tham chiếu flow bước 5 |
| 1.8 | Draft | Review vòng 5: (1) **H4** — alert stale source >14h chỉ áp dụng **source ACTIVE** (INACTIVE không alert — tắt có chủ đích, §8 Monitoring); (2) **H5** — BR-NEWS-012 + §8 bảng + §8 Monitoring nhất quán "job `FAILED`/`PARTIAL` 3 lần liên tiếp" |
| 1.9 | Draft | Review vòng 7: (1) **L1** — bổ sung dòng 1.8 vào version history (thiếu ở vòng 5); (2) **L3** — §12.3 checklist "Alert khi job fail 3 lần liên tiếp" → "job `FAILED`/`PARTIAL` 3 lần liên tiếp (BR-NEWS-012)" (đồng nhất BR-NEWS-012 + §8) |
| 1.10 | Draft | Review vòng 9 (N2): §7.1 error code `NEWS-102` "Job fail" → "Job status `FAILED`" (đồng nhất thuật ngữ với DDL `ai_news_batch_log.status` + BR-NEWS-012/§8) |
| 1.11 | Updated | Đồng bộ ngữ cảnh: Home chuyển sang **single page** (bỏ 2 tab Chart/Community + 9 chart theo SRS2 v1.1 — Home SRS v1.15); model user đổi sang **Email** (Login SRS v1.6) — không ảnh hưởng batch job (job không tham chiếu user identifier) |
| 1.12 | Updated | Gộp từ `SRSPhase2/` vào `..` (thư mục Phase2 đã xóa): sửa header `Module` — "single page, v1.11" → "Community tab" (Home SRS v1.16 đã đưa lại 2 tab Chart/Community). Nội dung yêu cầu không đổi. |
| 1.13 | Updated | Đồng bộ tham chiếu: AI News Detail **popup → màn hình độc lập `#/ai-news`** (đồng bộ `AI_News_Screen_SRS.md` v1.7 — file `AI_News_Detail_Popup_SRS.md` đã đổi tên): §6.1 note render body, §7.4 endpoint + tham chiếu, §13 QA (click title → điều hướng màn hình). Nội dung batch job không đổi. |
| 1.14 | Updated | Đồng bộ tham chiếu: AI News Screen thêm **Bình luận (threaded) + Like bản tin + Vote hữu ích trên comment** (đồng bộ `AI_News_Screen_SRS.md` v1.8): §7.4 thêm note 5 endpoint comment/like + 3 bảng DB mới (`ai_news_comment`, `ai_news_like`, `ai_news_comment_vote`). Batch job không ghi vào 3 bảng mới (BR-NEWS-001 giữ nguyên). Nội dung batch job không đổi. |
| 1.15 | Updated | Đồng bộ tham chiếu: AI News Screen **bỏ Like bản tin** (đồng bộ `AI_News_Screen_SRS.md` v1.9): §7.4 note sửa — còn 4 endpoint comment + 2 bảng DB (`ai_news_comment`, `ai_news_comment_vote`); bỏ `ai_news_like`. Batch job không ghi vào 2 bảng (BR-NEWS-001 giữ nguyên). Nội dung batch job không đổi. |
| 1.16 | Updated | Đồng bộ tham chiếu: AI News Screen **khôi phục Like bản tin** (đồng bộ `AI_News_Screen_SRS.md` v1.10): §7.4 note sửa — 5 endpoint comment/like + 3 bảng DB (`ai_news_comment`, `ai_news_comment_vote`, `ai_news_like`); like là **silent** (không toast khi like/unlike). Batch job không ghi vào 3 bảng (BR-NEWS-001 giữ nguyên). Nội dung batch job không đổi. |
| 1.17 | Updated | **Đối chiếu codebase thật + data thật (DEV/PROD)**: (1) **API prefix** — endpoint admin sửa `/api/admin/ai-news/*` → **`/api/v1/admin/ai-news/*`** (khớp `@RequestMapping` thật `AdminAiNewsController` + axios `baseURL: '/api/v1'`) ở §4.1, §7.1, §7.2, §9 UC-NEWS-002, §10 AC-NEWS-07/08, §12.1; (2) **§12.4 thêm checklist Ops** — cấu hình env trước khi job chạy thật: `AI_NEWS_DEFAULT_IMAP_HOST` + 6 secret env `AI_NEWS_SECRET_*` (6 mailbox seed `V27` dùng placeholder `imap.internal`; chưa cấu hình → job `PARTIAL`/`FAILED`); (3) **§13 QA** — `#/ai-news?news={newsId}` → `/ai-news?news={newsId}` (history mode — khớp router thật `createWebHistory`). Nội dung batch job không đổi. |
| 1.18 | Updated | **Fix logic BR-NEWS-009 (chống mất email)** — mốc `last_success_run_at` khi fetch chạm limit sửa từ "MAX(created_at) email **đã import**" → "MAX(created_at) email **đã FETCH** trong lượt đó" − 1s. Root cause: code chỉ track `maxImportedAt` (email mới insert); nếu 50 email fetch về mà toàn trùng (dedup skip) → mốc rỗng → fallback `now()` → email phía sau cửa sổ 50 bị bỏ sót vĩnh viễn. Chỗ sửa: BR-NEWS-009, flow §4.2 bước 5, §13 QA (thêm test case "fetch chạm limit toàn trùng"). Nội dung yêu cầu khác không đổi. |
