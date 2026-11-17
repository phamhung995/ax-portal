<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Eye, MessageCircle, Newspaper, Sparkles, ThumbsUp, TrendingUp } from 'lucide-vue-next'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismCard from '@/design-system/components/PrismCard.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismRadarChart from '@/design-system/components/PrismRadarChart.vue'
import PrismSkeleton from '@/design-system/components/PrismSkeleton.vue'
import PrismStarRating from '@/design-system/components/PrismStarRating.vue'
import PrismTabs from '@/design-system/components/PrismTabs.vue'
import { skillRequestApi } from '@/features/skill-market/api'
import type { SkillRequestFeedItem } from '@/features/skill-market/types'
import { homeApi } from '@/features/home/api'
import { aiNewsApi } from '@/features/ai-news/api'
import { useI18nStore } from '@/stores/i18n'
import { toUserError } from '@/lib/api'
import { orgRowLabel } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import type {
  ChartDimensions,
  ChartEntity,
  ChartStats,
  CommunitySkillItem,
  CommunitySkills,
  HomeSummary,
} from '@/features/home/types'
import type { AiNewsItem } from '@/features/ai-news/types'

const auth = useAuthStore()
const router = useRouter()
const i18n = useI18nStore()
const { t, formatDate } = i18n

const activeTab = ref<'chart' | 'community'>('community')

// Tab bar: Community is for everyone (default); Chart is admin-only.
const tabItems = computed(() => [
  { value: 'community', label: t('home.tabCommunity') },
  ...(auth.isAdmin ? [{ value: 'chart', label: t('home.tabChart') }] : []),
])

// ── Greeting + quick actions (Home SRS §5.3/§5.4) ───────────────────────────
const roleLabel = computed(() =>
  auth.isAdmin ? t('home.role.admin') : t('home.role.normal'),
)

const orgParts = computed(() => {
  const orgs = auth.user?.orgs ?? []
  const labels = [...new Set(orgs.map(orgRowLabel))].filter(Boolean)
  return [...labels, roleLabel.value].join(' · ')
})

function goToSkills(query?: Record<string, string>) {
  router.push({ name: 'skills', query })
}

// ── Region 1: KPI summary (Home SRS §5.6/§7.1) ──────────────────────────────
const kpiLoading = ref(true)
const kpiError = ref('')
const summary = ref<HomeSummary | null>(null)

async function loadKpi() {
  kpiLoading.value = true
  kpiError.value = ''
  try {
    summary.value = await homeApi.summary()
  } catch (err) {
    kpiError.value = toUserError(err).message
  } finally {
    kpiLoading.value = false
  }
}

const ratingDisplay = computed(() => {
  const s = summary.value
  if (!s) return ''
  if (s.myAverageRating == null || s.myRatedSkillCount === 0) return t('home.noRating')
  return `★ ${s.myAverageRating.toFixed(1)} (${s.myRatedSkillCount})`
})
const hasRating = computed(
  () => (summary.value?.myAverageRating ?? null) != null && (summary.value?.myRatedSkillCount ?? 0) > 0,
)

// ── Region 2: community skill cards (Home SRS §5.7/§7.2) ────────────────────
const commLoading = ref(true)
const commError = ref('')
const community = ref<CommunitySkills | null>(null)

async function loadCommunity() {
  commLoading.value = true
  commError.value = ''
  try {
    community.value = await homeApi.communitySkills()
  } catch (err) {
    commError.value = toUserError(err).message
  } finally {
    commLoading.value = false
  }
}

function openSkill(skill: CommunitySkillItem) {
  // Skill Detail Popup lives in the skills feature — navigate there with the
  // skill id so the popup opens (View Count +1 happens on the skills side).
  router.push({ name: 'skills', query: { detail: String(skill.skillId) } })
}

// ── Region 3: New Request card — 5 newest Skill Market requests ────────────
const reqLoading = ref(true)
const reqError = ref('')
const requests = ref<SkillRequestFeedItem[]>([])

async function loadRequests() {
  reqLoading.value = true
  reqError.value = ''
  try {
    requests.value = await skillRequestApi.recent(5)
  } catch (err) {
    reqError.value = toUserError(err).message
  } finally {
    reqLoading.value = false
  }
}

function openRequest(r: SkillRequestFeedItem) {
  router.push({ name: 'skill-market-detail', params: { id: r.id } })
}

// "View All" → Skill Market (/skill-market) — visible to all users.
function goSkillMarket() {
  router.push({ name: 'skill-market' })
}

// "View All" → Skills page, pre-filtered to APPROVED (matches the card,
// which only lists approved skills). The skills view runs the search on
// mount when any query param is present.
function goSkillsViewAll() {
  router.push({ name: 'skills', query: { status: 'APPROVED' } })
}

// "View All" → AI News page.
function goAiNewsViewAll() {
  router.push({ path: '/ai-news' })
}

// ── Region 4: AI News card (Home SRS §5.8/§7.4) ─────────────────────────────
const newsLoading = ref(true)
const newsError = ref('')
const news = ref<AiNewsItem[]>([])

async function loadNews() {
  newsLoading.value = true
  newsError.value = ''
  try {
    news.value = await aiNewsApi.list(5)
  } catch (err) {
    newsError.value = toUserError(err).message
  } finally {
    newsLoading.value = false
  }
}

const NEWS_DOT_TONES = ['accent', 'violet', 'success', 'warning', 'teal', 'rose']
function openNews(item: AiNewsItem) {
  router.push({ path: '/ai-news', query: { news: String(item.newsId) } })
}

// ── Chart tab (Home_Chart_SRS) — admin only ─────────────────────────────────
type ChartPeriod = '1m' | '3m' | '6m' | '1y'
type ChartDimension = 'group' | 'part'

const CHART_PERIODS: ChartPeriod[] = ['1m', '3m', '6m', '1y']
const CHART_COLORS = ['#67e8f9', '#a58aef', '#9ce8d0', '#f2c879', '#f28bb0', '#f28bb0', '#67e8f9', '#a58aef']

const chartPeriod = ref<ChartPeriod>('1y')
const chartDimension = ref<ChartDimension>('group')
const chartGroup = ref('')
const chartDimensions = ref<ChartDimensions | null>(null)
const chartStats = ref<ChartStats | null>(null)
const chartLoading = ref(true)
const chartError = ref('')
let chartLoadedOnce = false

async function loadChartDimensions() {
  try {
    const d = await homeApi.chartDimensions()
    chartDimensions.value = d
    if (chartDimension.value === 'part' && !d.groups.includes(chartGroup.value)) {
      chartGroup.value = d.groups[0] ?? ''
    }
  } catch {
    // Selector data is non-critical — stats still load with the current group.
  }
}

async function loadChartStats() {
  if (chartDimension.value === 'part' && !chartGroup.value) return
  chartLoading.value = true
  chartError.value = ''
  try {
    chartStats.value = await homeApi.chartStats(
      chartPeriod.value,
      chartDimension.value,
      chartDimension.value === 'part' ? chartGroup.value : undefined,
    )
    chartLoadedOnce = true
  } catch (err) {
    chartError.value = toUserError(err).message
  } finally {
    chartLoading.value = false
  }
}

watch([chartPeriod, chartDimension, chartGroup], () => {
  loadChartStats()
})

// The Group combobox dropdown is teleported to <body> — tag it when it
// appears so the unscoped compact panel styles (bottom of this file) apply.
let chartPanelObserver: MutationObserver | null = null
function tagChartPanel() {
  document
    .querySelectorAll('.p-combobox-panel:not(.home-chart-group-panel)')
    .forEach((el) => el.classList.add('home-chart-group-panel'))
}

function setChartDimension(dim: ChartDimension) {
  if (dim === chartDimension.value) return
  chartDimension.value = dim
  const groups = chartDimensions.value?.groups
  if (dim === 'part' && !chartGroup.value && groups?.length) {
    chartGroup.value = groups[0] ?? ''
  }
}

// KPI tiles — totals across all displayed entities (Home_Chart_SRS §4.3).
const chartEntities = computed<ChartEntity[]>(() => chartStats.value?.entities ?? [])
const chartUnit = computed(() =>
  t(chartDimension.value === 'part' ? 'home.chart.kpi.unit.part' : 'home.chart.kpi.unit.group'),
)
const kpiTiles = computed(() => {
  const es = chartEntities.value
  const sum = (f: (e: ChartEntity) => number) => es.reduce((s, e) => s + f(e), 0)
  const rated = es.filter((e) => e.avgRating != null)
  const avgRating = rated.length
    ? rated.reduce((s, e) => s + (e.avgRating as number), 0) / rated.length
    : null
  return [
    { label: t('home.chart.kpi.approved'), value: String(sum((e) => e.approvedSkills)), star: false },
    { label: t('home.chart.kpi.rating'), value: avgRating == null ? t('home.noRating') : avgRating.toFixed(1), star: avgRating != null },
    { label: t('home.chart.kpi.views'), value: sum((e) => e.totalViews).toLocaleString('vi-VN'), star: false },
    { label: t('home.chart.kpi.active'), value: String(sum((e) => e.activeUsers)), star: false },
    { label: t('home.chart.kpi.newUsers'), value: String(sum((e) => e.newUsers)), star: false },
  ]
})

// Mini bar charts — 4 vertical column charts (Home_Chart_SRS §4.4).
const miniCharts = computed(() => {
  const es = chartEntities.value
  const sum = (f: (e: ChartEntity) => number) => es.reduce((s, e) => s + f(e), 0)
  return [
    {
      title: t('home.chart.mini.approved'),
      suffix: t('home.chart.mini.total', { n: sum((e) => e.approvedSkills).toLocaleString('vi-VN') }),
      data: es.map((e) => ({ label: e.name, value: e.approvedSkills })),
    },
    {
      title: t('home.chart.mini.rating'),
      suffix: t('home.chart.mini.max5'),
      data: es.map((e) => ({ label: e.name, value: e.avgRating ?? 0 })),
    },
    {
      title: t('home.chart.mini.views'),
      suffix: t('home.chart.mini.total', { n: sum((e) => e.totalViews).toLocaleString('vi-VN') }),
      data: es.map((e) => ({ label: e.name, value: e.totalViews })),
    },
    {
      title: t('home.chart.mini.downloads'),
      suffix: t('home.chart.mini.total', { n: sum((e) => e.totalDownloads).toLocaleString('vi-VN') }),
      data: es.map((e) => ({ label: e.name, value: e.totalDownloads })),
    },
  ]
})

// Column value label: rating shows 1 decimal, counts use locale grouping.
function formatMiniValue(v: number): string {
  return Number.isInteger(v) ? v.toLocaleString('vi-VN') : v.toFixed(1)
}
// Column height as % of the tallest column in that chart.
function miniColHeight(mc: { data: { value: number }[] }, v: number): number {
  const max = Math.max(...mc.data.map((d) => d.value), 1)
  return Math.max(2, Math.round((v / max) * 100))
}

// Radar charts — one per entity, 6 axes normalized (Home_Chart_SRS §4.5):
// count axes = share of the total; rating axis = value / 5.
// `rows` = the real values + a % bar vs the leading entity (variant F).
const radarCharts = computed(() => {
  const es = chartEntities.value
  const sum = (f: (e: ChartEntity) => number) => es.reduce((s, e) => s + f(e), 0)
  const totalApproved = sum((e) => e.approvedSkills)
  const totalViews = sum((e) => e.totalViews)
  const totalDownloads = sum((e) => e.totalDownloads)
  const totalActive = sum((e) => e.activeUsers)
  const totalNew = sum((e) => e.newUsers)
  const maxOf = (f: (e: ChartEntity) => number) => Math.max(...es.map(f), 0)
  const maxApproved = maxOf((e) => e.approvedSkills)
  const maxRating = maxOf((e) => e.avgRating ?? 0)
  const maxViews = maxOf((e) => e.totalViews)
  const maxDownloads = maxOf((e) => e.totalDownloads)
  const maxActive = maxOf((e) => e.activeUsers)
  const maxNew = maxOf((e) => e.newUsers)
  return es.map((e, i) => {
    const color = CHART_COLORS[i % CHART_COLORS.length]
    const rows = [
      { label: t('home.chart.axis.approved'), value: String(e.approvedSkills), pct: maxApproved ? e.approvedSkills / maxApproved : 0, color },
      { label: t('home.chart.axis.rating'), value: e.avgRating == null ? t('home.noRating') : e.avgRating.toFixed(1), pct: maxRating ? (e.avgRating ?? 0) / maxRating : 0, color },
      { label: t('home.chart.axis.views'), value: e.totalViews.toLocaleString('vi-VN'), pct: maxViews ? e.totalViews / maxViews : 0, color },
      { label: t('home.chart.axis.downloads'), value: e.totalDownloads.toLocaleString('vi-VN'), pct: maxDownloads ? e.totalDownloads / maxDownloads : 0, color },
      { label: t('home.chart.axis.active'), value: String(e.activeUsers), pct: maxActive ? e.activeUsers / maxActive : 0, color },
      { label: t('home.chart.axis.newUsers'), value: String(e.newUsers), pct: maxNew ? e.newUsers / maxNew : 0, color },
    ]
    return {
      name: e.name,
      color,
      rows,
      data: [
        { label: t('home.chart.axis.approved'), value: totalApproved ? e.approvedSkills / totalApproved : 0 },
        { label: t('home.chart.axis.rating'), value: (e.avgRating ?? 0) / 5 },
        { label: t('home.chart.axis.views'), value: totalViews ? e.totalViews / totalViews : 0 },
        { label: t('home.chart.axis.downloads'), value: totalDownloads ? e.totalDownloads / totalDownloads : 0 },
        { label: t('home.chart.axis.active'), value: totalActive ? e.activeUsers / totalActive : 0 },
        { label: t('home.chart.axis.newUsers'), value: totalNew ? e.newUsers / totalNew : 0 },
      ],
    }
  })
})

const chartEmpty = computed(
  () => chartLoadedOnce && !chartLoading.value && !chartError.value && chartEntities.value.length === 0,
)

// ── Tab icons ───────────────────────────────────────────────────────────────
// PrismTabs renders labels as text only (design-system, not modifiable) —
// inject lucide SVGs into the tab buttons at app level. Buttons are matched
// by their id suffix (-tab-{value}), so the injection survives label
// re-renders (language switch) and is language-independent.
const TAB_ICONS: Record<string, string> = {
  community:
    '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><path d="M16 3.128a4 4 0 0 1 0 7.744"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><circle cx="9" cy="7" r="4"/></svg>',
  chart:
    '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3v16a2 2 0 0 0 2 2h16"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/></svg>',
}
let tabIconObserver: MutationObserver | null = null
function injectTabIcons(root: ParentNode) {
  root.querySelectorAll<HTMLButtonElement>('.p-tablist > button').forEach((btn) => {
    const m = btn.id.match(/-tab-(community|chart)$/)
    const key = m?.[1]
    const svg = key ? TAB_ICONS[key] : undefined
    if (!svg || btn.querySelector('.home-tab-icon')) return
    const span = document.createElement('span')
    span.className = 'home-tab-icon'
    span.innerHTML = svg
    btn.prepend(span)
  })
}

// ── Home banner (interactive robot, background layer) ───────────────────────
// The banner is a standalone page (public/home-banner/) loaded in an iframe
// behind the tab content (z-index 0 vs 1). The iframe gives perfect teardown
// (unmount → browser destroys the page: rAF loops, WebGL context, listeners)
// and isolates the banner's global CSS. The robot runs autonomously (roam,
// expressions, slogan writing) AND is interactive (click for hearts, drag
// to move) — the tab content above it is click-through (pointer-events:
// none) with interactive elements re-enabled, so clicks on empty space
// reach the robot while buttons/links stay clickable. Translations are
// posted into the iframe (postMessage).
const bannerIframe = ref<HTMLIFrameElement | null>(null)
const BANNER_SRC = '/home-banner/index.html'

// Banner height = from the top of .home down to the bottom of the ACTIVE
// tab's KPI row (both tabs end their first block with a KPI row). Syncing to
// the active tab keeps the robot banner behind that tab's KPI cards, so the
// glass cards look equally transparent on both tabs (the cards sit on the
// banner, not on the plain page background). The community KPI region is the
// reference for the community tab; the chart KPI row (v-if, present once the
// chart data loads) is the reference for the chart tab. A ResizeObserver
// keeps the iframe in sync when a KPI row reflows (loading → cards, error,
// narrow screens stacking the cards, admin alert banners appearing).
const homeEl = ref<HTMLElement | null>(null)
const kpiRegionEl = ref<HTMLElement | null>(null)
const chartKpiRowEl = ref<HTMLElement | null>(null)
const chartFilterBarEl = ref<HTMLElement | null>(null)
let bannerHeightObserver: ResizeObserver | null = null

function syncBannerHeight() {
  const home = homeEl.value
  const kpi = activeTab.value === 'chart' && chartKpiRowEl.value
    ? chartKpiRowEl.value
    : kpiRegionEl.value
  const iframe = bannerIframe.value
  if (!home || !kpi || !iframe) return
  const h = kpi.getBoundingClientRect().bottom - home.getBoundingClientRect().top
  if (h > 0) iframe.style.height = `${Math.ceil(h)}px`
}

// Post the right edge of the Chart-tab filter controls (TIME RANGE / TYPE),
// relative to the banner iframe, so the banner can flip its speech bubble to
// the robot's right side when the bubble would overlap them. The filter bar
// is v-if (admin + Chart tab) and reflows on language switch / resize, so the
// value is re-posted on every relevant change. The right edge is measured on
// the LAST filter group (the bar itself is full-width, so its right edge would
// be the whole tab). Called via nextTick because the Chart panel uses v-show
// — measuring before the DOM update returns 0.
function postFilterControls() {
  nextTick(() => {
    const iframe = bannerIframe.value
    if (!iframe) return
    const bar = activeTab.value === 'chart' ? chartFilterBarEl.value : null
    const groups = bar ? Array.from(bar.querySelectorAll<HTMLElement>('.chart-filter-group')) : []
    const last = groups[groups.length - 1]
    const right = last
      ? Math.round(last.getBoundingClientRect().right - iframe.getBoundingClientRect().left)
      : null
    iframe.contentWindow?.postMessage({ type: 'home-banner-filter-right', right }, '*')
  })
}

function buildBannerI18n() {
  const m = i18n.messages
  const pick = (key: string) => m[key]
  // Collect every banner.slogan.N key dynamically (N = 0,1,2,...) so a new
  // slogan added in Translation Management ("Message term") is picked up
  // automatically on the next i18n refresh — no code change needed.
  const sloganKeys: string[] = []
  for (const key of Object.keys(m)) {
    const match = /^banner\.slogan\.(\d+)$/.exec(key)
    if (match) sloganKeys.push(key)
  }
  sloganKeys.sort((a, b) => Number(a.split('.').pop()) - Number(b.split('.').pop()))
  // Every home.banner.speech.* key is sent as a flat map (key = the suffix)
  // plus a pool of all values — so a phrase added in Translation Management
  // is spoken by the robot automatically, and a deleted phrase is no longer
  // spoken (banner.js falls back to English only before the first load).
  const speech: Record<string, string> = {}
  const phrasePool: string[] = []
  for (const key of Object.keys(m)) {
    const match = /^home\.banner\.speech\.(.+)$/.exec(key)
    if (!match || !match[1]) continue
    const value = m[key]
    if (value) {
      speech[match[1]] = value
      phrasePool.push(value)
    }
  }
  // Personalized greeting: substitute {name} with the logged-in user's
  // display name (Home SRS §5.3 greeting region uses the same source).
  const userName = auth.user?.userName?.trim() || 'friend'
  const greeting = pick('home.banner.greeting')?.replaceAll('{name}', userName)
  return {
    caption: pick('banner.caption'),
    hint: pick('home.banner.hint'),
    greeting,
    slogans: sloganKeys.map((key) => pick(key)),
    speech,
    phrasePool,
    // True once the i18n messages have loaded — lets banner.js distinguish
    // "not loaded yet" (use English fallback) from "key was deleted" (stay
    // silent instead of falling back).
    loaded: Object.keys(m).length > 0,
  }
}

function postBannerI18n() {
  bannerIframe.value?.contentWindow?.postMessage(
    { type: 'home-banner-i18n', messages: buildBannerI18n() },
    '*',
  )
}

function onBannerLoad() {
  postBannerI18n()
  postFilterControls()
}

// Re-post when the messages change (initial load / language switch). Language
// switch also reflows the Chart filter bar, so re-post its right edge too.
watch(() => i18n.messages, () => {
  postBannerI18n()
  postFilterControls()
})

// ── Lifecycle ───────────────────────────────────────────────────────────────
onMounted(async () => {
  // Each region loads independently (Home SRS §5.9).
  loadKpi()
  loadCommunity()
  loadRequests()
  loadNews()
  // Banner height: track the KPI region (bottom edge = banner bottom) and
  // .home (top edge). The KPI region reflows on load/error/narrow screens
  // and admin alert banners push it down — the observer covers all of that.
  bannerHeightObserver = new ResizeObserver(syncBannerHeight)
  if (kpiRegionEl.value) bannerHeightObserver.observe(kpiRegionEl.value)
  if (homeEl.value) bannerHeightObserver.observe(homeEl.value)
  syncBannerHeight()
  // The chart KPI row is v-if (absent while loading/error/empty), so attach
  // the observer whenever it (re)appears.
  watch(chartKpiRowEl, (el, prev) => {
    bannerHeightObserver?.disconnect()
    if (kpiRegionEl.value) bannerHeightObserver?.observe(kpiRegionEl.value)
    if (el) bannerHeightObserver?.observe(el)
    if (homeEl.value) bannerHeightObserver?.observe(homeEl.value)
    syncBannerHeight()
  })
  // Switching tabs changes which KPI row anchors the banner bottom — re-sync
  // so the active tab's cards sit on the banner (same transparency look).
  watch(activeTab, () => {
    syncBannerHeight()
    postFilterControls()
  })
  // The chart KPI row is present while loading (skeletons) — the row height
  // changes when the real values land, and the ResizeObserver can miss that
  // (skeleton vs. values can render at the same box height). Re-sync
  // explicitly on the loading transition so the banner bottom tracks the
  // final KPI row.
  watch(chartLoading, syncBannerHeight)
  // The Chart filter bar (TIME RANGE / TYPE) is v-if and reflows on language
  // switch — post its right edge whenever it (re)appears.
  watch(chartFilterBarEl, (el, prev) => {
    if (el) bannerHeightObserver?.observe(el)
    postFilterControls()
  })
  // Window resize shifts the filter bar relative to the banner iframe.
  window.addEventListener('resize', postFilterControls)
  // Tab icons: observe the tab list so the SVGs are (re)injected whenever
  // PrismTabs re-renders its buttons (e.g. language switch).
  const tablist = document.querySelector<HTMLElement>('.home .p-tablist')
  if (tablist) {
    injectTabIcons(tablist)
    tabIconObserver = new MutationObserver(() => injectTabIcons(tablist))
    tabIconObserver.observe(tablist, { childList: true, subtree: true })
  }
  if (auth.isAdmin) {
    await loadChartDimensions()
    loadChartStats()
    // The combobox panel is teleported to <body> — observe it so the
    // compact styles apply each time it opens. The panel is a direct
    // child of body, so no subtree observation is needed.
    chartPanelObserver = new MutationObserver(tagChartPanel)
    chartPanelObserver.observe(document.body, { childList: true })
  }
})
onBeforeUnmount(() => {
  tabIconObserver?.disconnect()
  tabIconObserver = null
  chartPanelObserver?.disconnect()
  chartPanelObserver = null
  bannerHeightObserver?.disconnect()
  bannerHeightObserver = null
  window.removeEventListener('resize', postFilterControls)
})
</script>

<template>
  <div ref="homeEl" class="home">
    <!-- Interactive robot banner (standalone page in an iframe — perfect
         teardown + CSS isolation). Background layer behind the tab content. -->
    <iframe
      ref="bannerIframe"
      class="home-banner-bg"
      :src="BANNER_SRC"
      :title="t('home.banner.aria')"
      aria-hidden="true"
      tabindex="-1"
      @load="onBannerLoad"
    ></iframe>
    <PrismTabs
      v-model="activeTab"
      :label="t('home.title')"
      :items="tabItems"
    >
      <!-- ═══════════════ TAB: CHART (admin only — Home_Chart_SRS) ═══════════════ -->
      <template v-if="auth.isAdmin" #chart>
        <section class="home-chart-tab">
          <!-- Filter bar (Home_Chart_SRS §4.2) -->
          <div ref="chartFilterBarEl" class="chart-filter-bar p-card p-glass">
            <div class="chart-filter-group">
              <span class="chart-filter-label">{{ t('home.chart.periodLabel') }}</span>
              <div class="seg" role="group" :aria-label="t('home.chart.periodLabel')">
                <button
                  v-for="p in CHART_PERIODS"
                  :key="p"
                  type="button"
                  :class="{ active: chartPeriod === p }"
                  :disabled="chartLoading"
                  @click="chartPeriod = p"
                >
                  {{ t(`home.chart.period.${p}`) }}
                </button>
              </div>
            </div>
            <div class="chart-filter-group">
              <span class="chart-filter-label">{{ t('home.chart.dimLabel') }}</span>
              <div class="seg" role="group" :aria-label="t('home.chart.dimLabel')">
                <button
                  type="button"
                  :class="{ active: chartDimension === 'group' }"
                  :disabled="chartLoading"
                  @click="setChartDimension('group')"
                >
                  {{ t('home.chart.dim.group') }}
                </button>
                <button
                  type="button"
                  :class="{ active: chartDimension === 'part' }"
                  :disabled="chartLoading"
                  @click="setChartDimension('part')"
                >
                  {{ t('home.chart.dim.part') }}
                </button>
              </div>
            </div>
            <div v-if="chartDimension === 'part'" class="chart-filter-group">
              <div class="chart-group-combo">
                <PrismCombobox
                  v-model="chartGroup"
                  :label="t('home.chart.groupLabel')"
                  :placeholder="t('home.chart.groupLabel')"
                  :disabled="chartLoading || !chartDimensions?.groups.length"
                  :options="(chartDimensions?.groups ?? []).map((g) => ({ value: g, label: g }))"
                />
              </div>
            </div>
          </div>

          <!-- Error state (Home_Chart_SRS §4.7) -->
          <PrismAlert v-if="chartError" tone="warning" class="compact-banner" :title="chartError">
            <template #actions>
              <PrismButton variant="secondary" @click="loadChartStats">{{ t('home.retry') }}</PrismButton>
            </template>
          </PrismAlert>

          <!-- Empty state -->
          <PrismEmptyState
            v-else-if="chartEmpty"
            :title="t('home.chart.empty')"
            :description="t('home.chart.emptyDesc')"
          />

          <template v-else>
            <!-- KPI tiles — system totals (Home_Chart_SRS §4.3). ref: banner
                 height reference for the chart tab (banner bottom = this row's
                 bottom, so the glass cards sit on the banner like the
                 community KPI cards). -->
            <div ref="chartKpiRowEl" class="chart-kpi-row">
              <template v-if="chartLoading">
                <div v-for="i in 5" :key="i" class="chart-kpi p-glass">
                  <PrismSkeleton height="12px" width="70%" />
                  <PrismSkeleton height="24px" width="50%" />
                </div>
              </template>
              <template v-else>
                <div v-for="tile in kpiTiles" :key="tile.label" class="chart-kpi p-glass">
                  <div class="chart-kpi-label">{{ tile.label }}</div>
                  <div class="chart-kpi-value">
                    {{ tile.value }}<span v-if="tile.star" class="chart-kpi-star">★</span>
                  </div>
                  <div class="chart-kpi-sub">
                    {{ t('home.chart.kpi.totalOf', { n: chartEntities.length, unit: chartUnit }) }}
                  </div>
                </div>
              </template>
            </div>

            <!-- 3 mini bar charts (Home_Chart_SRS §4.4) — app-level column
                 chart: labels truncate with ellipsis + tooltip (design-system
                 PrismBarChart cannot truncate x-axis labels) -->
            <div class="chart-mini-row">
              <PrismCard v-for="mc in miniCharts" :key="mc.title" class="chart-mini-card">
                <template #header>
                  <h3>{{ mc.title }} <span class="chart-mini-suffix">{{ mc.suffix }}</span></h3>
                </template>
                <div v-if="!chartLoading" class="mini-cols">
                  <div v-for="(d, i) in mc.data" :key="d.label" class="mini-col">
                    <div class="mini-col-track">
                      <span class="mini-col-value">{{ formatMiniValue(d.value) }}</span>
                      <div
                        class="mini-col-fill"
                        :style="{ height: miniColHeight(mc, d.value) + '%', background: CHART_COLORS[i % CHART_COLORS.length] }"
                      ></div>
                    </div>
                    <span class="mini-col-label" :title="d.label">{{ d.label }}</span>
                  </div>
                </div>
                <PrismSkeleton v-else height="170px" />
              </PrismCard>
            </div>

            <!-- Radar grid — one per entity (Home_Chart_SRS §4.5) -->
            <PrismCard class="chart-radar-frame">
              <template #header>
                <h3>{{ t('home.chart.radarTitle') }}</h3>
              </template>
              <div v-if="chartLoading" class="chart-radar-grid">
                <div v-for="i in 3" :key="i" class="chart-radar-cell">
                  <PrismSkeleton height="200px" />
                </div>
              </div>
              <div v-else class="chart-radar-grid">
                <div v-for="rc in radarCharts" :key="rc.name" class="chart-radar-cell">
                  <div class="chart-radar-name">{{ rc.name }}</div>
                  <div class="chart-radar-body">
                    <PrismRadarChart :data="rc.data" :size="260" :max="1" :aria-label="rc.name" />
                    <div class="chart-radar-rows">
                      <div v-for="row in rc.rows" :key="row.label" class="radar-mrow">
                        <span class="radar-ml">{{ row.label }}</span>
                        <span class="radar-mv">{{ row.value }}</span>
                        <div class="radar-mtrack">
                          <div class="radar-mfill" :style="{ width: Math.max(3, Math.round(row.pct * 100)) + '%', background: row.color }"></div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </PrismCard>
          </template>
        </section>
      </template>


      <!-- ═══════════════ TAB: COMMUNITY ═══════════════ -->
      <template #community>
        <section class="home-community-tab">
          <!-- Greeting + inline quick actions (Home SRS §5.3/§5.4) -->
          <div class="greeting-row">
            <div class="greeting">
              <h2 class="greeting-title">{{ t('home.hello', { name: auth.user?.userName ?? '' }) }}</h2>
              <p v-if="orgParts" class="greeting-meta">{{ orgParts }}</p>
            </div>
          </div>

          <!-- Admin Review Queue banner (compact, Home SRS §5.5) -->
          <PrismAlert
            v-if="auth.isAdmin && (summary?.pendingReviews ?? 0) > 0"
            tone="warning"
            class="compact-banner"
            :title="t('home.banner.review', { n: summary?.pendingReviews ?? 0 })"
          >
            <template #actions>
              <PrismButton variant="secondary" @click="goToSkills({ status: 'PENDING_APPROVAL' })">
                {{ t('home.banner.reviewNow') }}
              </PrismButton>
            </template>
          </PrismAlert>

          <!-- Admin Feature Requests banner (compact, Home SRS §5.5a) -->
          <PrismAlert
            v-if="auth.isAdmin && (summary?.pendingRequestReviews ?? 0) > 0"
            tone="info"
            class="compact-banner"
            :title="t('home.banner.requests', { n: summary?.pendingRequestReviews ?? 0 })"
          >
            <template #actions>
              <PrismButton variant="secondary" @click="goToSkills({ requests: 'open' })">
                {{ t('home.banner.reviewNow') }}
              </PrismButton>
            </template>
          </PrismAlert>

          <!-- KPI region (6 cards, one row — Home SRS §5.6). ref: banner
               height reference (banner bottom = KPI region bottom). -->
          <section ref="kpiRegionEl" class="kpi-region" :aria-label="t('home.keyMetricsAria')">
            <template v-if="kpiLoading">
              <div v-for="i in 6" :key="i" class="p-card p-glass kpi-card">
                <PrismSkeleton height="14px" width="60%" />
                <PrismSkeleton height="28px" width="40%" />
              </div>
            </template>
            <template v-else-if="kpiError">
              <div class="region-error p-glass">
                <p>{{ kpiError }}</p>
                <PrismButton variant="secondary" @click="loadKpi">{{ t('home.retry') }}</PrismButton>
              </div>
            </template>
            <template v-else-if="summary">
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ creator: 'me' })">
                <span class="kpi-label">{{ t('home.kpi.mySkills') }}</span>
                <span class="kpi-value kpi-value--accent">{{ summary.mySkills }}</span>
              </button>
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ creator: 'me', status: 'APPROVED' })">
                <span class="kpi-label">{{ t('home.kpi.published') }}</span>
                <span class="kpi-value kpi-value--success">{{ summary.myPublished }}</span>
              </button>
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ creator: 'me', status: 'PENDING_APPROVAL' })">
                <span class="kpi-label">{{ t('home.kpi.pending') }}</span>
                <span class="kpi-value kpi-value--warning">{{ summary.myPendingApproval }}</span>
              </button>
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ creator: 'me', status: 'APPROVED', sortBy: 'viewCount' })">
                <span class="kpi-label">{{ t('home.kpi.totalViews') }}</span>
                <span class="kpi-value kpi-value--violet">{{ summary.myTotalViews }}</span>
              </button>
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ creator: 'me', status: 'APPROVED', sortBy: 'avgRating' })">
                <span class="kpi-label">{{ t('home.kpi.avgRating') }}</span>
                <span class="kpi-value kpi-value--sm" :class="hasRating ? 'kpi-value--amber' : 'kpi-value--neutral'">{{ ratingDisplay }}</span>
              </button>
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ subscribed: '1' })">
                <span class="kpi-label">{{ t('home.kpi.subscribed') }}</span>
                <span class="kpi-value kpi-value--teal">{{ summary.subscribedSkills }}</span>
              </button>
            </template>
          </section>

          <!-- Three-column community row (Home SRS §5.7) -->
          <div class="community-row">
            <template v-if="commLoading">
              <div v-for="i in 3" :key="i" class="p-card p-glass community-card">
                <PrismSkeleton height="16px" width="50%" />
                <div v-for="j in 5" :key="j" class="skeleton-row">
                  <PrismSkeleton height="13px" width="85%" />
                </div>
              </div>
            </template>
            <template v-else-if="commError">
              <div class="region-error p-glass community-card">
                <p>{{ commError }}</p>
                <PrismButton variant="secondary" @click="loadCommunity">{{ t('home.retry') }}</PrismButton>
              </div>
            </template>
            <template v-else-if="community">
              <PrismCard class="community-card">
                <template #header>
                  <h3><Eye :size="16" aria-hidden="true" class="card-title-icon" />{{ t('home.card.hot') }}</h3>
                </template>
                <PrismEmptyState v-if="!community.hot.length" :title="t('home.emptyList')" />
                <ol v-else class="skill-list">
                  <li v-for="(s, i) in community.hot" :key="s.skillId">
                    <button type="button" class="skill-row" @click="openSkill(s)">
                      <span class="row-main">
                        <span class="skill-rank">{{ i + 1 }}</span>
                        <span class="skill-name" :title="s.skillName">{{ s.skillName }}</span>
                      </span>
                      <span v-if="s.categories.length || s.stages.length" class="skill-tags">
                        <span v-for="c in s.categories.slice(0, 2)" :key="'c' + c" class="skill-tag skill-tag--cat" :title="c">{{ c }}</span>
                        <span v-if="s.categories.length > 2" class="skill-tag skill-tag--more">+{{ s.categories.length - 2 }}</span>
                        <span v-for="st in s.stages.slice(0, 2)" :key="'st' + st" class="skill-tag skill-tag--stage" :title="st">{{ st }}</span>
                        <span v-if="s.stages.length > 2" class="skill-tag skill-tag--more">+{{ s.stages.length - 2 }}</span>
                      </span>
                      <span class="row-meta">
                        <span class="skill-creator">{{ s.creatorName ?? '' }}</span>
                        <span class="skill-stats">
                          <Eye :size="14" aria-hidden="true" /> {{ s.viewCount ?? 0 }}
                          <PrismStarRating :model-value="s.ratingScore ?? 0" :total="s.ratingCount" :size="13" :no-rating-text="t('home.noRating')" />
                        </span>
                      </span>
                    </button>
                  </li>
                </ol>
              </PrismCard>

              <PrismCard class="community-card">
                <template #header>
                  <h3><TrendingUp :size="16" aria-hidden="true" class="card-title-icon" />{{ t('home.card.trending') }}</h3>
                  <span class="card-sub">{{ t('home.card.trendingSub') }}</span>
                </template>
                <PrismEmptyState v-if="!community.trending.length" :title="t('home.emptyList')" />
                <ol v-else class="skill-list">
                  <li v-for="(s, i) in community.trending" :key="s.skillId">
                    <button type="button" class="skill-row" @click="openSkill(s)">
                      <span class="row-main">
                        <span class="skill-rank">{{ i + 1 }}</span>
                        <span class="skill-name" :title="s.skillName">{{ s.skillName }}</span>
                      </span>
                      <span v-if="s.categories.length || s.stages.length" class="skill-tags">
                        <span v-for="c in s.categories.slice(0, 2)" :key="'c' + c" class="skill-tag skill-tag--cat" :title="c">{{ c }}</span>
                        <span v-if="s.categories.length > 2" class="skill-tag skill-tag--more">+{{ s.categories.length - 2 }}</span>
                        <span v-for="st in s.stages.slice(0, 2)" :key="'st' + st" class="skill-tag skill-tag--stage" :title="st">{{ st }}</span>
                        <span v-if="s.stages.length > 2" class="skill-tag skill-tag--more">+{{ s.stages.length - 2 }}</span>
                      </span>
                      <span class="row-meta">
                        <span class="skill-creator">{{ s.creatorName ?? '' }}</span>
                        <span class="skill-stats">
                          <Eye :size="14" aria-hidden="true" /> {{ s.viewCount ?? 0 }}
                          <PrismStarRating :model-value="s.ratingScore ?? 0" :total="s.ratingCount" :size="13" :no-rating-text="t('home.noRating')" />
                        </span>
                      </span>
                    </button>
                  </li>
                </ol>
              </PrismCard>

              <PrismCard class="community-card">
                <template #header>
                  <h3><Sparkles :size="16" aria-hidden="true" class="card-title-icon" />{{ t('home.card.newSkills') }}</h3>
                  <button
                    type="button"
                    class="view-all-link"
                    @click="goSkillsViewAll"
                  >
                    {{ t('home.card.viewAll') }}
                  </button>
                </template>
                <PrismEmptyState v-if="!community.new.length" :title="t('home.emptyList')" />
                <ol v-else class="skill-list">
                  <li v-for="(s, i) in community.new" :key="s.skillId">
                    <button type="button" class="skill-row" @click="openSkill(s)">
                      <span class="row-main">
                        <span class="skill-rank">{{ i + 1 }}</span>
                        <span class="skill-name" :title="s.skillName">{{ s.skillName }}</span>
                      </span>
                      <span v-if="s.categories.length || s.stages.length" class="skill-tags">
                        <span v-for="c in s.categories.slice(0, 2)" :key="'c' + c" class="skill-tag skill-tag--cat" :title="c">{{ c }}</span>
                        <span v-if="s.categories.length > 2" class="skill-tag skill-tag--more">+{{ s.categories.length - 2 }}</span>
                        <span v-for="st in s.stages.slice(0, 2)" :key="'st' + st" class="skill-tag skill-tag--stage" :title="st">{{ st }}</span>
                        <span v-if="s.stages.length > 2" class="skill-tag skill-tag--more">+{{ s.stages.length - 2 }}</span>
                      </span>
                      <span class="row-meta">
                        <span class="skill-creator">{{ s.creatorName ?? '' }}</span>
                        <span class="skill-date">{{ formatDate(s.createdAt) }}</span>
                        <span class="skill-stats">
                          <Eye :size="14" aria-hidden="true" /> {{ s.viewCount ?? 0 }}
                          <PrismStarRating :model-value="s.ratingScore ?? 0" :total="s.ratingCount" :size="13" :no-rating-text="t('home.noRating')" />
                        </span>
                      </span>
                    </button>
                  </li>
                </ol>
              </PrismCard>
            </template>
          </div>

          <!-- Two-card row: New Request | AI News (Home SRS §5.8) -->
          <div class="bottom-row">
            <PrismCard class="bottom-card">
              <template #header>
                <h3><ThumbsUp :size="16" aria-hidden="true" class="card-title-icon" />{{ t('home.card.newRequest') }}</h3>
                <button
                  type="button"
                  class="view-all-link"
                  @click="goSkillMarket"
                >
                  {{ t('home.card.viewAll') }}
                </button>
              </template>
              <div v-if="reqLoading" class="skeleton-row"><PrismSkeleton height="13px" width="80%" /></div>
              <PrismAlert v-else-if="reqError" tone="warning">{{ reqError }}
                <template #actions>
                  <PrismButton variant="secondary" @click="loadRequests">{{ t('home.retry') }}</PrismButton>
                </template>
              </PrismAlert>
              <PrismEmptyState
                v-else-if="!requests.length"
                :title="t('home.noNewRequests')"
                :description="t('home.noNewRequestsDesc')"
              />
              <ol v-else class="skill-list" role="list">
                <li v-for="(r, i) in requests" :key="r.id">
                  <button type="button" class="skill-row" @click="openRequest(r)">
                    <span class="row-main">
                      <span class="skill-rank">{{ i + 1 }}</span>
                      <span class="skill-name" :title="r.title">{{ r.title }}</span>
                    </span>
                    <span class="row-meta">
                      <span class="skill-creator">{{ r.creatorName ?? '' }}</span>
                      <span class="skill-date">{{ formatDate(r.createdAt) }}</span>
                      <span class="skill-stats">
                        <ThumbsUp :size="14" aria-hidden="true" /> {{ r.voteCount }}
                        <MessageCircle :size="14" aria-hidden="true" /> {{ r.commentCount }}
                      </span>
                    </span>
                  </button>
                </li>
              </ol>
            </PrismCard>

            <PrismCard class="bottom-card">
              <template #header>
                <h3><Newspaper :size="16" aria-hidden="true" class="card-title-icon" />{{ t('home.card.aiNews') }}</h3>
                <button
                  type="button"
                  class="view-all-link"
                  @click="goAiNewsViewAll"
                >
                  {{ t('home.card.viewAll') }}
                </button>
              </template>
              <div v-if="newsLoading" class="skeleton-row"><PrismSkeleton height="13px" width="80%" /></div>
              <PrismAlert v-else-if="newsError" tone="warning">{{ newsError }}
                <template #actions>
                  <PrismButton variant="secondary" @click="loadNews">{{ t('home.retry') }}</PrismButton>
                </template>
              </PrismAlert>
              <PrismEmptyState
                v-else-if="!news.length"
                :title="t('home.noAiNews')"
                :description="t('home.noAiNewsDesc')"
              />
              <ol v-else class="home-ainews-list" role="list">
                <li
                  v-for="(n, idx) in news"
                  :key="n.newsId"
                  class="home-ainews-item"
                  :class="`home-ainews--${NEWS_DOT_TONES[idx % NEWS_DOT_TONES.length]}`"
                >
                  <span class="home-ainews-dot" aria-hidden="true"></span>
                  <div class="home-ainews-body">
                    <button
                      type="button"
                      class="home-ainews-title"
                      :title="n.title"
                      @click="openNews(n)"
                    >{{ n.title }}</button>
                    <p class="home-ainews-desc" :title="n.sender ?? n.source">{{ n.sender ?? n.source }} · {{ formatDate(n.createdAt) }}</p>
                  </div>
                </li>
              </ol>
            </PrismCard>
          </div>
        </section>
      </template>
    </PrismTabs>
  </div>
</template>

<style scoped>
.home {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-4);
  /* Pull the tab bar up against the app header (main padding is 16px) */
  margin-top: calc(-1 * var(--ds-space-2));
}
/* Interactive robot banner — background layer behind the tab content.
   The iframe page is self-contained (public/home-banner/). Height is set
   at runtime by syncBannerHeight() (top of .home → bottom of the KPI
   region, both tabs); the CSS value is only the initial fallback.
   The iframe itself receives pointer events (so the robot is interactive,
   like the v3 demo) — the tab content above it is click-through instead
   (see .home > .p-tabs below). */
.home-banner-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: clamp(180px, 22vh, 230px);
  z-index: 0;
  border: 0;
  background: #090626;
  /* The height is set at runtime by syncBannerHeight() — without a
     transition the iframe jumps from the CSS fallback (180–230px) to the
     KPI-anchored height in one frame, and the robot visibly resizes
     (the "giật to nhỏ" on first load). Animating the height makes the
     banner's ResizeObserver follow smoothly, so the robot rescales
     gradually instead of snapping. */
  transition: height .35s ease-out;
  /* Bottom corners rounded to match the KPI cards (p-glass → --ds-radius-md)
     so the banner's bottom edge reads as part of the KPI region; the top
     edge stays square against the app header. */
  border-radius: 0 0 var(--ds-radius-md) var(--ds-radius-md);
}
.home > .p-tabs {
  position: relative;
  z-index: 1;
  /* Click-through: the tab content sits above the banner iframe, so it
     would swallow every pointer event and the robot could never be
     clicked/dragged. Make the content transparent to the pointer — but
     re-enable it on every interactive element (buttons, links, inputs),
     which stay fully clickable. Clicks on empty space (card padding,
     gaps, the tab bar background) fall through to the robot. */
  pointer-events: none;
}
.home .p-tabs :deep(button),
.home .p-tabs :deep(a),
.home .p-tabs :deep(input),
.home .p-tabs :deep(select),
.home .p-tabs :deep(textarea) {
  pointer-events: auto;
}
/* Tab content sits closer to the underline — the design-system default
   .p-tabpanel padding-top (24px) leaves a visible gap. */
.home :deep(.p-tabpanel) {
  padding-top: var(--ds-space-4);
}
/* Tab icons are injected at app level (PrismTabs renders text-only labels). */
.home :deep(.home-tab-icon) {
  display: inline-flex;
  align-items: center;
}

/* ── Chart tab (Home_Chart_SRS) ──────────────────────────────────────────── */
.home-chart-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-3);
}
.chart-filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
  padding: 12px 16px;
}
.chart-filter-group {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.chart-filter-label {
  font-size: 12px;
  color: var(--ds-color-text-muted);
  text-transform: uppercase;
  letter-spacing: .4px;
}
/* Segmented control (period / dimension) */
.chart-filter-bar .seg {
  display: inline-flex;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  overflow: hidden;
}
.chart-filter-bar .seg button {
  padding: 6px 14px;
  font-size: 13px;
  font-family: inherit;
  background: transparent;
  color: var(--ds-color-text-secondary);
  border: none;
  cursor: pointer;
}
.chart-filter-bar .seg button.active {
  background: rgb(103 232 249 / .15);
  color: var(--ds-color-accent);
  font-weight: 600;
}
.chart-filter-bar .seg button:disabled {
  opacity: .5;
  cursor: not-allowed;
}
/* Group combobox (design-system PrismCombobox) — compact inline layout to
   match the filter bar: label sits left of the input instead of above it,
   and the control is shrunk to the filter-bar scale. */
.chart-group-combo {
  min-width: 190px;
}
.chart-group-combo :deep(.p-field) {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.chart-group-combo :deep(label) {
  font-size: 12px;
  color: var(--ds-color-text-muted);
  text-transform: uppercase;
  letter-spacing: .4px;
}
.chart-group-combo :deep(.p-input-wrap) {
  padding: 0 8px;
  border-radius: var(--ds-radius-sm);
}
.chart-group-combo :deep(.p-input-wrap input) {
  min-height: 30px;
  font-size: 13px;
}
.chart-group-combo :deep(.p-combobox-caret) {
  width: 24px;
  height: 24px;
}
/* KPI tiles */
.chart-kpi-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
/* The banner sits BEHIND the chart tab's first block (banner bottom = KPI
   row bottom), so the default p-glass fill + blur would hide the robot and
   slogan. Keep these cards nearly transparent — light fill, no blur/shadow,
   border only — so the home banner stays clearly visible through them. */
.chart-filter-bar,
.chart-kpi {
  background: rgb(24 55 92 / .05);
  -webkit-backdrop-filter: none;
  backdrop-filter: none;
  box-shadow: none;
}
.chart-kpi {
  padding: 12px 14px;
  display: grid;
  gap: 4px;
}
.chart-kpi-label {
  font-size: 11px;
  color: var(--ds-color-text-muted);
  text-transform: uppercase;
  letter-spacing: .4px;
}
.chart-kpi-value {
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.chart-kpi-star {
  margin-left: 4px;
  font-size: 16px;
  color: var(--ds-color-warning);
}
.chart-kpi-sub {
  font-size: 11px;
  color: var(--ds-color-text-muted);
}
/* 4 mini bar charts */
.chart-mini-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.chart-mini-card {
  padding: 16px 18px;
}
.chart-mini-card :deep(header) {
  margin-bottom: 10px;
}
.chart-mini-card :deep(h3) {
  margin: 0;
  font-size: 14px;
}
.chart-mini-suffix {
  font-size: 12px;
  font-weight: 400;
  color: var(--ds-color-text-muted);
}
/* App-level column chart (PrismBarChart cannot truncate x-axis labels):
   value on top, column in the middle, label below (ellipsis + tooltip). */
.mini-cols {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 8px;
  height: 170px;
  padding-top: 6px;
}
.mini-col {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
.mini-col-value {
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.mini-col-track {
  flex: 1;
  width: 100%;
  max-width: 44px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.mini-col-fill {
  width: 100%;
  border-radius: 5px 5px 2px 2px;
  min-height: 3px;
}
.mini-col-label {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--ds-color-text-secondary);
  font-weight: 600;
}
/* Radar grid */
.chart-radar-frame {
  padding: 16px 18px;
}
.chart-radar-frame :deep(h3) {
  margin: 0;
  font-size: 15px;
}
/* 3 radars per row; an odd last row is centered; cells in the same row
   stretch to equal height (flex default align-items: stretch). */
.chart-radar-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 14px;
  margin-top: 14px;
}
.chart-radar-cell {
  flex: 1 1 calc((100% - 28px) / 3);
  max-width: calc((100% - 28px) / 3);
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: rgb(9 20 45 / .3);
}
/* Let the radar SVG fill the cell width and ZOOM it: the design-system
   viewBox is 2:1 (wide) to leave room for the left/right axis labels, so
   most of the width is empty margin. We crop those margins — the polygon
   itself grows ~53% while the labels stay fully visible
   (content spans ~80..420 of the 520 viewBox units). */
.chart-radar-cell :deep(.p-chart) {
  width: 100%;
  overflow: hidden;
}
.chart-radar-cell :deep(.p-chart svg) {
  width: 153%;
  height: auto;
  margin-left: -24%;
}
.chart-radar-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--ds-color-text);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* Radar (dominant, left) + 5 real values as a compact secondary list
   on the right (variant F, de-emphasized: 10px text, 3px bars). */
.chart-radar-body {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 10px;
  align-items: center;
}
.chart-radar-rows {
  min-width: 0;
}
.radar-mrow {
  display: grid;
  grid-template-columns: 1fr 64px;
  gap: 6px;
  align-items: center;
  font-size: 10px;
  margin-bottom: 4px;
}
.radar-mrow .radar-ml {
  color: var(--ds-color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.radar-mrow .radar-mv {
  font-weight: 600;
  text-align: right;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.radar-mtrack {
  grid-column: 1 / 3;
  height: 3px;
  background: rgb(9 20 45 / .6);
  border-radius: 2px;
  overflow: hidden;
  margin-top: -1px;
  margin-bottom: 6px;
}
.radar-mfill {
  height: 100%;
  border-radius: 3px;
}
@media (max-width: 1199px) {
  .chart-kpi-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .chart-mini-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .chart-radar-cell {
    flex-basis: calc((100% - 14px) / 2);
    max-width: calc((100% - 14px) / 2);
  }
}
@media (max-width: 600px) {
  .chart-kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .chart-mini-row {
    grid-template-columns: 1fr;
  }
  .chart-radar-cell {
    flex-basis: 100%;
    max-width: 100%;
  }
}

/* ── Community tab ───────────────────────────────────────────────────────── */
.home-community-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-3);
}
.greeting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}
.greeting {
  min-width: 0;
}
.greeting-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--ds-color-text-primary);
}
.greeting-meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--ds-color-text-muted);
}

.compact-banner {
  /* compact single-line alert (Home SRS §5.5) */
  padding: 5px 12px;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 10px;
  align-items: center;
}
.compact-banner :deep(.p-alert-content) {
  display: none;
}
.compact-banner :deep(> div) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.compact-banner :deep(strong) {
  font-size: 13px;
}
.compact-banner :deep(.p-button) {
  min-height: 28px;
  padding: 4px 12px;
  font-size: 13px;
}

/* KPI region — 6 cards, one row (Home SRS §5.6 rule 7) */
.kpi-region {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
@media (max-width: 1199px) {
  .kpi-region {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .kpi-region {
    grid-template-columns: 1fr;
  }
}
.kpi-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
  padding: 8px 10px;
  cursor: pointer;
}
.kpi-card:hover {
  border-color: var(--ds-color-border-strong);
  box-shadow: var(--ds-shadow-glow);
}
.kpi-label {
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.kpi-value {
  font: 700 18px var(--ds-font-mono);
  color: var(--ds-color-text-primary);
}
.kpi-value--sm {
  font-size: 16px;
}
.kpi-value--accent { color: var(--ds-color-accent); }
.kpi-value--success { color: var(--ds-color-success); }
.kpi-value--warning { color: var(--ds-color-warning); }
.kpi-value--violet { color: var(--ds-color-violet-400); }
.kpi-value--amber { color: var(--ds-color-amber-300); }
.kpi-value--teal { color: var(--ds-color-teal-400); }
.kpi-value--neutral { color: var(--ds-color-text-muted); }

.region-error {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-2);
  align-items: flex-start;
  padding: var(--ds-space-4);
  border-radius: var(--ds-radius-md);
  grid-column: 1 / -1;
}
.region-error p {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}

/* Three-column community row (Home SRS §5.7) */
.community-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}
@media (max-width: 999px) {
  .community-row {
    grid-template-columns: 1fr;
  }
}
.community-card {
  padding: 0;
  overflow: hidden;
}
.community-card :deep(header) {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid rgb(154 182 219 / .1);
}
.community-card :deep(h3) {
  margin: 0;
  font-size: 15px;
}
.card-title-icon {
  vertical-align: -2px;
  margin-right: 6px;
  color: var(--ds-color-accent);
}
.card-sub {
  font-size: 11px;
  color: var(--ds-color-text-muted);
  white-space: nowrap;
}
.skill-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.skill-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-bottom: 1px solid rgb(154 182 219 / .07);
  background: none;
  cursor: pointer;
  text-align: left;
}
.skill-row:last-child {
  border-bottom: 0;
}
@media (hover: hover) {
  .skill-row:hover {
    background: rgb(103 232 249 / .045);
  }
}
.skill-row .row-main {
  display: flex;
  align-items: center;
  gap: 8px;
}
.skill-row .row-main .skill-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--ds-color-accent);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.skill-row .row-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--ds-color-text-muted);
  margin-left: 28px;
  flex-wrap: wrap;
}
.skill-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  flex-shrink: 0;
  font: 700 12px var(--ds-font-mono);
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .1);
  border: 1px solid rgb(103 232 249 / .25);
}
.skill-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ds-color-text-primary);
}
.skill-creator {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
/* Category / stage chips under the skill name (compact, max 2 each). */
.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-left: 28px;
}
.skill-tag {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 16px;
  color: var(--ds-color-text-secondary);
  background: rgb(154 182 219 / .08);
  border: 1px solid rgb(154 182 219 / .18);
}
.skill-tag--cat {
  color: var(--ds-color-violet-400);
  background: rgb(165 138 239 / .1);
  border-color: rgb(165 138 239 / .3);
}
.skill-tag--stage {
  color: var(--ds-color-teal-400);
  background: rgb(73 220 207 / .1);
  border-color: rgb(73 220 207 / .3);
}
.skill-tag--more {
  color: var(--ds-color-text-muted);
}
/* AI News card — timeline matching the AI News menu (dot + connector line +
   title + "sender · date"), with tighter spacing to fit the Home frame. */
.home-ainews-list {
  list-style: none;
  margin: 0;
  padding: 14px 16px 6px 12px;
  display: block;
}
.home-ainews-item {
  position: relative;
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 10px;
  padding-bottom: 20px;
}
.home-ainews-item:last-child {
  padding-bottom: 6px;
}
.home-ainews-item::before {
  content: '';
  position: absolute;
  left: 8px;
  top: 18px;
  bottom: 0;
  width: 2px;
  background: var(--ds-color-border);
}
.home-ainews-item:last-child::before {
  display: none;
}
.home-ainews-item::after {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  position: absolute;
  left: 6px;
  top: 6px;
}
.home-ainews-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--ds-color-border-strong);
  background: var(--ds-color-space-900);
  display: grid;
  place-items: center;
}
.home-ainews--accent .home-ainews-dot { border-color: var(--ds-color-accent); box-shadow: 0 0 8px rgb(103 232 249 / .4); }
.home-ainews--accent::after { background: var(--ds-color-accent); }
.home-ainews--violet .home-ainews-dot { border-color: var(--ds-color-violet-400); box-shadow: 0 0 8px rgb(165 138 239 / .35); }
.home-ainews--violet::after { background: var(--ds-color-violet-400); }
.home-ainews--success .home-ainews-dot { border-color: var(--ds-color-success); box-shadow: 0 0 8px rgb(156 232 208 / .3); }
.home-ainews--success::after { background: var(--ds-color-success); }
.home-ainews--warning .home-ainews-dot { border-color: var(--ds-color-warning); box-shadow: 0 0 8px rgb(243 210 140 / .3); }
.home-ainews--warning::after { background: var(--ds-color-warning); }
.home-ainews--teal .home-ainews-dot { border-color: var(--ds-color-teal-400); box-shadow: 0 0 8px rgb(73 220 207 / .3); }
.home-ainews--teal::after { background: var(--ds-color-teal-400); }
.home-ainews--rose .home-ainews-dot { border-color: var(--ds-color-rose-300); box-shadow: 0 0 8px rgb(255 172 196 / .3); }
.home-ainews--rose::after { background: var(--ds-color-rose-300); }
.home-ainews-body {
  min-width: 0;
}
.home-ainews-title {
  display: block;
  width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  color: var(--ds-color-text-primary);
  cursor: pointer;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.home-ainews-title:hover {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.home-ainews-title:focus-visible {
  outline: 2px solid var(--ds-color-accent);
  outline-offset: 1px;
}
.home-ainews-desc {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--ds-color-text-secondary);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skill-date {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  flex-shrink: 0;
}
.skill-stats {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  flex-shrink: 0;
}

/* Two-card bottom row (Home SRS §5.8) — 2 equal tracks */
.bottom-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
@media (max-width: 999px) {
  .bottom-row {
    grid-template-columns: 1fr;
  }
}
.bottom-card {
  padding: 0;
  overflow: hidden;
}
.bottom-card :deep(header) {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid rgb(154 182 219 / .1);
}
.bottom-card :deep(h3) {
  margin: 0;
  font-size: 15px;
}
.view-all-link {
  border: none;
  background: none;
  color: var(--ds-color-accent);
  font-size: 12px;
  cursor: pointer;
}
.view-all-link:hover {
  text-decoration: underline;
}
.skeleton-row {
  padding: 6px 16px;
}
</style>

<!-- Unscoped: the combobox panel is teleported to <body>, so it cannot be
     reached from the scoped block. Class names are prefixed to avoid
     collisions with other pages. -->
<style>
/* Chart filter bar — Group combobox dropdown panel (compact, matches the
   shrunk control in the filter bar). */
.home-chart-group-panel {
  z-index: 1200;
  max-height: 260px;
  --ds-surface-tint: var(--ds-dialog-background);
  --ds-surface-blur: var(--ds-dialog-blur);
  border-color: var(--ds-color-border-strong);
  border-radius: var(--ds-radius-md);
  box-shadow: 0 8px 28px rgb(0 0 0 / .45), var(--ds-shadow-glow);
}
.home-chart-group-panel .p-combobox-list {
  padding: 6px;
  gap: 2px;
}
.home-chart-group-panel .p-combobox-list li {
  padding: 6px 10px;
  font-size: 13px;
  border-radius: var(--ds-radius-sm);
}
.home-chart-group-panel .p-combobox-empty {
  padding: 8px 10px;
  font-size: 13px;
  color: var(--ds-color-text-muted);
}
</style>
