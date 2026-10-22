# Kế hoạch triển khai Chatbot trợ lý cho AX Portal

**Trạng thái:** Kế hoạch đã thống nhất, chưa triển khai tính năng  
**Ngày ghi nhận:** 2026-10-23  
**Phạm vi:** Ứng dụng web AX Portal; không gồm ứng dụng Desktop Pilot riêng

## 1. Mục tiêu và quyết định đã chốt

Chatbot giúp người dùng hiểu cách sử dụng hệ thống, diễn đạt nhu cầu bằng ngôn ngữ tự nhiên và nhận gợi ý phù hợp từ dữ liệu trong ứng dụng. Bot có mặt trên mọi trang sau đăng nhập, trả lời theo căn cứ có thể kiểm tra và dẫn người dùng đến đúng module hoặc bản ghi.

Các quyết định sản phẩm đã thống nhất:

- Phạm vi v1 bao phủ các module web hiện có: Home, Skill Management, Skill Forum (Skill Requests), AI News, VOC/Feedback, User Management, Organization Management, Settings và Notifications; hướng dẫn hệ thống lấy từ SRS liên quan.
- Vị trí là nút nổi toàn ứng dụng sau đăng nhập. Giao diện hỗ trợ hội thoại nhiều phiên và phản hồi streaming.
- Chatbot chỉ đọc dữ liệu và điều hướng. Không tự tạo Skill, Skill Request, VOC, comment hoặc thực hiện thao tác thay user. Nếu chưa có Skill phù hợp, có thể gợi ý mở Skill Forum để user tự tạo yêu cầu.
- Trả lời có nguồn và liên kết hữu ích; khi thiếu dữ liệu đáng tin cậy, bot nêu rõ chưa tìm thấy căn cứ thay vì bịa câu trả lời.
- Dùng dữ liệu hồ sơ và hoạt động phù hợp của chính user để cá nhân hóa kết quả. Việc cá nhân hóa thực hiện lúc truy vấn bằng retrieval/ranking; v1 không fine-tune model.
- Model chạy qua endpoint AI nội bộ hiện có, được gọi từ backend. Không gọi dịch vụ AI công khai và không gửi khóa dịch vụ xuống trình duyệt.
- Hội thoại lưu theo user và session. Chính sách mặc định giữ lâu dài; user được xóa hội thoại của mình; admin có thể xem/quản lý hội thoại và điều chỉnh chính sách lưu.
- Tìm kiếm v1 dùng PostgreSQL full-text cùng metadata và ranking. Index được nạp ban đầu và cập nhật gần thời gian thực sau thay đổi dữ liệu.

## 2. Hiện trạng repo làm cơ sở thiết kế

- Frontend dùng Vue 3, TypeScript, Vue Router và AppShell dùng chung. Nút nổi phù hợp được tích hợp tại [`AppShell.vue`](../frontend/src/components/layout/AppShell.vue); guard đăng nhập/admin nằm trong [`router/index.ts`](../frontend/src/router/index.ts).
- Backend dùng Spring Boot, PostgreSQL, JWT stateless và method security. Mọi API ngoài auth/health yêu cầu xác thực; `ROLE_ADMIN_USER` hiện được áp dụng ở các controller quản trị trong [`SecurityConfig.java`](../backend/src/main/java/com/skillmanagement/config/SecurityConfig.java).
- Các module đã có service truy vấn riêng: Skill có search visibility-aware, Skill Forum có feed, AI News có list/detail, VOC tách dữ liệu của owner và admin. Chatbot cần tái sử dụng các service hoặc quy tắc domain này, không tự suy diễn quyền từ UI.
- Migration V5 ghi rõ `pgvector` đã bị bỏ vì DB user không có quyền superuser. Vì vậy v1 không giả định có thể lưu embedding trong PostgreSQL; kiến trúc tìm kiếm cần có ranh giới thay thế được nếu sau này được cấp search/vector infrastructure.
- GNB và Sitemap trong SRS phản ánh cấu trúc nhiều module, nhưng một số file SRS/index có ghi trạng thái triển khai hoặc route cũ. Khi triển khai, code hiện tại là nguồn xác nhận route/API; SRS là nguồn ý nghĩa nghiệp vụ.

## 3. Trải nghiệm người dùng

### 3.1 Widget hội thoại

- Hiển thị nút mở chatbot trên mọi route được bọc trong AppShell sau khi phiên đăng nhập hợp lệ. Không hiển thị ở Login/Register/Forgot Password.
- Mở thành panel/drawer phù hợp desktop và mobile; đóng bằng nút đóng, Escape hoặc thao tác UI phù hợp. Không làm mất nội dung đang streaming khi người dùng chỉ đóng panel.
- Hỗ trợ danh sách session, tạo session mới, tiếp tục session hiện có, xóa session của mình, gửi tin nhắn và trạng thái đang trả lời/lỗi/thử lại.
- Mỗi phản hồi có thể chứa đoạn văn, thẻ kết quả và nguồn. Thẻ kết quả có tên module, tiêu đề, đoạn trích khi phù hợp, và link nội bộ đến đối tượng/màn hình đích.
- Ưu tiên ngôn ngữ lưu trong Settings (hiện có vi/en/ko); nếu user chủ động chat bằng ngôn ngữ khác trong danh sách hỗ trợ, trả lời theo ngôn ngữ của câu hỏi.
- Không tự tạo action button gây thay đổi dữ liệu. Link “Tạo Skill Request” chỉ điều hướng tới luồng hiện có.

### 3.2 Gợi ý Skill và fallback

Với câu như “tôi cần test ứng dụng dễ dàng hơn”, bot xác định mục tiêu/ngữ cảnh, tìm Skill người dùng được xem, xếp hạng và giải thích ngắn vì sao Skill phù hợp. Nếu không có kết quả phù hợp, bot tìm các Skill Request liên quan để tham khảo và có thể dẫn đến form tạo yêu cầu. Bot phân biệt rõ Skill có sẵn với yêu cầu cộng đồng, không trình bày request như Skill đã phát hành.

## 4. Kiến trúc đề xuất

```text
Vue floating chat
    │ authenticated API + fetch stream
    ▼
Chat API / session service (Spring Boot)
    ├── identity + authorization context (current JWT user)
    ├── conversation store (PostgreSQL)
    ├── query understanding + personalization context
    ├── SearchIndex / domain retrievers
    │      ├── PostgreSQL full-text index
    │      └── live authorization re-check via domain services
    ├── grounded prompt/context builder
    └── Internal LLM adapter → internal AI endpoint

Domain writes ── transaction + outbox ── index worker ── search index
SRS/help bundle ──────────────────────────────────────────────┘
```

### 4.1 Chat orchestration

Một service điều phối giữ các bước sau tách biệt: nhận câu hỏi từ session user hiện tại; lấy cửa sổ hội thoại gần nhất; tạo intent/search terms và các filter; lấy tín hiệu cá nhân liên quan; tìm candidate; kiểm tra lại quyền trên candidate; tạo context giới hạn; gọi model nội bộ; stream câu trả lời và nguồn; lưu user/assistant messages.

Không gửi toàn bộ database, toàn bộ SRS hoặc toàn bộ hồ sơ user trong mỗi request. Gửi chỉ các đoạn truy xuất phù hợp, metadata cần để giải thích/xây link và tín hiệu cá nhân tối thiểu có ích. Chỉ nạp chat history của đúng session/user; không đưa lịch sử chat của người khác vào retrieval.

### 4.2 Search/index abstraction

Tạo interface nội bộ `SearchIndex` hoặc tương đương với thao tác upsert/delete/search/status. V1 backend là PostgreSQL full-text với cấu hình phù hợp nội dung đa ngôn ngữ; dùng title, description/content, tags/categories/stages và metadata module. Dùng `simple`/token-based search phù hợp hơn giả định stemming tiếng Anh; bổ sung query expansion/từ đồng nghĩa theo nghiệp vụ khi cần. Không dùng vector type/extension trong migration v1.

Khi chuyển quy mô hoặc chất lượng semantic search chưa đạt, có thể triển khai adapter khác cho internal embedding/reranking service và managed/on-prem vector search mà không đổi API chat hoặc cách xây link. Việc nâng cấp đó cần hạ tầng/quyền DB/search được duyệt riêng.

### 4.3 Nạp và cập nhật index

- Nạp dữ liệu ban đầu bằng job có thể resume, ghi nhận tổng số nguồn đã đọc/thành công/thất bại và hỗ trợ chạy lại idempotent.
- Sau commit tạo/sửa/xóa nguồn, ghi outbox row cùng transaction domain. Worker lấy sự kiện, chuyển nguồn sang normalized document, cập nhật index, đánh dấu processed; lỗi retry có backoff và lưu trạng thái lỗi, không làm rollback nghiệp vụ người dùng.
- Mục tiêu vận hành là nội dung được tìm thấy trong vài giây sau commit thông thường. UI/admin có thể thấy trạng thái backlog; nếu tài liệu chưa vào index thì bot không được giả vờ đã tìm đủ.
- Mỗi document mang source type/id, title, route, updatedAt, checksum, visibility metadata, owner nếu có và trạng thái xóa/archive. Xóa hoặc archive phải loại khỏi kết quả nhanh nhất có thể.
- Với SRS/hướng dẫn, đóng gói một bộ tài liệu hướng dẫn đã chọn lọc/versioned vào tài nguyên triển khai (classpath hoặc bundle build), không đọc một đường dẫn filesystem tùy ý của máy chạy production. Mỗi chunk giữ file/section làm nguồn trích dẫn.

### 4.4 Personalization và ranking

Hard filters (quyền xem, trạng thái không bị xóa/archive, module được phép) áp dụng trước khi kết quả được gửi cho model. Trong tập hợp hợp lệ, thứ tự ưu tiên mặc định:

1. Mức khớp với nhu cầu/ngữ cảnh mà user nêu trong chat.
2. Khớp category, stage, tags, nội dung và thuộc tính domain của kết quả.
3. Mức liên quan với role/Part/Group và hoạt động Skill của chính user (ví dụ viewed/rated/subscribed nếu nguồn hiện có và được quyền đọc).
4. Tín hiệu chất lượng/độ mới như rating hoặc cập nhật gần đây khi phù hợp.

Độ liên quan trực tiếp của nội dung luôn cao hơn tín hiệu phổ biến. Không dùng email/password/token hoặc thông tin hồ sơ không giúp trả lời. Mỗi nguồn index vẫn phải được kiểm tra lại qua quy tắc domain hiện hành trước khi đưa vào model context để tránh ACL stale sau thay đổi quyền.

## 5. Phạm vi dữ liệu và quyền truy cập

| Nguồn | Cách dùng | Giới hạn quyền |
|---|---|---|
| Hướng dẫn/SRS | Hỏi cách sử dụng app, luồng và quy tắc nghiệp vụ | Chỉ nạp tài liệu hướng dẫn đã chọn lọc; trả link tới section/file nguồn |
| Skill Management | Gợi ý Skill, taxonomy, trạng thái và thông tin liên quan | Tái sử dụng `SkillService.search/getDetail` visibility rules; không lấy skill draft/restricted nếu viewer không được xem |
| Skill Forum / requests | Tìm request tương tự, gợi ý luồng tạo request | Feed hiện dành cho user đăng nhập; không lộ file ngoài quyền hiện có |
| AI News | Tìm và tóm tắt tin, comment nếu nghiệp vụ/ACL cho phép | Bỏ tin archived; dùng detail/body khi viewer có quyền đọc |
| VOC/Feedback | Trả lời về feedback cá nhân hoặc dữ liệu quản trị | User thường chỉ feedback của họ; admin theo quyền quản trị hiện tại |
| User Management / Organizations | Hướng dẫn hoặc tra cứu quản trị | Chỉ admin; không đưa hồ sơ nhân sự vào kết quả user thường |
| Settings/Profile/Preferences | Dùng locale và tín hiệu profile cần thiết cho cá nhân hóa | Chỉ profile của user hiện tại; không index credential, password, token, OTP |
| Home/Notifications | Tìm hoạt động/gợi ý liên quan user | Chỉ dữ liệu của session user; không lấy thông báo của người khác |
| Chat history | Duy trì ngữ cảnh và cá nhân hóa tiếp nối | Chỉ session/user sở hữu; admin được xem/quản lý qua trang admin và audit |

Ứng dụng hiện dùng `ROLE_ADMIN_USER` cho khu vực quản trị. Backend phải kiểm tra principal và quyền ở từng thao tác; ẩn nút/route trên frontend chỉ là UX, không phải kiểm soát bảo mật. Không cho model tự gọi URL tùy ý hoặc tự thực thi SQL/API. Mọi tool/retriever là allowlist code phía server.

## 6. API dự kiến

Tất cả endpoint dưới đây thuộc `/api/v1`, yêu cầu JWT; response JSON thường dùng `ApiResponse` hiện có. Tên route là hợp đồng đề xuất cho implementation.

### 6.1 User chat

| Method | Route | Ý nghĩa |
|---|---|---|
| POST | `/chat/sessions` | Tạo session mới |
| GET | `/chat/sessions` | Danh sách session của principal hiện tại, phân trang |
| GET | `/chat/sessions/{sessionId}/messages` | Lấy tin nhắn trong session sở hữu |
| POST | `/chat/sessions/{sessionId}/messages/stream` | Nhận `{ message }`, stream câu trả lời và nguồn |
| DELETE | `/chat/sessions/{sessionId}` | Xóa session/tin nhắn của chính user |

Streaming dùng `text/event-stream` và `fetch`/ReadableStream để gửi Authorization header hiện có (không phụ thuộc EventSource không hỗ trợ custom Authorization header). Event types: `delta` cho phần chữ, `sources` cho citations/result cards, `done` cho message/session id cuối, `error` cho lỗi sau khi stream bắt đầu. Lỗi xác thực/validation trước khi mở stream trả envelope/status HTTP bình thường. Không lưu token hoặc system prompt vào event.

Citation object tối thiểu: `sourceType`, `sourceId`, `title`, `snippet`, `href`, `updatedAt` khi có. Link được backend tạo từ route allowlist, không lấy URL tùy ý do model phát sinh.

### 6.2 Admin

Admin route được bảo vệ bằng `ROLE_ADMIN_USER` cả ở route và method/API:

| Method | Route | Ý nghĩa |
|---|---|---|
| GET | `/admin/chatbot/settings` | Đọc retention/feature configuration |
| PUT | `/admin/chatbot/settings` | Cập nhật chính sách retention và các cấu hình được phép |
| GET | `/admin/chatbot/conversations` | Tìm/phân trang hội thoại toàn hệ thống |
| GET | `/admin/chatbot/conversations/{sessionId}/messages` | Xem hội thoại |
| DELETE | `/admin/chatbot/conversations/{sessionId}` | Xóa hội thoại theo yêu cầu quản trị |
| GET | `/admin/chatbot/index/status` | Xem tiến độ/backlog/lỗi index, không lộ prompt/credential |

Admin không chỉnh sửa lời user hoặc câu trả lời đã lưu; thao tác quản trị nội dung là đọc và xóa. Các thao tác admin trên hội thoại được audit. Cấu hình model endpoint/secrets vận hành đặt ở secret manager/environment, không cho chỉnh URL arbitrary qua UI.

## 7. Data model đề xuất

Tên bảng/cột là đề xuất để triển khai bằng Flyway migration mới; cần theo naming convention thực tế trong code trước khi tạo migration.

- `chat_session`: id, `user_id`, title (có thể sinh từ câu đầu), created/updated timestamps, deleted timestamp/actor khi dùng soft-delete/audit.
- `chat_message`: id, `session_id`, role (`USER`/`ASSISTANT`), content, citations JSONB, created timestamp. Không lưu system prompt, access token, raw provider headers hoặc chain-of-thought.
- `chatbot_config`: cấu hình singleton; `retention_days` nullable nghĩa là giữ vô thời hạn, updated by/time, chatbot enabled và metadata vận hành phù hợp.
- `chat_index_document`: id, source type/id, title, normalized text, chunk number, route, visibility metadata, owner id khi là dữ liệu cá nhân, source updatedAt/checksum/indexedAt, search vector/text fields và deleted/archive state.
- `chat_index_outbox`: id, source type/id, operation, createdAt, attempts, processedAt, lastError; unique/idempotency key để tránh xử lý sự kiện trùng.
- `chat_admin_audit`: actor id, action, target session/user, timestamp và metadata cần cho kiểm tra; không copy message content vào audit log.

Chat messages không được đưa vào corpus dùng chung. Khi cần cá nhân hóa từ chat cũ, chỉ truy xuất history của đúng user/session theo giới hạn ngữ cảnh. Thực hiện cleanup theo config; xóa của user phải xóa hội thoại và dữ liệu phụ thuộc theo một transaction/luồng được xác định, đồng thời không xóa audit bắt buộc.

## 8. Model nội bộ và quy tắc grounding

- Khai báo `InternalChatModel`/adapter tương đương ở backend. Endpoint nội bộ, timeout, model id, credentials và giới hạn được cấu hình qua secret manager/environment. Tách khỏi controller/orchestrator để đổi model mà không đổi hợp đồng frontend.
- Gọi model qua mạng nội bộ; egress public không phải dependency của chatbot. Không fallback ngầm sang public provider.
- Context builder gửi câu hỏi, history gần nhất, language, tín hiệu cá nhân đã chọn và các đoạn nguồn được phép. Giới hạn tổng context theo cấu hình model; không dump toàn bộ retrieved corpus.
- System policy yêu cầu chỉ nêu sự thật có căn cứ từ context/source, phân biệt hướng dẫn với dữ liệu bản ghi, không tiết lộ dữ liệu không có trong context, và trả về “chưa tìm thấy” khi nguồn thiếu/mâu thuẫn.
- Model không được phép gọi tool động, tự truy cập Internet, chạy SQL, thao tác file hoặc commit thay user. Backend chỉ cấp citations/link từ retriever đã kiểm tra quyền.
- Không fine-tune v1. Đánh giá chất lượng bằng test set và feedback/relevance metrics; chỉ lập kế hoạch huấn luyện sau khi có vấn đề cụ thể retrieval/ranking không giải quyết được.

## 9. Bảo mật, riêng tư và retention

- Lấy user id/role từ JWT principal ở server, không tin `userId` do client gửi.
- Filter theo quyền trước retrieval/context; re-check candidate qua domain service trước khi gọi model; không chỉ lọc kết quả sau khi model đã đọc.
- Bảo vệ session ownership ở mọi endpoint; người khác đoán session id phải nhận not-found/forbidden theo quy ước an toàn hiện có.
- Không ghi message body, prompt, access token hay thông tin nhạy cảm vào application logs mặc định. Ghi metrics dạng counts/latency/error class; tránh label chứa nội dung user.
- Giới hạn tốc độ/kích thước câu hỏi, lịch sử context và số kết quả để ngăn lạm dụng/tăng tải; giới hạn cụ thể cấu hình theo khả năng model nội bộ và tải server trong implementation.
- Retention mặc định vô thời hạn (`retention_days = null`). Admin có thể cấu hình thời hạn dương; scheduler xóa dữ liệu vượt hạn. User có thể xóa session riêng bất kể retention. Admin có thể xem/xóa hội thoại toàn cục; ghi audit actor/action/target/time.
- Hiển thị/ghi nhận rõ khi session bị xóa; nếu sau này có yêu cầu khôi phục hay legal hold thì cần bổ sung policy riêng, không mặc định giữ bản sao ngoài hệ thống chat.

## 10. Xử lý lỗi và vận hành

| Tình huống | Hành vi |
|---|---|
| Token hết hạn/không hợp lệ | Dùng luồng refresh/login hiện có; không tạo session ẩn danh |
| Session không thuộc user | Không trả message; không cho dùng session id để dò tài nguyên |
| Không có kết quả đủ liên quan | Trả lời chưa tìm thấy, đề xuất từ khóa/module hoặc Skill Forum nếu hợp ngữ cảnh |
| Index backlog/stale | Hiển thị trạng thái khi cần; worker retry; không tuyên bố đã tìm toàn bộ dữ liệu |
| Lỗi/timeout endpoint nội bộ | Kết thúc stream bằng event lỗi thân thiện; giữ câu hỏi để retry, không gửi public fallback |
| Mất kết nối giữa stream | Lưu user message; assistant message chỉ lưu theo nội dung đã hoàn tất hoặc đánh dấu không hoàn tất rõ ràng; tránh message giả là hoàn chỉnh |
| Model trả nội dung không có citations | Hạ thành câu trả lời không khẳng định dữ kiện hoặc yêu cầu làm rõ; không tạo link không được backend xác thực |
| Nguồn bị archive/xóa/quyền đổi sau index | Re-check source; bỏ khỏi context và citation |
| Admin xem/xóa hội thoại | Cho phép theo role; audit metadata; không ghi nội dung chat vào audit |

Metrics tối thiểu: số session/message, latency time-to-first-token và tổng thời gian, lỗi model theo nhóm, tỷ lệ no-result, số citation mỗi answer, backlog/index lag, số retry, hoạt động cleanup. Không thu prompt text vào metric.

## 11. Kế hoạch kiểm thử và tiêu chí chấp nhận

### Backend

- Unit test cho ownership/ACL, query intent/context selection, ranking thứ tự ưu tiên, citation allowlist, outbox idempotency/retry, cleanup retention và audit.
- Integration test: user thường tìm Skill visibility-aware; Skill Request/AI News public theo quy tắc hiện tại; VOC/profile/chat chỉ owner; admin xem module quản trị và chat toàn hệ thống; session id của user khác không đọc được.
- Kiểm tra stream event `delta/sources/done/error`, provider timeout, disconnect, refresh/unauthorized và không có fallback ra Internet.
- Kiểm tra indexing create/update/delete/archive, backfill có thể chạy lại, dữ liệu mới xuất hiện trong vài giây ở điều kiện tải thường và lỗi index không làm hỏng giao dịch domain.
- Kiểm tra câu trả lời không có nguồn thì không bịa; câu hỏi không có kết quả dẫn user tới nơi thích hợp hoặc nêu rõ không tìm thấy.

### Frontend

- Widget chỉ hiện khi authenticated, hoạt động responsive, keyboard Escape, loading/stream/error/retry/empty state.
- Session list, resume, new, delete; tải lại trang vẫn thấy lịch sử nếu chưa xóa.
- Render text/citations an toàn; link nội bộ đúng, link ngoài không sinh tự do; không render HTML model không sanitize.
- Admin route/menu/API bị từ chối ở frontend lẫn backend nếu không có `ROLE_ADMIN_USER`.
- Ngôn ngữ theo Settings và theo ngôn ngữ chat được hỗ trợ.

### Acceptance

1. User mô tả công việc như tìm Skill hỗ trợ test app và nhận gợi ý có lý do, nguồn và đường mở chi tiết.
2. Khi không có Skill phù hợp, bot nói rõ và gợi ý mở Skill Forum; không tự tạo request.
3. User thường không truy xuất được user, VOC, organization, notification hoặc chat của người khác qua prompt, citation, endpoint hay đoán id.
4. Admin quản lý được retention và hội thoại theo quyền; mọi lần truy cập/xóa của admin có audit record.
5. Thay đổi nội dung được index gần thời gian thực; khi index/model lỗi, bot thể hiện trạng thái đúng và không đưa kết quả cũ/không có quyền.
6. Câu trả lời chỉ được grounded bởi nguồn đã truy xuất; citations luôn trỏ đến route hợp lệ.

## 12. Rollout và các giai đoạn

1. **Nền tảng:** migration hội thoại/config/outbox/index/audit; `SearchIndex`, adapter model nội bộ, service orchestration và backend API; unit/integration tests bảo mật.
2. **Nạp dữ liệu:** processor theo module, backfill idempotent, index status/retry, bản bundle tài liệu hướng dẫn SRS đã chọn lọc.
3. **Giao diện user:** widget AppShell, session/message UI, stream parser, citations/result cards, i18n vi/en/ko.
4. **Admin:** route/menu admin, cấu hình retention, danh sách/chi tiết/xóa hội thoại, trạng thái index, audit.
5. **Pilot:** bật bằng feature flag cho nhóm nội bộ nhỏ; đánh giá chất lượng retrieval, câu trả lời có nguồn, latency và tải index; sửa ranking/knowledge trước khi bật toàn bộ.

Không bật v1 rộng nếu còn lỗi phân quyền cross-user, câu trả lời không grounded hoặc endpoint nội bộ chưa được cấu hình. Feature flag cho phép ngắt chatbot mà không ảnh hưởng các module hiện tại.

## 13. Tài liệu/code tham chiếu

- Bản đồ route, GNB và module: [`SRS sitemap`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/sitemap.md)
- Quy tắc Home/GNB/notifications: [`Home Screen SRS`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/home/Home_Screen_SRS.md)
- Skill và quyền hiển thị: [`Skill Management Screen Spec`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/1.%20Skill%20Management%20module/Skill_Management_Screen_Spec.md), [`Detail Popup SRS`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/1.%20Skill%20Management%20module/Detail_Popup_SRS.md)
- User/profile/settings: [`User Management SRS`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/2.%20User%20management%20module/User%20Management%20Screen_SRS.md), [`Settings SRS`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/2.%20User%20management%20module/Settings_SRS.md)
- VOC, AI News, Skill Forum và errors: [`VOC SRS`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/3.%20VOC%20Module/VOC_Feedback_SRS.md), [`AI News SRS`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/ai-news/AI_News_Screen_SRS.md), [`Skill Forum SRS`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/4.%20Skill%20Forum/Skill_Forum_SRS.md), [`Error Codes`](../Vibe%20coding%20basline/Vibe%20coding%20basline/SRS/0.%20Error_Codes_Messages.md)
- Điểm tích hợp frontend hiện có: [`AppShell.vue`](../frontend/src/components/layout/AppShell.vue), [`router/index.ts`](../frontend/src/router/index.ts)
- Quyền backend và PostgreSQL constraint: [`SecurityConfig.java`](../backend/src/main/java/com/skillmanagement/config/SecurityConfig.java), [`V5 skill schema migration`](../backend/src/main/resources/db/migration/V5__add_skill_store_schema.sql)

## 14. Giả định và điều kiện cần xác nhận khi triển khai

- Endpoint AI nội bộ, mạng nội bộ, credentials và giới hạn concurrency đã có; chi tiết protocol/model id sẽ được cấu hình ở backend adapter.
- “Mọi module” là các module web hiện có, không bao gồm Desktop Pilot riêng.
- PostgreSQL full-text là backend tìm kiếm đầu tiên. Độ khớp ngữ nghĩa có thể thấp hơn vector search; theo dõi chất lượng ở pilot và chỉ nâng cấp khi hạ tầng nội bộ hỗ trợ.
- Retention giữ vô thời hạn nếu admin chưa cấu hình số ngày. Chat history không được dùng để fine-tune hoặc huấn luyện model trong v1.
- Các route/API đề xuất trong tài liệu là hợp đồng kế hoạch; trước khi code phải đối chiếu conventions/DTO hiện hành và tạo Flyway migration mới, không sửa migration đã chạy.
