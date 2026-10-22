# AI Skill Management — User Management Module

Hệ thống quản lý Skill (AISTEA) — module **User Management** full-stack: xác thực (Login / Register 3 bước OTP / Forgot Password / Reset Password), Home + GNB, User Management (search / create / detail / bulk upload / export) và Settings (Profile / Preferences / Notifications / Change Password).

**Nền tảng UI:** Prism (Neon) Design System — `frontend/src/design-system/` (tokens `--ds-*`, components `Prism*`, dark-first).

**Tài liệu SRS:** `V2/Vibe coding basline/.../SRS/2. User management module/` (5 file: Login, Home/GNB, User Management, Settings SRS + Spec).

## Tech stack

| Lớp | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring Security (JWT stateless), Spring Data JPA, Flyway, Apache POI, Spring Mail, Maven |
| Frontend | Vue 3.5, TypeScript, Vite, Pinia, Vue Router, Axios, Prism Design System, lucide-vue-next |
| Database | PostgreSQL (remote DB dev — credentials trong `application-local.yml` hoặc env vars); Flyway migrations V1–V13 |

## Cấu trúc

```text
backend/
  src/main/java/com/skillmanagement/
    controller/    Authentication, AdminUser, User, Home, Notification, Health
    service/       Authentication, AdminUser, User, Home, Notification, Otp, Email, RateLimiter
    model/         User, Role, UserHistory, LoginManagement, OtpManagement, EmailManagement, UserSettings, Notification
    repository/    ...
    dto/           envelope ApiResponse { success, data | errorCode, message } + request/response records
    exception/     BusinessErrorCode, BusinessException, GlobalExceptionHandler
    config/        SecurityConfig, JwtAuthenticationFilter, DataSeeder
    db/migration/  V1–V11 (V7 user_management_schema, V8 seed roles, V9 version, V10 sequence, V11 settings defaults)
  src/test/        48 tests (Otp, Authentication, AdminUser, BulkUpload, Jwt, RefreshToken, FileStorage)
frontend/
  src/
    design-system/ Prism DS (56 components + tokens.css + components.css + useFloatingPanel.js)
    views/         auth/ (Login, Register, ForgotPassword, ResetPassword), Home, Settings, users/ (List, Create, Detail, BulkUpload)
    components/    layout/AppShell.vue (GNB), auth/OtpStep, auth/PasswordPolicy
    stores/        auth.ts, userList.ts
    services/      api.ts (axios + envelope), endpoints.ts, errorMessages.ts (errorCode → message)
    router/        guest routes + auth routes + admin guard
```

## Chạy local

Yêu cầu: Java 21, Node `^22.18.0 || >=24.12.0`, PostgreSQL.

Cấu hình DB dev: tạo file `backend/src/main/resources/application-local.yml` (đã gitignore) chứa datasource thật, hoặc truyền env vars `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

```bash
# Terminal 1 — backend (port 8080)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local    # hoặc: mvn.cmd spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — frontend (port 5173, proxy /api → 8080)
cd frontend
npm install
npm run dev
```

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/health`

**Admin credentials (dev):** `admin@skillmgmt.local` / `Admin@12345` (DataSeeder tạo nếu chưa có; nếu đã reset password qua Forgot Password thì dùng password mới).

**OTP dev mode:** OTP được log ra console backend (`[DEV OTP EMAIL] OTP: ...`). SMTP (`SmtpEmailServiceImpl`) chỉ bật khi `spring.mail.host` được cấu hình.

## API chính (prefix `/api/v1`)

Envelope: `{ success, data }` hoặc `{ success:false, errorCode, message }`.

| Nhóm | Endpoint |
|---|---|
| Auth | `POST /auth/login`, `/auth/register/request-otp`, `/auth/register/verify-otp`, `/auth/register`, `/auth/forgot-password/request-otp`, `/auth/forgot-password/verify-otp`, `/auth/reset-password`, `/auth/refresh`, `/auth/logout` |
| Home | `GET /home/summary`, `/home/my-recent-skills` |
| Notifications (GNB) | `GET /notifications/unread-count`, `GET /notifications?limit=10`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |
| User Management (ROLE_ADMIN_USER) | `POST /admin/users/search`, `GET /admin/users/{id}`, `POST /admin/users`, `PUT /admin/users/{id}`, `POST /admin/users/bulk/upload`, `POST /admin/users/bulk/save`, `POST /admin/users/export`, `GET /admin/users/organization-options` |
| Settings | `GET /users/me`, `PUT /users/me/profile` (multipart), `PUT /users/me/preferences`, `PUT /users/me/notifications`, `PUT /users/me/password` |

## Quyết định thiết kế chính

- **Email immutable** (Login SRS v1.6) — Settings Profile hiển thị Email read-only; backend reject `EMAIL_IMMUTABLE`.
- **Login rule:** email → password → status DEACTIVE → Part/Group (≥1 populated). User self-register có Part/Group NULL → không login được cho tới khi admin/batch gán org (`ORGANIZATION_NOT_SYNCED` / `ORGANIZATION_UNAVAILABLE`).
- **Optimistic lock** User Detail bằng `version` (409 `CONCURRENT_UPDATE_CONFLICT`).
- **Self-protection:** admin không đổi role/status của chính mình.
- **Bulk upload:** 1 file xlsx/xls ≤10MB, ≤500 rows; validate per-row giữ thứ tự; save partial (chỉ SUCCESS rows), backend re-validate.
- **Home skill data MOCKED** (`HomeServiceImpl`) cho tới khi có SRS module Skill Management.

## Kiểm tra

```bash
cd backend && mvn test          # 48 tests
cd frontend && npm run build    # type-check + vite build
```

## Phase tiếp theo

Module **Skill Management** (list, detail, add, review queue) + Skill Detail/Add popups — chờ SRS. Home hiện mock data; KPI cards và skill rows link về `/skills` (placeholder).
