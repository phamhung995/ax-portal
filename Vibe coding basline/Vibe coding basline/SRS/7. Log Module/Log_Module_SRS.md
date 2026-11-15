# SRS — Log Module (Audit Log)

## Software Requirements Specification

| Mục | Nội dung |
|-----|----------|
| **Tên chức năng** | Log Module — Audit Log (nhật ký thao tác) |
| **Dự án** | AX Portal — Hệ thống Quản lý AI Skill (AISTEA) |
| **Phiên bản** | 1.0 |
| **Ngày tạo** | 23/10/2026 |
| **Tác giả** | BA Team |
| **Trạng thái** | Baseline (đã implement backend; UI admin chưa có) |
| **Module code** | Backend: `com.furelise.skillmanagement.audit` · Migration: `V46__audit_log.sql` |

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu mô tả yêu cầu chức năng và phi chức năng của module **Log (Audit Log)** — ghi lại mọi thao tác viết (create / update / delete / import) quan trọng của người dùng vào một bảng audit tập trung, để admin có thể truy vết: **ai đã làm gì, trên object nào, lúc nào, từ IP nào**.

### 1.2. Bối cảnh

Hệ thống đã có một số bảng log chuyên biệt cho từng nghiệp vụ:

| Bảng | Ghi gì |
|------|--------|
| `login_management` | Mọi lần đăng nhập (thành công + thất bại) |
| `user_history` | Snapshot user mỗi lần tạo/sửa |
| `skill_status_history` | Mỗi lần đổi trạng thái skill (approve/reject/...) |
| `skill_view_log` / `skill_download_log` | Lượt xem / lượt tải skill |
| `email_management` / `otp_management` | Email gửi / OTP tạo |

Tuy nhiên **không có nơi tập trung** để trả lời câu hỏi "user X đã làm gì trong khoảng thời gian Y" — các thao tác như admin sửa user, xóa skill, đổi category/stage, import org... không được ghi ở đâu. Log Module bổ sung khoảng trống này bằng một bảng audit log chung.

### 1.3. Phạm vi

**Trong phạm vi (đã implement backend):**
- Bảng `audit_log` + entity + repository
- `AuditService` ghi log fail-safe tại 6 nhóm write operations
- API admin xem audit log (paged + filter)

**Ngoài phạm vi (chưa implement, để dev sau triển khai):**
- Màn hình Audit Log trên frontend (admin)
- Ghi log cho AI News admin actions, user_settings
- Rotation / archive dữ liệu audit log cũ
- Export Excel audit log

### 1.4. Công nghệ

| Lớp | Công nghệ |
|-----|-----------|
| Frontend | Vue 3 + TypeScript + Design System (Prism) |
| Backend | Java 21 (Spring Boot 3.4.2) |
| Database | PostgreSQL (Flyway migration V46) |
| Authentication | JWT (access 30 phút + refresh 24h) |

### 1.5. Tài liệu tham chiếu

| Tài liệu | Path |
|----------|------|
| Sitemap tổng thể | `../sitemap.md` |
| Error codes tổng thể | `../0. Error_Codes_Messages.md` |

---

## 2. Mô tả tổng thể

### 2.1. Người dùng (Actors)

| Vai trò | Mô tả |
|---------|-------|
| **Admin User** | Xem audit log qua API (tương lai: màn Audit Log) |
| **System** | Tự động ghi log khi các write operations thành công (user không thao tác trực tiếp với module này) |

### 2.2. Nguyên tắc thiết kế

| # | Nguyên tắc | Chi tiết |
|---|-----------|----------|
| BR-LOG-01 | **Fail-safe** | Lỗi ghi audit log (DB down, timeout...) chỉ log warn, **không bao giờ** làm fail thao tác nghiệp vụ |
| BR-LOG-02 | **Transaction riêng** | Audit log commit trong transaction `REQUIRES_NEW` — nghiệp vụ rollback thì audit vẫn giữ (biết có ai đó đã thử thao tác); audit lỗi thì không ảnh hưởng nghiệp vụ |
| BR-LOG-03 | **Ghi sau khi thành công** | Chỉ ghi log sau khi save thành công; validation fail → không ghi log (tránh log rác) |
| BR-LOG-04 | **Chỉ write operations** | Read operations (search, detail, list) không ghi audit log |
| BR-LOG-05 | **Không ghi log cho thao tác đã có bảng log riêng** | Login, OTP, email, skill status change, view/download — tránh trùng lặp |
| BR-LOG-06 | **IP từ request hiện tại** | Lấy qua `RequestContextHolder`: header `X-Forwarded-For` (nginx) → fallback `remoteAddr`; batch job (không có HTTP request) → `ip_address = NULL` |
| BR-LOG-07 | **Actor nullable** | Hệ thống (batch job, seeder) ghi log với `actor_id = NULL` |
| BR-LOG-08 | **Chi tiết có giới hạn** | Cột `details` truncate tối đa 2000 ký tự |

### 2.3. Mô hình dữ liệu

Bảng `audit_log` (migration `V46__audit_log.sql`):

| Cột | Kiểu | Ràng buộc | Ý nghĩa |
|-----|------|-----------|---------|
| `id` | BIGINT | PK, identity | ID log |
| `actor_id` | BIGINT | FK → `"user"(id)`, nullable | Người thực hiện |
| `actor_name` | VARCHAR(100) | nullable | Tên người thực hiện (denormalized cho tiện xem) |
| `action` | VARCHAR(100) | NOT NULL | `CREATE` / `UPDATE` / `DELETE` / `IMPORT` |
| `entity_type` | VARCHAR(50) | NOT NULL | Loại object (xem §3.3) |
| `entity_id` | VARCHAR(64) | nullable | ID object (string — hỗ trợ cả id không phải số) |
| `details` | TEXT | nullable | Tóm tắt thay đổi, vd: `name=Foo, status=APPROVED` |
| `ip_address` | VARCHAR(64) | nullable | IP client |
| `created_at` | TIMESTAMPTZ | NOT NULL, default now | Thời điểm thao tác |

Index:
- `idx_audit_log_actor_time (actor_id, created_at)` — filter theo người dùng
- `idx_audit_log_entity (entity_type, entity_id)` — filter theo object
- `idx_audit_log_created_at (created_at)` — filter theo thời gian / sort

### 2.4. Code structure

```
backend/src/main/java/com/furelise/skillmanagement/audit/
├── model/AuditLog.java                  # JPA entity
├── repository/AuditLogRepository.java   # JpaRepository + JpaSpecificationExecutor
├── service/AuditService.java            # Interface (2 overload)
├── service/impl/AuditServiceImpl.java   # Fail-safe log + paged query
├── controller/AuditLogController.java   # GET /api/v1/admin/audit-logs
└── dto/
    ├── AuditLogItem.java                # 1 row log
    └── AuditLogPage.java                # {total, page, pageSize, items}
```

---

## 3. Yêu cầu chức năng

### 3.1. Ghi audit log (System)

`AuditService` cung cấp 2 overload:

| Method | Actor lấy từ | Dùng khi |
|--------|-------------|----------|
| `log(User actor, String action, String entityType, String entityId, String details)` | Truyền trực tiếp | Service đã có sẵn đối tượng `User` |
| `log(String action, String entityType, String entityId, String details)` | `SecurityContextHolder` (principal đã auth) | Controller không truyền user (taxonomy, org import) |

### 3.2. Các vị trí ghi log hiện tại

| # | Entity | Action | Vị trí code | Details |
|---|--------|--------|-------------|---------|
| 1 | SKILL | CREATE | `SkillServiceImpl.create` | `name=..., scope=...` |
| 2 | SKILL | UPDATE | `SkillServiceImpl.update` | `name=..., scope=..., status=...` |
| 3 | SKILL | DELETE | `SkillServiceImpl.deleteBatch` (mỗi skill 1 row) | `name=...` |
| 4 | USER | CREATE | `AdminUserServiceImpl.createUser` | `name=..., email=...` |
| 5 | USER | UPDATE | `AdminUserServiceImpl.updateUser` (chỉ khi có thay đổi thật) | `name=..., role=..., status=...` |
| 6 | USER | CREATE | `AdminUserServiceImpl.saveBulkUpload` (1 row tổng) | `bulk upload: N created, M skipped` |
| 7 | SKILL_CATEGORY | CREATE / UPDATE / DELETE | `SkillTaxonomyServiceImpl` | `name=...` |
| 8 | PROCESS_STAGE | CREATE / UPDATE / DELETE | `SkillTaxonomyServiceImpl` | `name=...` |
| 9 | FEEDBACK | CREATE | `FeedbackServiceImpl.submit` | `type=...` |
| 10 | FEEDBACK | UPDATE | `FeedbackServiceImpl.changeStatus` | `status=..., note=...` |
| 11 | ORGANIZATION | IMPORT | `OrganizationServiceImpl.importExcel` (1 row tổng) | `rows=..., success=..., matched=..., deactivated=...` |

**Quy tắc khi thêm vị trí ghi log mới:**
- Gọi `auditService.log(...)` **sau** `repository.save(...)` thành công, trong cùng transaction nghiệp vụ
- `action` dùng chữ hoa: `CREATE`, `UPDATE`, `DELETE`, `IMPORT`
- `entity_type` dùng chữ hoa, không dấu: `SKILL`, `USER`, `SKILL_CATEGORY`, `PROCESS_STAGE`, `FEEDBACK`, `ORGANIZATION`
- `details` là chuỗi key=value ngắn gọn, không chứa secret (password, token)

### 3.3. API xem audit log (Admin)

**`GET /api/v1/admin/audit-logs`** — chỉ `ADMIN_USER`

| Tham số | Kiểu | Mặc định | Mô tả |
|---------|------|----------|-------|
| `page` | int | 1 | Số trang (1-based) |
| `pageSize` | int | 20 | Số item/trang (max 100) |
| `actorName` | string | — | Filter tên người thực hiện (LIKE, case-insensitive) |
| `entityType` | string | — | Filter loại object (exact, case-insensitive) |
| `action` | string | — | Filter hành động (exact, case-insensitive) |

**Response** (envelope chuẩn):
```json
{
  "success": true,
  "data": {
    "total": 137,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "id": 137,
        "actorId": 2,
        "actorName": "Tạ Quang Hợp",
        "action": "DELETE",
        "entityType": "SKILL",
        "entityId": "45",
        "details": "name=Test Skill",
        "ipAddress": "10.1.2.3",
        "createdAt": "2026-10-23T09:15:00Z"
      }
    ]
  }
}
```

Sort: mới nhất trước (`id DESC`).

### 3.4. Màn hình Audit Log (frontend — CHƯA implement)

Yêu cầu đề xuất cho dev triển khai sau:

- **Route:** `/audit-logs`, chỉ `ADMIN_USER` (thêm vào adminNavItems trong `AppShell.vue`)
- **Grid:** cột Actor, Action, Entity, ID, Details, IP, Time
- **Filter:** Actor (text), Entity Type (dropdown: SKILL / USER / SKILL_CATEGORY / PROCESS_STAGE / FEEDBACK / ORGANIZATION), Action (dropdown: CREATE / UPDATE / DELETE / IMPORT)
- **Paging:** theo chuẩn grid chung (20/50/100)
- **Chi tiết:** click row → popup xem full `details` (có thể dài > 2000 ký tự bị truncate)
- Tham khảo màn `UserManagementView` / `FeedbackManagementView` về layout grid + filter

---

## 4. Yêu cầu phi chức năng

| # | Yêu cầu |
|---|---------|
| NFR-LOG-01 | Ghi log không được tăng thời gian phản hồi của API > 10ms (1 insert đơn, transaction riêng) |
| NFR-LOG-02 | Audit log là append-only: không có API update/delete log (chỉ DBA can thiệp trực tiếp DB khi cần) |
| NFR-LOG-03 | Dữ liệu log không chứa secret (password, OTP, token) — chỉ metadata thao tác |
| NFR-LOG-04 | Thời gian lưu trữ: tối thiểu 12 tháng (chưa có auto-archive — dev sau bổ sung) |

---

## 5. Error handling

| Tình huống | Xử lý |
|-----------|-------|
| DB lỗi khi insert audit log | Catch trong `AuditServiceImpl.log`, log warn `Audit log write failed (...)`, nghiệp vụ tiếp tục |
| Admin không có quyền gọi API | 403 (Spring Security `@Secured("ROLE_ADMIN_USER")`) |
| Filter param không hợp lệ | Trả về trang rỗng (không có row khớp), không báo lỗi |

---

## 6. Acceptance criteria

| # | AC | Kịch bản |
|---|----|---------|
| AC-LOG-01 | Admin xóa 1 skill → có 1 row `audit_log` (action=DELETE, entity_type=SKILL, entity_id đúng, actor đúng, IP đúng) |
| AC-LOG-02 | User thường tạo skill → có row CREATE SKILL |
| AC-LOG-03 | Admin sửa user đổi role → có row UPDATE USER với details chứa role mới; sửa mà không đổi gì → không ghi log |
| AC-LOG-04 | Bulk upload 10 user (8 thành công, 2 lỗi) → 1 row CREATE USER `bulk upload: 8 created, 2 skipped` |
| AC-LOG-05 | Import org excel → 1 row IMPORT ORGANIZATION với thống kê |
| AC-LOG-06 | `GET /api/v1/admin/audit-logs?entityType=SKILL&action=DELETE` chỉ trả về log xóa skill, sort mới nhất trước |
| AC-LOG-07 | User thường gọi API audit-logs → 403 |
| AC-LOG-08 | Tắt DB (mô phỏng) rồi tạo skill → skill vẫn tạo thành công, log warn hiện trong console |
| AC-LOG-09 | Transaction nghiệp vụ bị rollback (vd: lỗi sau khi save) → row audit log vẫn tồn tại (REQUIRES_NEW) |
| AC-LOG-10 | Batch job (không có HTTP request) ghi log → `ip_address = NULL`, không crash |

---

## 7. Ghi chú triển khai

- **Branch:** `tq.dat1/organization_mgt` (backend đã commit-ready, chưa commit tại thời điểm viết SRS)
- **Test:** 201/201 backend tests pass (kèm fix test `update_publishedAdminNameChange_allowed` — stub rateLimiter thừa do PR #49)
- **Thứ tự deploy:** migration V46 chạy tự động qua Flyway khi app khởi động; không cần can thiệp thủ công
- **Điểm cần lưu ý khi dev sau mở rộng:**
  - Nếu thêm dependency `AuditService` vào service đang có test `@InjectMocks`, phải thêm `@Mock AuditService auditService` vào test
  - `OrganizationServiceImpl` dùng constructor explicit (không `@RequiredArgsConstructor`) — thêm tham số constructor khi inject
  - Không ghi audit log cho skill approve/reject — đã có `skill_status_history` (BR-LOG-05)
