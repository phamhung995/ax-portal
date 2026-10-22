# Báo cáo kiểm thử chức năng — 24/09/2026

## Cập nhật sau khi sửa lỗi — PostgreSQL 17

Các mục **Phát hiện** và bảng kịch bản bên dưới ghi lại kết quả của lượt kiểm thử ban đầu. Trạng thái hiện tại của 11 lỗi được chốt theo lượt kiểm thử lại sau khi sửa:

| Mã | Trạng thái | Bằng chứng kiểm thử lại |
|---|---|---|
| F-01 | Đã sửa | Upload `.md` từ Vue thành công, tạo skill PENDING trên PostgreSQL. Multipart dùng part `metadata`. |
| F-02 | Đã sửa | Curator hiển thị 2 skill PENDING từ `Page.content`, duyệt skill QA thành công; có phân trang. |
| F-03 | Đã sửa | Sửa title trong My Skills qua Vue thành công, PUT multipart trả 200. |
| F-04 | Đã sửa | Dashboard hiển thị 5 skill phát hành, 496 lượt dùng, 4,75 MM/tháng và leaderboard; không có lỗi console. |
| F-05 | Đã sửa | `/skills/my` chỉ trả skill của principal; tài khoản USER thấy 3 skill của mình ở tab ALL, KPI published/pending/MM hiển thị đúng. |
| F-06 | Đã sửa | Chọn tên tăng dần trên trình duyệt cho thứ tự `AI Code...`, `Automated...`, `Kubernetes...`; backend nhận `sortBy/sortOrder`. |
| F-07 | Đã sửa | Ghi impact 0,5 MM trên Vue; tổng hiện 0,5 MM ngay sau lưu và sau reload. |
| F-08 | Đã sửa | Nhập tên skill mẫu trên form upload làm xuất hiện banner cảnh báo trùng; endpoint chỉ gợi ý skill PUBLISHED. |
| F-09 | Đã sửa | Logout UI chuyển về Login và gọi API thu hồi access/refresh token; API thử lại access trả 403, refresh cũ trả 401. |
| F-10 | Đã sửa | Tạo/xóa user QA qua API sinh audit `USER_CREATE`/`USER_DELETE`; các thao tác đổi role/status/reset cũng đã thêm log và unit assertion. |
| F-11 | Đã sửa | USER khác không thấy skill PENDING của admin trong `status=ALL`; tải file và xem phiên bản đều trả 403. |

**Vấn đề phát hiện thêm khi kiểm thử lại:** File seed PostgreSQL dùng `APPROVED` cho `skill.status` trong khi workflow dùng `PUBLISHED`; migration Flyway V6 đã chuẩn hóa 5 bản ghi cũ và file seed được sửa cho lần nạp sau. Endpoint deprecate trước đây trả thông báo thành công nhưng không đổi trạng thái; nay gọi đúng nghiệp vụ, đã xác minh `PUBLISHED → DEPRECATED` kèm lý do trên PostgreSQL.

**Xác minh cuối:** 57/57 JUnit pass, 5/5 Vitest pass, Vue type check và Vite build pass, `git diff --check` pass. Backend kết nối PostgreSQL 17.11, Flyway lên version 6. Skill/file QA và user QA tạo trong lượt kiểm thử lại đã được dọn; sau dọn DB còn 7 skill mẫu, 5 PUBLISHED, tổng MM tháng là 4,75 như trước khi tạo fixture. Vẫn còn cảnh báo build chunk >500 kB và các nhánh `NOT RUN` trong lượt kiểm thử ban đầu chưa được mở rộng kiểm thử ở lượt sửa lỗi này.

## Phạm vi và cách chạy

Đã lập kịch bản cho các route Vue và nhóm REST API trong project. Kiểm thử trình duyệt dùng Vite tại `http://localhost:5173` và backend Spring Boot tại `http://localhost:8080`; các luồng ghi dữ liệu chính được chạy trên H2 in-memory (`jdbc:h2:mem:qatest` và `jdbc:h2:mem:qaaccess`, Flyway tắt). Có một lượt kiểm tra đăng ký ban đầu trên PostgreSQL local đang tồn tại; ba tài khoản QA được tạo trong lượt đó đã được xóa qua API `/user/account`. Ba file skill và hai file nguồn QA đã được xóa sau khi dừng các lượt H2.

**Quy ước:** `PASS` = đã quan sát đúng kết quả; `FAIL` = đã tái hiện sai lệch; `PARTIAL` = mới kiểm tra một nhánh; `NOT RUN` = kịch bản đã thiết kế nhưng chưa thực thi. `API` là gọi REST trực tiếp để tạo fixture/xác minh hợp đồng; không đồng nghĩa giao diện hoạt động.

### Kết quả bộ kiểm thử sẵn có

| Kiểm tra | Lệnh / môi trường | Kết quả |
|---|---|---|
| Backend JUnit | `backend/mvnw.cmd -o test`, Java 21, H2 test | **54/54 pass**, 0 failures/errors/skipped |
| Frontend Vitest | `node node_modules/vitest/vitest.mjs run` | **2/2 pass** (chỉ auth store khởi tạo/logout) |
| Type check | `node node_modules/vue-tsc/bin/vue-tsc.js --noEmit` | Pass |
| Build | `node node_modules/vite/bin/vite.js build` | Pass; cảnh báo bundle >500 kB |
| PostgreSQL local | Backend profile mặc định, PostgreSQL 17.11 | Khởi động và đọc schema Flyway version 5 thành công; **chưa** chạy migration từ DB trắng |
| Docker Compose | `docker info` | Không chạy được: quyền truy cập Docker Engine bị từ chối |

`npm` trong PATH bị lỗi trỏ tới `npm-cli.js` không tồn tại; chạy trực tiếp các CLI đã cài trong `frontend/node_modules`. Maven wrapper cần `JAVA_HOME=C:\Program Files\Java\jdk-21` và `MAVEN_OPTS=-Duser.home=C:\Users\Harry` trên máy này. Lượt `spring-boot:run` đầu với profile test vẫn kết nối PostgreSQL do cấu hình datasource test không được nạp trong lệnh chạy; lượt H2 sau đã **xác minh trong log** là `jdbc:h2:mem:qatest` bằng các biến môi trường datasource/Flyway/JPA tường minh.

## Danh mục kịch bản toàn chức năng

Mỗi dòng là một nhóm ca kiểm thử gồm nhánh hợp lệ, không hợp lệ và kiểm tra quyền/trạng thái khi áp dụng. Các ca chưa chạy không được tính là pass.

| ID | Chức năng / kịch bản | Kỳ vọng | Kết quả |
|---|---|---|---|
| HLT-01 | `GET /health`, có/không JWT | `200`, `status=UP` | PASS API |
| AUT-01 | Đăng ký đủ trường; đăng ký thiếu/sai email, mật khẩu ngắn, email trùng | Hợp lệ tạo USER; lỗi trả 400 rõ trường | PARTIAL: đăng ký hợp lệ PASS UI; các nhánh lỗi chưa chạy trên origin chuẩn (mã UI không khai báo validation rules) |
| AUT-02 | Đăng nhập đúng/sai mật khẩu; tài khoản khóa | Đúng vào Dashboard, sai bị từ chối | PARTIAL: đăng nhập đúng PASS UI; nhánh sai/chặn chưa chạy UI |
| AUT-03 | Refresh rotation; logout; logout-all; dùng lại access/refresh token | Token cũ bị từ chối theo chính sách | UNIT backend; browser logout chỉ xóa token phía client, xem F-09 |
| AUT-04 | Guest vào route bảo vệ; USER vào `/admin`, `/curator`; ADMIN vào route quyền cao | Guard redirect và API trả 403 tương ứng | PARTIAL: guest và USER vào `/admin` redirect; ADMIN vào Admin/Curator PASS UI |
| USR-01 | Xem/sửa profile, validation tên/điện thoại | Hồ sơ hiển thị/cập nhật đúng | PARTIAL: xem profile PASS UI; cập nhật NOT RUN |
| USR-02 | Đổi mật khẩu đúng/sai mật khẩu cũ, xác nhận mật khẩu; đăng nhập lại | Chỉ mật khẩu hợp lệ được lưu | NOT RUN UI; UNIT backend |
| USR-03 | Avatar đúng định dạng/kích thước, file sai; xóa tài khoản | Lưu/xóa theo quyền, dọn file | NOT RUN UI; ba tài khoản QA PostgreSQL đã xóa qua API để dọn dữ liệu |
| ADM-01 | Liệt kê role/user, tạo user, email trùng | ADMIN tạo được user, bảng cập nhật | PASS UI với user H2; nhánh email trùng NOT RUN |
| ADM-02 | Đổi role, khóa/mở user, tự khóa admin | Quyền và trạng thái áp dụng đúng | NOT RUN UI; UNIT backend |
| ADM-03 | Reset mật khẩu, xóa user và chặn tự xóa admin | Mật khẩu mới/xóa đúng đối tượng | NOT RUN UI; UNIT backend |
| ADM-04 | Audit log: danh sách, lọc action/entity/ngày, phân trang, chỉ ADMIN | Hành động quan trọng được ghi và xem được | PARTIAL: trang/lọc render; tạo user qua Admin UI không sinh log, xem F-10 |
| CAT-01 | Danh mục list/create/update/delete, quyền curator/admin | Dữ liệu và quyền đúng | PARTIAL: category được seed và hiển thị trong select; CRUD API NOT RUN |
| SKL-01 | Upload skill hợp lệ, thiếu trường/file, file quá lớn/sai loại, security scan | Hợp lệ tạo PENDING; lỗi được giải thích | FAIL UI: multipart thiếu `metadata` (F-01); validation thiếu tên PASS UI; API upload đúng hợp đồng PASS |
| SKL-02 | Cảnh báo skill tương tự khi rời title/description | Gợi ý skill trùng phù hợp | NOT RUN động; endpoint frontend gọi không tồn tại (F-08) |
| SKL-03 | Danh sách published: tìm theo title/description/tag, category, phân trang | Kết quả đúng filter | PARTIAL: hai skill published hiện; search/filter/page chưa kiểm đủ |
| SKL-04 | Sắp xếp mới nhất/rating/usage/tên; tăng/giảm | Thứ tự khớp lựa chọn | FAIL UI: chọn “Tên (A-Z)” nhưng vẫn theo createdAt giảm dần (F-06) |
| SKL-05 | “My Skills” ALL/PENDING/PUBLISHED/REJECTED/DEPRECATED, chỉ skill của mình | Tất cả tab và KPI theo user đăng nhập | FAIL UI: ALL rỗng dù có skill PENDING, tab PENDING hiện cả skill của user khác; KPI trống (F-04/F-05) |
| SKL-06 | Chi tiết/phiên bản/tải latest và phiên bản cũ; quyền xem pending | Nội dung/file đúng, pending chỉ author/curator | PARTIAL: chi tiết và version hiện; API tải PENDING cho user khác sai quyền (F-11); tải phiên bản cũ NOT RUN |
| SKL-07 | Sửa metadata, upload version mới, xóa mềm, deprecate | Cập nhật đúng và theo workflow | FAIL UI khi sửa: HTTP 415 (F-03); các nhánh còn lại NOT RUN |
| APR-01 | Pending list và quyền curator/admin | Danh sách PENDING thật, phân trang đúng | FAIL UI: API trả 2 bản ghi nhưng bảng rỗng (F-02) |
| APR-02 | Approve/reject với lý do, trạng thái và audit | Skill chuyển PUBLISHED/REJECTED đúng | PASS API approve 2 skill H2; UI bị chặn bởi F-02; reject NOT RUN |
| APR-03 | Merge 2 skill hợp lệ/trùng ID/lý do trống, bảo toàn rating/review | Merge và chuyển dữ liệu đúng | PARTIAL: form chặn thiếu skill; merge thực NOT RUN |
| RAT-01 | Gửi/cập nhật rating 1–5, rating của tôi, trung bình | Điểm và count đúng | UNIT backend; browser slider NOT RUN |
| REV-01 | Tạo review rỗng/hợp lệ, sửa/xóa của mình, quyền người khác | Validation và quyền đúng | PARTIAL: rỗng bị chặn, tạo review PASS UI; edit/delete NOT RUN |
| REV-02 | Vote helpful một lần/ngày hoặc theo chính sách; báo cáo review | Count, chống spam và báo cáo đúng | PARTIAL: vote PASS UI; dialog report render; gửi report NOT RUN |
| USE-01 | Record usage lần đầu/lặp cùng ngày, tổng lượt | Lần lặp không tăng count | PARTIAL: lần đầu PASS UI; H2 không mô phỏng được cột generated `usage_date` của PostgreSQL nên nhánh idempotent chưa xác nhận |
| IMP-01 | Ghi impact với RFC, score, MM; validation; tổng MM | Bản ghi và tổng hiển thị đúng | FAIL UI: bản ghi tạo được nhưng tổng thành `NaN MM` (F-07) |
| STA-01 | Dashboard KPI, leaderboard 4 tab, empty state | Dữ liệu khớp REST và không crash | FAIL UI: `TypeError ... reading 'length'` (F-04) |
| STA-02 | My stats theo user, tổng skill, usage, MM | KPI đúng user | FAIL UI: nhiều KPI trống do lệch tên trường (F-05) |
| SEC-01 | Không JWT/JWT lỗi/hết hạn; 401/403; direct API tới tài nguyên pending | Không lộ tài nguyên trái quyền | UNIT backend một phần; API H2 xác nhận user khác thấy PENDING qua `status=ALL` và tải file được (F-11) |
| DB-01 | Flyway V1/V2/V4/V5 trên PostgreSQL sạch; H2 vs PG, unique usage theo ngày | Migration chạy và ràng buộc DB đúng | NOT RUN migration sạch; startup schema PG hiện có PASS |

## Phát hiện

### F-01 — P0 — Upload skill từ UI luôn lỗi HTTP 400

**Tái hiện:** ADMIN hoặc USER vào `/skills/upload`, nhập title/description, chọn category, đính kèm `.md`, bấm “Đăng tải Skill”. **Thực tế:** toast `Required part 'metadata' is not present.`; skill không tạo. **Nguyên nhân:** [SkillUploadView.vue](../frontend/src/views/SkillUploadView.vue) append multipart part tên `data`, trong khi [SkillController.java](../backend/src/main/java/com/skillmanagement/controller/SkillController.java) yêu cầu `@RequestPart("metadata")`. API upload cùng nội dung với part `metadata` trả 201/PENDING trên H2. Cần đồng bộ tên part và thêm test hợp đồng multipart.

### F-02 — P0 — Curator không thấy skill chờ duyệt

**Tái hiện:** API tạo 2 skill PENDING; mở `/curator/pending`. **Thực tế:** bảng không có dòng, console `Expected Array, got Object` và `TypeError: rows is not iterable`; API `/api/v1/skills/pending` trả `Page` với `content` gồm 2 skill. [curatorService.ts](../frontend/src/services/curatorService.ts) khai báo trả `PendingSkillResponse[]`; [PendingApprovalView.vue](../frontend/src/views/PendingApprovalView.vue) gán cả response cho `el-table`. Vì nút approve/reject nằm trong dòng, workflow kiểm duyệt từ UI bị chặn. Cần đọc `response.content` và xử lý phân trang.

### F-03 — P1 — Sửa skill từ My Skills lỗi HTTP 415

**Tái hiện:** mở tab PENDING của `/skills/my`, bấm “Sửa” cho skill do chính admin tạo, nhập changelog rồi lưu. **Thực tế:** toast `Content-Type 'application/json' is not supported`. [skillService.ts](../frontend/src/services/skillService.ts) gửi JSON cho PUT; [SkillController.java](../backend/src/main/java/com/skillmanagement/controller/SkillController.java) chỉ nhận `multipart/form-data` với part `metadata`. Cần thống nhất payload cập nhật, gồm cả trường hợp chỉ sửa metadata.

### F-04 — P1 — Dashboard lỗi render sau khi tải leaderboard

**Tái hiện:** đăng nhập và chờ `/dashboard` tải xong. **Thực tế:** console `TypeError: Cannot read properties of undefined (reading 'length')` trong `DashboardView.vue`; các KPI không hiện. Backend [StatsDto.java](../backend/src/main/java/com/skillmanagement/dto/StatsDto.java) trả `topRatedSkills/topUsedSkills/topImpactSkills`; [DashboardView.vue](../frontend/src/views/DashboardView.vue) đọc `byRating/byUsage/byImpact`. Shape của dashboard stats cũng khác (`skillsByStatus` so với `publishedSkills/pendingSkills`). Type check pass vì các kiểu TypeScript không kiểm JSON runtime. Cần chuẩn hóa DTO tại API hoặc adapter frontend và test hợp đồng.

### F-05 — P1 — “My Skills” lọc sai chủ sở hữu và KPI trống

**Tái hiện:** trên H2 tạo skill 1 bởi Admin và skill 2 bởi QA H2 User; đăng nhập Admin, vào `/skills/my`. **Thực tế:** tab ALL rỗng dù Admin có skill PENDING; tab PENDING hiển thị cả skill của QA H2 User, kèm nút Sửa/Xóa. [MySkillsView.vue](../frontend/src/views/MySkillsView.vue) gọi API `/skills` theo status, không có author filter; [SkillService.java](../backend/src/main/java/com/skillmanagement/service/SkillService.java) mặc định status=PUBLISHED và không lọc theo user. Chỉ số Published/Pending/MM trống vì frontend dùng `myPublishedSkillsCount/myPendingSkillsCount/totalMmSavedByMySkills`, còn backend trả `myPublishedCount/mySkillsCount/totalMmSavedByMe`. Cần endpoint hoặc filter theo principal và đồng bộ stats DTO. Kiểm tra quyền ở backend update/delete vẫn cần test riêng; đây là lỗi hiển thị và phạm vi dữ liệu trong UI.

### F-06 — P1 — Bộ sắp xếp trên trang Skills không tác dụng

**Tái hiện:** có hai skill `QA H2 API Seed Skill` (tạo trước) và `QA H2 Other User Skill` (tạo sau); chọn “Tên (A-Z)”. **Thực tế:** `Other` vẫn đứng trước `API Seed`. [SkillListView.vue](../frontend/src/views/SkillListView.vue) gửi `sortBy/sortOrder`, nhưng [SkillController.java](../backend/src/main/java/com/skillmanagement/controller/SkillController.java) không nhận hai query param và [SkillService.java](../backend/src/main/java/com/skillmanagement/service/SkillService.java) luôn sort `createdAt` giảm dần.

### F-07 — P1 — Tổng impact thành `NaN MM`

**Tái hiện:** tại `/skills/2`, ghi impact RFC `QA-RFC-H2`, 0.5 MM. **Thực tế:** bản ghi hiện 0.5 MM, nhưng header đổi từ `Tổng tiết kiệm: MM` thành `Tổng tiết kiệm: NaN MM`. [SkillDetailView.vue](../frontend/src/views/SkillDetailView.vue) dùng `skill.totalMmSaved` và cộng 0.5; [SkillDto.java](../backend/src/main/java/com/skillmanagement/dto/SkillDto.java) không có trường `totalMmSaved` trong `SkillResponse`. Cần lấy tổng từ impact records hoặc bổ sung DTO, rồi kiểm thử sau ghi/reload.

### F-08 — P2 — Gợi ý skill tương tự không có API

[SkillUploadView.vue](../frontend/src/views/SkillUploadView.vue) gọi `GET /skills/similar` khi blur title/description và nuốt lỗi; [SkillController.java](../backend/src/main/java/com/skillmanagement/controller/SkillController.java) không có mapping này. Đây là **phát hiện từ mã nguồn**, chưa tái hiện giao diện với dữ liệu tương tự. Người dùng sẽ không thấy cảnh báo trùng dù tính năng được trình bày trong form.

### F-09 — P1 — Logout UI không thu hồi token ở backend

[auth.ts](../frontend/src/stores/auth.ts) chỉ xóa `token` trong localStorage và điều hướng `/login`; không gọi `/auth/logout` hoặc gửi refresh token để revoke. [AuthenticationController.java](../backend/src/main/java/com/skillmanagement/controller/AuthenticationController.java) có endpoint blacklist/revoke, nhưng UI không sử dụng. **Phát hiện từ luồng mã nguồn**; chưa thực thi ca dùng lại chính token sau logout. Cần xác định yêu cầu bảo mật mong muốn và thêm test bằng token đã đăng xuất.

### F-10 — P2 — Hành động quản trị user không có audit record

Trong H2, tạo user bằng Admin UI thành công rồi mở `/admin/audit` vẫn `No Data`. [AdminServiceImpl.java](../backend/src/main/java/com/skillmanagement/service/impl/AdminServiceImpl.java) không gọi `AuditLogService` ở create/role/status/reset/delete user. Nếu audit log nhằm theo dõi “toàn bộ hành động quan trọng” như mô tả ở UI, cần bổ sung các event quản trị và test lọc.

### F-11 — P0 — USER khác xem metadata và tải file skill PENDING

**Tái hiện trên H2 với hai tài khoản USER:** author tạo `QA Pending Private Skill` ở trạng thái PENDING. USER khác gọi `GET /skills?status=ALL` nhận HTTP 200, trong `content` có title/status/fileName của skill; `GET /skills/1/download` nhận HTTP 200 và 30 byte nội dung file. Cùng token gọi `GET /skills/1` nhận HTTP 403. [SkillService.java](../backend/src/main/java/com/skillmanagement/service/SkillService.java) có kiểm quyền ở `getSkillDetail`, nhưng `getSkills` và `getDownloadFile` không áp dụng kiểm tra tương tự. Cần chặn danh sách/download theo trạng thái và principal tại backend; không dựa vào router/frontend để bảo vệ dữ liệu.

## Giới hạn và thứ tự xử lý đề xuất

Các luồng upload → pending → approve/reject bị chặn liên tiếp bởi F-01 và F-02; skill test phải tạo/approve qua API nên không thể kết luận UI hoàn chỉnh. Chưa kiểm thử trực tiếp tải file, avatar, đổi mật khẩu, role/status/reset/delete của Admin, review edit/delete/report, rating slider, merge/deprecate, và migration PostgreSQL trên DB sạch. H2 không có cột `usage_date` generated giống migration PostgreSQL; không dùng kết quả H2 để kết luận tính idempotent của usage.

Ưu tiên xử lý F-11 vì làm lộ nội dung chưa duyệt, rồi F-01/F-02 để mở đường kiểm thử quy trình chính; tiếp theo F-03/F-04/F-05/F-07. Bộ test hiện tại xanh nhưng frontend chỉ có 2 test auth store; cần thêm test hợp đồng API/DTO và test giao diện cho upload, pending, dashboard, My Skills. [TESTCASE_CATALOG.csv](testcases/TESTCASE_CATALOG.csv) là danh mục cũ: một số kỳ vọng như “đăng ký chuyển Dashboard” và JWT 24 giờ không còn khớp hành vi hiện tại (đăng ký chuyển Login, JWT mặc định 15 phút); cần cập nhật trước khi dùng làm oracle.




