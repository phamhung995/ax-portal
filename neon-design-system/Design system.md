# Prism Design System: Component Catalog

> Tài liệu đặc tả để xây dựng, mở rộng và kiểm tra một design system hoàn chỉnh cho sản phẩm số. Prism DS hiện dùng Vue 3, CSS custom properties và một visual language spatial/holographic. Các component có trạng thái **Implemented** đã tồn tại trong `src/components/`; các component **Planned** là danh mục chuẩn cần được triển khai khi sản phẩm có nhu cầu.

## 1. Design system là gì?

Design system là một hệ thống sản phẩm gồm:

1. **Nguyên tắc**: cách sản phẩm thể hiện tính cách, ưu tiên và hành vi.
2. **Foundations**: màu sắc, typography, spacing, grid, elevation, motion, icon và accessibility.
3. **Components**: các khối UI có API, trạng thái, semantics và hành vi ổn định.
4. **Patterns**: cách ghép nhiều component để giải quyết workflow thực tế.
5. **Tooling**: package, token build, Storybook/catalog, test, lint và release.
6. **Governance**: ai được thêm/sửa component, cách review breaking change và cách deprecate.
7. **Documentation**: hướng dẫn dùng đúng, dùng sai, ví dụ và giới hạn.

Một component chỉ hoàn chỉnh khi cả hình thức và hợp đồng hành vi đều rõ ràng. Một bản mockup đẹp chưa phải là design system.

### Tiêu chí hoàn chỉnh cấp hệ thống

- Có token nguồn duy nhất, không rải màu hoặc spacing tùy ý trong component.
- Có quy ước đặt tên, phân loại, version và backward compatibility.
- Mỗi component có public API nhỏ, có thể dự đoán và có ví dụ.
- Có state model: default, hover, focus-visible, active, disabled, loading, error, success, empty và read-only khi phù hợp.
- Có semantic HTML, keyboard behavior, accessible name, focus management và screen-reader output.
- Có responsive behavior và hỗ trợ `prefers-reduced-motion`.
- Có unit test cho logic, interaction test cho keyboard/form/overlay và visual review cho token/state.
- Có tiêu chí Definition of Done và quy trình thay đổi.

## 2. Nguyên tắc nền tảng

### 2.1. Tính nhất quán có chủ đích

- Ưu tiên token và primitive hiện có trước khi tạo API mới.
- Một khái niệm chỉ có một tên: chọn `disabled`, không dùng lẫn `isDisabled` và `inactive`.
- Cùng một hành vi phải có cùng keyboard interaction ở mọi nơi.
- Không dùng màu làm tín hiệu duy nhất; luôn kết hợp text, icon, shape hoặc position.

### 2.2. Native-first

Dùng phần tử HTML native khi nó cung cấp semantics và keyboard behavior phù hợp: `button`, `a`, `input`, `select`, `textarea`, `fieldset`, `dialog`, `table`. Chỉ dùng ARIA để bổ sung semantics, không dùng để thay thế HTML native khi không cần.

### 2.3. Controlled state

Component mutable dùng `v-model` và emit `update:modelValue`. Component hành động dùng event rõ nghĩa như `click`, `submit`, `select`, `close`, `change`. Không để component tự che giấu state quan trọng khỏi consumer.

```vue
<PrismInput
  v-model="projectName"
  id="project-name"
  label="Project name"
  hint="Required for deployment"
  :error="errors.projectName"
/>

<PrismButton
  variant="primary"
  :loading="saving"
  @click="saveProject"
>
  Create project
</PrismButton>
```

### 2.4. API phải mô tả được trong một bảng

Mỗi component cần tài liệu cho:

- Purpose và khi nào không nên dùng.
- Anatomy và slot.
- Props: type, default, required, allowed values.
- Emits và payload.
- `v-model` contract nếu có.
- Keyboard và focus behavior.
- Các state và validation rule.
- Responsive behavior.
- Ví dụ tối thiểu và ví dụ nâng cao.

## 3. Foundations

### 3.1. Design tokens

`src/tokens.css` là source of truth. Token nên có ba lớp:

| Lớp | Ví dụ | Mục đích |
| --- | --- | --- |
| Global token | `--ds-color-cyan-400` | Giá trị nguyên thủy, không gắn ngữ cảnh |
| Semantic token | `--ds-color-text-primary` | Ý nghĩa sử dụng |
| Component token | `--ds-button-primary-background` | Tùy chỉnh cục bộ có kiểm soát |

Không hard-code màu, shadow, radius hoặc spacing trong feature component nếu token tương ứng đã tồn tại.

### 3.2. Color

- **Space**: canvas và surface nền.
- **Cyan**: focus, selected, active signal, primary action.
- **Violet/indigo**: secondary action và depth.
- **Mint**: success và system health.
- **Danger/pink**: destructive và invalid.
- **Warning/yellow**: attention, warning và rating.
- Text phải đạt contrast phù hợp trên surface; trạng thái phải còn hiểu được khi xem grayscale.

### 3.3. Typography

- Sans cho nội dung đọc và heading.
- Mono cho metadata, ID, value, keyboard hint và technical label.
- Có scale cho display, heading, body, label, caption và code.
- Không dùng uppercase cho đoạn văn dài.
- Line-height phải đủ để đọc ở mobile và zoom 200%.

### 3.4. Spacing, sizing và layout

- Dùng scale spacing chung, không tạo khoảng cách lẻ nếu không có lý do.
- Control có chiều cao tối thiểu ổn định; text dài phải wrap hoặc truncate có chủ đích.
- Desktop dùng grid 12 cột; tablet 6 cột; mobile 1 cột.
- Không để card hoặc control gây horizontal overflow.
- Touch target nên đạt tối thiểu 44 x 44 CSS px khi có thể.

### 3.5. Radius, border và elevation

Prism dùng radius nhỏ, border mảnh, surface trong và shadow tiết chế. Elevation thể hiện thứ bậc, không trang trí mọi phần tử. Không lồng page section trong card; card dành cho item lặp lại, tool compact, dialog và data surface.

### 3.6. Iconography

- Icon phải có nghĩa và cùng một hệ lưới/kích thước.
- Icon-only button luôn có accessible name và tooltip nếu hành động không hiển nhiên.
- Icon trang trí dùng `aria-hidden="true"`.
- Không dùng icon để thay thế label của form.

### 3.7. Motion

Motion phải giải thích thay đổi trạng thái: loading, selected, drawer xuất hiện, toast vào màn hình. Có duration ngắn và easing nhất quán. Tắt hoặc giảm motion khi `prefers-reduced-motion: reduce`.

## 4. Component taxonomy

| Nhóm | Component |
| --- | --- |
| Actions | Button, IconButton, ButtonGroup, SplitButton, Link, CopyButton |
| Forms | Input, Textarea, NumberInput, SearchInput, PasswordInput, Select, Combobox, Checkbox, Radio, RadioGroup, Switch, Slider, DatePicker, DateRangePicker, FileUpload, FormField |
| Navigation | Breadcrumbs, Tabs, Tab, Pagination, Stepper, Sidebar, NavigationMenu, Link |
| Feedback | Badge, Chip, Alert, Toast, Progress, Spinner, Skeleton, EmptyState, ErrorState |
| Overlays | Dialog, Drawer, Popover, Tooltip, DropdownMenu, ContextMenu, CommandPalette |
| Data display | Avatar, AvatarGroup, Card, List, ListItem, Table, DataTable, Stat, Timeline, Tree, Accordion |
| Date/time | Calendar, DatePicker, DateRangePicker, TimePicker |
| Layout | Stack, Grid, Cluster, Divider, Container, ScrollArea, AspectRatio |
| Media/content | Image, Figure, CodeBlock, Markdown, Kbd |
| Advanced | Combobox, MultiSelect, ColorPicker, RichTextEditor, Chart, DataGrid |

## 5. Component contract chung

### 5.1. Naming

- Component public dùng prefix `Prism`: `PrismButton`, `PrismDialog`.
- Tên mô tả object, không mô tả visual effect: dùng `PrismAlert`, không dùng `PrismGlowBox`.
- Variant dùng token semantic: `primary`, `secondary`, `ghost`, `danger`, `success`; tránh tên màu trực tiếp.
- Props boolean dùng tính từ rõ nghĩa: `disabled`, `loading`, `readonly`, `open`.

### 5.2. Common props

Các prop chỉ thêm khi component thật sự dùng:

| Prop | Type | Ý nghĩa |
| --- | --- | --- |
| `id` | `String` | Định danh để liên kết label, description và error |
| `disabled` | `Boolean` | Không cho tương tác, vẫn hiển thị trạng thái |
| `readonly` | `Boolean` | Cho xem giá trị nhưng không sửa |
| `required` | `Boolean` | Trường bắt buộc, phải có cả semantics và visual cue |
| `loading` | `Boolean` | Đang xử lý; khóa action nếu có nguy cơ submit lặp |
| `invalid` / `error` | `Boolean` / `String` | Validation state và message |
| `size` | `String` | Kích thước trong scale được định nghĩa |
| `variant` | `String` | Semantic style đã được tài liệu hóa |
| `aria-label` | `String` | Accessible name khi không có visible label |

### 5.3. Common events

- `update:modelValue`: thay đổi giá trị controlled.
- `change`: giá trị đã thay đổi theo semantics của control.
- `focus`, `blur`: chỉ expose khi consumer cần theo dõi.
- `submit`: form hợp lệ gửi dữ liệu.
- `close`: overlay yêu cầu đóng.
- `select`: item được chọn, payload gồm item hoặc id.

### 5.4. State matrix

| State | Visual | Behavior | Accessibility |
| --- | --- | --- | --- |
| Default | Surface và text chuẩn | Có thể tương tác | Semantics mặc định |
| Hover | Tăng contrast hoặc lift nhẹ | Không đổi giá trị | Không phụ thuộc hover |
| Focus-visible | Focus ring rõ | Keyboard target hiện tại | Ring không bị cắt |
| Active/pressed | Signal đậm hơn | Phản ánh đang nhấn | `aria-pressed` nếu toggle |
| Selected | Indicator + text/shape | Giá trị đang chọn | `aria-selected`/`checked` |
| Disabled | Contrast giảm hợp lý | Không nhận input | Native disabled hoặc `aria-disabled` |
| Loading | Spinner/skeleton | Khóa hành động nguy hiểm | `aria-busy`, status message |
| Error | Danger border + message | Cho sửa hoặc retry | `aria-invalid`, describedby |
| Success | Success signal | Có thể cho tiếp tục | Status text nếu cần |
| Empty | Nội dung thay thế | Có action tiếp theo nếu có | Heading và description |
| Read-only | Vẫn đọc được | Không sửa | `readonly` hoặc semantics tương ứng |

## 6. Catalog chi tiết

## 6.1. Actions

### PrismButton — Implemented

**Mục đích:** action chính hoặc phụ có chủ đích. Dùng `a` cho điều hướng, không dùng button để thay thế link.

**API hiện tại:**

- Props: `variant = primary | secondary | ghost | danger`, `type = button | submit | reset`, `loading`, `disabled`.
- Slot mặc định: label và icon.
- Events: native button events.
- Khi `loading`, component tự disabled và expose `aria-busy="true"`.

**States:** default, hover, focus-visible, pressed, disabled, loading. Loading phải giữ label hoặc accessible label ổn định để layout không nhảy.

**A11y:** native `button`, keyboard Enter/Space, focus ring, không dùng icon-only nếu thiếu accessible name.

### PrismIconButton — Planned

- Props: `icon`, `label` bắt buộc, `size`, `variant`, `disabled`, `loading`, `pressed`.
- Không render nếu thiếu accessible label trong production.
- Dùng `aria-pressed` khi là toggle; tooltip chỉ bổ trợ, không thay label.
- Hit area ổn định tối thiểu 44px.

### PrismButtonGroup — Planned

- Nhóm action liên quan, không dùng để gom action không cùng ngữ cảnh.
- Props: `orientation`, `attached`, `label`.
- A11y: group có label; mỗi button vẫn là native button.
- Mobile chuyển từ ngang sang dọc khi không đủ chiều rộng.

### PrismSplitButton — Planned

- Nút chính thực hiện action mặc định và trigger mở menu action phụ.
- Props: `loading`, `disabled`, `menuItems`, `actionLabel`, `menuLabel`.
- Hai control có accessible name riêng; Escape đóng menu; Arrow keys di chuyển item.

### PrismLink — Planned

- Props: `href`, `to`, `external`, `disabled` không khuyến nghị.
- External link phải báo trước hoặc có icon và text phù hợp.
- Dùng `a` native cho URL; không dùng click handler thay link.

### PrismCopyButton — Planned

- Props: `value`, `label`, `successLabel`, `timeout`.
- Events: `copy`, `error`.
- Dùng Clipboard API với fallback; toast/status phải có live region.
- Không hiển thị secret đã bị che chỉ để copy nếu sản phẩm không cho phép.

## 6.2. Forms

### PrismFormField — Implemented

Component bao quanh label, control, hint, error và required indicator.

- Props: `id`, `label`, `hint`, `error`, `required`, `disabled`, `orientation`.
- Slots: `default` control, `label`, `hint`, `error`, `prefix`, `suffix`.
- Tạo id ổn định cho `aria-describedby`; error được ưu tiên hơn hint.
- Không dùng placeholder làm label.

### PrismInput — Implemented

- Props hiện tại: `modelValue`, `label`, `hint`, `error`, `type`, `placeholder`, `id`.
- Emit: `update:modelValue`.
- Hỗ trợ text, email, password, url, search khi semantics phù hợp.
- Cần bổ sung theo roadmap: `name`, `required`, `disabled`, `readonly`, `autocomplete`, `maxlength`, `prefix/suffix`, `aria-label`.
- Error dùng `aria-invalid`; message liên kết qua `aria-describedby`.

### PrismTextarea — Implemented

- Props: `modelValue`, `label`, `rows`, `maxlength`, `resize`, `hint`, `error`, `disabled`, `readonly`.
- Hiển thị counter nếu có `maxlength`; counter phải có accessible text.
- Không tự động thay đổi chiều cao làm layout nhảy nếu chưa có cấu hình rõ.

### PrismNumberInput — Implemented

- Props: `modelValue`, `min`, `max`, `step`, `precision`, `allowEmpty`.
- Có input text với parsing rõ ràng hoặc native number phù hợp; không dùng spinner làm cách duy nhất để nhập.
- Arrow Up/Down chỉ thay đổi giá trị khi focus; phải tôn trọng min/max.
- A11y: input là text thường — **không dùng `aria-valuemin`/`aria-valuemax`** (chỉ hợp lệ với role slider/spinbutton); giới hạn min/max thể hiện qua hành vi clamp, không phải ARIA attribute.
- Hành vi nhập (draft model): khi đang focus, giá trị gõ giữ nguyên trong draft — không clamp giữa chừng, không phá chuỗi dở dang như `4.` hay dấu âm; chỉ parse + clamp khi blur/Enter. Stepper ↑/↓ đọc từ giá trị đang gõ; trường rỗng + bấm tăng/giảm nhảy về `min`/`max` (giống input number native); Escape hủy về giá trị trước đó. Model đổi từ ngoài (khi không focus) đồng bộ về display.

### PrismSearchInput — Planned

- Props: `modelValue`, `query`, `clearable`, `loading`, `resultCount`.
- Emit `submit`, `clear`.
- Enter submit; Escape clear hoặc đóng suggestion theo thứ tự; có debounce ở layer dữ liệu, không ép vào primitive.

### PrismSelect — Implemented

- Props: `modelValue`, `options`, `label`, `id` theo implementation hiện tại.
- Emit: `update:modelValue`.
- Native `select` được ưu tiên vì có semantics và keyboard behavior ổn định.
- Roadmap: option object `{ label, value, disabled }`, hint/error, placeholder và `multiple` nếu nhu cầu thực tế xuất hiện.

### PrismCombobox — Implemented

- Dành cho danh sách lớn hoặc cần tìm kiếm; không thay Select cho danh sách nhỏ.
- Props: `modelValue`, `items`, `searchable`, `clearable`, `loading`, `multiple`, `allowCustom`.
- A11y theo combobox pattern: `role="combobox"`, `aria-expanded`, `aria-controls`, `aria-activedescendant`.
- Keyboard: Arrow Up/Down, Enter, Escape, Home/End; focus không mất khi chọn item.
- Panel option teleport ra `body` + `position: fixed` (z-index 85) qua `useFloatingPanel` — class panel **bắt buộc** có `position: fixed`, thiếu sẽ rơi vào flow thường cuối `<body>` (ngoài màn hình, không thao tác được).

### PrismMultiSelect — Planned

- Props: `modelValue` là array, `options`, `max`, `closeOnSelect`, `searchable`.
- Selected values hiển thị bằng Chip có remove action; vẫn có text summary khi quá nhiều item.
- A11y phải đọc được số lượng item đã chọn và từng action remove.

### PrismPasswordInput — Implemented

- Toggle show/hide có accessible label thay đổi theo state.
- Không log giá trị; autocomplete dùng `current-password` hoặc `new-password`.
- Error, hint, strength meter phải là nội dung bổ trợ, không biến thành quy tắc bảo mật duy nhất.

### PrismCheckbox — Implemented

- Props: `modelValue`, `id`, `label`, `disabled` theo implementation hiện tại.
- Emit: `update:modelValue`.
- Native checkbox; nhóm nhiều checkbox dùng `fieldset` và `legend`.
- Indeterminate state phải dùng DOM property và `aria-checked="mixed"` nếu được hỗ trợ.

### PrismRadio / PrismRadioGroup — Implemented

- Một lựa chọn trong tập loại trừ nhau.
- Group có `name`, `label`, `modelValue`, `orientation`.
- Arrow keys di chuyển trong group; Tab vào group một lần theo roving focus hoặc native radio semantics.
- Không dùng checkbox khi chỉ có một lựa chọn.

### PrismSwitch — Implemented

- Props: `modelValue`, `label`, `disabled` theo implementation hiện tại.
- Emit: `update:modelValue`.
- Dùng cho setting bật/tắt có hiệu lực tức thời; không dùng cho submit form nhiều lựa chọn.
- A11y: `role="switch"`, `aria-checked`, Space để toggle.

### PrismSlider — Implemented

- Props: `modelValue`, `min`, `max`, `step`, `label`, `formatValue`, `showValue`.
- Native range khi một thumb; multi-thumb cần quan hệ label và keyboard rõ ràng.
- Arrow, Page Up/Down, Home/End phải hoạt động; không truyền ý nghĩa bằng màu gradient duy nhất.

### PrismFileUpload — Implemented

- Props: `accept`, `multiple`, `maxSize`, `maxFiles`, `dropzone`, `disabled`, `error`.
- Native file input ẩn chỉ là implementation detail; dropzone vẫn phải hoạt động bằng keyboard.
- Hiển thị filename, size, progress, error và retry/remove.
- Không upload tự động nếu UI chưa nói rõ; không tin MIME type từ client cho validation bảo mật.

## 6.3. Navigation

### PrismTabs — Implemented

- Props: `modelValue`, `items` theo implementation hiện tại.
- Emit: `update:modelValue`.
- Cần chuẩn hóa roadmap cho item object `{ label, value, disabled, panelId }`.
- A11y: `tablist`, `tab`, `tabpanel`, `aria-selected`, `aria-controls`.
- Quyết định rõ activation mode: automatic khi focus hoặc manual khi Enter/Space; không trộn hai mode.

### PrismBreadcrumbs — Implemented

- Props: `items`, `currentLabel`, `collapseAt`.
- Mục tiêu là định vị; item cuối là current page và không cần link.
- `nav` có accessible label; separator `aria-hidden`.
- Emit `navigate` (index, item) khi bấm link — consumer tự xử lý điều hướng; item cuối không emit.

### PrismPagination — Implemented

- Props: `page`, `pageCount`, `pageSize`, `total`, `siblingCount`.
- Emit: `update:page`, `change`.
- Current page có `aria-current="page"`; first/previous/next/last có label rõ.
- Khi page thay đổi, focus hoặc live region phải giúp người dùng biết vị trí mới.

### PrismStepper — Planned

- Props: `steps`, `current`, `orientation`, `linear`.
- Trạng thái: upcoming, current, completed, error, skipped.
- Không khóa step đã hoàn tất nếu người dùng cần quay lại sửa dữ liệu.
- Dùng heading và status text, không chỉ số thứ tự.

### PrismSidebar / PrismNavigationMenu — Planned

- Hỗ trợ expanded/collapsed, active route, nested item và mobile drawer.
- Collapse phải giữ accessible label cho icon-only state.
- Active route không chỉ dựa vào màu; có `aria-current` hoặc indicator rõ.
- Không làm mất focus khi menu chuyển trạng thái.

## 6.4. Feedback

### PrismBadge — Implemented

- Props: `tone`, `dot`; slot mặc định là label.
- Tone nên gồm `neutral`, `info`, `success`, `warning`, `danger`.
- Badge mô tả status ngắn; không dùng thay button hoặc link.
- Dot chỉ là bổ trợ và phải `aria-hidden` nếu text đã nói cùng ý.

### PrismChip — Planned

- Dùng cho filter, tag hoặc giá trị đã chọn.
- Props: `label`, `tone`, `removable`, `disabled`, `selected`.
- Remove action có label cụ thể; chip không được chứa button lồng button.

### PrismAlert — Implemented

- Props: `tone`, `title`, `dismissible`, `role`.
- Slot: title, default, actions.
- `role="alert"` chỉ cho nội dung khẩn; thông báo thường dùng `status` để không ngắt screen reader.
- Có icon nhưng icon không thay title/message.

### PrismToast — Implemented

- Toast manager có queue, placement, duration và pause on hover/focus.
- Props: `tone`, `title`, `message`, `action`, `duration`, `dismissible`.
- Dùng live region phù hợp; không dùng toast cho lỗi cần người dùng nhập lại dữ liệu dài.
- Toast phải có close action và tránh che action quan trọng trên mobile.

### PrismProgress — Implemented

- Props: `value`, `max`, `indeterminate`, `label`, `showValue`.
- Determinate dùng `aria-valuenow`, `aria-valuemin`, `aria-valuemax`; indeterminate không giả giá trị.
- Kết hợp text percentage khi completion quan trọng.

### PrismSpinner — Implemented

- Dùng cho chờ ngắn; có label cho screen reader và không chỉ hiển thị vòng xoay.
- Không làm layout shift; kích thước là token.

### PrismSkeleton — Implemented

- Placeholder giữ nguyên kích thước content thật.
- `aria-hidden="true"` khi có loading status riêng; không animate khi reduced motion.
- Không dùng skeleton cho thao tác hoàn thành trong vài trăm ms nếu gây nhấp nháy.

### PrismEmptyState — Implemented

- Anatomy: icon/visual tùy chọn, heading, description, primary action, secondary action.
- Dùng khi danh sách hợp lệ nhưng chưa có dữ liệu; phân biệt với error state.
- Copy hướng người dùng tới hành động tiếp theo, không giải thích dài dòng.

### PrismErrorState — Planned

- Anatomy: title, cause ngắn, retry, support link hoặc fallback.
- Không đổ lỗi người dùng; giữ lại context và input đã nhập nếu có thể.
- Error boundary của app và error state của component phải là hai lớp riêng.

## 6.5. Overlays

### PrismDialog — Implemented

- Props hiện tại: `modelValue`, `title`.
- Emit: `update:modelValue`.
- Cần hoàn thiện roadmap: `description`, `size`, `closeOnOverlay`, `closeOnEscape`, `preventClose`, `initialFocus`.
- A11y: `role="dialog"`, `aria-modal="true"`, accessible title/description, Escape, close action.
- Khi production-ready phải trap focus và restore focus về trigger sau khi đóng.

### PrismDrawer — Implemented

- Props: `modelValue`, `placement`, `size`, `title`, `closeOnOverlay`, `closeOnEscape`.
- Focus trap, Escape, restore focus; animation slide tôn trọng reduced motion.
- Trên mobile nên gần full viewport; tránh drawer lồng drawer.

### PrismPopover — Implemented

- Dùng cho content contextual, không dùng cho thông báo khẩn.
- Props: `placement` (mặc định `bottom-start`). Trigger là named slot `#trigger` — consumer truyền control của mình (thường là `PrismButton`); wrapper là `<span>` (không phải `<button>`) để **không lồng button trong button** (browser sẽ phá DOM → button mất style).
- Panel teleport ra `body` + `position: fixed` (z-index 85) qua `useFloatingPanel`; đóng khi click ngoài hoặc Escape. Class panel bắt buộc có `position: fixed`.
- Không đóng khi người dùng chuyển focus hợp lệ vào content popover.
- A11y: span wrapper **không** mang `aria-expanded`/`aria-controls` (span không có role — axe `aria-allowed-attr`); state mở/đóng thuộc về trigger control của consumer.

### PrismTooltip — Implemented

- Chỉ bổ sung thông tin ngắn cho control; không chứa action hoặc nội dung thiết yếu.
- Hiện sau delay nhỏ, ẩn khi focus rời trigger; mobile cần fallback phù hợp.
- Trigger có `aria-describedby`; không render tooltip vô nghĩa nếu label đã đủ.

### PrismDropdownMenu — Implemented

- Props: `items`, `placement`, `closeOnSelect`, `disabled`.
- Keyboard: Enter/Space mở, Arrow di chuyển, Escape đóng và trả focus.
- Item có loại action, checkbox hoặc radio phải biểu diễn semantics tương ứng.
- Panel teleport ra `body` + `position: fixed` (z-index 85) qua `useFloatingPanel`; class panel bắt buộc có `position: fixed`.

### PrismContextMenu — Planned

- Mở bằng right-click hoặc keyboard equivalent; không phải chức năng duy nhất để truy cập action.
- Vị trí phải tránh viewport edge; Escape và click outside đóng.

### PrismCommandPalette — Planned

- Props: `open`, `commands`, `placeholder`, `loading`, `emptyText`.
- Search, grouped results, keyboard navigation, recent commands, Escape.
- Dialog có title; result active dùng `aria-activedescendant`; không thực thi command khi chỉ highlight.

## 6.6. Data display

### PrismCard — Implemented

- Dùng cho một đơn vị nội dung độc lập hoặc item lặp lại.
- Anatomy tùy chọn: header, eyebrow, title, description, media, body, footer.
- Variant visual không được quyết định semantics; card clickable phải expose link/button thật.
- Tránh card lồng card và tránh biến toàn page section thành card.

### PrismAvatar / PrismAvatarGroup — Implemented

- Props: `src`, `alt`, `name`, `size`, `status`, `max`, `overflowLabel`.
- Có fallback initials nhưng phải xử lý tên không Latin và tránh expose dữ liệu nhạy cảm.
- Avatar decorative dùng alt rỗng; avatar đại diện người dùng phải có accessible name.
- A11y: avatar initials dùng `role="img"` + `aria-label` (span thường không được phép `aria-label` — axe `aria-prohibited-attr`); span overflow "+N" không thêm `aria-label` vì text hiển thị đã đủ.

### PrismList / PrismListItem — Implemented

- Dùng cho nhóm item đồng nhất; hỗ trợ leading, content, trailing, selected, disabled.
- Clickable item phải dùng link/button; không gắn click mơ hồ vào `div`.
- Virtualized list cần thêm keyboard và screen-reader strategy riêng.

### PrismTable — Implemented

- Dùng cho dữ liệu tĩnh vừa phải; header dùng `th`, quan hệ row/column rõ.
- Hỗ trợ caption, sortable header, empty, loading và responsive overflow.
- Table luôn bọc trong `.p-table-scroll` (`overflow-x: auto`): khi tổng chiều rộng cột vượt container, bảng scroll **ngang trong wrapper** thay vì tràn màn hình — áp dụng ở mọi kích thước viewport.
- Không ép table thành card trên mobile nếu làm mất quan hệ dữ liệu; horizontal scroll có nhãn (wrapper `role="region"` + `aria-label` = caption, `tabindex="0"` để scroll bằng bàn phím).

### PrismDataTable — Planned

- Thêm sorting, filtering, pagination, row selection, column visibility, bulk action.
- Props: `rows`, `columns`, `loading`, `total`, `page`, `sort`, `selection`.
- Emit: `update:page`, `update:sort`, `update:selection`, `row-click`.
- Phải có text summary cho trạng thái lọc/sắp xếp và confirmation cho bulk destructive action.

### PrismStat — Implemented

- Hiển thị label, value, delta, trend và supporting text.
- Trend cần text (`up 12%`) bên cạnh màu/icon; không dùng màu xanh/đỏ duy nhất.

### PrismTimeline — Implemented

- Props: `items`, `orientation`, `status`, `density`.
- Mỗi item có time, title, description, actor/action; semantic list ưu tiên.
- Không dùng timeline cho dữ liệu cần so sánh số lượng chính xác theo cột.

### PrismTree — Implemented

- Props: `items`, `expanded`, `selected`, `checkable`.
- Keyboard theo tree pattern: Arrow, Home/End, Right/Left, Enter/Space.
- `aria-level`, `aria-expanded`, `aria-selected` phải phản ánh DOM state.
- Cấu trúc theo APG: mỗi node là **một nút `treeitem` duy nhất** (chứa label + meta); chevron mở/thu gọn là phần tử trang trí `aria-hidden` bên cạnh (vẫn click được) — không dùng button toggle riêng để tránh `role="tree"` chứa con không hợp lệ (axe `aria-required-children`).

### PrismAccordion — Implemented

- Props: `items`, `modelValue`, `multiple`, `collapsible`.
- Header là button; panel liên kết bằng `aria-controls`; trạng thái bằng `aria-expanded`.
- Không ẩn nội dung quan trọng chỉ vì responsive nếu không có lý do UX.

## 6.7. Date và time

### PrismCalendar — Planned

- Props: `modelValue`, `month`, `min`, `max`, `disabledDates`, `locale`, `weekStart`.
- Keyboard: Arrow ngày, Page Up/Down tháng, Home/End tuần, Enter chọn.
- Hiển thị rõ ngày hiện tại, ngày được chọn, ngày disabled; không dựa riêng vào màu.
- Locale và timezone phải được truyền rõ, không lấy ngầm từ browser nếu domain cần tính nhất quán.

### PrismDatePicker / PrismDateRangePicker — Implemented

- Input hiển thị formatted value nhưng value nội bộ phải có format contract rõ, ưu tiên ISO/date object có timezone policy.
- Có text input, calendar popup, clear, validation, min/max và error.
- Range picker phải mô tả start/end và trạng thái đang chờ ngày thứ hai.
- Mobile có thể mở full-screen picker nếu popover không đủ diện tích.

### PrismTimePicker — Planned

- Props: `modelValue`, `step`, `min`, `max`, `format`, `locale`.
- Hỗ trợ keyboard, invalid time và timezone label nếu dữ liệu cross-timezone.

## 6.8. Layout

### PrismGrid — Implemented (Stack / Cluster: Planned)

- Layout primitive nhận `gap`, `direction`, `align`, `justify`, `wrap`, breakpoint token.
- Không chứa business logic; không tạo component layout cho một page duy nhất.
- Kích thước và gap dùng token.

### PrismContainer — Planned

- Props: `maxWidth`, `padding`, `centered`.
- Giữ readable line length; không ép mọi nội dung về cùng một độ rộng nếu data table cần rộng hơn.

### PrismDivider — Implemented

- Props: `orientation`, `label`, `decorative`.
- Separator decorative có `aria-hidden`; divider mang meaning cần accessible label.
- Cấu trúc có nhãn: 2 đoạn line (`::before`/`::after`, `flex: 1`) 2 bên label — label nằm **giữa** line, không absolute-position đè lên line (tránh chữ bị cắt khi divider nằm trong flex row hẹp).

### PrismScrollArea — Planned

- Scrollbar có thể style nhưng keyboard và native scroll phải còn hoạt động.
- Có affordance khi content bị cắt; tránh nested scroll container không cần thiết.

## 6.9. Content và media

### PrismImage / PrismFigure — Planned

- Props: `src`, `alt`, `fit`, `loading`, `width`, `height`, `fallback`.
- Luôn giữ aspect ratio để tránh layout shift; alt mô tả mục đích, không lặp caption.
- Hỗ trợ error và loading; không che hình ảnh bằng overlay decorative khi người dùng cần inspect.

### PrismCodeBlock — Implemented

- Props: `code`, `language`, `filename`, `copyable`, `lineNumbers`.
- Code có thể scroll ngang, không wrap bắt buộc; copy feedback có live region.
- Syntax highlighting là bổ trợ, text vẫn đọc được khi tắt màu.

### PrismKbd — Implemented

- Hiển thị phím tắt ngắn; không phải control.
- Shortcut phải có alternative và không xung đột với screen reader/browser shortcut.

## 6.10. Advanced

### PrismColorPicker — Implemented

- Hỗ trợ color input native hoặc picker có keyboard; giá trị phải có format rõ (`hex`, `rgb`, `hsl`).
- Luôn hiển thị text value và contrast preview khi màu dùng cho text/background.
- Panel (preset + hue slider + hex input) teleport ra `body` + `position: fixed` (z-index 85) qua `useFloatingPanel`; class panel bắt buộc có `position: fixed`.

### PrismRichTextEditor — Planned

- Chỉ thêm khi content model và sanitization đã xác định.
- Toolbar keyboard-accessible; output được sanitize ở server; có fallback textarea cho nội dung cơ bản.
- Không coi HTML từ browser là dữ liệu an toàn.

### PrismChart — Implemented (9 loại: Bar, Line, Donut, Sparkline, Area, Radar, Gauge, Heatmap, StackedBar)

- API nhận series, axes, domain, legend, tooltip, loading, empty, error.
- Mỗi chart có accessible title và text summary; bảng dữ liệu hoặc download data cho insight quan trọng.
- Màu series phải có pattern/label phụ trợ và hoạt động với color vision deficiency.
- A11y: SVG dùng `role="img"` + `aria-label` tổng; **không đặt `aria-label` trên `<rect>`/`<path>`** (SVG shape không có role — axe `aria-prohibited-attr`); chi tiết từng cell để ở tầng data, không phải ARIA.

### PrismDataGrid — Planned

- Chỉ dùng khi table không đủ: editing cell, pinned column, virtualization, keyboard navigation.
- Đây là component có rủi ro accessibility cao; cần test screen reader và keyboard riêng trước khi phát hành.

## 7. Patterns cấp sản phẩm

Component riêng lẻ chưa tạo thành workflow. Các pattern nên được chuẩn hóa sau khi có ít nhất hai use case thật:

### Form pattern

- Form header, field grouping, required convention, inline validation, submit/loading, server error summary.
- Khi submit lỗi, focus vào error summary hoặc field lỗi đầu tiên và giữ lại dữ liệu người dùng.

### List/detail pattern

- List có loading, empty, error, filter, pagination.
- Detail có breadcrumb, title, metadata, primary action và unsaved changes handling.

### Search/filter pattern

- Search query, applied filter chips, clear all, result count, no-result state, loading và URL synchronization.

### Destructive action pattern

- Nêu rõ object bị ảnh hưởng, hậu quả, khả năng undo/recovery.
- Dialog confirmation chỉ dùng cho action thật sự nguy hiểm, không lạm dụng cho mọi click.

### Async operation pattern

- Phân biệt pending, success, partial success, retryable error và terminal error.
- Dùng idempotency hoặc khóa action để tránh submit trùng.

### Notification pattern

- Toast cho kết quả ngắn; Alert cho context trong page; Dialog cho quyết định cần chặn flow; inline error cho lỗi field.

## 8. Quy tắc accessibility bắt buộc

- Keyboard-only có thể đi tới và sử dụng mọi interactive control.
- Focus-visible luôn nhìn thấy trên mọi surface.
- Heading hierarchy không nhảy cấp tùy tiện.
- Label visible cho form; `aria-describedby` cho hint/error.
- Modal trap focus, Escape, close action và restore focus.
- Tab/combobox/menu/tree dùng đúng WAI-ARIA pattern, không tự chế keyboard behavior mâu thuẫn.
- Text và control đạt contrast; focus ring không bị `overflow: hidden` cắt.
- Không dùng animation làm thông tin duy nhất; hỗ trợ reduced motion.
- Zoom 200% và mobile không làm mất nội dung hoặc action.
- Dynamic status dùng live region có mức độ phù hợp, không đọc quá nhiều.

## 9. Testing strategy

### Unit test

Kiểm tra props, emits, computed state và rendering condition. Ví dụ hiện tại nằm ở `src/components/components.test.js`.

### Interaction test

Mỗi interactive component cần test:

- Click và keyboard tương đương.
- Controlled update qua `v-model`.
- Disabled/loading không phát sinh action.
- Error và accessible attributes.
- Escape, focus return và outside click cho overlay.

### Accessibility test

- Accessible name và role.
- Tab order và focus-visible.
- `aria-expanded`, `aria-selected`, `aria-checked`, `aria-invalid`, `aria-busy` đúng state.
- Screen-reader text không trùng hoặc gây nhiễu.

### Visual regression

Chụp default, focus, disabled, error, loading, empty ở desktop, tablet, mobile; kiểm tra reduced motion và long text. Visual test không thay thế interaction test.

### Contract test

API public phải được kiểm tra để phát hiện breaking change: tên prop, default, event payload, slot và export từ `src/components/index.js`.

## 10. Definition of Done cho component

Một component chỉ được đánh dấu ready khi:

- [ ] Có tên, category, purpose và anti-use case.
- [ ] Có anatomy, variants, props, emits, slots và `v-model` contract.
- [ ] Có token, responsive rule và state matrix.
- [ ] Có native semantics hoặc ARIA pattern được xác định.
- [ ] Có keyboard behavior và focus behavior.
- [ ] Có default, hover, focus, disabled, loading, error, success/empty phù hợp.
- [ ] Có ví dụ cơ bản, controlled example và misuse example.
- [ ] Có unit/interaction/accessibility test phù hợp rủi ro.
- [ ] Được export từ public entry point nếu là public component.
- [ ] Có cập nhật changelog hoặc migration note nếu API thay đổi.
- [ ] Đã review ở mobile, zoom 200% và reduced motion.

## 11. Quy trình thêm hoặc thay đổi component

1. Viết problem statement và chứng minh component/pattern được dùng lặp lại.
2. Chọn foundation/token và kiểm tra component tương tự hiện có.
3. Viết contract trước: anatomy, API, states, keyboard, a11y.
4. Tạo implementation nhỏ nhất dùng native semantics.
5. Thêm test hành vi trước khi polish visual.
6. Đưa component vào catalog với ví dụ và các state.
7. Review responsive, accessibility, content dài và error path.
8. Export public API, cập nhật tài liệu và version note.
9. Deprecate trước khi xóa API; cung cấp migration path.

## 12. Governance và versioning

- **Patch**: sửa bug, visual token không đổi contract.
- **Minor**: thêm component, prop optional hoặc variant backward-compatible.
- **Major**: đổi default behavior, xóa prop/event, đổi semantics hoặc breaking markup.
- Mọi component public cần owner và trạng thái: `experimental`, `stable`, `deprecated`.
- Không merge component mới chỉ vì một màn hình cần nó; xác định trước đây là primitive, pattern hay feature-specific component.
- Feature-specific component có thể sống trong feature folder và không được export vào design system nếu chưa chứng minh khả năng tái sử dụng.

## 13. Cấu trúc tài liệu đề xuất cho từng component

Mỗi trang component nên có cùng template:

```md
# PrismComponent

## Purpose
## When to use / when not to use
## Anatomy
## Variants
## Props
## Events
## Slots
## States
## Keyboard behavior
## Accessibility
## Responsive behavior
## Basic example
## Controlled example
## Validation/error example
## Do / Don't
## Tests
```

## 14. Trạng thái hiện tại của Prism DS

### Đã có trong repo

56 component (v0.7.0, experimental) — đầy đủ danh sách trong `neon-design-system/src/components/index.js`:

- **Actions:** PrismButton
- **Forms:** PrismFormField, PrismInput, PrismTextarea, PrismSelect, PrismCheckbox, PrismRadio, PrismRadioGroup, PrismSwitch, PrismSlider, PrismNumberInput, PrismPasswordInput, PrismCombobox, PrismFileUpload
- **Navigation & data:** PrismTabs, PrismPagination, PrismTable, PrismCard, PrismGrid, PrismAccordion, PrismBreadcrumbs, PrismList, PrismAvatar, PrismAvatarGroup, PrismStat, PrismTimeline, PrismTree
- **Date & time:** PrismDatePicker, PrismDateRangePicker, PrismDatetimePicker
- **Feedback:** PrismBadge, PrismAlert, PrismToast, PrismProgress, PrismSpinner, PrismSkeleton, PrismEmptyState
- **Overlays:** PrismDialog, PrismDrawer, PrismPopover, PrismTooltip, PrismDropdownMenu
- **Content:** PrismCodeBlock, PrismAmbientBackground, PrismDivider, PrismKbd
- **Advanced:** PrismColorPicker
- **Charts:** PrismBarChart, PrismLineChart, PrismDonutChart, PrismSparkline, PrismAreaChart, PrismRadarChart, PrismGauge, PrismHeatmap, PrismStackedBarChart

### Áp dụng trong demo (AI Skill Management)

Toàn bộ 56 component đã được đăng ký toàn cục trong `demo/src/main.js`. Các component mới (v0.7.0) được dùng thực tế ở:

| Component | Vị trí trong demo |
| --- | --- |
| PrismStat | Home — 6 KPI cards (`.kpi-card`) |
| PrismDonutChart / PrismBarChart / PrismAreaChart / PrismHeatmap / PrismRadarChart / PrismGauge | Home — analytics grid (PrismGrid, 6 card) |
| PrismSparkline / PrismStackedBarChart / PrismLineChart | Home — analytics row 2 (3 card) |
| PrismTimeline | Home — "Cập nhật gần đây" |
| PrismAvatarGroup | Home — header card trạng thái skill |
| PrismBreadcrumbs | AppShell — header (Trang chủ / {view}) |
| PrismTooltip | AppShell — nút chuông thông báo |
| PrismAvatar | AppShell — user menu; UserMgmt — cột User Name |
| PrismCombobox | Skills — filter Skill Scope |
| PrismDropdownMenu | Skills — nút ⋯ mỗi row (Xem chi tiết / Subscribe / Xóa) |
| PrismDivider / PrismColorPicker / PrismRadioGroup | Settings — Preferences |
| PrismPasswordInput | Settings — Security (3 field, giữ autocomplete) |
| PrismFileUpload | AddSkillPopup + EditSkillPopup — upload file skill |
| PrismAccordion | DetailPopup — tab Thông tin (Mô tả / File & hashtag / Lịch sử phiên bản) |
| PrismKbd | AuthScreen — hint demo credentials |
| PrismAmbientBackground | AppShell + AuthScreen — nền ambient (thay markup inline) |

### Nên ưu tiên tiếp theo

1. Nhóm Actions còn lại: IconButton, ButtonGroup, SplitButton, Link, CopyButton.
2. Feedback: Chip, ErrorState; Toast action và giới hạn queue.
3. Overlay: ContextMenu, CommandPalette.
4. Data: DataTable; Navigation: Stepper, Sidebar, NavigationMenu.
5. Layout: Stack, Cluster, Container, ScrollArea, AspectRatio; Content: Image, Figure, Markdown.
6. Advanced: RichTextEditor, DataGrid; Date/time: Calendar, TimePicker public.
7. Viết accessibility/interaction test cho mọi state mới trước khi thêm visual polish.

Tài liệu này là catalog và contract định hướng. Khi API thực tế thay đổi, implementation, test và tài liệu phải được cập nhật cùng một change để tránh tài liệu đi trước sản phẩm.
