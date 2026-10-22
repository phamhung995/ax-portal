# Hợp đồng component · v0.7

Tất cả component public xuất từ `src/components/index.js`. Owner: maintainer của workspace. Trạng thái: **experimental**. Props boolean mặc định `false`, props String mặc định chưa có giá trị trừ khi được ghi rõ. Các prop giá trị phải được consumer cập nhật qua event; component không tự lưu dữ liệu domain.

## Quy tắc chung

- `v-model` tương đương `modelValue` + `update:modelValue`; riêng Pagination dùng `v-model:page`.
- Native attrs của Button đi xuống button. Input/Textarea/Select chuyển attrs như `name`, `autocomplete`, `placeholder`, `aria-label` xuống control thật.
- Label bắt buộc theo hướng dẫn sử dụng; không dùng placeholder thay label.
- Native button hỗ trợ Enter/Space; input/select/checkbox/range giữ keyboard browser.
- Focus-visible có outline cyan 2px, offset 4px. Disabled/loading không dựa riêng vào alpha của cha.
- Token nền dùng alpha, backdrop blur nhẹ; text không có glow. Không lồng nhiều card để chia section.
- Kích thước component co theo container; Table/CodeBlock scroll ngang khi cần. Dialog không vượt viewport.
- Các component control nên đặt trong vùng có nền đủ yên, tránh đường sáng chạy ngang chữ.

## Actions

### PrismButton

**Purpose:** hành động; dùng link native cho điều hướng. Anatomy: button → spinner tùy chọn → default slot.

| Prop | Type / default | Giá trị |
| --- | --- | --- |
| variant | String / `primary` | `primary`, `secondary`, `ghost`, `danger` |
| type | String / `button` | `button`, `submit`, `reset` |
| loading | Boolean / false | Khóa click, hiện spinner, aria-busy |
| disabled | Boolean / false | Native disabled |

Slot: default cho text/icon. Events: native, gồm click. States: default, hover, focus-visible, pressed, disabled, loading. Loading giữ nhãn để hạn chế layout shift. Icon-only consumer phải có `aria-label`.

```vue
<PrismButton type="submit" :loading="saving">Lưu thay đổi</PrismButton>
<PrismButton variant="danger" :disabled="!selected" @click="remove">Xóa</PrismButton>
```

## Forms

### PrismFormField

Wrapper label/control/hint/error. Không dùng như control độc lập.

Props: `id: String` bắt buộc; `label`, `hint`, `error`: String; `required`, `disabled`: Boolean. Slots: `label`; default scoped `{ describedby, invalid }`. Error ưu tiên hint và có ID `${id}-error`; hint là `${id}-hint`. Không emit giá trị.

```vue
<PrismFormField id="custom" label="Giá trị" error="Vui lòng nhập" v-slot="field">
  <input id="custom" :aria-describedby="field.describedby" :aria-invalid="field.invalid" />
</PrismFormField>
```

### PrismInput

Text/email/password/url/search và các type native phù hợp. Không thay select cho lựa chọn hữu hạn. Anatomy: FormField → prefix/input/suffix → feedback.

| Prop | Type / default |
| --- | --- |
| modelValue | String hoặc Number |
| id | String / tự sinh bằng Vue useId |
| label, hint, error | String |
| type | String / `text` |
| disabled, readonly, required | Boolean |

Slots: prefix, suffix. Event: `update:modelValue(String)` theo native input. Numeric input vẫn emit chuỗi; consumer tự parse khi cần. States: empty, filled, hover, focus, disabled, readonly, error. Error liên kết `aria-invalid` và `aria-describedby`.

```vue
<PrismInput v-model="email" type="email" label="Email" name="email"
  autocomplete="email" :error="errors.email" required />
<PrismInput model-value="workspace-01" label="ID" readonly />
```

### PrismTextarea

Nội dung plain text nhiều dòng; không xử lý rich text. Props: `modelValue: String`, `id`, `label`, `hint`, `error`; `rows: Number = 3`, `maxlength: Number`; `disabled`, `readonly`, `required`. Event `update:modelValue(String)`. Khi có maxlength hiện số ký tự. Resize dọc; không tự nhảy chiều cao. State/feedback như Input; không có slot nội dung.

```vue
<PrismTextarea v-model="description" label="Mô tả" :maxlength="240" :rows="4" />
```

### PrismSelect

Native select cho danh sách gọn. Props: `modelValue: String|Number`, `options: Array = []`, `id`, `label`, `hint`, `error`, `placeholder`, `disabled`, `required`. Option: primitive string hoặc `{ label, value: String|Number, disabled?: Boolean }`. Placeholder dùng giá trị rỗng; không chọn placeholder làm giá trị thật. Event `update:modelValue` giữ kiểu số của option. Không hỗ trợ multiple/search trong v0.1. Native popup do OS/browser vẽ, không bảo đảm hiệu ứng kính của danh sách option.

```vue
<PrismSelect v-model="size" label="Số lượng" :options="[
  { label: 'Một', value: 1 }, { label: 'Hai', value: 2, disabled: true }
]" />
```

### PrismCheckbox

Lựa chọn độc lập; nhóm nhiều checkbox đặt trong fieldset/legend. Props: `modelValue: Boolean`, `id`, `label`, `disabled`, `indeterminate`. Event `update:modelValue(Boolean)`. Slot default bổ sung nhãn. Native indeterminate property biểu diễn trạng thái mixed; consumer cập nhật lại indeterminate khi xử lý lựa chọn. Space toggle; không dùng thay radio cho lựa chọn loại trừ nhau.

```vue
<PrismCheckbox v-model="selected" label="Design tokens" />
```

### PrismSwitch

Setting bật/tắt tức thời. Props: `modelValue: Boolean`, `label: String` bắt buộc, `disabled`. Event `update:modelValue(Boolean)`. Native checkbox với role switch và aria-checked; Space toggle. Không đổi nội dung nhãn theo on/off để giữ accessible name ổn định.

```vue
<PrismSwitch v-model="notifications" label="Thông báo" />
```

### PrismSlider

Khoảng số một thumb. Props: `modelValue: Number = 0`, `min = 0`, `max = 100`, `step = 1`, `label: String`, `unit: String = ''`, `disabled`. Event `update:modelValue(Number)`. Label + output + native range. Arrow/Home/End theo native browser; step phải dương và max lớn hơn min. Không dùng cho giá trị cần nhập chính xác mà không có input bổ sung.

```vue
<PrismSlider v-model="alpha" :min="8" :max="65" label="Độ che" unit="%" />
```

### PrismRadio

Một lựa chọn trong nhóm; dùng `PrismRadioGroup` khi cần quản lý nhóm. Props: `value: String|Number`, `id`, `label`, `name`, `checked: Boolean`, `disabled`. Event `update:modelValue(value)` khi người dùng chọn. Native radio; Space/Arrow theo browser.

```vue
<PrismRadio v-model="plan" value="pro" label="Pro" name="plan" />
```

### PrismRadioGroup

Nhóm radio với `role="radiogroup"`. Props: `modelValue: String|Number`, `options: Array = []` (`{ label, value, disabled? }`), `id`, `label` required, `hint`, `error`, `required`, `disabled`, `inline: Boolean`. Event `update:modelValue`. **Keyboard:** ArrowUp/Down/Left/Right di chuyển lựa chọn (bỏ qua disabled, có wrap) và focus theo giá trị mới. Không dùng cho lựa chọn độc lập — đó là Checkbox.

```vue
<PrismRadioGroup v-model="plan" label="Gói" :options="[
  { label: 'Basic', value: 'basic' }, { label: 'Pro', value: 'pro' }
]" />
```

### PrismNumberInput

Giá trị số với stepper và phím mũi tên. Props: `modelValue: String|Number`, `min`, `max`, `step: Number = 1`, `unit: String`, `placeholder`, `readonly`, còn lại như Input. Event `update:modelValue(Number)` — giá trị luôn được kẹp trong `[min, max]`; input rỗng emit chuỗi rỗng. Stepper ↑/↓ và phím ArrowUp/ArrowDown tăng/giảm theo `step`; `readonly` ẩn stepper và chặn mọi thay đổi. Không dùng cho số định dạng phức tạp (tiền tệ, mã) — đó là Input + consumer format.

```vue
<PrismNumberInput v-model="qty" label="Số lượng" :min="0" :max="10" unit="bản" />
```

### PrismPasswordInput

Nhập mật khẩu với nút hiện/ẩn. Props: `modelValue: String = ''`, `strength: Number = 0` (0–4, thanh độ mạnh), còn lại như Input. Event `update:modelValue(String)`. Nút toggle có `aria-pressed` và aria-label đổi theo trạng thái; icon eye/eye-off. `strength` là prop trình bày — consumer tự tính.

```vue
<PrismPasswordInput v-model="password" label="Mật khẩu" :strength="3" />
```

### PrismCombobox

Lựa chọn có tìm kiếm, single hoặc multiple. Props: `modelValue: String|Number|Array`, `options: Array = []` (`{ label, value, disabled? }`), `multiple: Boolean`, `clearable: Boolean`, `placeholder`, còn lại như Input. Event `update:modelValue` — single emit giá trị option; multiple emit mảng giá trị. Input `role="combobox"` + `aria-expanded`/`aria-controls`/`aria-activedescendant`; panel `role="listbox"` teleport ra `body` + `position: fixed` (z-index 85, dùng `useFloatingPanel`) nên không bị che bởi stacking context; lật lên/kẹp viewport, đóng khi click ngoài/Escape. **Keyboard:** ArrowDown/Up di chuyển highlight (option active scroll vào view), Enter chọn, Backspace (multiple, input rỗng) gỡ chip cuối. Multiple hiển thị chips có nút gỡ; clearable thêm nút xóa toàn bộ.

```vue
<PrismCombobox v-model="team" label="Đội" :options="teamOptions" />
<PrismCombobox v-model="tags" label="Nhãn" multiple clearable :options="tagOptions" />
```

### PrismFileUpload

Chọn/kéo thả file, kiểm tra loại và dung lượng phía client. Props: `modelValue: Array = []` (mảng `File`), `accept: String` (extension `.pdf` hoặc MIME `image/*`), `maxSize: Number = 5MB` (bytes), `multiple: Boolean`, `disabled`, `required`, còn lại như Input. Event `update:modelValue(Array)`. File không hợp lệ bị loại và hiện thông báo lỗi inline; không có upload thật (library không có backend). Input file ẩn (visually hidden, vẫn focusable); vùng drop là button — Enter/Space mở picker; hỗ trợ drag & drop. Danh sách file có nút xóa từng file và "Xóa tất cả".

```vue
<PrismFileUpload v-model="files" label="Tài liệu" accept=".pdf,.png" :max-size="2097152" />
```

## Navigation và data display

### PrismTabs

Hiển thị nội dung cùng ngữ cảnh. Props: `modelValue: String`, `items: Array = []`, `label: String = 'Nội dung'`. Item `{ label, value, disabled?, panelId? }`; value cần duy nhất, phù hợp làm phần ID. Consumer phải khởi tạo modelValue bằng một value enabled. Slots theo tên value. Event `update:modelValue(String)`.

**Automatic activation:** ArrowLeft/Right bỏ qua tab disabled, có wrap; Home/End tới đầu/cuối. Tab đang chọn có tabindex 0; mỗi panel có aria-labelledby. Không dùng cho điều hướng URL.

```vue
<PrismTabs v-model="tab" :items="[{ label: 'Tổng quan', value: 'overview' }]">
  <template #overview>Nội dung tổng quan</template>
</PrismTabs>
```

### PrismPagination

Props: `page: Number = 1`, `pageSize: Number = 5`, `total: Number = 0`. Event `update:page(Number)`. Page bắt đầu từ 1; pageSize > 0; consumer reset page khi total thay đổi làm page vượt giới hạn. Native previous/next buttons khóa ở biên, text trạng thái có role status, trang hiện tại aria-current. v0.1 dùng previous/next gọn, chưa có sibling/boundary count.

```vue
<PrismPagination v-model:page="page" :page-size="3" :total="rows.length" />
```

### PrismTable

Dữ liệu tĩnh có quan hệ hàng/cột; không phải DataGrid. Props: `columns: Array = []` với `{ key, label }`, `rows: Array = []`, `caption: String` bắt buộc, `rowKey: String = 'id'`, `loading`, `emptyText = 'Không có dữ liệu.'`. Mỗi row cần key duy nhất. Slots `cell-${column.key}` nhận `{ row, value }`. Không emit sort hoặc selection trong v0.1. Native table/caption/th scope col; vùng scroll có label, tabindex. Loading và empty là hai trạng thái riêng.

```vue
<PrismTable :columns="columns" :rows="rows" caption="Dự án">
  <template #cell-status="{ row }"><PrismBadge :tone="row.tone">{{ row.status }}</PrismBadge></template>
</PrismTable>
```

### PrismCard

Một đơn vị nội dung độc lập. Props: `title`, `eyebrow`, `description`: String. Slots: header, default, footer. Native article; không tự clickable, không emit. Consumer đặt button/link thật cho action. Surface kính alpha theo token, padding 24px; không dùng để bọc mọi section hoặc lồng nhiều card.

```vue
<PrismCard eyebrow="WORKSPACE" title="Aurora">
  Nội dung dự án
  <template #footer><PrismButton>Mở dự án</PrismButton></template>
</PrismCard>
```

### PrismGrid

Lưới layout cho KPI card, dashboard. Props: `columns: Number = 3`, `responsive: Boolean = false`, `gap: String = 'var(--ds-space-4)'`. Slot default chứa các item. CSS grid `repeat(columns, minmax(0, 1fr))`; khi `responsive`, tụt xuống 2 cột (≤1000px) và 1 cột (≤600px). Không emit, không state.

```vue
<PrismGrid :columns="3" responsive>
  <PrismCard title="Views" eyebrow="KPI">…</PrismCard>
</PrismGrid>
```

### PrismAvatar

Khối đại diện người dùng: ảnh hoặc initials. Props: `name: String`, `src: String`, `size: Number = 36`, `tone: String = 'accent'` (accent/violet/mint/amber/rose). Ảnh lỗi tự fallback về initials (2 chữ cái đầu). `name` làm `aria-label`/`title`. Không emit.

```vue
<PrismAvatar name="Lan Phạm" tone="violet" :size="40" />
```

### PrismAvatarGroup

Nhóm avatar chồng nhau. Props: `items: Array = []` (`{ name, src?, tone? }`), `max: Number = 4`, `size: Number = 32`, `label: String = 'Nhóm thành viên'`. Vượt `max` hiện `+N` (aria-label "+N thành viên khác"). `role="group"`.

```vue
<PrismAvatarGroup :items="members" :max="4" />
```

### PrismTimeline

Trục sự kiện theo thời gian. Props: `items: Array = []` (`{ title, description?, time?, tone? }` — tone: accent/success/warning, mặc định accent cho item đầu, neutral cho các item còn lại), `label: String = 'Trình tự sự kiện'`. Native `ol/li`; không emit.

```vue
<PrismTimeline :items="[{ title: 'Đã tạo', time: '09:00', tone: 'accent' }]" />
```

### PrismStat

Chỉ số KPI. Props: `label: String`, `value: String|Number`, `delta: String|Number` (tùy chọn), `deltaTone: String = 'auto'` (auto: `Number(delta) >= 0` → up, ngược lại → down; hoặc up/down/neutral), `hint: String`, `tone: String = 'accent'` (accent/violet/mint). Delta có icon mũi tên lên/xuống. Không emit.

```vue
<PrismStat label="Doanh thu" value="48.200" delta="+12%" hint="so với tháng trước" />
```

### PrismTree

Cây phân cấp mở/thu gọn, render recursive bằng `h()`. Props: `items: Array = []` (`{ id, label, meta?, children? }`), `label: String = 'Cây phân cấp'`, `defaultExpanded: Array = []` (mảng id mở sẵn). Event `select(node)`. `role="tree"`/`treeitem`, `aria-expanded`/`aria-level`/`aria-selected` trên node; nút toggle có aria-label "Mở rộng/Thu gọn". **Keyboard:** ArrowDown/Up di chuyển theo thứ tự hiển thị (chỉ node đang mở), ArrowRight mở nhánh hoặc xuống con đầu, ArrowLeft thu gọn nhánh hoặc lên node cha, Home/End tới đầu/cuối, Enter/Space chọn (emit `select` + `aria-selected`). Không có selection state nội bộ ngoài `aria-selected` — consumer tự theo dõi qua `select`.

```vue
<PrismTree :items="treeItems" :default-expanded="['src']" @select="onSelect" />
```

### PrismList

Danh sách có cấu trúc. Props: `items: Array = []` (`{ title, subtitle?, trailing? }`), `label: String = 'Danh sách'`, `selectable: Boolean`, `selected: Number = -1`. Event `select(index, item)` khi `selectable`. Khi `selectable`: `role="listbox"`/`option` + `aria-selected`. Slots `icon`, `icon-${i}`, `trailing-${i}`.

```vue
<PrismList :items="spaces" label="Không gian" selectable @select="openSpace" />
```

### PrismBreadcrumbs

Dấu vết điều hướng. Props: `items: Array = []` (`{ label }`), `label: String = 'Điều hướng'`. Event `navigate(index, item)` khi click item không phải cuối. Item cuối có `aria-current="page"`, các item trước là button. Native `nav` + `ol`.

```vue
<PrismBreadcrumbs :items="[{ label: 'Trang chủ' }, { label: 'Dự án' }, { label: 'Alpha' }]" />
```

## Date & time

Ba picker dùng chung calendar nội bộ (`PrismCalendar`, không export) và composable `useDatePopup` (không export). Input là readonly `role="combobox"` — không nhập tay, chỉ chọn qua lịch; mở bằng click/Enter/ArrowDown/Space, đóng bằng Escape, click ngoài, hoặc chọn xong. Popup là `.p-glass` được **teleport ra `body`** (`position: fixed`, z-index 90) tính vị trí từ input — không bị chặn bởi stacking context/overflow của container; tự lật lên khi gần đáy màn hình, kẹp trong viewport khi gần mép phải, recalibrate khi resize/scroll. Nếu picker nằm **trong `PrismDialog`** (native `<dialog>` ở top-layer), popup tự teleport vào dialog và chuyển sang `position: absolute` theo dialog (class `.p-date-popup--in-dialog`), đóng khi dialog đóng.

### PrismDatePicker

Props: `modelValue: String = ''` (ISO `YYYY-MM-DD`), `label`, `hint`, `error`, `placeholder = 'Chọn ngày…'`, `min`, `max` (ISO), `disabled`, `required`. Events: `update:modelValue(String)`, `change(String)`. Calendar 42 ô (6 tuần), thứ Hai là ngày đầu, ngày khác tháng hiện mờ, hôm nay có viền accent, ngày chọn nền accent. `min`/`max` disable ngày ngoài phạm vi.

```vue
<PrismDatePicker v-model="date" label="Ngày bắt đầu" :min="'2026-01-01'" />
```

### PrismDateRangePicker

Props: `modelValue: Array = []` (`[startISO, endISO]`), còn lại như DatePicker. Chọn lần 1 = start (emit `[start, '']`), chọn lần 2 = end (emit `[start, end]`, tự đảo nếu chọn ngược); vùng giữa highlight `is-range`. Chọn lần 3 đặt lại start.

```vue
<PrismDateRangePicker v-model="range" label="Khoảng thời gian" />
```

### PrismDatetimePicker

Props: `modelValue: String = ''` (ISO `YYYY-MM-DDTHH:mm`), `minuteStep: Number = 5`, còn lại như DatePicker. Popup gồm calendar + 2 cột giờ/phút; chọn ngày emit ngay, chọn giờ/phút giữ popup mở (chọn cả hai) rồi đóng bằng nút **Xong**/Escape/click ngoài.

```vue
<PrismDatetimePicker v-model="datetime" label="Thời gian họp" :minute-step="5" />
```

## Charts

Tất cả chart là SVG thuần, không dependency, không canvas. Data là props tĩnh; chart không sort/filter, không tooltip — consumer tự làm nếu cần. SVG có `role="img"` + `aria-label`; nhãn và số liệu render thành text SVG để screen reader đọc được.

### PrismBarChart

Props: `data: Array = []` với `{ label, value }`, `height: Number = 160`, `showValues: Boolean = true`, `ariaLabel: String = 'Biểu đồ cột'`. Cột dùng `--ds-button-primary-background` + viền accent; lưới ngang mờ chia 3 mức; nhãn trục X dưới mỗi cột, giá trị trên đỉnh cột (ẩn khi cột quá thấp). Không emit.

```vue
<PrismBarChart :data="[{ label: 'T1', value: 42 }, { label: 'T2', value: 68 }]" :height="170" />
```

### PrismLineChart

Props: `data: Array = []` với `{ label, value }`, `height: Number = 180`, `showDots: Boolean = true`, `ariaLabel: String = 'Biểu đồ đường'`. Đường accent 2px + vùng fill gradient cyan; dot đánh dấu mỗi điểm; trục Y tự scale theo min/max của data. Ít nhất 2 điểm để vẽ đường; 1 điểm chỉ hiện dot. Không emit.

```vue
<PrismLineChart :data="[{ label: 'T1', value: 12 }, { label: 'T2', value: 34 }]" />
```

### PrismDonutChart

Props: `data: Array = []` với `{ label, value, color? }`, `size: Number = 180`, `thickness: Number = 22`, `centerLabel: String`, `ariaLabel: String = 'Biểu đồ tròn'`. Màu mặc định theo thứ tự tokens accent/violet/teal/mint/amber/rose, có thể ghi đè bằng `color`. Legend tự render bên dưới (label + giá trị); `centerLabel` hiển thị số tổng ở giữa. Không emit.

```vue
<PrismDonutChart :data="[{ label: 'App', value: 56 }, { label: 'Web', value: 28 }]" center-label="84" />
```

### PrismSparkline

Đường xu hướng mini cho KPI card hoặc table row. Props: `data: Array = []` (mảng số), `width: Number = 120`, `height: Number = 36`, `ariaLabel: String = 'Xu hướng'`. Đường accent 1.5px + fill gradient; không trục, không nhãn. Không emit.

```vue
<PrismSparkline :data="[4, 8, 6, 12, 9, 15]" :width="120" :height="36" />
```

### PrismAreaChart

Đường xu hướng có vùng gradient. Props: `data: Array = []` với `{ label, value }`, `height: Number = 180`, `color: String = 'var(--ds-color-accent)'`, `showDots: Boolean = false`, `ariaLabel: String = 'Biểu đồ vùng'`. Vùng fill dùng `linearGradient` với id duy nhất (`useId`) để nhiều chart cùng trang không xung đột; trục Y tự scale. Không emit.

```vue
<PrismAreaChart :data="[{ label: 'T1', value: 18 }, { label: 'T2', value: 34 }]" />
```

### PrismRadarChart

So sánh nhiều chiều trên radar. Props: `data: Array = []` với `{ label, value }` (ít nhất 3 chiều), `size: Number = 240`, `levels: Number = 4`, `max: Number` (tự scale nếu không có), `ariaLabel: String = 'Biểu đồ radar'`. `levels` vòng lưới đồng tâm; nhãn trục tự căn theo góc. Không emit.

```vue
<PrismRadarChart :data="[{ label: 'Tốc độ', value: 80 }, { label: 'Chất lượng', value: 65 }, { label: 'Chi phí', value: 40 }]" />
```

### PrismGauge

Đồng hồ nửa tròn cho chỉ số. Props: `value: Number = 0`, `min: Number = 0`, `max: Number = 100`, `label: String`, `ariaLabel: String = 'Chỉ số'`. Vẽ nửa vòng cung track + value arc, 5 tick (0/25/50/75/100%), số giá trị ở trung tâm. `aria-label` mang "label: value/max". Không emit.

```vue
<PrismGauge :value="72" :max="100" label="Hoàn thành" />
```

### PrismHeatmap

Lưới nhiệt kiểu GitHub theo ngày. Props: `data: Array = []` với `{ date: 'YYYY-MM-DD', value }`, `weeks: Number = 26` (số cột hiển thị, kết thúc hôm nay), `max: Number` (tự scale nếu không có), `ariaLabel: String = 'Biểu đồ nhiệt'`. 5 mức cường độ (0–4) theo tỷ lệ value/max; mỗi ô có `aria-label` "date: value"; nhãn tháng ở đầu cột. Không emit.

```vue
<PrismHeatmap :data="activity" :weeks="20" />
```

### PrismStackedBarChart

Cột chồng nhiều series. Props: `data: Array = []` với `{ label, series: { name: value } }`, `height: Number = 200`, `ariaLabel: String = 'Biểu đồ cột chồng'`. Series lấy tên theo thứ tự xuất hiện; màu theo tokens accent/violet/teal/amber/rose; legend tự render bên dưới; tổng mỗi cột hiện trên đỉnh. Không emit.

```vue
<PrismStackedBarChart :data="[{ label: 'T1', series: { A: 30, B: 20 } }, { label: 'T2', series: { A: 45, B: 15 } }]" />
```

### PrismAccordion

Props: `items: Array = []` với `{ value, label, content }`, `modelValue: Array = []`, `multiple`, `collapsible = true`. Event `update:modelValue(Array)`. Slots theo value thay content. Native buttons Enter/Space; aria-expanded, controls, labelledby; panel dùng v-show. Với collapsible=false, consumer khởi tạo ít nhất một item mở. Multiple=false chỉ mở một item khi chọn mới. Không giấu nội dung quan trọng chỉ để tiết kiệm diện tích.

```vue
<PrismAccordion v-model="expanded" :items="questions" multiple />
```

## Feedback

### PrismBadge

Status ngắn, không phải button. Props: `tone: String = 'neutral'` trong neutral/info/success/warning/danger; `dot: Boolean`. Default slot là text. Dot trang trí aria-hidden, trạng thái luôn có chữ. Không emit.

### PrismAlert

Thông tin tại chỗ. Props: `tone = 'info'`, `title: String`, `dismissible`, `role: String = 'status'`. Slot default cho nội dung, actions cho thao tác. Event `dismiss()` là yêu cầu, consumer tự ẩn alert. Chỉ đặt role alert khi thông tin khẩn; không dùng role alert cho toàn bộ ví dụ tĩnh.

```vue
<PrismAlert v-if="visible" title="Đã lưu" tone="success" dismissible @dismiss="visible = false">
  Mọi thay đổi đã được đồng bộ.
</PrismAlert>
```

### PrismToast

Manager dùng một lần ở app root. Không có props; template ref expose `push({ title, message = '', tone = 'success', duration = 5000 }) → id` và `dismiss(id)`. Queue giữ thứ tự, placement bottom-right, duration=0 giữ tới khi đóng. Timer tạm dừng hover/focus rồi chạy thời gian còn lại. Có nút đóng và live region polite; không cướp focus. Unmount dọn timer. Không dùng thay inline validation hoặc làm cách duy nhất cung cấp thông tin quan trọng. Chưa hỗ trợ action button và giới hạn hàng đợi trong v0.1.

```js
toast.value.push({ title: 'Đã lưu', message: 'Workspace đã cập nhật.', duration: 5000 })
```

### PrismProgress

Props: `value: Number = 0`, `max: Number = 100` (phải > 0), `indeterminate`, `label: String`, `showValue = true`. Native progress liên kết label, bỏ value khi indeterminate. Không emit. Consumer cung cấp value trong 0..max; phần text phần trăm được clamp. Không giả % cho công việc chưa biết tiến độ.

### PrismSpinner

Props: `label: String = 'Đang tải…'`. Role status, vòng quay aria-hidden. Không emit/slot. Dùng chờ ngắn; nhãn vẫn hiện khi reduced motion tắt vòng quay.

### PrismSkeleton

Props: `width: String = '100%'`, `height: String = '16px'`. aria-hidden; không emit. Consumer cần status loading riêng. Dùng kích thước gần nội dung thật, không dùng cho trạng thái lỗi. Animation tắt khi reduced motion.

### PrismEmptyState

Props: `title: String = 'Chưa có dữ liệu'`, `description: String`. Slots icon và default cho actions. Heading, description và action gợi bước tiếp theo. Không emit; không dùng cho lỗi mạng — lỗi cần lời giải thích và retry.

```vue
<PrismEmptyState title="Chưa có dự án" description="Tạo dự án đầu tiên.">
  <PrismButton @click="create">Tạo mới</PrismButton>
</PrismEmptyState>
```

## Overlays

### PrismDialog

Modal cần tập trung. Native dialog.showModal(), Teleport body, backdrop nhẹ, title/description gắn ID. Không dùng cho thông báo ngắn vốn phù hợp Toast/Alert.

| Prop | Type / default | Mô tả |
| --- | --- | --- |
| modelValue | Boolean / false | Mở/đóng controlled |
| title | String, required | Accessible title |
| description | String | aria-describedby khi có |
| size | String / md | sm 400px, md 520px, lg 720px |
| closeOnOverlay | Boolean / true | Click thực sự ngoài bounds mới yêu cầu đóng |
| closeOnEscape | Boolean / true | Escape yêu cầu đóng |
| preventClose | Boolean / false | Chặn mọi yêu cầu đóng từ người dùng khi pending |
| initialFocus | String | Selector bên trong dialog |
| placement | String | Dành cho Drawer, left/right |

Slots: default body, footer actions, `title-extra` (render giữa title và nút đóng — dùng cho metadata như điểm đánh giá/views). Emits: `update:modelValue(Boolean)`, `close()` khi người dùng yêu cầu đóng. Thay modelValue từ consumer không emit close. Modal native giới hạn focus, Escape được xử lý controlled, đóng khôi phục focus về trigger còn tồn tại. Consumer vẫn có thể đóng bằng đổi modelValue dù preventClose=true.

Body cuộn khi dài; width không vượt viewport trừ margin 16px. Không mở nhiều dialog lồng nhau. Thử trên browser có native dialog; không có polyfill legacy.

```vue
<PrismDialog v-model="open" title="Tạo dự án" :prevent-close="saving" initial-focus="#name">
  <PrismInput id="name" v-model="name" label="Tên dự án" :error="error" required />
  <template #footer><PrismButton :loading="saving" @click="save">Lưu</PrismButton></template>
</PrismDialog>
```

### PrismDrawer

Wrapper của Dialog cho panel ngữ cảnh. Props: `modelValue`, `title` required, `description`, `placement: String = 'right'` (`left` hoặc `right`), `closeOnOverlay = true`, `closeOnEscape = true`. Slots default/footer. Events update:modelValue và close. Full viewport height, tối đa 420px rộng, mobile chừa 16px. Cùng keyboard/focus semantics của Dialog; không lồng drawer.

### PrismTooltip

Gợi ý nhỏ hiện khi hover hoặc focus. Props: `content: String = ''`, `placement: String = 'top'` (top/bottom/left/right), `delay: Number = 120` (ms), `disabled: Boolean`. Slot default là trigger; slot `content` ghi đè text. Bubble `role="tooltip"` teleport ra `body` + `position: fixed` (z-index 90), tự kẹp trong viewport và lật vị trí khi gần mép. `pointer-events: none` — không chặn tương tác. Escape ẩn khi focus. Không dùng cho thông tin quan trọng chỉ có ở tooltip.

```vue
<PrismTooltip content="Sao chép" placement="top">
  <PrismButton variant="ghost">Copy</PrismButton>
</PrismTooltip>
```

### PrismPopover

Panel neo vào trigger. Props: `placement: String = 'bottom-start'` (trình bày). Slot `trigger` (bọc button) và default (nội dung panel). Panel `role="dialog"` teleport ra `body` + `position: fixed` (z-index 85, dùng `useFloatingPanel`); đóng khi click ngoài hoặc Escape. Trigger có `aria-expanded`/`aria-controls`. Dùng cho nội dung mở rộng ngắn; dialog dài hơn nên dùng PrismDialog.

```vue
<PrismPopover>
  <template #trigger><PrismButton variant="secondary">Chi tiết</PrismButton></template>
  <p>Nội dung panel…</p>
</PrismPopover>
```

### PrismDropdownMenu

Thực đơn hành động. Props: `items: Array = []` (`{ label, value?, danger?, disabled?, type?: 'separator' }`), `label: String = 'Thực đơn'`. Event `select(item)`. Slot `trigger` ghi đè nút mở (mặc định là label + caret). Panel `role="menu"` teleport ra `body` + `position: fixed` (z-index 85, align phải với trigger); item `role="menuitem"`, separator `role="separator"`. **Keyboard:** Enter/Space/ArrowDown mở menu và focus item đầu, ArrowUp/Down di chuyển focus (bỏ qua separator/disabled, wrap), Home/End tới đầu/cuối, Enter/Space chọn, Escape đóng và trả focus về trigger; chọn bằng chuột cũng trả focus về trigger.

```vue
<PrismDropdownMenu label="Hành động" :items="[
  { label: 'Đổi tên', value: 'rename' },
  { type: 'separator' },
  { label: 'Xóa', value: 'delete', danger: true },
]" @select="onSelect" />
```

## Content

### PrismCodeBlock

Props: `code: String` required, `filename: String = 'example.vue'`. Nội dung render text escaped, không v-html. Native pre/code có scroll và tabindex; button copy dùng Clipboard API, status thành công/thất bại. Nếu clipboard bị từ chối, người dùng chọn và copy text trực tiếp. Không syntax highlighting hoặc line numbers trong v0.1. Không emit.

```vue
<PrismCodeBlock filename="tokens.css" :code="cssSource" />
```

### PrismAmbientBackground

Nền không gian decorative cho app root. Không props, không emit, không state. Render 8 lớp: base gradient 5 radial glow, 3 orb ánh sáng trôi chậm, mạng plexus 3 lớp (depth of field), bokeh mờ, dot grid 2 tầng, noise hạt mịn, vignette. Root có `aria-hidden="true"` + `pointer-events: none`, `position: fixed; z-index: 0` — consumer đặt nội dung chính ở `z-index ≥ 1`. Animation tôn trọng `prefers-reduced-motion` (rule global tắt animation). Đặt đúng một lần ở app root, không lồng trong scroll container.

```vue
<PrismAmbientBackground />
<!-- Nội dung app phải có position + z-index ≥ 1 để nổi trên nền -->
```

### PrismDivider

Đường phân cách. Props: `label: String` (tùy chọn — có nhãn thì `role="separator"`), `orientation: String = 'horizontal'` (`horizontal`/`vertical`). Không emit.

```vue
<PrismDivider label="HOẶC" />
<PrismDivider orientation="vertical" />
```

### PrismKbd

Phím tắt hiển thị chuẩn. Props: `code: String` required (làm `aria-label`). Native `<kbd>`. Không emit.

```vue
<PrismKbd code="Ctrl" /> <PrismKbd code="K" />
```

### PrismColorPicker

Chọn màu: preset + 3 slider HSL + hex input. Props: `modelValue: String = '#67e8f9'` (hex), `presets: Array = []` (mảng hex, mặc định 8 màu token), `disabled`, `required`, còn lại như Input. Event `update:modelValue(String hex)`. Trigger là button (swatch + hex + caret) với `aria-haspopup`/`aria-expanded`; panel teleport ra `body` + `position: fixed` (z-index 85, dùng `useFloatingPanel`), đóng khi click ngoài/Escape. Slider Hue (0–360°), Saturation và Lightness (0–100%) emit hex ngay khi kéo, giữ 2 thành phần còn lại. Hex input dùng draft nội bộ (gõ thoải mái), commit khi blur/Enter; giá trị không hợp lệ bị bỏ qua và input trở về giá trị hiện tại. Không có 2D saturation/brightness picker trong v0.7.

```vue
<PrismColorPicker v-model="accent" label="Màu nhấn" :presets="['#67e8f9', '#a58aef']" />
```

## Utility

### `.p-list-item`

Dòng tương tác trong menu/dropdown (notification, user menu, list). Dùng trên `<button>` hoặc `<a>`; không phải component Vue.

Cấu trúc: `<button class="p-list-item is-unread|is-read">` → `.p-list-item-dot` (trạng thái) → `.p-list-item-title` → `.p-list-item-sub` (metadata, tùy chọn).

States: default (trong suốt), hover, active, focus-visible. Hover dùng nền gradient tối `--ds-list-item-background` (mặc định `rgb(8 13 32 / .92)`) phủ lên `.p-glass` để che nền ambient phía sau, giúp chữ rõ hơn; kèm viền trái 2px `--ds-list-item-border` (accent) định vị dòng. Dòng `is-read` giảm opacity title còn `.65`; hover khôi phục opacity 1. `.p-list-item-sub` sáng lên thành `text-secondary` khi hover.

Tokens (lớp component, có thể override sau stylesheet):

| Token | Mặc định | Vai trò |
| --- | --- | --- |
| `--ds-list-item-background` | `rgb(8 13 32 / .92)` | Nền tối khi hover/active |
| `--ds-list-item-hover-accent` | `rgb(103 232 249 / .14)` | Đầu gradient hover (góc trái) |
| `--ds-list-item-border` | `var(--ds-color-accent)` | Viền trái khi hover |

```html
<button class="p-list-item is-unread">
  <span class="p-list-item-dot" aria-hidden="true"></span>
  <span style="flex:1; min-width:0">
    <span class="p-list-item-title" style="display:block">Skill mới được duyệt</span>
    <span class="p-list-item-sub">2 phút trước</span>
  </span>
</button>
```

## State và kiểm thử

| Nhóm | Trạng thái / kiểm thử chính |
| --- | --- |
| Button | Loading/disabled chặn click, label giữ nguyên; native keyboard |
| Forms | Controlled emit, label/description/error, required, readonly/disabled; native input |
| Tabs/Accordion | Keyboard tabs, skip disabled, wrap, expanded/selected và liên kết panel |
| Pagination/Table | Biên trang, loading/empty, caption và scope, scroll mobile |
| Overlay | Validation, focus ban đầu, Escape, focus return, focus nằm trong modal |
| Toast | Queue, timeout, hover pause/resume, live region |
| List item | Hover nền tối che ambient, viền trái accent, is-read dim + hover khôi phục |
| Grid/Charts | Cột grid + responsive breakpoint; chart render đúng số bar/điểm/đoạn, role=img + aria-label |
| Ambient | aria-hidden, đủ 8 lớp (plexus/grid/noise/vignette/3 orb), pointer-events none |
| Date pickers | Mở/đóng + chọn ngày emit ISO; range 2 lần chọn; datetime giờ/phút + Xong; min/max disable |
| Radio/Number/Password | Radiogroup role + phím mũi tên wrap/skip disabled; number clamp min/max + stepper; password toggle type + aria-pressed |
| Combobox/FileUpload | Filter option + chọn emit; multiple chips; panel teleport body; file accept theo extension/MIME + maxSize reject |
| Tooltip/Popover/Dropdown | Tooltip show/hide theo hover; popover open/close trigger; dropdown menu role + select + separator |
| Data display | Avatar initials fallback + group overflow +N; tree expand/collapse aria-expanded; list select index; breadcrumbs aria-current; stat delta direction; timeline tone |
| ColorPicker/Charts mới | Color picker preset + hue emit hex; area/radar/gauge/heatmap/stacked bar render đúng số phần tử, role=img + aria-label |
| Visual | Desktop/mobile screenshot, overflow, reduced motion; axe tự động |

Không giả định bộ test đã kiểm mọi tổ hợp props. Cần kiểm tra thêm screen reader và contrast khi theme/background thay đổi. Các status chưa đủ điều kiện ổn định phải tiếp tục giữ experimental.
