<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Eye, Info, Plus } from 'lucide-vue-next'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import PrismAreaChart from '@/design-system/components/PrismAreaChart.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismBarChart from '@/design-system/components/PrismBarChart.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismCard from '@/design-system/components/PrismCard.vue'
import PrismCheckbox from '@/design-system/components/PrismCheckbox.vue'
import PrismDonutChart from '@/design-system/components/PrismDonutChart.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismGauge from '@/design-system/components/PrismGauge.vue'
import PrismHeatmap from '@/design-system/components/PrismHeatmap.vue'
import PrismLineChart from '@/design-system/components/PrismLineChart.vue'
import PrismPopover from '@/design-system/components/PrismPopover.vue'
import PrismRadarChart from '@/design-system/components/PrismRadarChart.vue'
import PrismSkeleton from '@/design-system/components/PrismSkeleton.vue'
import PrismSparkline from '@/design-system/components/PrismSparkline.vue'
import PrismStackedBarChart from '@/design-system/components/PrismStackedBarChart.vue'
import PrismStarRating from '@/design-system/components/PrismStarRating.vue'
import PrismTabs from '@/design-system/components/PrismTabs.vue'
import AddSkillPopup from '@/features/skills/views/AddSkillPopup.vue'
import { useSkillListStore } from '@/features/skills/store'
import { homeApi } from '@/features/home/api'
import { aiNewsApi } from '@/features/ai-news/api'
import { formatDate, loadHomePrefs, t } from '@/features/home/i18n'
import { toUserError } from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import type { CommunitySkillItem, CommunitySkills, HomeActivity, HomeSummary, NewRequestItem } from '@/features/home/types'
import type { AiNewsItem } from '@/features/ai-news/types'

const auth = useAuthStore()
const router = useRouter()
const skillList = useSkillListStore()

const activeTab = ref<'chart' | 'community'>('chart')

// ── Greeting + quick actions (Home SRS §5.3/§5.4) ───────────────────────────
const roleLabel = computed(() =>
  auth.isAdmin ? t('home.role.admin') : t('home.role.normal'),
)

const orgParts = computed(() => {
  const orgs = auth.user?.orgs ?? []
  const parts = [...new Set(orgs.map((o) => o.part))].filter(Boolean)
  const groups = [...new Set(orgs.map((o) => o.group))].filter(Boolean)
  return [...parts, ...groups, roleLabel.value].join(' · ')
})

function goToSkills(query?: Record<string, string>) {
  router.push({ name: 'skills', query })
}

const addOpen = ref(false)
function onSkillSaved() {
  skillList.markDirty()
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

// ── Region 3: New Request card (Home SRS §5.8/§7.3) ─────────────────────────
const reqLoading = ref(true)
const reqError = ref('')
const requests = ref<NewRequestItem[]>([])

async function loadRequests() {
  reqLoading.value = true
  reqError.value = ''
  try {
    requests.value = await homeApi.newRequests(8)
  } catch (err) {
    reqError.value = toUserError(err).message
  } finally {
    reqLoading.value = false
  }
}

const REQUEST_DOT: Record<string, string> = {
  SUBMITTED: 'accent',
  UNDER_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  FULFILLED: 'teal',
  CLOSED: 'neutral',
}

// PrismBadge only supports the design-system tones (neutral/info/success/
// warning/danger) — map the semantic request statuses onto those.
const REQUEST_BADGE_TONE: Record<string, string> = {
  SUBMITTED: 'info',
  UNDER_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  FULFILLED: 'success',
  CLOSED: 'neutral',
}

// ── Region 4: AI News card (Home SRS §5.8/§7.4) ─────────────────────────────
const newsLoading = ref(true)
const newsError = ref('')
const news = ref<AiNewsItem[]>([])

async function loadNews() {
  newsLoading.value = true
  newsError.value = ''
  try {
    news.value = await aiNewsApi.list(8)
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

// ── Chart tab (Home SRS §5.2) — 9 toggleable charts ─────────────────────────
const CHART_KEYS = [
  'status',
  'personal',
  'byCreator',
  'viewsByCreator',
  'activity6w',
  'coverage',
  'publishedRatio',
  'viewsSpark',
  'updatedByWeek',
] as const
type ChartKey = (typeof CHART_KEYS)[number]

const visibleCharts = ref<Record<ChartKey, boolean>>(
  Object.fromEntries(CHART_KEYS.map((k) => [k, true])) as Record<ChartKey, boolean>,
)

// Chart data is derived from the loaded Home data (no extra endpoints in v1 —
// the SRS defines the 9 charts over the same Home data sources).
const mySkills = computed(() => summary.value?.mySkills ?? 0)
const myPublished = computed(() => summary.value?.myPublished ?? 0)
const myPending = computed(() => summary.value?.myPendingApproval ?? 0)
const myDraft = computed(() => summary.value?.myDraft ?? 0)
const myTotalViews = computed(() => summary.value?.myTotalViews ?? 0)
const mySubscribed = computed(() => summary.value?.subscribedSkills ?? 0)

const statusDonutData = computed(() => {
  const rejected = Math.max(0, mySkills.value - myPublished.value - myPending.value - myDraft.value)
  return [
    { label: t('home.status.DRAFT'), value: myDraft.value },
    { label: t('home.status.PENDING_APPROVAL'), value: myPending.value },
    { label: t('home.status.APPROVED'), value: myPublished.value },
    { label: t('home.status.REJECTED'), value: rejected },
  ].filter((d) => d.value > 0)
})

const personalRadarData = computed(() => [
  { label: 'Skills', value: mySkills.value },
  { label: 'Views', value: myTotalViews.value },
  { label: 'Average Rating', value: (summary.value?.myAverageRating ?? 0) * 20 },
  { label: 'Subscribed', value: mySubscribed.value },
  { label: 'Published', value: myPublished.value },
])

const byCreatorData = computed(() => {
  const all = community.value
  if (!all) return []
  // Deduplicate by skillId — hot/trending/new lists overlap.
  const seen = new Set<number>()
  const byCreator = new Map<string, Record<string, number>>()
  for (const list of [all.hot, all.trending, all.new]) {
    for (const s of list) {
      if (seen.has(s.skillId)) continue
      seen.add(s.skillId)
      const key = s.creatorName ?? '—'
      const row = byCreator.get(key) ?? { DRAFT: 0, PENDING_APPROVAL: 0, APPROVED: 0, REJECTED: 0 }
      row[s.status] = (row[s.status] ?? 0) + 1
      byCreator.set(key, row)
    }
  }
  return [...byCreator.entries()].map(([label, series]) => ({ label, series }))
})

const viewsByCreatorData = computed(() => {
  const all = community.value
  if (!all) return []
  // Deduplicate by skillId — hot/trending/new lists overlap.
  const seen = new Set<number>()
  const byCreator = new Map<string, number>()
  for (const list of [all.hot, all.trending, all.new]) {
    for (const s of list) {
      if (seen.has(s.skillId)) continue
      seen.add(s.skillId)
      const key = s.creatorName ?? '—'
      byCreator.set(key, (byCreator.get(key) ?? 0) + (s.viewCount ?? 0))
    }
  }
  return [...byCreator.entries()].map(([label, value]) => ({ label, value }))
})

const publishedRatio = computed(() =>
  mySkills.value === 0 ? 0 : Math.round((myPublished.value / mySkills.value) * 100),
)

// ── Region 5: Activity chart data (real, from /home/activity) ──────────────
const WEEKS = 6
const activityLoading = ref(false)
const activityError = ref('')
const activity = ref<HomeActivity | null>(null)

async function loadActivity() {
  activityLoading.value = true
  activityError.value = ''
  try {
    activity.value = await homeApi.activity(WEEKS)
  } catch (err) {
    activityError.value = toUserError(err).message
  } finally {
    activityLoading.value = false
  }
}

const weekLabel = (i: number) => `W${WEEKS - i}`
const activity6wData = computed(() => {
  const a = activity.value
  if (!a) return []
  return Array.from({ length: WEEKS }, (_, i) => ({
    label: weekLabel(i),
    value: (a.weeklyViews[i] ?? 0) + (a.weeklyUpdates[i] ?? 0),
  }))
})
const viewsSparkData = computed(() => {
  const a = activity.value
  if (!a) return []
  return Array.from({ length: WEEKS }, (_, i) => a.weeklyViews[i] ?? 0)
})
const updatedByWeekData = computed(() => {
  const a = activity.value
  if (!a) return []
  return Array.from({ length: WEEKS }, (_, i) => ({
    label: weekLabel(i),
    value: a.weeklyUpdates[i] ?? 0,
  }))
})
const coverageData = computed(() => {
  const a = activity.value
  if (!a) return []
  const rows: { date: string; value: number }[] = []
  for (let i = 0; i < WEEKS * 7; i++) {
    const d = new Date()
    d.setDate(d.getDate() - (WEEKS * 7 - 1 - i))
    rows.push({
      date: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`,
      value: a.dailyViews[i] ?? 0,
    })
  }
  return rows
})

// ── Lifecycle ───────────────────────────────────────────────────────────────
onMounted(async () => {
  // Prefs load in the background — regions render with the cached/default
  // language and re-render when the server prefs arrive.
  loadHomePrefs()
  // Each region loads independently (Home SRS §5.9).
  loadKpi()
  loadCommunity()
  loadRequests()
  loadNews()
  loadActivity()
})
</script>

<template>
  <div class="home">
    <PrismTabs
      v-model="activeTab"
      :label="t('home.title')"
      :items="[
        { value: 'chart', label: t('home.tabChart') },
        { value: 'community', label: t('home.tabCommunity') },
      ]"
    >
      <!-- ═══════════════ TAB: CHART ═══════════════ -->
      <template #chart>
        <section class="home-chart-tab">
          <!-- Chart filter bar (Home SRS §5.2: 9 checkboxes) -->
          <div class="chart-filter-bar p-glass" role="group" :aria-label="t('home.tabChart')">
            <label v-for="key in CHART_KEYS" :key="key" class="chart-filter-item">
              <PrismCheckbox v-model="visibleCharts[key]" :label="t(`home.chart.${key}`)" />
            </label>
          </div>

          <PrismAlert v-if="activityError" tone="warning" class="chart-error-banner">
            {{ activityError }}
            <template #actions>
              <PrismButton variant="secondary" @click="loadActivity">{{ t('home.retry') }}</PrismButton>
            </template>
          </PrismAlert>

          <!-- 3-column chart grid -->
          <div class="chart-grid">
            <PrismCard v-if="visibleCharts.status" class="chart-card chart-card--status">
              <template #header>
                <h3>{{ t('home.chart.status') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.status')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.status') }}</p>
                </PrismPopover>
              </template>
              <PrismDonutChart :data="statusDonutData" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.personal" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.personal') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.personal')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.personal') }}</p>
                </PrismPopover>
              </template>
              <PrismRadarChart :data="personalRadarData" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.byCreator" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.byCreator') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.byCreator')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.byCreator') }}</p>
                </PrismPopover>
              </template>
              <PrismStackedBarChart :data="byCreatorData" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.viewsByCreator" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.viewsByCreator') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.viewsByCreator')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.viewsByCreator') }}</p>
                </PrismPopover>
              </template>
              <PrismBarChart :data="viewsByCreatorData" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.activity6w" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.activity6w') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.activity6w')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.activity6w') }}</p>
                </PrismPopover>
              </template>
              <PrismAreaChart :data="activity6wData" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.coverage" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.coverage') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.coverage')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.coverage') }}</p>
                </PrismPopover>
              </template>
              <PrismHeatmap :data="coverageData" :weeks="WEEKS" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.publishedRatio" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.publishedRatio') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.publishedRatio')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.publishedRatio') }}</p>
                </PrismPopover>
              </template>
              <PrismGauge :value="publishedRatio" :max="100" :label="`${publishedRatio}%`" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.viewsSpark" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.viewsSpark') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.viewsSpark')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.viewsSpark') }}</p>
                </PrismPopover>
              </template>
              <PrismSparkline :data="viewsSparkData" :width="320" :height="80" />
            </PrismCard>

            <PrismCard v-if="visibleCharts.updatedByWeek" class="chart-card">
              <template #header>
                <h3>{{ t('home.chart.updatedByWeek') }}</h3>
                <PrismPopover placement="bottom-end">
                  <template #trigger>
                    <button type="button" class="chart-info-btn" :aria-label="t('home.chart.updatedByWeek')">
                      <Info :size="15" aria-hidden="true" />
                    </button>
                  </template>
                  <p class="chart-info-text">{{ t('home.chart.info.updatedByWeek') }}</p>
                </PrismPopover>
              </template>
              <PrismLineChart :data="updatedByWeekData" />
            </PrismCard>
          </div>
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
            <div class="quick-actions">
              <PrismButton variant="primary" :disabled="kpiLoading" @click="addOpen = true">
                <Plus :size="15" aria-hidden="true" /> {{ t('home.addSkillBtn') }}
              </PrismButton>
              <PrismButton variant="secondary" :disabled="kpiLoading" @click="goToSkills()">
                {{ t('home.skillMgmt') }}
              </PrismButton>
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

          <!-- KPI region (6 cards, one row — Home SRS §5.6) -->
          <section class="kpi-region" aria-label="Key metrics">
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
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ creator: 'me' })">
                <span class="kpi-label">{{ t('home.kpi.totalViews') }}</span>
                <span class="kpi-value kpi-value--violet">{{ summary.myTotalViews }}</span>
              </button>
              <button type="button" class="p-card p-glass kpi-card" @click="goToSkills({ creator: 'me' })">
                <span class="kpi-label">{{ t('home.kpi.avgRating') }}</span>
                <span class="kpi-value kpi-value--sm" :class="hasRating ? 'kpi-value--amber' : 'kpi-value--neutral'">{{ ratingDisplay }}</span>
              </button>
              <div class="p-card p-glass kpi-card kpi-card--static" aria-label="Subscribed skills">
                <span class="kpi-label">{{ t('home.kpi.subscribed') }}</span>
                <span class="kpi-value kpi-value--teal">{{ summary.subscribedSkills }}</span>
              </div>
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
                  <h3>{{ t('home.card.hot') }}</h3>
                  <span class="card-sub">{{ t('home.card.hotSub') }}</span>
                </template>
                <PrismEmptyState v-if="!community.hot.length" :title="t('home.emptyList')" />
                <ol v-else class="skill-list">
                  <li v-for="(s, i) in community.hot" :key="s.skillId">
                    <button type="button" class="skill-row" @click="openSkill(s)">
                      <span class="row-main">
                        <span class="skill-rank">{{ i + 1 }}</span>
                        <span class="skill-name" :title="s.skillName">{{ s.skillName }}</span>
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
                  <h3>{{ t('home.card.trending') }}</h3>
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
                  <h3>{{ t('home.card.newSkills') }}</h3>
                  <span class="card-sub">{{ t('home.card.newSkillsSub') }}</span>
                </template>
                <PrismEmptyState v-if="!community.new.length" :title="t('home.emptyList')" />
                <ul v-else class="skill-list skill-list--scroll">
                  <li v-for="s in community.new" :key="s.skillId">
                    <button type="button" class="skill-row" @click="openSkill(s)">
                      <span class="row-main">
                        <span class="skill-name" :title="s.skillName">{{ s.skillName }}</span>
                        <PrismBadge :tone="s.status === 'APPROVED' ? 'success' : s.status === 'PENDING_APPROVAL' ? 'warning' : 'neutral'">
                          {{ t(`home.status.${s.status}`) }}
                        </PrismBadge>
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
                </ul>
              </PrismCard>
            </template>
          </div>

          <!-- Two-card row: New Request | AI News (Home SRS §5.8) -->
          <div class="bottom-row">
            <PrismCard class="bottom-card">
              <template #header>
                <h3>{{ t('home.card.newRequest') }}</h3>
                <button type="button" class="view-all-link" @click="goToSkills({ requests: 'open' })">
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
              <ul v-else class="new-req-list" role="list">
                <li
                  v-for="r in requests"
                  :key="r.requestId"
                  class="new-req-item"
                  :class="`new-req--${REQUEST_DOT[r.status] ?? 'neutral'}`"
                >
                  <span class="ai-news-dot" aria-hidden="true"></span>
                  <div class="new-req-body">
                    <p class="new-req-title" :title="r.title">{{ r.title }}</p>
                    <p class="new-req-desc">
                      <PrismBadge :tone="REQUEST_BADGE_TONE[r.status] ?? 'neutral'">{{ r.status }}</PrismBadge>
                      <span>{{ r.requesterName }} · {{ formatDate(r.createdAt) }}</span>
                    </p>
                  </div>
                </li>
              </ul>
            </PrismCard>

            <PrismCard class="bottom-card bottom-card--wide">
              <template #header>
                <h3>{{ t('home.card.aiNews') }}</h3>
                <span class="card-subtitle">{{ t('home.card.aiNewsSub') }}</span>
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
              <ol v-else class="ai-news-list" role="list">
                <li
                  v-for="(n, idx) in news"
                  :key="n.newsId"
                  class="ai-news-item"
                  :class="`ai-news--${NEWS_DOT_TONES[idx % NEWS_DOT_TONES.length]}`"
                >
                  <span class="ai-news-dot" aria-hidden="true"></span>
                  <div class="ai-news-body">
                    <button
                      type="button"
                      class="ai-news-title"
                      :title="n.title"
                      @click="openNews(n)"
                    >{{ n.title }}</button>
                    <p class="ai-news-desc">{{ n.sender ?? n.source }} · {{ formatDate(n.createdAt) }}</p>
                  </div>
                </li>
              </ol>
            </PrismCard>
          </div>
        </section>
      </template>
    </PrismTabs>

    <AddSkillPopup v-model:open="addOpen" @saved="onSkillSaved" />
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-4);
}

/* ── Chart tab ───────────────────────────────────────────────────────────── */
.home-chart-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-3);
}
.chart-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid rgb(154 182 219 / .1);
  border-radius: var(--ds-radius-md);
  background: rgb(103 232 249 / .03);
}
.chart-filter-item {
  display: inline-flex;
  align-items: center;
}
.chart-filter-item :deep(.p-check) {
  min-height: auto;
  font-size: 13px;
}
.chart-error-banner {
  margin-bottom: var(--ds-space-2);
}
.chart-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
@media (max-width: 1199px) {
  .chart-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
.chart-card {
  padding: 16px 18px;
}
.chart-card :deep(header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.chart-card :deep(h3) {
  margin: 0;
  font-size: 15px;
}
.chart-card :deep(.p-chart) {
  margin: 0;
}
/* Donut "Skill by status": thu nhỏ 20% + căn giữa */
.chart-card--status :deep(.p-donut svg) {
  width: 80%;
  margin: 0 auto;
}
.chart-card :deep(.p-donut-legend) {
  font-size: 12px;
}
.chart-info-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  flex-shrink: 0;
  border: none;
  border-radius: var(--ds-radius-sm);
  background: transparent;
  color: var(--ds-color-text-muted);
  cursor: pointer;
}
.chart-info-btn:hover {
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .1);
}
.chart-info-btn:focus-visible {
  outline: 2px solid var(--ds-color-accent);
  outline-offset: 1px;
}
.chart-info-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  max-width: 280px;
  color: var(--ds-color-text-secondary);
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
.quick-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
  margin-left: auto;
}
.quick-actions :deep(.p-button) {
  min-height: 34px;
  padding: 6px 14px;
  font-size: 13px;
}
@media (max-width: 600px) {
  .quick-actions {
    width: 100%;
    margin-left: 0;
  }
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
/* Non-interactive KPI card (e.g. Subscribed) — no pointer cursor. */
.kpi-card--static {
  cursor: default;
}
.kpi-card:not([aria-label]):hover {
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
.skill-list--scroll {
  max-height: 332px;
  overflow-y: auto;
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

/* Two-card bottom row (Home SRS §5.8) — 3 tracks, AI News spans 2 */
.bottom-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
@media (max-width: 999px) {
  .bottom-row {
    grid-template-columns: 1fr;
  }
}
.bottom-card--wide {
  grid-column: span 2;
}
@media (max-width: 999px) {
  .bottom-card--wide {
    grid-column: auto;
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
.card-subtitle {
  font-size: 11px;
  color: var(--ds-color-text-muted);
  white-space: nowrap;
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

/* New Request timeline (Home SRS §5.8) */
.new-req-list {
  list-style: none;
  margin: 0;
  padding: 0 16px;
  display: grid;
}
.new-req-item {
  position: relative;
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: var(--ds-space-3);
  padding-bottom: var(--ds-space-5);
  cursor: pointer;
}
.new-req-item:last-child {
  padding-bottom: 0;
}
.new-req-item::before {
  content: '';
  position: absolute;
  left: 24px;
  top: 18px;
  bottom: 0;
  width: 2px;
  background: var(--ds-color-border);
}
.new-req-item:last-child::before {
  display: none;
}
.new-req-item::after {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  position: absolute;
  left: 22px;
  top: 6px;
}
.new-req--accent .ai-news-dot { border-color: var(--ds-color-accent); box-shadow: 0 0 8px rgb(103 232 249 / .4); }
.new-req--accent::after { background: var(--ds-color-accent); }
.new-req--violet .ai-news-dot { border-color: var(--ds-color-violet-400); box-shadow: 0 0 8px rgb(165 138 239 / .35); }
.new-req--violet::after { background: var(--ds-color-violet-400); }
.new-req--success .ai-news-dot { border-color: var(--ds-color-success); box-shadow: 0 0 8px rgb(156 232 208 / .3); }
.new-req--success::after { background: var(--ds-color-success); }
.new-req--warning .ai-news-dot { border-color: var(--ds-color-warning); box-shadow: 0 0 8px rgb(243 210 140 / .3); }
.new-req--warning::after { background: var(--ds-color-warning); }
.new-req--teal .ai-news-dot { border-color: var(--ds-color-teal-400); box-shadow: 0 0 8px rgb(73 220 207 / .3); }
.new-req--teal::after { background: var(--ds-color-teal-400); }
.new-req--danger .ai-news-dot { border-color: var(--ds-color-danger); box-shadow: 0 0 8px rgb(255 120 140 / .3); }
.new-req--danger::after { background: var(--ds-color-danger); }
.new-req--neutral .ai-news-dot { border-color: var(--ds-color-border-strong); }
.new-req--neutral::after { background: var(--ds-color-text-muted); }
.new-req-body {
  min-width: 0;
}
.new-req-title {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--ds-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.new-req-item:hover .new-req-title {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.new-req-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  line-height: 1.5;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.new-req-desc :deep(.p-badge) {
  white-space: nowrap;
}

/* AI News timeline (Home SRS §5.8) */
.ai-news-list {
  list-style: none;
  margin: 0;
  padding: 0 16px;
  display: block;
}
.ai-news-item {
  position: relative;
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: var(--ds-space-3);
  padding-bottom: var(--ds-space-5);
}
.ai-news-item:last-child {
  padding-bottom: 0;
}
.ai-news-item::before {
  content: '';
  position: absolute;
  left: 24px;
  top: 18px;
  bottom: 0;
  width: 2px;
  background: var(--ds-color-border);
}
.ai-news-item:last-child::before {
  display: none;
}
.ai-news-item::after {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  position: absolute;
  left: 22px;
  top: 6px;
}
.ai-news-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--ds-color-border-strong);
  background: var(--ds-color-space-900);
  display: grid;
  place-items: center;
}
.ai-news--accent .ai-news-dot { border-color: var(--ds-color-accent); box-shadow: 0 0 8px rgb(103 232 249 / .4); }
.ai-news--accent::after { background: var(--ds-color-accent); }
.ai-news--violet .ai-news-dot { border-color: var(--ds-color-violet-400); box-shadow: 0 0 8px rgb(165 138 239 / .35); }
.ai-news--violet::after { background: var(--ds-color-violet-400); }
.ai-news--success .ai-news-dot { border-color: var(--ds-color-success); box-shadow: 0 0 8px rgb(156 232 208 / .3); }
.ai-news--success::after { background: var(--ds-color-success); }
.ai-news--warning .ai-news-dot { border-color: var(--ds-color-warning); box-shadow: 0 0 8px rgb(243 210 140 / .3); }
.ai-news--warning::after { background: var(--ds-color-warning); }
.ai-news--teal .ai-news-dot { border-color: var(--ds-color-teal-400); box-shadow: 0 0 8px rgb(73 220 207 / .3); }
.ai-news--teal::after { background: var(--ds-color-teal-400); }
.ai-news--rose .ai-news-dot { border-color: var(--ds-color-rose-300); box-shadow: 0 0 8px rgb(255 172 196 / .3); }
.ai-news--rose::after { background: var(--ds-color-rose-300); }
.ai-news-body {
  min-width: 0;
}
.ai-news-title {
  display: block;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  font-size: 15px;
  font-weight: 500;
  color: var(--ds-color-text-primary);
  cursor: pointer;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  width: 100%;
}
.ai-news-title:hover {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.ai-news-title:focus-visible {
  outline: 2px solid var(--ds-color-accent);
  outline-offset: 1px;
}
.ai-news-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  line-height: 1.5;
}
</style>
