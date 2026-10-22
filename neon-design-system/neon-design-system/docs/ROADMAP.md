# Phạm vi và phát triển

## v0.7 đã triển khai

56 component. v0.7 bổ sung 24 component mới theo các nhóm Planned:

| Nhóm | Thành phần mới (v0.7) |
| --- | --- |
| Forms | Radio + RadioGroup, NumberInput, PasswordInput, Combobox (single/multiple), FileUpload |
| Overlay | Tooltip, Popover, DropdownMenu |
| Data | Avatar + AvatarGroup, List, Stat, Timeline, Tree, Breadcrumbs |
| Content | Divider, Kbd |
| Advanced | ColorPicker |
| Charts | AreaChart, RadarChart, Gauge, Heatmap, StackedBarChart |

## v0.1–v0.6 đã triển khai

23 component lõi: Button, FormField, Input, Textarea, Select, Checkbox, Switch, Slider, Tabs, Dialog, Drawer, Badge, Alert, Toast, Progress, Spinner, Skeleton, EmptyState, Card, Pagination, Table, Accordion, CodeBlock.

Tiếp theo: Grid, BarChart, LineChart, DonutChart, Sparkline (v0.4); AmbientBackground (v0.5); DatePicker, DateRangePicker, DatetimePicker (v0.6). Tất cả có prefix Prism.

## Planned — chưa triển khai

Các mục này giữ đúng vai trò roadmap của tài liệu nguồn. Không export placeholder hoặc giả rằng đã có API hoạt động.

| Nhóm | Thành phần |
| --- | --- |
| Actions | IconButton, ButtonGroup, SplitButton, Link, CopyButton |
| Forms | SearchInput, MultiSelect |
| Navigation | Stepper, Sidebar, NavigationMenu public primitives |
| Feedback | Chip, ErrorState; Toast action và giới hạn queue |
| Overlay | ContextMenu, CommandPalette |
| Data | DataTable |
| Date/time | Calendar, TimePicker (public) |
| Layout | Stack, Cluster, Container, ScrollArea, AspectRatio |
| Content | Image, Figure, Markdown |
| Advanced | RichTextEditor, DataGrid |

Sidebar, các link, search, hình chart và layout trong showcase là phần riêng của ứng dụng demo, chưa phải public primitives cho các mục Planned tương ứng.

## Ưu tiên kế tiếp

1. Bổ sung kiểm thử screen reader, Firefox/WebKit, contrast trên nhiều background; nâng coverage cho props và states.
2. Actions nhóm (IconButton, ButtonGroup, SplitButton, Link, CopyButton) và Feedback (Chip, ErrorState, Toast action + queue limit).
3. ContextMenu/CommandPalette theo keyboard contract; chỉ xây khi có use case thực tế.
4. Chuẩn hóa patterns sau ít nhất hai use case, tránh coi demo duy nhất là pattern production.
5. CI, version release và distribution package khi có consumer ngoài workspace.

## Definition of Done và governance

- Owner: maintainer của workspace; trạng thái ban đầu experimental.
- Mỗi component có purpose/anti-use, props/slots/events, state và keyboard contract.
- Token global → semantic → component; không thêm màu riêng tùy hứng vào feature.
- Kiểm tra native semantics, label và focus trước visual polish.
- Kiểm tra dữ liệu dài, empty/error/loading, mobile, zoom, reduced motion.
- Unit/interaction/visual checks theo rủi ro; ghi rõ các kiểm tra chưa thực hiện.
- Chỉ đánh dấu stable sau khi kiểm tra thủ công và use case sản phẩm xác nhận.
- Patch: sửa bug không đổi contract; minor: thêm API tương thích; major: thay đổi phá vỡ.
- Deprecate trước khi xóa API; cập nhật implementation/test/docs/changelog cùng change.

## Giới hạn có chủ đích

Không có backend, auth, lưu persistent hoặc gửi dữ liệu. Create flow chỉ minh họa validation/loading và thêm dữ liệu in-memory. Library private, chưa publish. Màu, blur và alpha là thông số tái tạo từ tài liệu style; không phải đo trực tiếp từ ảnh gốc.
