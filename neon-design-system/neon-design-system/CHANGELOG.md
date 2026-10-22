# Changelog

## 0.8.0 — 2026-09-25

- **Glass defaults: surface alpha 10% + backdrop blur 4px** (trước 15%/5px) — token `--ds-surface-alpha: .10`, `--ds-surface-blur: 4px` trong `tokens.css`; Glass Lab showcase (`App.vue`) reset về 10/4; FAQ cập nhật. Áp dụng cho cả demo (theme `prism` dùng chung token).
- **Demo: Theme Lab trong Settings** — section mới (icon SlidersHorizontal) cho phép chỉnh trực tiếp "Độ che của mặt kính" (0–65%) và "Backdrop blur" (0–16px) với live preview; giá trị lưu `localStorage 'demo-glass'`, chỉ áp dụng lại khi user đã từng chỉnh (không đè theme mặc định khi mới mở); nút "Đặt lại mặc định (10% / 4px)". Module `demo/src/glass.js` (reactive state + `setGlass`/`resetGlass`).
- **Demo: i18n 3 ngôn ngữ (Tiếng Việt / English / 한국어)** — module `demo/src/i18n.js` (dict vi/en/ko, `t()`, `setLang()`, `LANG_OPTIONS`); chọn trong Settings → Preferences (System language), áp dụng khi Save. Mặc định **`vi`** — trùng đúng UI hiện tại để e2e không đổi. Toàn bộ views/popups (AppShell, Home, Skills, UserMgmt, Settings, AuthScreen, Detail/AddSkill/EditSkill/UserDetail/CreateUser popups, MultiSelect, TagInput) đã chuyển sang `t()`.
- **Animations mượt hơn** — `--ds-ease` đổi sang `cubic-bezier(.33,1,.68,1)` (easeOutCubic); dialog/drawer có animation mở (scale+fade, backdrop fade, drawer slide); floating panels (dropdown/combobox/popover/colorpicker/mselect) fade+slide nhẹ; switch knob, radio dot, tablist, accordion chevron dùng duration/ease token; demo: settings section fade, nav/row/bell-btn easing đồng bộ.
- **Tests cập nhật** — demo e2e F5: `--ds-surface-alpha` 0.15 → 0.10 (149/149 pass); showcase `showcase.spec.js` glass reset 20 → 10 (8/8 pass); vitest 42/42 pass.

## 0.7.0 — 2026-09-24

- Bổ sung **24 component mới** (32 → 56) để thư viện đầy đủ và đa dạng hơn, theo nhóm trong ROADMAP:
  - **Forms nâng cao (6)**: `PrismRadio` + `PrismRadioGroup` (role=radiogroup, điều hướng phím mũi tên, wrap, skip disabled), `PrismNumberInput` (stepper ↑/↓, phím mũi tên, kẹp min/max, unit), `PrismPasswordInput` (nút hiện/ẩn + thanh độ mạnh 4 bậc), `PrismCombobox` (gõ để lọc, single/multiple với chips, clearable, điều hướng bàn phím, panel teleport body + fixed như `useFloatingPanel`), `PrismFileUpload` (kéo thả hoặc chọn, kiểm tra `accept` theo extension/MIME + `maxSize`, danh sách file + xóa từng file).
  - **Overlay (3)**: `PrismTooltip` (hover/focus, 4 vị trí, delay, teleport body + fixed z-index 90), `PrismPopover` (panel neo vào trigger, teleport body + fixed z-index 85, đóng click ngoài/Escape), `PrismDropdownMenu` (role=menu/menuitem, separator, item danger, điều hướng phím mũi tên + Enter, emit `select`).
  - **Data & navigation (6)**: `PrismAvatar` (ảnh hoặc initials, fallback khi ảnh lỗi, 5 tone), `PrismAvatarGroup` (chồng nhau, overflow thành +N), `PrismTimeline` (trục thời gian, tone accent/success/warning), `PrismStat` (KPI: giá trị + delta tăng/giảm + hint), `PrismTree` (render recursive bằng `h()`, role=tree/treeitem, aria-expanded, defaultExpanded), `PrismList` (subtitle/trailing, selectable với role=listbox + emit `select`).
  - **Content + Advanced (4)**: `PrismDivider` (ngang/dọc, nhãn tùy chọn, role=separator), `PrismKbd` (phím tắt, aria-label), `PrismBreadcrumbs` (aria-current=page, emit `navigate`), `PrismColorPicker` (preset swatches + hue slider + hex input, panel teleport body + fixed, tính toán HSL/RGB thuần).
  - **Charts mới (5)** — SVG thuần, token colors, `role="img"`: `PrismAreaChart` (vùng gradient fill, useId cho gradient id), `PrismRadarChart` (n-trúc, 4 vòng mức), `PrismGauge` (nửa tròn, 5 tick, số trung tâm), `PrismHeatmap` (lưới nhiệt kiểu GitHub theo ngày, 5 mức cường độ, nhãn tháng), `PrismStackedBarChart` (nhiều series chồng, legend tự động).
- `catalog.js`: thêm 24 specimens; recount nhóm (all=56, forms=13, overlays=5, data=13, charts=9, content=5).
- Showcase `App.vue`: 24 demo tương tác mới; số component 32 → 56 ở nav, system-strip, filter tabs; docs accordion cập nhật phạm vi v0.7.
- `components.css`: thêm style `p-*` cho cả 24 component (radio, number stepper, password, combobox panel/chips, fileupload, tooltip, popover, dropdown, avatar, timeline, stat, tree, list, divider, kbd, breadcrumbs, colorpicker, 5 chart mới).
- Unit test: danh sách export 32 → 56 + 17 test mới (radio group phím mũi tên, number clamp/stepper, password toggle, combobox filter/chọn, tooltip show/hide, tree expand/collapse, gauge, heatmap, stacked bar, avatar/group overflow, breadcrumbs, stat, timeline, list select, kbd/divider, dropdown menu, popover, file upload accept/reject, color picker, area/radar). Tổng 42 test.
- **Demo (AI Skill Management) áp dụng toàn bộ 56 component** (đăng ký global trong `demo/src/main.js`):
  - **Home**: KPI cards dùng `PrismStat`; analytics 2 hàng (9 card) trong `PrismGrid` — Donut, Bar, Area, Heatmap, Radar, Gauge, Sparkline, StackedBar, Line; `PrismTimeline` cập nhật gần đây; `PrismAvatarGroup` đội ngũ.
  - **AppShell**: `PrismBreadcrumbs` header, `PrismTooltip` chuông thông báo, `PrismAvatar` user menu, `PrismAmbientBackground` thay markup ambient inline.
  - **Skills**: `PrismCombobox` filter Skill Scope, `PrismDropdownMenu` row action ⋯ (Xem chi tiết / Subscribe / Xóa).
  - **UserMgmt**: `PrismAvatar` cột User Name.
  - **Settings**: `PrismDivider` + `PrismColorPicker` (accent) + `PrismRadioGroup` (digest) trong Preferences; `PrismPasswordInput` (giữ `autocomplete`) trong Security.
  - **DetailPopup**: `PrismAccordion` tab Thông tin (Mô tả / File & hashtag / Lịch sử phiên bản).
  - **AddSkillPopup / EditSkillPopup**: `PrismFileUpload` thay component FileUpload tự viết (đã xóa `demo/src/components/FileUpload.vue`).
  - **AuthScreen**: `PrismKbd` hint demo credentials, `PrismAmbientBackground`.
  - **Bảng (Skills/UserMgmt)**: bọc table trong `.p-table-scroll` (`overflow-x: auto`) để scroll **ngang trong wrapper** khi tổng chiều rộng cột vượt container, không tràn màn hình ở mọi kích thước viewport (thay cơ chế `overflow-x` chỉ ở `≤999px` cũ). `PrismTable` của DS vốn đã có wrapper này.
  - **Sửa `PrismPopover`**: trigger trước đây là `<button class="p-popover-trigger"` bọc quanh slot — khi consumer truyền `PrismButton` vào slot `#trigger` sẽ sinh ra **button lồng button**, browser tự đóng tag button ngoài → button trong bị rơi ra ngoài, mất toàn bộ style `.p-button`. Giờ trigger là `<span class="p-popover">` (ref + click + aria), button của consumer giữ nguyên style. Bỏ CSS chết `.p-popover-trigger`. Unit test khóa lại: trigger là SPAN, không có `button button`.
  - E2E demo thêm section G (G0–G9, 44 checks, gồm G5b kiểm tra wrapper scroll ngang + body không tràn) cho các component trên; tổng 149 checks.
- **Sửa (2026-09-25) — panel nổi không thao tác được trong showcase** (Combobox #25, Dropdown menu #29, Color picker #38, Popover #30): 4 panel teleport ra `body` và được đặt `top`/`left` inline nhưng class CSS **thiếu `position: fixed`** (khác với `.p-date-popup` vốn đã có — nên date picker không bị lỗi). Panel vì vậy render ở flow thường cuối `<body>` (dưới chân trang, ngoài màn hình) → không click được. Thêm `position: fixed` cho `.p-combobox-panel`, `.p-dropdown-panel`, `.p-colorpicker-panel`, `.p-popover-panel`. Demo e2e vẫn pass vì Playwright tự scroll tới element ngoài màn hình, nên lỗi chỉ lộ khi thao tác tay trong showcase.
- **Sửa (2026-09-25) — 5 lỗi a11y (axe critical) của các component v0.7**, phát hiện khi chạy lại suite showcase (trước đó chưa chạy được do thiếu browser Playwright):
  - `PrismTree`: `role="tree"` chứa button toggle không có role hợp lệ → chuẩn APG: chevron thành phần tử trang trí (`aria-hidden`, vẫn click được), `aria-expanded`/`aria-level`/`aria-selected` nằm trên nút `treeitem` duy nhất; click chevron hoặc node đều mở/thu gọn.
  - `PrismNumberInput`: bỏ `aria-valuemin`/`aria-valuemax` trên input text (chỉ hợp lệ với role slider/spinbutton).
  - `PrismPopover`: bỏ `aria-expanded`/`aria-controls` trên `<span>` wrapper (không có role) — state thuộc về trigger button của consumer.
  - `PrismHeatmap`: bỏ `aria-label` trên `<rect>` (không có role); SVG đã có `role="img"` + label tổng.
  - `PrismAvatar`/`PrismAvatarGroup`: `role="img"` cho avatar initials để `aria-label` hợp lệ; bỏ `aria-label` trùng với text hiển thị của span +N.
- **Showcase suite** (`tests/showcase.spec.js`): thêm test regression "floating panels open fixed and interactable" (panel `position: fixed`, trong viewport, chọn được option/item/preset, Escape đóng popover); cập nhật số specimen 12 → 43; tăng timeout 2 test axe; config Playwright dùng `channel: 'chrome'` (Chrome hệ thống). Kết quả: 6/6 pass, axe 0 violation (desktop + mobile). Tổng kiểm chứng: vitest 42/42, showcase Playwright 6/6, demo e2e 149/149, smoke pass, build showcase + library + demo xanh.
- **Sửa (2026-09-25) — `PrismNumberInput` không nhập được / tăng giảm sai** (specimen #05): input cũ bind trực tiếp `:value="modelValue"` và emit giá trị clamp ngay mỗi lần gõ — gõ `99` (max 10) bị đổi thành `10` giữa chừng nên không gõ tiếp được, gõ `4.` bị parse mất, stepper bấm liên tục bị lệch do model chưa cập nhật. Chuyển sang **draft model**: khi focus, giá trị gõ giữ nguyên trong draft (không clamp, không phá chuỗi dở dang), chỉ parse + clamp khi blur/Enter; stepper ↑/↓ và phím mũi tên đọc từ draft (trường rỗng + bấm tăng/giảm nhảy về `min`/`max` như input number native); Escape hủy về giá trị trước đó; model đổi từ ngoài khi không focus vẫn đồng bộ về display. Unit test cập nhật + test regression showcase "number input: typing, stepper and keyboard all work".
- **Sửa (2026-09-25) — Breadcrumbs #35 không thao tác được + text không căn giữa**:
  - Showcase: inline handler `@navigate="breadcrumbNav = ...$event[1].label..."` — `$event` chỉ là **argument đầu** của emit (`index`), nên `$event[1]` là `undefined` → click link ném lỗi `Cannot read properties of undefined (reading 'label')`. Sửa bằng named handler `onBreadcrumbNavigate(index, item)`; demo hiển thị "Đã bấm: {label} (index {n})" để thấy sự khác.
  - `PrismDivider` có nhãn: label cũ `position: absolute; left: 50%` của `.p-divider` (chỉ rộng bằng text khi nằm trong flex row) → chữ "HOẶC" bị cắt/đè lên element bên cạnh. Chuyển sang cấu trúc flex: 2 đoạn line `::before`/`::after` (`flex: 1`) 2 bên label, thêm class `has-label`; divider không nhãn giữ nguyên 1 line. Showcase: `.demo-row .p-divider:not(.is-vertical) { flex: 1; min-width: 40px; margin: 0 }` để divider ngang co giãn trong row.
  - Test regression showcase "breadcrumbs navigate event works and divider label is centered" + unit test divider `has-label`.
  - Kèm theo: sửa Vue warn `triggerId` không còn định nghĩa trong `PrismPopover` (bỏ `:id` thừa) và prop `size="48"` (string) → `:size="48"` trong showcase.

## 0.6.2 — 2026-09-24

- Sửa các panel nổi (combobox/dropdown) bị ẩn dưới các div sibling (cùng nguyên nhân stacking context của `.p-glass`/`backdrop-filter` như date picker):
  - **Demo**: `MultiSelect` (Creation Status / Skill Scope / Role / Status / Organization) + 2 dropdown header (notification bell, user menu) giờ teleport ra `body` + `position: fixed` (z-index 85), tính vị trí từ control, lật lên khi gần đáy màn hình, kẹp trong viewport, recalibrate khi resize/scroll, đóng khi click ngoài/Escape.
  - **Showcase**: notification bell dropdown cùng cơ chế.
  - Tách logic thành composable dùng chung `useFloatingPanel` (2 bản: `demo/src/components/` và `neon-design-system/src/`), có hỗ trợ dùng trong `PrismDialog` (đóng theo `toggle` của dialog).
- **Demo**: date filter (Skills: From/To Date; UserMgmt: Created From/To) chuyển từ `PrismInput type="date"` (native) sang `PrismDatePicker` — model vẫn ISO `YYYY-MM-DD`, logic filter không đổi.
- **Demo**: dialog **Search User** — item có hover highlight giống notifications (class `.user-search-row`: nền gradient cyan→tối + viền trái accent + meta sáng lên, copy hiệu ứng `.gnb-notif:hover`; class riêng để không ảnh hưởng `.skill-row` dùng trên Home).
- E2E demo thêm nhóm kiểm tra E3b (MultiSelect panel teleport/viewport/chọn option), E3c (hover item Search User → viền accent); E3 chọn ngày qua calendar popup. Tổng 103 checks.

## 0.6.1 — 2026-09-24

- Sửa popup của `PrismDatePicker` / `PrismDateRangePicker` / `PrismDatetimePicker` bị ẩn dưới các thẻ sibling: popup giờ được **Teleport ra `body`** và dùng `position: fixed` (z-index 90) tính vị trí từ rect của input, nên không còn bị chặn bởi stacking context của `.p-glass`/`backdrop-filter` hay `overflow` của container. Tự động lật lên phía trên khi gần đáy màn hình, kẹp trong viewport khi nằm gần mép phải, và recalibrate khi resize/scroll.
- Tách logic popup thành composable dùng chung `useDatePopup` (3 picker dùng chung 1 nguồn code). Hỗ trợ dùng lại an toàn trong mọi ngữ cảnh:
  - **Trong `PrismDialog`** (native `<dialog>` ở top-layer): popup tự teleport **vào dialog** và chuyển sang `position: absolute` theo padding box của dialog (`.p-date-popup--in-dialog`), vẫn lật lên khi gần đáy dialog; đóng popup khi dialog đóng (lắng nghe `toggle`).
  - Ngoài trang: teleport `body` + `position: fixed` như trên.
  - Đóng khi click ngoài hoặc Escape; click bên trong popup không đóng.
- Thêm unit test regression: popup teleport vào dialog đang mở.

## 0.6.0 — 2026-09-24

- Thêm 3 component date: `PrismDatePicker` (1 ngày), `PrismDateRangePicker` (khoảng ngày), `PrismDatetimePicker` (ngày + giờ phút). Calendar nội bộ `PrismCalendar` dùng chung; input readonly combobox, popup glass, Escape/click ngoài để đóng, hỗ trợ min/max, thứ Hai là ngày đầu tuần.
- Thêm nhóm **Date & time** vào catalog + demo showcase. Tổng 32 component.

## 0.5.0 — 2026-09-24

- Thêm `PrismAmbientBackground`: nền không gian nhiều lớp port từ demo (5 radial glow + 3 orb trôi + plexus constellation 3 lớp + bokeh + dot grid 2 tầng + noise + vignette). Decorative, `aria-hidden`, `pointer-events: none`, z-index 0.
- Showcase dùng nền mới thay gradient đơn cũ; `.main-shell` lên z-index 1. Tổng 29 component.

## 0.4.0 — 2026-09-24

- Thêm 5 component: `PrismGrid` (layout grid responsive), `PrismBarChart`, `PrismLineChart`, `PrismDonutChart`, `PrismSparkline` (SVG thuần, không dependency).
- Thêm nhóm **Charts** vào catalog (4 component) + demo trong showcase; tổng 28 component.
- Chart dùng design tokens, `role="img"` + `aria-label`, nhãn/số liệu là text SVG.

## 0.3.0 — 2026-09-24

- Trọn bộ hover/focus tinh tế cho controls: input/select/textarea hover sáng viền + nền nhẹ; focus dùng highlight một lớp mềm (ring 3px cyan .10 + glow .05), bỏ outline kép thô; state error (aria-invalid) có ring rose riêng, không áp hover.
- Checkbox/switch hover: chữ sáng lên, ring glow nhẹ quanh control; tabs/accordion hover đổi màu chữ.
- Thêm tokens `--ds-input-hover-background`, `--ds-input-focus-background` (lớp component, override được).

## 0.2.0 — 2026-09-24

- Thêm utility `.p-list-item` cho dòng menu/notification: hover nền tối mờ đục che nền ambient giúp chữ rõ hơn, viền trái accent, trạng thái `is-unread`/`is-read` (dim title + khôi phục khi hover).
- Thêm tokens `--ds-list-item-background`, `--ds-list-item-hover-accent`, `--ds-list-item-border` (lớp component, override được).
- Thêm demo notification bell + dropdown trong topbar showcase.

## 0.1.0 — 2026-09-22

- Khởi tạo Prism Neon Design System bằng Vue 3, Vite và CSS custom properties.
- Triển khai 23 component public và contract controlled state.
- Thêm catalog tiếng Việt, tìm kiếm/lọc, code preview, foundations, Glass Lab và patterns.
- Tái tạo kính xuyên thấu, viền cyan/violet, phản xạ nhẹ và nền không gian.
- Thêm native modal/drawer, keyboard tabs, linked form feedback và toast lifecycle.
- Thêm unit tests, Playwright flows, axe checks và screenshot desktop/mobile.
- Xuất riêng build showcase và build thư viện. Tất cả component ở trạng thái experimental.
