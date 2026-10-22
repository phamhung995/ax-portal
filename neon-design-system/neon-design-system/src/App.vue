<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowUpRight, ArrowRight, Search, Box, Layers, Palette, Sparkles, BookOpen, ChevronRight, Plus, Mail, SlidersHorizontal, PanelLeftClose, Menu, X, Check, Copy, RotateCcw, CheckCheck, Download, Bell } from 'lucide-vue-next'
import * as Prism from './components'
import ShowcasePanel from './ShowcasePanel.vue'
import Hologram from './Hologram.vue'
import PrismAmbientBackground from './components/PrismAmbientBackground.vue'
import { useFloatingPanel } from './useFloatingPanel.js'
import { groups, specimens } from './catalog'
const { PrismButton, PrismInput, PrismSelect, PrismCheckbox, PrismSwitch, PrismSlider, PrismTabs, PrismDialog, PrismDrawer, PrismBadge, PrismAlert, PrismProgress, PrismSpinner, PrismSkeleton, PrismEmptyState, PrismCard, PrismPagination, PrismTable, PrismTextarea, PrismAccordion, PrismCodeBlock, PrismToast, PrismGrid, PrismBarChart, PrismLineChart, PrismDonutChart, PrismSparkline, PrismDatePicker, PrismDateRangePicker, PrismDatetimePicker, PrismRadio, PrismRadioGroup, PrismNumberInput, PrismPasswordInput, PrismCombobox, PrismFileUpload, PrismTooltip, PrismPopover, PrismDropdownMenu, PrismAvatar, PrismAvatarGroup, PrismTimeline, PrismStat, PrismTree, PrismList, PrismBreadcrumbs, PrismDivider, PrismKbd, PrismColorPicker, PrismAreaChart, PrismRadarChart, PrismGauge, PrismHeatmap, PrismStackedBarChart } = Prism
const view = ref('components')
const query = ref('')
const category = ref('all')
const menuOpen = ref(false)
const notifControl = ref(null)
const { open: notifOpen, panelRef: notifPanelRef, panelPos: notifPos, togglePanel: toggleNotifPanel, closePanel: closeNotifPanel } = useFloatingPanel(notifControl, { align: 'right' })
const notifs = [
  { id: 1, title: 'Skill mới được duyệt', time: '2 phút trước', unread: true },
  { id: 2, title: 'Bình luận mới trên "Data Pipeline"', time: '18 phút trước', unread: true },
  { id: 3, title: 'Đã đồng bộ thư viện hoàn tất', time: '1 giờ trước', unread: false },
  { id: 4, title: 'Cập nhật phiên bản design tokens', time: 'Hôm qua', unread: false },
]
function toggleNotifs() { toggleNotifPanel() }
const mobile = ref(window.matchMedia('(max-width: 760px)').matches)
const searchElement = ref(null)
const toast = ref(null)
const dialogOpen = ref(false)
const drawerOpen = ref(false)
const projectName = ref('')
const projectError = ref('')
const projectDescription = ref('')
const saving = ref(false)
const formName = ref('Neon workspace')
const email = ref('hello@prism.design')
const framework = ref('vue')
const notifications = ref(true)
const autoSave = ref(false)
const checked = ref(true)
const unchecked = ref(false)
const intensity = ref(68)
const description = ref('Một không gian mới cho những ý tưởng táo bạo.')
const miniTab = ref('overview')
const expanded = ref(['glass'])
const alpha = ref(10)
const blur = ref(4)
const glow = ref(true)
const page = ref(1)
const projects = ref([
  { id: 1, name: 'Orbit dashboard', type: 'Application', status: 'Hoạt động', tone: 'success' },
  { id: 2, name: 'Prism landing', type: 'Website', status: 'Đang thiết kế', tone: 'info' },
  { id: 3, name: 'Nebula mobile', type: 'Mobile', status: 'Bản nháp', tone: 'neutral' },
  { id: 4, name: 'Lunar analytics', type: 'Application', status: 'Hoạt động', tone: 'success' },
  { id: 5, name: 'Cosmos studio', type: 'Website', status: 'Bản nháp', tone: 'neutral' },
])
const columns = [{ key: 'name', label: 'Dự án' }, { key: 'type', label: 'Loại' }, { key: 'status', label: 'Trạng thái' }]
const chartBars = [{ label: 'T1', value: 42 }, { label: 'T2', value: 68 }, { label: 'T3', value: 31 }, { label: 'T4', value: 85 }, { label: 'T5', value: 54 }]
const chartLine = [{ label: 'T1', value: 12 }, { label: 'T2', value: 34 }, { label: 'T3', value: 26 }, { label: 'T4', value: 48 }, { label: 'T5', value: 41 }, { label: 'T6', value: 62 }]
const chartDonut = [{ label: 'Application', value: 56 }, { label: 'Website', value: 28 }, { label: 'Mobile', value: 20 }]
const sparkData = [4, 8, 6, 12, 9, 15, 13, 18, 16, 22]
const pickDate = ref('2026-09-24')
const pickRange = ref(['2026-09-01', '2026-09-15'])
const pickDatetime = ref('2026-09-24T14:30')
const planChoice = ref('pro')
const quantity = ref(3)
const passwordValue = ref('neon-glass-2026')
const teamChoice = ref('core')
const tagChoices = ref(['design'])
const uploadedFiles = ref([])
const dropdownAction = ref('')
const breadcrumbTrail = [{ label: 'Trang chủ' }, { label: 'Dự án' }, { label: 'Alpha' }]
const breadcrumbNav = ref('')
const accentColor = ref('#67e8f9')
const treeItems = [
  { id: 'src', label: 'src', meta: '3', children: [
    { id: 'app', label: 'App.vue', meta: '1.2 KB' },
    { id: 'components', label: 'components', meta: '24', children: [
      { id: 'button', label: 'PrismButton.vue' },
      { id: 'input', label: 'PrismInput.vue' },
    ] },
  ] },
  { id: 'docs', label: 'docs', meta: '2', children: [
    { id: 'components-doc', label: 'COMPONENTS.md' },
    { id: 'roadmap-doc', label: 'ROADMAP.md' },
  ] },
]
const timelineItems = [
  { title: 'Đã tạo không gian', time: '09:00', tone: 'accent', description: 'Workspace Aurora được khởi tạo.' },
  { title: 'Đã đồng bộ tokens', time: '09:04', tone: 'success', description: 'Design tokens v0.7 đã áp dụng.' },
  { title: 'Chờ duyệt bản thiết kế', time: '11:30', tone: 'warning' },
]
const listItems = [
  { title: 'Alpha studio', subtitle: '12 dự án · 8 thành viên', trailing: 'Active' },
  { title: 'Beta lab', subtitle: '5 dự án · 3 thành viên', trailing: 'Draft' },
]
const statRevenue = { label: 'Doanh thu', value: '48.200', delta: '+12%', hint: 'so với tháng trước' }
const statUptime = { label: 'Uptime', value: '99.98%', delta: '+0.02%', hint: '30 ngày gần nhất' }
const radarData = [{ label: 'Tốc độ', value: 82 }, { label: 'Chất lượng', value: 68 }, { label: 'Chi phí', value: 45 }, { label: 'Bảo trì', value: 74 }, { label: 'Mở rộng', value: 58 }]
const areaData = [{ label: 'T1', value: 18 }, { label: 'T2', value: 34 }, { label: 'T3', value: 26 }, { label: 'T4', value: 52 }, { label: 'T5', value: 44 }, { label: 'T6', value: 68 }]
const stackedData = [
  { label: 'T1', series: { 'Desktop': 32, 'Mobile': 18, 'Tablet': 8 } },
  { label: 'T2', series: { 'Desktop': 45, 'Mobile': 22, 'Tablet': 6 } },
  { label: 'T3', series: { 'Desktop': 38, 'Mobile': 30, 'Tablet': 10 } },
  { label: 'T4', series: { 'Desktop': 52, 'Mobile': 26, 'Tablet': 12 } },
]
const heatmapData = computed(() => {
  const out = []
  const today = new Date()
  for (let d = 0; d < 140; d++) {
    const date = new Date(today)
    date.setDate(date.getDate() - (139 - d))
    const seed = (d * 37 + 11) % 17
    out.push({ date: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`, value: seed === 0 ? 0 : seed })
  }
  return out
})
const pageRows = computed(() => projects.value.slice((page.value - 1) * 3, page.value * 3))
const normalize = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
const filtered = computed(() => specimens.filter(item => (category.value === 'all' || item.category === category.value) && normalize(item.name + item.names + item.description).includes(normalize(query.value))))
const views = { components: 'Components', foundations: 'Foundations', playground: 'Glass Lab', patterns: 'Patterns', docs: 'Tài liệu' }
const nav = [{ id: 'components', icon: Box }, { id: 'foundations', icon: Palette }, { id: 'playground', icon: Sparkles }, { id: 'patterns', icon: Layers }, { id: 'docs', icon: BookOpen }]
const colors = [{ name: 'Cyan', token: 'cyan-400', hex: '#67E8F9', role: 'Primary / Focus' }, { name: 'Violet', token: 'violet-400', hex: '#A58AEF', role: 'Accent / Depth' }, { name: 'Mint', token: 'mint-300', hex: '#9CE8D0', role: 'Success' }, { name: 'Rose', token: 'rose-300', hex: '#FFACC4', role: 'Danger' }, { name: 'Amber', token: 'amber-300', hex: '#F3D28C', role: 'Warning' }, { name: 'Space', token: 'space-950', hex: '#100D2B', role: 'Canvas' }]
const questions = [{ value: 'glass', label: 'Vì sao kính vẫn nhìn xuyên được?', content: 'Màu nền dùng alpha thấp (mặc định 10%), blur chỉ 4px. Chữ và icon giữ nguyên độ rõ; ánh sáng phía sau vẫn đi qua bề mặt.' }, { value: 'keyboard', label: 'Có hỗ trợ bàn phím không?', content: 'Có. Dùng Tab để di chuyển, Enter hoặc Space để tương tác; phím mũi tên để chuyển tab, Escape để đóng dialog.' }]
const startCode = `import { ref } from 'vue'\nimport { PrismButton, PrismDialog } from './src/components'\nimport './src/tokens.css'\nimport './src/components.css'\n\nconst open = ref(false)`
const glowValue = computed(() => glow.value ? '0 0 6px rgb(103 232 249 / .16), 0 0 26px rgb(103 232 249 / .08)' : 'none')
const tokensCode = computed(() => `:root {\n  --ds-surface-alpha: ${(alpha.value / 100).toFixed(2)};\n  --ds-surface-blur: ${blur.value}px;\n  --ds-shadow-glow: ${glowValue.value};\n  --ds-dialog-background: rgb(15 32 62 / .40);\n  --ds-dialog-blur: 7px;\n  --ds-color-accent: #67e8f9;\n}`)
function go(id) { view.value = id; query.value = ''; menuOpen.value = false; window.location.hash = id; window.scrollTo({ top: 0, behavior: 'instant' }); nextTick(() => document.getElementById('main')?.focus({ preventScroll: true })) }
function closeMenu() { menuOpen.value = false; nextTick(() => document.querySelector('.mobile-menu')?.focus()) }
function resized() { mobile.value = window.matchMedia('(max-width: 760px)').matches; if (!mobile.value) menuOpen.value = false }
function explore() { document.getElementById('library')?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' }) }
function notify(title, message = '', tone = 'success') { toast.value?.push({ title, message, tone }) }
async function copy(value) { try { await navigator.clipboard.writeText(value); notify('Đã sao chép', value) } catch { notify('Không thể sao chép', 'Hãy chọn và sao chép nội dung trực tiếp.', 'warning') } }
let saveTimer
function createProject() {
  if (!projectName.value.trim()) { projectError.value = 'Vui lòng nhập tên không gian.'; document.getElementById('new-project-name')?.focus(); return }
  saving.value = true
  saveTimer = setTimeout(() => { projects.value.unshift({ id: Date.now(), name: projectName.value.trim(), type: 'Application', status: 'Bản nháp', tone: 'neutral' }); saving.value = false; dialogOpen.value = false; page.value = 1; notify('Đã tạo không gian', projectName.value.trim()); projectName.value = ''; projectDescription.value = ''; projectError.value = '' }, 650)
}
function onBreadcrumbNavigate(index, item) { breadcrumbNav.value = `Đã bấm: ${item.label} (index ${index})` }
function downloadTokens() { const url = URL.createObjectURL(new Blob([tokensCode.value], { type: 'text/css' })); const a = document.createElement('a'); a.href = url; a.download = 'prism-overrides.css'; a.click(); URL.revokeObjectURL(url); notify('Đã xuất design tokens') }
function shortcut(event) { if ((event.ctrlKey || event.metaKey) && event.key === 'k' && !dialogOpen.value && !drawerOpen.value && !menuOpen.value) { event.preventDefault(); searchElement.value?.focus() } if (event.key === 'Escape' && menuOpen.value) closeMenu() }
function syncHash() { const value = window.location.hash.slice(1); if (views[value]) view.value = value }
watch([alpha, blur, glow], () => { document.documentElement.style.setProperty('--ds-surface-alpha', alpha.value / 100); document.documentElement.style.setProperty('--ds-surface-blur', `${blur.value}px`); document.documentElement.style.setProperty('--ds-shadow-glow', glow.value ? '0 0 6px rgb(103 232 249 / .16), 0 0 26px rgb(103 232 249 / .08)' : 'none') })
watch(query, value => { if (value) view.value = 'components' })
watch(projectName, () => { projectError.value = '' })
watch(menuOpen, value => { if (value) nextTick(() => document.querySelector('.mobile-close')?.focus()) })
onMounted(() => { syncHash(); window.addEventListener('keydown', shortcut); window.addEventListener('hashchange', syncHash); window.addEventListener('resize', resized) })
onBeforeUnmount(() => { clearTimeout(saveTimer); window.removeEventListener('keydown', shortcut); window.removeEventListener('hashchange', syncHash); window.removeEventListener('resize', resized) })
</script>

<template>
  <a href="#main" class="skip-link">Đến nội dung chính</a>
  <PrismAmbientBackground />
  <button v-if="menuOpen" class="sidebar-scrim" aria-label="Đóng nền điều hướng" @click="closeMenu"></button>
  <aside class="sidebar" :class="{ 'is-open': menuOpen }" :inert="mobile && !menuOpen">
    <button v-if="mobile" class="p-icon-button mobile-close" aria-label="Đóng điều hướng" @click="closeMenu"><X :size="18" /></button>
    <a class="brand" href="#components" @click.prevent="go('components')"><img src="/prism.svg" alt="" width="34" height="34" /><span>prism<span class="brand-dot">.</span></span><span class="version">v0.7</span></a>
    <p class="sidebar-caption">THE NEON DESIGN SYSTEM</p>
    <div class="sidebar-group-label">WORKSPACE</div>
    <nav aria-label="Điều hướng chính"><a v-for="item in nav" :key="item.id" :href="`#${item.id}`" :aria-current="view === item.id ? 'page' : undefined" @click.prevent="go(item.id)"><component :is="item.icon" :size="17" /><span>{{ views[item.id] }}</span><span v-if="item.id === 'components'" class="nav-count">56</span><span v-if="item.id === 'playground'" class="nav-new">LAB</span></a></nav>
    <div class="sidebar-divider"></div>
    <div class="sidebar-group-label">COMPONENT LIBRARY</div>
    <nav class="category-nav" aria-label="Nhóm component"><button v-for="group in groups.filter(g => g.id !== 'all')" :key="group.id" :class="{ selected: view === 'components' && category === group.id }" @click="go('components'); category = group.id"><span class="category-dot"></span><span>{{ group.label }}</span><small>{{ group.count.toString().padStart(2, '0') }}</small></button></nav>
    <div class="sidebar-bottom"><div class="sidebar-note"><div class="sidebar-note-icon"><Sparkles :size="19" /></div><strong>Designed to glow.</strong><p>Một hệ thống nhất quán.<br />Vô hạn khả năng sáng tạo.</p><button @click="go('docs')">Bắt đầu xây dựng <ArrowUpRight :size="14" /></button></div><div class="sidebar-status"><span class="live-dot"></span> Vue 3 · CSS tokens <span>↗</span></div></div>
  </aside>
  <div class="main-shell" :inert="mobile && menuOpen">
    <header class="topbar"><div class="breadcrumbs"><button class="p-icon-button mobile-menu" aria-label="Mở điều hướng" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen"><Menu :size="20" /></button><span>Design system</span><ChevronRight :size="12" /><strong>{{ views[view] }}</strong></div><div class="topbar-actions"><label class="searchbox"><Search :size="15" /><input ref="searchElement" v-model="query" aria-label="Tìm component" placeholder="Tìm component…" /><kbd>Ctrl K</kbd></label><div class="notif-anchor"><button ref="notifControl" class="p-icon-button" aria-label="Thông báo" :aria-expanded="notifOpen" @click="toggleNotifPanel"><Bell :size="17" /><span class="notif-badge">2</span></button></div><Teleport to="body"><div v-if="notifOpen" ref="notifPanelRef" class="notif-panel p-glass" :style="{ top: `${notifPos.top}px`, left: `${notifPos.left}px` }"><div class="notif-panel-head">Notifications<button @click="closeNotifPanel()">Đánh dấu tất cả đã đọc</button></div><button v-for="n in notifs" :key="n.id" class="p-list-item" :class="n.unread ? 'is-unread' : 'is-read'" @click="closeNotifPanel()"><span class="p-list-item-dot" aria-hidden="true"></span><span style="flex:1; min-width:0"><span class="p-list-item-title" style="display:block">{{ n.title }}</span><span class="p-list-item-sub">{{ n.time }}</span></span></button></div></Teleport><button class="p-icon-button settings-button" aria-label="Tùy chỉnh giao diện" @click="drawerOpen = true"><SlidersHorizontal :size="17" /></button><span class="topbar-divider"></span><span class="profile" aria-label="Prism workspace">P</span></div></header>
    <main id="main" tabindex="-1">
      <section v-if="view === 'components' && !query && category === 'all'" class="hero"><div class="hero-copy"><div class="release-label"><span class="live-dot"></span> OPEN SOURCE MINDSET. FUTURE-READY DESIGN.</div><h1>Build beyond<br />the <span>ordinary.</span></h1><p>Giao diện tương lai, bắt đầu từ những chi tiết.<br />Khám phá hệ thống component neon glass<br class="desktop-break" /> — nhẹ, nhất quán và đầy cảm hứng.</p><div class="hero-actions"><PrismButton @click="explore()">Khám phá components <ArrowRight :size="15" /></PrismButton><button class="text-link" @click="go('docs')">Đọc tài liệu <ArrowUpRight :size="14" /></button></div><div class="hero-footnote"><span><Check :size="12" /> Vue 3 native</span><span><Check :size="12" /> Keyboard friendly</span><span><Check :size="12" /> Token-driven</span></div></div><Hologram /></section>
      <template v-if="view === 'components'">
        <div v-if="!query && category === 'all'" class="system-strip"><div><Box :size="18" /><strong>56</strong><span>Components</span></div><div><Palette :size="18" /><strong>3</strong><span>Token layers</span></div><div><Layers :size="18" /><strong>100%</strong><span>Vue composable</span></div><div><Sparkles :size="18" /><strong>Neon glass</strong><span>Visual language</span></div></div>
        <section id="library" class="library"><div class="section-heading"><div><div class="section-eyebrow">THE BUILDING BLOCKS</div><h2>Component library<span class="heading-dot">.</span></h2><p>Những chi tiết nhỏ. Trải nghiệm lớn.</p></div><button class="text-link" @click="go('playground')"><SlidersHorizontal :size="14" /> Tùy chỉnh style</button></div>
          <div class="library-toolbar"><div class="filter-tabs" aria-label="Lọc nhóm component"><button v-for="group in groups" :key="group.id" :aria-pressed="category === group.id" @click="category = group.id">{{ group.label }}<span v-if="group.id === 'all'">56</span></button></div><span class="result-count">{{ filtered.length }} nhóm</span></div>
          <div v-if="filtered.length" class="specimen-grid">
            <ShowcasePanel v-for="(item, index) in filtered" :key="item.id" :specimen="item" :index="index">
              <template v-if="item.id === 'buttons'"><div class="demo-label">VARIANTS</div><div class="demo-row"><PrismButton @click="dialogOpen = true"><Plus :size="15" /> Tạo mới</PrismButton><PrismButton variant="secondary" @click="go('playground')">Khám phá <ArrowUpRight :size="14" /></PrismButton><PrismButton variant="ghost" @click="notify('Ghost button', 'Hành động nhẹ, phản hồi rõ ràng.', 'info')">Ghost <ArrowRight :size="14" /></PrismButton></div><div class="demo-label spaced">STATES</div><div class="demo-row"><PrismButton loading>Đang xử lý</PrismButton><PrismButton disabled>Disabled</PrismButton><PrismButton variant="danger" @click="notify('Danger variant', 'Đây là bản xem thử trạng thái cảnh báo.', 'danger')">Danger</PrismButton></div><div class="inline-note"><span class="keycap">↵</span> Sẵn sàng cho chuột và bàn phím.</div></template>
              <template v-else-if="item.id === 'inputs'"><PrismInput v-model="email" label="Email" type="email" placeholder="you@example.com"><template #prefix><Mail :size="15" /></template></PrismInput><div class="form-two"><PrismInput v-model="formName" label="Tên workspace" /><PrismCombobox v-model="framework" label="Framework" :options="[{ label: 'Vue 3', value: 'vue' }, { label: 'Nuxt', value: 'nuxt' }]" /></div><span class="inline-note"><CheckCheck :size="14" /> Label, hint và validation tích hợp.</span></template>
              <template v-else-if="item.id === 'selection'"><div class="selection-grid"><div><div class="demo-label">SWITCH</div><PrismSwitch v-model="notifications" label="Thông báo" /><PrismSwitch v-model="autoSave" label="Tự động lưu" /></div><div><div class="demo-label">CHECKBOX</div><PrismCheckbox v-model="checked" label="Design tokens" /><PrismCheckbox v-model="unchecked" label="Component docs" /></div></div><PrismSlider v-model="intensity" label="Cường độ ánh sáng" unit="%" /></template>
              <template v-else-if="item.id === 'date-picker'"><PrismDatePicker v-model="pickDate" label="Ngày bắt đầu" :min="'2026-01-01'" :max="'2026-12-31'" /><span class="inline-note">Giá trị ISO: <code>{{ pickDate || '—' }}</code></span></template>
              <template v-else-if="item.id === 'date-range'"><PrismDateRangePicker v-model="pickRange" label="Khoảng thời gian" :min="'2026-01-01'" /><span class="inline-note">Chọn lần 1 = ngày bắt đầu, lần 2 = ngày kết thúc. Giá trị: <code>[{{ pickRange.join(', ') || '—' }}]</code></span></template>
              <template v-else-if="item.id === 'datetime-picker'"><PrismDatetimePicker v-model="pickDatetime" label="Thời gian họp" :minute-step="5" /><span class="inline-note">Chọn ngày trên lịch, sau đó giờ/phút bên phải. Giá trị: <code>{{ pickDatetime || '—' }}</code></span></template>
              <template v-else-if="item.id === 'badges'"><div class="demo-row badge-row"><PrismBadge tone="success" dot>Hoạt động</PrismBadge><PrismBadge tone="info" dot>Đang xử lý</PrismBadge><PrismBadge tone="warning" dot>Chờ duyệt</PrismBadge><PrismBadge tone="danger" dot>Lỗi</PrismBadge></div><PrismAlert tone="success" title="Mọi thay đổi đã được đồng bộ">Không gian sáng tạo của bạn đã sẵn sàng.</PrismAlert><PrismAlert tone="info" title="Một hệ thống, nhiều khả năng">Dùng design tokens để giữ mọi thứ nhất quán.</PrismAlert></template>
              <template v-else-if="item.id === 'overlays'"><div class="overlay-mini"><div class="mini-backline"></div><div class="mini-dialog"><div><span class="p-dot"></span><span>Không gian cho ý tưởng mới</span><X :size="12" /></div><i></i><i></i><span class="mini-dialog-action">Let’s create <ArrowUpRight :size="11" /></span></div><span class="glass-annotation">TRANSPARENT BY DESIGN</span></div><div class="demo-row"><PrismButton @click="dialogOpen = true">Mở dialog <ArrowUpRight :size="14" /></PrismButton><PrismButton variant="ghost" @click="drawerOpen = true">Mở drawer <PanelLeftClose :size="14" /></PrismButton></div></template>
              <template v-else-if="item.id === 'loading'"><PrismProgress :value="intensity" label="Đồng bộ thư viện" /><div class="loading-demo"><PrismSpinner label="Đang tải assets…" /><span class="demo-label">SKELETON</span></div><div class="skeleton-person"><PrismSkeleton width="42px" height="42px" /><div><PrismSkeleton width="62%" height="10px" /><PrismSkeleton width="90%" height="8px" /><PrismSkeleton width="42%" height="8px" /></div></div><span class="inline-note">Chuyển động nhẹ. Luôn có phản hồi.</span></template>
              <template v-else-if="item.id === 'table'"><PrismTable :columns="columns" :rows="pageRows" caption="Thư viện dự án"><template #cell-status="{ row }"><PrismBadge :tone="row.tone" dot>{{ row.status }}</PrismBadge></template></PrismTable><PrismPagination v-model:page="page" :page-size="3" :total="projects.length" /></template>
              <template v-else-if="item.id === 'cards'"><PrismEmptyState title="Chương tiếp theo đang chờ bạn" description="Tạo một không gian cho ý tưởng đầu tiên."><PrismButton variant="secondary" @click="dialogOpen = true"><Plus :size="15" /> Tạo không gian</PrismButton></PrismEmptyState></template>
              <template v-else-if="item.id === 'textarea'"><PrismTextarea v-model="description" label="Mô tả dự án" :maxlength="240" hint="Một vài dòng về điều bạn muốn xây dựng." /><PrismInput model-value="prism-workspace" label="Workspace ID" readonly hint="Giá trị chỉ đọc, vẫn có thể sao chép." /></template>
              <template v-else-if="item.id === 'navigation'"><PrismTabs v-model="miniTab" :items="[{ label: 'Tổng quan', value: 'overview' }, { label: 'Chi tiết', value: 'details' }]" label="Ví dụ navigation"><template #overview><p class="muted small">Dùng phím ← → để chuyển tab.</p></template><template #details><p class="muted small">Nội dung được liên kết bằng tabpanel và aria-controls.</p></template></PrismTabs><PrismAccordion v-model="expanded" :items="questions" /></template>
              <template v-else-if="item.id === 'toast'"><PrismAlert tone="info" title="Một phản hồi vừa đủ">Toast xếp hàng, có thể đóng và tạm dừng khi hover hoặc focus.</PrismAlert><div class="demo-row"><PrismButton @click="notify('Đã lưu thay đổi', 'Mọi thứ đã được cập nhật trong workspace.')"><Check :size="15" /> Lưu thay đổi</PrismButton><PrismButton variant="secondary" @click="notify('Đã thêm vào hàng đợi', 'Bạn có thể tiếp tục công việc.', 'info')">Thử thông báo</PrismButton></div></template>
              <template v-else-if="item.id === 'code'"><PrismCodeBlock filename="your-next-idea.vue" :code="'<PrismButton variant=&quot;primary&quot;>\n  Build something extraordinary.\n</PrismButton>'" /></template>
              <template v-else-if="item.id === 'grid'"><PrismGrid :columns="3" responsive><PrismCard title="Alpha" eyebrow="01"><PrismSparkline :data="sparkData" :width="140" :height="34" aria-label="Xu hướng Alpha" /></PrismCard><PrismCard title="Beta" eyebrow="02"><PrismSparkline :data="[9, 5, 11, 8, 14, 10, 17, 15, 20, 19]" :width="140" :height="34" aria-label="Xu hướng Beta" /></PrismCard><PrismCard title="Gamma" eyebrow="03"><PrismSparkline :data="[15, 12, 10, 13, 9, 11, 8, 12, 10, 14]" :width="140" :height="34" aria-label="Xu hướng Gamma" /></PrismCard></PrismGrid><span class="inline-note">3 cột → 2 cột (≤1000px) → 1 cột (≤600px).</span></template>
              <template v-else-if="item.id === 'bar-chart'"><PrismBarChart :data="chartBars" :height="170" aria-label="Doanh thu theo tuần" /><span class="inline-note">Cột neon, lưới mờ, giá trị trên đỉnh.</span></template>
              <template v-else-if="item.id === 'line-chart'"><PrismLineChart :data="chartLine" :height="180" aria-label="Lượt truy cập theo tuần" /><span class="inline-note">Đường gradient + vùng fill, dot đánh dấu điểm.</span></template>
              <template v-else-if="item.id === 'donut-chart'"><div class="donut-demo"><PrismDonutChart :data="chartDonut" :size="170" center-label="104" aria-label="Phân bổ loại dự án" /><div class="donut-demo-side"><PrismCard eyebrow="QUICK KPI" title="Tổng dự án"><PrismSparkline :data="sparkData" :width="150" :height="34" aria-label="Xu hướng tổng" /><p class="muted small">104 dự án · +18% so với tháng trước</p></PrismCard></div></div></template>
              <template v-else-if="item.id === 'sparkline'"><div class="spark-row"><div class="spark-card p-glass"><span class="spark-label">VIEWS</span><strong>12.480</strong><PrismSparkline :data="sparkData" :width="150" :height="38" aria-label="Xu hướng views" /></div><div class="spark-card p-glass"><span class="spark-label">RATING</span><strong>4.6</strong><PrismSparkline :data="[4, 4.2, 4.1, 4.4, 4.5, 4.6, 4.5, 4.6]" :width="150" :height="38" aria-label="Xu hướng rating" /></div></div><span class="inline-note">Nhỏ gọn, đặt trong KPI card hoặc table row.</span></template>
              <template v-else-if="item.id === 'ambient'"><div class="ambient-demo"><div class="ambient-demo-legend"><span class="p-dot"></span> Nền không gian đang hiển thị phía sau trang này</div><div class="ambient-demo-layers"><span>5 radial glow</span><span>3 orb trôi</span><span>plexus 3 lớp</span><span>bokeh</span><span>dot grid</span><span>noise</span><span>vignette</span></div><span class="inline-note">Decorative: <code>aria-hidden</code>, <code>pointer-events: none</code>. Đặt một lần ở app root, nằm dưới nội dung (z-index 0).</span></div></template>
              <template v-else-if="item.id === 'radio'"><PrismRadioGroup v-model="planChoice" label="Gói dịch vụ" :options="[{ label: 'Basic', value: 'basic' }, { label: 'Pro', value: 'pro' }, { label: 'Enterprise', value: 'enterprise', disabled: true }]" /><div class="form-two"><PrismRadio v-model="planChoice" value="basic" label="Basic" name="plan-single" /><PrismRadio v-model="planChoice" value="pro" label="Pro" name="plan-single" /></div><span class="inline-note">Phím mũi tên di chuyển giữa các lựa chọn.</span></template>
              <template v-else-if="item.id === 'number-input'"><PrismNumberInput v-model="quantity" label="Số lượng" :min="0" :max="10" unit="bản" /><div class="form-two"><PrismNumberInput v-model="quantity" label="Giá trị" :min="0" :max="100" :step="5" /><PrismNumberInput model-value="42.5" label="Tỷ lệ" :step="0.5" unit="%" readonly /></div><span class="inline-note">Giá trị luôn bị kẹp trong min/max; phím ↑/↓ tăng giảm.</span></template>
              <template v-else-if="item.id === 'password-input'"><PrismPasswordInput v-model="passwordValue" label="Mật khẩu" :strength="3" placeholder="••••••••" /><span class="inline-note">Nút hiện/ẩn mật khẩu + thanh độ mạnh 4 bậc.</span></template>
              <template v-else-if="item.id === 'combobox'"><PrismCombobox v-model="teamChoice" label="Đội" placeholder="Chọn đội…" :options="[{ label: 'Core', value: 'core' }, { label: 'Growth', value: 'growth' }, { label: 'Platform', value: 'platform' }]" /><PrismCombobox v-model="tagChoices" label="Nhãn (nhiều)" multiple clearable :options="[{ label: 'design', value: 'design' }, { label: 'frontend', value: 'frontend' }, { label: 'infra', value: 'infra' }]" /><span class="inline-note">Gõ để lọc, Enter để chọn; multiple hiển thị chips.</span></template>
              <template v-else-if="item.id === 'file-upload'"><PrismFileUpload v-model="uploadedFiles" label="Tài liệu" accept=".pdf,.png,.svg" :max-size="2097152" multiple /><span class="inline-note">Kéo thả hoặc click; kiểm tra loại file và dung lượng.</span></template>
              <template v-else-if="item.id === 'tooltip'"><div class="demo-row"><PrismTooltip content="Sao chép vào clipboard"><PrismButton variant="ghost">Hover vào đây</PrismButton></PrismTooltip><PrismTooltip content="Tooltip placement bottom" placement="bottom"><PrismButton variant="secondary">Placement bottom</PrismButton></PrismTooltip><PrismTooltip content="Hiện cả khi focus bàn phím" placement="right"><PrismButton>Focus (Tab)</PrismButton></PrismTooltip></div><span class="inline-note">Hiện khi hover hoặc focus, ẩn khi rời; 4 vị trí.</span></template>
              <template v-else-if="item.id === 'popover'"><PrismPopover><template #trigger><PrismButton variant="secondary">Mở popover</PrismButton></template><div class="p-field"><label>Đặt tên không gian</label><PrismInput placeholder="Ví dụ: Aurora studio" /><p class="p-field-hint">Tên hiển thị trong workspace.</p></div></PrismPopover><span class="inline-note">Panel teleport ra body, đóng khi click ngoài hoặc Escape.</span></template>
              <template v-else-if="item.id === 'dropdown-menu'"><PrismDropdownMenu label="Hành động" :items="[{ label: 'Đổi tên', value: 'rename' }, { label: 'Nhân bản', value: 'duplicate' }, { type: 'separator' }, { label: 'Xóa', value: 'delete', danger: true }]" @select="dropdownAction = $event.value" /><span class="inline-note">Lựa chọn: <code>{{ dropdownAction || '—' }}</code> · Mũi tên + Enter, Escape đóng.</span></template>
              <template v-else-if="item.id === 'avatar'"><div class="demo-row"><PrismAvatar name="Lan Phạm" tone="accent" /><PrismAvatar name="Minh Trần" tone="violet" /><PrismAvatar name="An Nguyễn" tone="mint" :size="48" /><PrismAvatar name="Khách" /></div><PrismAvatarGroup :items="[{ name: 'Lan Phạm' }, { name: 'Minh Trần', tone: 'violet' }, { name: 'An Nguyễn', tone: 'mint' }, { name: 'Huy Lê', tone: 'amber' }, { name: 'Quỳnh Vũ', tone: 'rose' }, { name: 'Đạt Phạm' }]" :max="4" /><span class="inline-note">Ảnh hoặc initials; nhóm chồng nhau, overflow thành +N.</span></template>
              <template v-else-if="item.id === 'timeline'"><PrismTimeline :items="timelineItems" label="Lịch sử không gian" /><span class="inline-note">Trục thời gian có tone: accent, success, warning.</span></template>
              <template v-else-if="item.id === 'stat'"><div class="form-two"><PrismStat v-bind="statRevenue" tone="accent" /><PrismStat v-bind="statUptime" tone="mint" /></div><span class="inline-note">KPI card: giá trị lớn, delta tăng/giảm, hint.</span></template>
              <template v-else-if="item.id === 'tree'"><PrismTree :items="treeItems" label="Cấu trúc thư mục" :default-expanded="['src', 'components']" /><span class="inline-note">Mở/thu gọn từng nhánh, role=tree, aria-expanded.</span></template>
              <template v-else-if="item.id === 'list'"><PrismList :items="listItems" label="Không gian làm việc" selectable /></template>
              <template v-else-if="item.id === 'breadcrumbs'"><PrismBreadcrumbs :items="breadcrumbTrail" label="Điều hướng" @navigate="onBreadcrumbNavigate" /><div class="demo-row"><PrismDivider label="HOẶC" /><PrismKbd code="Ctrl" /><PrismKbd code="K" /><span class="muted small">— mở tìm kiếm nhanh</span></div><span class="inline-note">Bấm link để điều hướng · <code>{{ breadcrumbNav || 'chưa chọn' }}</code></span></template>
              <template v-else-if="item.id === 'divider'"><PrismDivider label="PHÂN VÙNG" /><div class="demo-row"><PrismBadge tone="info">Trước</PrismBadge><PrismDivider orientation="vertical" /><PrismBadge tone="success">Sau</PrismBadge></div><span class="inline-note">Ngang có nhãn, dọc không nhãn (role=separator).</span></template>
              <template v-else-if="item.id === 'kbd'"><div class="demo-row"><PrismKbd code="⌘" /><PrismKbd code="K" /><span class="muted small">Tìm kiếm nhanh</span></div><div class="demo-row"><PrismKbd code="Esc" /><span class="muted small">Đóng popup</span></div><span class="inline-note">Phím tắt hiển thị chuẩn, aria-label đầy đủ.</span></template>
              <template v-else-if="item.id === 'color-picker'"><PrismColorPicker v-model="accentColor" label="Màu nhấn" /><span class="inline-note">Preset + hue slider + hex input. Giá trị: <code>{{ accentColor }}</code></span></template>
              <template v-else-if="item.id === 'area-chart'"><PrismAreaChart :data="areaData" :height="180" aria-label="Lượt tải theo tuần" /><span class="inline-note">Vùng gradient dưới đường xu hướng.</span></template>
              <template v-else-if="item.id === 'radar-chart'"><PrismRadarChart :data="radarData" :size="240" aria-label="Đánh giá sản phẩm theo chiều" /><span class="inline-note">So sánh nhiều chiều, 4 vòng mức.</span></template>
              <template v-else-if="item.id === 'gauge'"><PrismGauge :value="intensity" :max="100" label="Hoàn thành" aria-label="Tỷ lệ hoàn thành" /><span class="inline-note">Nửa tròn, tick 5 mức, số ở trung tâm.</span></template>
              <template v-else-if="item.id === 'heatmap'"><PrismHeatmap :data="heatmapData" :weeks="20" aria-label="Hoạt động 20 tuần gần nhất" /><span class="inline-note">Lưới nhiệt kiểu GitHub, 5 mức cường độ.</span></template>
              <template v-else-if="item.id === 'stacked-bar-chart'"><PrismStackedBarChart :data="stackedData" :height="200" aria-label="Lượt truy cập theo thiết bị" /><span class="inline-note">Cột chồng 3 series, legend tự động.</span></template>
            </ShowcasePanel>
          </div><PrismEmptyState v-else title="Không tìm thấy component" description="Thử một từ khóa khác hoặc bỏ bộ lọc."><PrismButton @click="query = ''; category = 'all'">Xóa bộ lọc</PrismButton></PrismEmptyState>
        </section>
        <div class="bottom-callout"><div class="callout-icon"><Sparkles :size="25" /></div><div><h3>Cùng một hệ thống. Dấu ấn của bạn.</h3><p>Tinh chỉnh chất liệu kính và ánh sáng trong Glass Lab.</p></div><PrismButton variant="secondary" @click="go('playground')">Vào Glass Lab <ArrowUpRight :size="15" /></PrismButton></div>
      </template>

      <template v-else-if="view === 'foundations'">
        <div class="page-heading"><span class="section-eyebrow">THE VISUAL LANGUAGE</span><h1>Built on <span>light.</span></h1><p>Màu sắc, khoảng cách và chất liệu — nền tảng cho mọi trải nghiệm.</p></div>
        <section class="foundation-section"><div class="section-heading"><div><h2>Color spectrum</h2><p>Neon có tiết chế. Mỗi màu một vai trò.</p></div><PrismBadge tone="info">GLOBAL → SEMANTIC → COMPONENT</PrismBadge></div><div class="swatch-grid"><button v-for="color in colors" :key="color.name" class="swatch p-glass" @click="copy(color.hex)"><span class="swatch-color" :style="{ background: color.hex }"><Copy :size="15" /></span><strong>{{ color.name }}</strong><code>{{ color.hex }}</code><small>{{ color.role }}</small></button></div></section>
        <section class="foundation-section type-section"><div><span class="section-eyebrow">TYPOGRAPHY</span><h2>Clarity in every character.</h2><p>System sans cho nội dung.<br />Monospace cho dữ liệu và thông số.</p></div><div class="type-specimens"><div><span>DISPLAY / 48</span><strong>Ideas, illuminated.</strong></div><div><span>HEADING / 24</span><h3>Thiết kế cho điều tiếp theo.</h3></div><div><span>BODY / 14</span><p>Chữ sắc nét. Phân cấp rõ ràng. Không glow trên nội dung dài.</p></div><div><span>MONO / 12</span><code>--ds-color-accent: #67e8f9;</code></div></div></section>
        <section class="foundation-section"><div class="section-heading"><div><h2>Space & geometry</h2><p>Một nhịp điệu nhất quán trên mọi kích thước màn hình.</p></div></div><div class="spacing-ruler p-glass"><div v-for="space in [4,8,12,16,24,32,48,64]" :key="space"><span :style="{ height: `${space}px` }"></span><code>{{ space }}</code></div></div><div class="geometry-grid"><div v-for="radius in [8,12,16]" :key="radius" class="p-glass" :style="{ borderRadius: `${radius}px` }"><span>{{ radius }}px</span><small>{{ radius === 8 ? 'Controls' : radius === 12 ? 'Cards' : 'Dialogs' }}</small></div></div></section>
        <PrismCodeBlock filename="tokens.css — semantic aliases" :code="':root {\n  --ds-color-accent: var(--ds-color-cyan-400);\n  --ds-color-canvas: var(--ds-color-space-950);\n  --ds-color-success: var(--ds-color-mint-300);\n  --ds-button-primary-background: rgb(39 151 180 / .20);\n}'" />
      </template>

      <template v-else-if="view === 'playground'">
        <div class="page-heading"><span class="section-eyebrow">MATERIAL EXPLORER</span><h1>A little light.<br /><span>A new dimension.</span></h1><p>Chỉnh chất liệu trực tiếp. Nhìn ánh sáng đi xuyên qua từng lớp kính.</p></div>
        <div class="lab-layout"><div class="lab-stage"><div class="lab-grid"></div><div class="lab-orb orb-cyan"></div><div class="lab-orb orb-violet"></div><svg class="lab-line" viewBox="0 0 600 450"><path d="M-50 350C100 0 180 480 300 220S540 460 670 100"/></svg><div class="lab-label">LIVE MATERIAL PREVIEW <span class="live-dot"></span></div><PrismCard class="lab-card" eyebrow="THE GLASS EFFECT" title="See what lies beyond." description="Ánh sáng và đường nét phía sau vẫn hiện qua bề mặt."><div class="lab-card-stat"><span>{{ alpha }}<small>%</small></span><div>SURFACE TINT<br /><span>{{ blur }}px backdrop blur</span></div></div><template #footer><PrismButton @click="dialogOpen = true">Thử popup xuyên thấu <ArrowUpRight :size="15" /></PrismButton></template></PrismCard><span class="lab-bottom">LAYER 01 — AMBIENT / LAYER 02 — GLASS / LAYER 03 — CONTENT</span></div><div class="lab-controls p-glass"><div class="section-eyebrow">MATERIAL SETTINGS</div><h3>Make it yours.</h3><PrismSlider v-model="alpha" :min="8" :max="65" label="Độ che của mặt kính" unit="%" /><PrismSlider v-model="blur" :min="0" :max="16" label="Backdrop blur" unit="px" /><PrismSwitch v-model="glow" label="Neon glow" /><div class="lab-rule"></div><p class="small muted">Tint thấp và blur nhẹ giúp giữ chi tiết background. Popup dùng tint 40% để nội dung dễ đọc hơn.</p><PrismButton variant="ghost" @click="alpha = 10; blur = 4; glow = true"><RotateCcw :size="14" /> Đặt lại mặc định</PrismButton><PrismButton @click="downloadTokens"><Download :size="15" /> Xuất CSS tokens</PrismButton></div></div><PrismCodeBlock filename="prism-overrides.css" :code="tokensCode" />
      </template>

      <template v-else-if="view === 'patterns'">
        <div class="page-heading"><span class="section-eyebrow">BETTER TOGETHER</span><h1>From pieces<br />to <span>possibilities.</span></h1><p>Ghép các primitive thành luồng công việc có ý nghĩa.</p></div>
        <div class="patterns-grid"><PrismCard eyebrow="01 / CREATE FLOW" title="Tạo workspace" description="Label rõ ràng, kiểm tra dữ liệu và phản hồi sau khi lưu."><div class="pattern-steps"><span>01 · Nhập thông tin</span><ChevronRight :size="14" /><span>02 · Xác nhận</span><ChevronRight :size="14" /><span>03 · Hoàn tất</span></div><PrismButton @click="dialogOpen = true">Thử luồng tạo mới <Plus :size="15" /></PrismButton></PrismCard><PrismCard eyebrow="02 / LIST & DETAIL" title="Không gian làm việc" description="Dữ liệu demo trong phiên hiện tại; tạo mới để thêm vào bảng."><PrismTable :columns="columns" :rows="pageRows" caption="Danh sách workspace"><template #cell-status="{ row }"><PrismBadge :tone="row.tone">{{ row.status }}</PrismBadge></template></PrismTable><PrismPagination v-model:page="page" :page-size="3" :total="projects.length" /></PrismCard><PrismCard eyebrow="03 / ASYNC FEEDBACK" title="Trạng thái có ngữ cảnh"><PrismProgress :value="intensity" label="Đang chuẩn bị thư viện" /><div class="pattern-gap"></div><PrismAlert tone="info" title="Công việc vẫn tiếp tục">Giữ nội dung hiện tại trong lúc chờ kết quả.</PrismAlert><template #footer><PrismButton variant="secondary" @click="notify('Đã hoàn tất đồng bộ', 'Thư viện đã được cập nhật.')">Mô phỏng hoàn tất</PrismButton></template></PrismCard><PrismCard eyebrow="04 / EMPTY STATE" title="Một điểm bắt đầu"><PrismEmptyState title="Ý tưởng đầu tiên của bạn?" description="Bắt đầu nhỏ, tạo nên điều khác biệt."><PrismButton @click="dialogOpen = true">Tạo dự án <ArrowRight :size="14" /></PrismButton></PrismEmptyState></PrismCard></div>
      </template>

      <template v-else-if="view === 'docs'">
        <div class="page-heading"><span class="section-eyebrow">START BUILDING</span><h1>Your next idea,<br /><span>powered by Prism.</span></h1><p>Vue 3. Native semantics. Một nguồn design tokens duy nhất.</p></div>
        <div class="docs-layout"><div><h2>Bắt đầu trong dự án này</h2><p class="muted">Import component từ public entry và nạp hai stylesheet nền tảng.</p><PrismCodeBlock filename="App.vue · script setup" :code="startCode" /><div class="pattern-gap"></div><PrismCodeBlock filename="App.vue · template" :code="'<PrismButton @click=&quot;open = true&quot;>\n  Tạo không gian\n</PrismButton>\n<PrismDialog v-model=&quot;open&quot; title=&quot;Xin chào, tương lai&quot;>\n  Ý tưởng của bạn bắt đầu từ đây.\n</PrismDialog>'" /><h2 class="docs-subheading">Hợp đồng component</h2><PrismAccordion v-model="expanded" multiple :items="[{ value: 'controlled', label: 'Controlled state & v-model', content: 'Input, Select, Checkbox, Switch, Slider, Tabs, Accordion và Dialog dùng modelValue / update:modelValue. Pagination dùng page / update:page.' }, { value: 'semantics', label: 'Keyboard & accessibility', content: 'Native button, input, select và dialog. Tabs dùng mũi tên, Home/End và automatic activation. Dialog giới hạn focus trong modal, Escape đóng và trả focus về trigger.' }, { value: 'status', label: 'Phạm vi v0.7', content: '56 component đã triển khai, trạng thái experimental. Các component nâng cao còn lại trong catalog như DataGrid, RichTextEditor và CommandPalette thuộc roadmap; không được xem là đã triển khai.' }]" /></div><aside class="docs-aside p-glass"><BookOpen :size="24" /><h3>Mọi chi tiết đều có lý do.</h3><p>API, props, events, trạng thái, checklist và roadmap được lưu cùng mã nguồn.</p><div class="doc-file">README.md<span>Khởi chạy & kiến trúc</span></div><div class="doc-file">docs/COMPONENTS.md<span>API & cách sử dụng</span></div><div class="doc-file">docs/ROADMAP.md<span>Phạm vi & phát triển</span></div><div class="doc-file">CHANGELOG.md<span>Lịch sử thay đổi</span></div><PrismBadge tone="warning" dot>v0.1 · Experimental</PrismBadge></aside></div>
      </template>
      <footer class="site-footer"><span class="footer-brand">prism<span>.</span></span><span>Thoughtfully crafted. Beautifully transparent.</span><span>DESIGN SYSTEM / 2026</span></footer>
    </main>
  </div>
  <PrismDialog v-model="dialogOpen" title="Tạo không gian mới" description="Dành một không gian cho ý tưởng tiếp theo của bạn." initial-focus="#new-project-name" :prevent-close="saving">
    <form id="create-project" @submit.prevent="createProject" novalidate><PrismInput id="new-project-name" v-model="projectName" label="Tên không gian" placeholder="Ví dụ: Aurora studio" :error="projectError" required :disabled="saving" /><div class="pattern-gap"></div><PrismTextarea v-model="projectDescription" label="Mô tả" placeholder="Bạn muốn xây dựng điều gì?" :maxlength="240" :disabled="saving" /><p class="form-note"><Layers :size="14" /> Không gian demo được lưu trong phiên hiện tại.</p></form><template #footer><PrismButton variant="ghost" :disabled="saving" @click="dialogOpen = false">Hủy</PrismButton><PrismButton type="submit" form="create-project" :loading="saving"><Plus v-if="!saving" :size="15" /> {{ saving ? 'Đang tạo…' : 'Tạo không gian' }}</PrismButton></template>
  </PrismDialog>
  <PrismDrawer v-model="drawerOpen" title="Tinh chỉnh chất liệu" description="Những thay đổi nhỏ tạo nên một chiều sâu mới."><PrismSlider v-model="alpha" :min="8" :max="65" label="Độ che của mặt kính" unit="%" /><PrismSlider v-model="blur" :min="0" :max="16" label="Backdrop blur" unit="px" /><PrismSwitch v-model="glow" label="Neon glow" /><PrismAlert tone="info" title="Nhìn xuyên qua bề mặt">Đặt độ che khoảng 10% và blur 4px để gần chất liệu trong ảnh tham chiếu.</PrismAlert><template #footer><PrismButton variant="ghost" @click="alpha = 10; blur = 4; glow = true">Đặt lại</PrismButton><PrismButton @click="drawerOpen = false">Hoàn tất <Check :size="15" /></PrismButton></template></PrismDrawer>
  <PrismToast ref="toast" />
</template>
