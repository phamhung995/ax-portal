# Prism · Neon Design System

Design system Vue 3 theo `../Design system.md`, sử dụng vật liệu kính và neon từ `../FUTURISTIC-NEON-DESIGN-SYSTEM.md`.

Phiên bản **0.8.0 — experimental**. Có 56 component tái sử dụng, catalog tương tác, design tokens ba lớp, Glass Lab, patterns, tài liệu API và kiểm thử. “Implemented” trong tài liệu nguồn được xem là phạm vi cần xây, không phải bằng chứng rằng mã đã tồn tại trong folder ban đầu.

## Chạy local

Yêu cầu Node 22+ và npm. Dependency được khóa trong `package-lock.json`.

```sh
npm ci
npm run dev
```

Mở URL Vite in ra trong terminal, mặc định `http://127.0.0.1:5173`.

```sh
npm run build       # dist/ cho showcase; dist-library/ cho thư viện
npm run preview     # xem bản production showcase
npm test            # kiểm thử hợp đồng component
npm run test:e2e    # browser, accessibility, responsive và screenshot
```

Nếu máy chưa có Chromium cho Playwright, chạy `npx playwright install chromium` trước `test:e2e`. Không cần API key, font từ CDN hay backend.

## Cấu trúc

```text
src/
  tokens.css             Global → semantic → component tokens
  components.css         Chất liệu và state của public components
  components/            56 Vue SFC + barrel export + unit tests
  index.js               Public library entry, kèm stylesheet
  App.vue                Catalog, search, filters, patterns, Glass Lab
  Hologram.vue           Minh họa giao diện bằng SVG/CSS
  ShowcasePanel.vue      Preview/code cho từng nhóm
  showcase.css           Layout riêng của ứng dụng tài liệu
docs/
  COMPONENTS.md          Hợp đồng API và ví dụ
  ROADMAP.md             Phạm vi, ưu tiên và governance
tests/
  showcase.spec.js       Playwright + axe + ảnh desktop/mobile
```

## Dùng component

Trong dự án Vue đang dùng mã nguồn:

```vue
<script setup>
import { ref } from 'vue'
import { PrismButton, PrismInput, PrismDialog } from './components'
import './tokens.css'
import './components.css'

const open = ref(false)
const name = ref('')
</script>

<template>
  <PrismButton @click="open = true">Tạo mới</PrismButton>
  <PrismDialog v-model="open" title="Không gian mới">
    <PrismInput v-model="name" label="Tên không gian" required />
    <template #footer>
      <PrismButton @click="open = false">Hoàn tất</PrismButton>
    </template>
  </PrismDialog>
</template>
```

Sau build, có thể import từ `dist-library/prism.js` và `dist-library/prism.css`. Vue và `lucide-vue-next` là dependency external của bản thư viện. Package để `private: true` nhằm phục vụ workspace local; chưa publish registry.

## Chất liệu

- Canvas navy/indigo với ánh sáng cyan/violet cục bộ và đường nền rất nhẹ.
- Surface alpha mặc định 10%, blur 4px, viền mảnh; không giảm opacity của cả component.
- Dialog alpha 40%, blur 7px và scrim navy 30%. Có thể nhận ra biến thiên nền qua popup.
- Chữ sắc, ít glow. Ánh sáng chủ yếu nằm ở viền, icon và trạng thái cần chú ý.
- Glass Lab thay đổi alpha/blur trên component trong catalog; dialog giữ token riêng để dễ đọc.
- Không hỗ trợ backdrop-filter: tăng tint làm fallback. Reduced motion: tắt animation/transition.

Đặt override sau stylesheet:

```css
:root {
  --ds-surface-alpha: .24;
  --ds-surface-blur: 4px;
  --ds-dialog-background: rgb(15 32 62 / .40);
}
```

Glass Lab cho tải file CSS override. Thay đổi token và dữ liệu demo chỉ lưu trong phiên, không gửi mạng hay lưu máy chủ.

## Các màn hình

- **Components:** tìm kiếm không phân biệt dấu, lọc nhóm, preview/code, ví dụ state.
- **Foundations:** màu có thể copy, typography, spacing và radius.
- **Glass Lab:** thử độ trong/blur/glow, mở popup và tải token.
- **Patterns:** create flow, list, async feedback và empty state.
- **Tài liệu:** ví dụ import, nguyên tắc API và phạm vi phiên bản.

Ctrl/Cmd+K focus tìm kiếm. Sidebar có chế độ mobile. Navigation dùng hash để mở trực tiếp từng màn hình.

## Kiểm thử và giới hạn

Unit tests kiểm tra controlled events, khóa button khi loading/disabled, liên kết label/error, giá trị select dạng số, keyboard tabs, accordion, pagination, table loading/empty và toast pause/resume. Browser tests kiểm tra create flow, Escape/focus return, search, tabs, pagination, export CSS, desktop/mobile và reduced motion. Axe kiểm tra tự động trên catalog và modal; screenshot nằm trong `test-results/`.

Kiểm thử tự động không thay thế đánh giá screen reader thủ công, tất cả trình duyệt hoặc contrast trên mọi background tùy biến. Không gắn nhãn production-ready khi chưa hoàn tất kiểm tra môi trường sản phẩm. Roadmap nâng cao được liệt kê riêng trong [docs/ROADMAP.md](docs/ROADMAP.md).
