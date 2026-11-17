<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, ChevronDown, ChevronLeft, ChevronUp, Eye, Heart, Mail, MessageCircle, Search, Upload } from 'lucide-vue-next'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismCard from '@/design-system/components/PrismCard.vue'
import PrismDatePicker from '@/design-system/components/PrismDatePicker.vue'
import PrismDrawer from '@/design-system/components/PrismDrawer.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismSkeleton from '@/design-system/components/PrismSkeleton.vue'
import PrismTooltip from '@/design-system/components/PrismTooltip.vue'
import ReputationBadge from '@/features/skills/components/ReputationBadge.vue'
import { reputationApi, type ReputationExplain } from '@/features/reputation/api'
import AiNewsComments from '@/features/ai-news/components/AiNewsComments.vue'
import AiNewsImportDialog from '@/features/ai-news/components/AiNewsImportDialog.vue'
import { aiNewsApi, type AiNewsSearchFilters } from '@/features/ai-news/api'
import { useAiNewsComments } from '@/features/ai-news/composables/useAiNewsComments'
import { toUserError } from '@/lib/api'
import { toast } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { AiNewsDetail, AiNewsItem } from '@/features/ai-news/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const i18n = useI18nStore()
const { t, formatDate } = i18n

// ── Manual .mht import (admin only — FR-21) ────────────────────────────────
const importOpen = ref(false)

// ── List pane ───────────────────────────────────────────────────────────────
const listLoading = ref(true)
const listError = ref('')
const items = ref<AiNewsItem[]>([])
const selectedId = ref<number | null>(null)

// ── Advanced search box (collapsible) ───────────────────────────────────────
// Draft = what the user is typing; applied = what the last Search/Reset sent
// to the API. Closing the box keeps the applied filters (results persist).
const searchOpen = ref(false)
const searched = ref(false)
const draft = ref<AiNewsSearchFilters>({})
const applied = ref<AiNewsSearchFilters>({})

const activeFilterCount = computed(() => {
  const f = applied.value
  let n = 0
  if (f.title?.trim()) n++
  if (f.sender?.trim()) n++
  if (f.from) n++
  if (f.to) n++
  return n
})

function toggleSearch() {
  searchOpen.value = !searchOpen.value
}

function runSearch() {
  const f: AiNewsSearchFilters = {
    title: draft.value.title?.trim() || undefined,
    sender: draft.value.sender?.trim() || undefined,
    from: draft.value.from || undefined,
    to: draft.value.to || undefined,
  }
  if (f.from && f.to && f.from > f.to) {
    toast({ title: t('aiNews.invalidDateRange'), tone: 'warning' })
    return
  }
  applied.value = f
  searched.value = true
  loadList()
}

function resetSearch() {
  draft.value = {}
  applied.value = {}
  searched.value = false
  loadList()
}

const DOT_TONES = ['accent', 'violet', 'success', 'warning', 'teal', 'rose']

// Grid sort mode (newest / most liked / most commented / most reputable).
// Applied client-side on the already-fetched items — no extra API call needed.
const gridSort = ref<'newest' | 'likes' | 'comments' | 'reputation'>('newest')
const gridSortMenuOpen = ref(false)
// 'Uy tín nhất' has no i18n key yet (same as the hardcoded badge labels) —
// FR-8 translation keys are still pending for reputation strings.
const gridSortLabel = computed(() =>
  gridSort.value === 'newest'
    ? t('aiNews.gridSortNewest')
    : gridSort.value === 'likes'
      ? t('aiNews.gridSortLikes')
      : gridSort.value === 'comments'
        ? t('aiNews.gridSortComments')
        : 'Uy tín nhất',
)
const gridSortedItems = computed(() => {
  const arr = [...items.value]
  if (gridSort.value === 'likes') {
    arr.sort((a, b) => (b.likeCount ?? 0) - (a.likeCount ?? 0) || (b.newsId - a.newsId))
  } else if (gridSort.value === 'comments') {
    arr.sort((a, b) => (b.commentCount ?? 0) - (a.commentCount ?? 0) || (b.newsId - a.newsId))
  } else if (gridSort.value === 'reputation') {
    // Best score first; unscored items (null) last; tie-break newest first.
    arr.sort(
      (a, b) =>
        (b.reputationScore ?? -1) - (a.reputationScore ?? -1) ||
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() ||
        b.newsId - a.newsId,
    )
  } else {
    // 'newest' — sort by createdAt DESC (parse to epoch ms — lexicographic
    // ISO comparison is unreliable with variable fractional-second precision).
    arr.sort((a, b) => {
      const ta = new Date(a.createdAt).getTime()
      const tb = new Date(b.createdAt).getTime()
      return ta === tb ? b.newsId - a.newsId : tb - ta
    })
  }
  return arr
})

// Monotonic counter to discard stale list responses when the user re-triggers
// a search while a fetch is in flight (out-of-order responses).
let listReqId = 0

async function loadList() {
  listLoading.value = true
  listError.value = ''
  const reqId = ++listReqId
  try {
    // When the reputation mode is active the API sorts by cached score too
    // (within the fetched newest-50 window — see backend AiNewsServiceImpl).
    const data = await aiNewsApi.list(
      50,
      applied.value,
      gridSort.value === 'reputation' ? 'reputation' : undefined,
    )
    if (reqId !== listReqId) return // superseded by a newer search
    items.value = data
    // FR-03: ?news={newsId} → select it (if in list); otherwise the first
    // item in the ACTIVE grid sort order (not raw API order).
    const queryId = Number(route.query.news)
    const wanted = Number.isFinite(queryId) && queryId > 0 ? queryId : null
    const exists = wanted != null && items.value.some((n) => n.newsId === wanted)
    // Compute the first visible item respecting the current grid sort.
    const sorted = [...data]
    if (gridSort.value === 'likes') {
      sorted.sort((a, b) => (b.likeCount ?? 0) - (a.likeCount ?? 0) || b.newsId - a.newsId)
    } else if (gridSort.value === 'comments') {
      sorted.sort((a, b) => (b.commentCount ?? 0) - (a.commentCount ?? 0) || b.newsId - a.newsId)
    } else if (gridSort.value === 'reputation') {
      sorted.sort(
        (a, b) =>
          (b.reputationScore ?? -1) - (a.reputationScore ?? -1) ||
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() ||
          b.newsId - a.newsId,
      )
    }
    selectedId.value = exists ? wanted : (sorted[0]?.newsId ?? null)
    syncHash()
  } catch (error) {
    if (reqId !== listReqId) return
    listError.value = toUserError(error).message
  } finally {
    if (reqId === listReqId) listLoading.value = false
  }
}

function syncHash() {
  if (selectedId.value == null) return
  // A stale list response resolving after the user navigated away must not
  // yank them back to /ai-news (the component is unmounted — only the
  // router survives).
  if (route.path !== '/ai-news') return
  // Only preserve ?comment when it belongs to the CURRENT item — a stale
  // comment from a different news item must not linger in the URL.
  const comment =
    typeof route.query.comment === 'string' && route.query.news === String(selectedId.value)
      ? route.query.comment : undefined
  const news = String(selectedId.value)
  if (route.query.news === news && (route.query.comment ?? undefined) === comment) return
  router.replace({
    path: '/ai-news',
    query: { news, ...(comment ? { comment } : {}) },
  })
}

function selectItem(newsId: number) {
  if (newsId === selectedId.value) return
  selectedId.value = newsId
  // A stale ?comment from a PREVIOUS news item must not survive an item
  // switch — BUT a fresh ?comment arriving WITH this news (bell deep-link
  // to a different item) must be kept so scrollToPendingComment can fire.
  // Only strip when the URL's news param doesn't match the target.
  if (typeof route.query.comment === 'string' && route.query.news !== String(newsId)) {
    const query: Record<string, string> = { news: String(newsId) }
    router.replace({ path: '/ai-news', query })
    return
  }
  syncHash()
}

// FR-04: title + meta render immediately from the list data — the detail
// fetch only supplies the body (and fresh like state).
const selectedItem = computed(
  () => items.value.find((n) => n.newsId === selectedId.value) ?? null,
)
const headerTitle = computed(() => detail.value?.title ?? selectedItem.value?.title ?? '')
const headerMeta = computed(() => {
  const d = detail.value
  const s = selectedItem.value
  const sender = d ? d.sender ?? d.source : s ? s.sender ?? s.source : ''
  const createdAt = d?.createdAt ?? s?.createdAt ?? null
  return { sender, createdAt }
})

// FR-05: selecting a different item aborts the in-flight detail fetch.
let detailAbort: AbortController | null = null

// ── Detail pane ─────────────────────────────────────────────────────────────
const detailLoading = ref(false)
const detailState = ref<'idle' | 'loading' | 'error' | 'notfound' | 'empty' | 'ok'>('idle')
const detail = ref<AiNewsDetail | null>(null)
/** Reputation explain (badge + "why" reasons) for the selected item. */
const reputation = ref<ReputationExplain | null>(null)

async function loadDetail() {
  const id = selectedId.value
  if (id == null) {
    detailState.value = 'idle'
    detail.value = null
    reputation.value = null
    return
  }
  detailAbort?.abort()
  const controller = new AbortController()
  detailAbort = controller
  detailLoading.value = true
  detailState.value = 'loading'
  detail.value = null
  reputation.value = null
  try {
    // Fire detail + comments in parallel — they are independent endpoints
    // (comments does its own requireActiveNews). Saves one RTT on every
    // item selection.
    const [data] = await Promise.all([
      aiNewsApi.detail(id, controller.signal),
      loadComments(),
    ])
    if (controller.signal.aborted) return
    detail.value = data
    detailState.value = data.body ? 'ok' : 'empty'
    void loadReputation(id)
  } catch (error) {
    if (controller.signal.aborted) return
    const code = (error as { code?: string })?.code
    detailState.value = code === 'NEWS_NOT_FOUND' ? 'notfound' : 'error'
  } finally {
    if (!controller.signal.aborted) detailLoading.value = false
  }
}

async function loadReputation(id: number) {
  try {
    const explain = await reputationApi.explain('AI_NEWS', id)
    // Only apply if the user is still on the same item.
    if (selectedId.value === id) reputation.value = explain
  } catch {
    if (selectedId.value === id) reputation.value = null
  }
}

// ── View tracking (stay on the detail 30s → record the view) ────────────────
// The backend is idempotent per (news, user), so a re-fire after a page
// reload is a no-op — the Set below only avoids redundant calls within a
// single page session. The 30s only accumulates while the tab is VISIBLE —
// a background tab must not count as "reading" (true pause: hidden time is
// subtracted out, so a user who reads 10s, switches tabs for 10 minutes,
// and comes back still needs 20s of VISIBLE reading).
const VIEW_TRACK_MS = 30_000
let viewTimer: number | null = null
let viewRemaining: number | null = null // ms of visible reading still needed
let viewTickStart: number | null = null // when the current visible segment started
const viewedIds = new Set<number>()

function stopViewTimer() {
  if (viewTimer != null) {
    window.clearTimeout(viewTimer)
    viewTimer = null
  }
  viewRemaining = null
  viewTickStart = null
}

function startViewTimer() {
  stopViewTimer()
  const id = selectedId.value
  if (id == null || viewedIds.has(id)) return
  viewRemaining = VIEW_TRACK_MS
  scheduleViewTimer()
}

function scheduleViewTimer() {
  if (viewTimer != null) window.clearTimeout(viewTimer)
  viewTimer = null
  if (viewRemaining == null) return
  // Tab hidden → paused; the visibilitychange handler resumes with the
  // remaining time when the tab comes back.
  if (document.hidden) return
  viewTickStart = Date.now()
  viewTimer = window.setTimeout(() => {
    viewTimer = null
    viewTickStart = null
    viewRemaining = null
    fireViewRecord()
  }, viewRemaining)
}

function fireViewRecord() {
  const id = selectedId.value
  if (id == null || viewedIds.has(id)) return
  viewedIds.add(id)
  aiNewsApi.recordView(id)
    .then(({ viewCount }) => {
      // Only apply if the user is still on the same item.
      if (selectedId.value === id && detail.value) {
        detail.value = { ...detail.value, viewCount }
      }
    })
    .catch(() => {
      /* silent — view tracking must never break the UI */
    })
}

// True pause: while the tab is hidden, bank the elapsed visible time so
// hidden time does NOT count toward the 30s. On return, resume the timer
// with whatever visible reading is still needed.
function onViewVisibilityChange() {
  if (document.hidden) {
    if (viewTimer != null && viewRemaining != null && viewTickStart != null) {
      window.clearTimeout(viewTimer)
      viewTimer = null
      viewRemaining -= Date.now() - viewTickStart
      viewTickStart = null
    }
  } else {
    scheduleViewTimer()
  }
}

// The timer starts when the detail is actually readable (body ok OR empty
// body — the user is on the article either way) and resets on every item
// switch / load failure.
watch(detailState, (state) => {
  if (state === 'ok' || state === 'empty') startViewTimer()
  else stopViewTimer()
})

// ── Like (FR-15 — silent, no toast) ────────────────────────────────────────
const likeSending = ref(false)
async function onLike() {
  const id = selectedId.value
  if (id == null || !detail.value || likeSending.value) return
  likeSending.value = true
  const prev = detail.value
  try {
    const { likeCount } = await aiNewsApi.toggleLike(id)
    if (selectedId.value === id && detail.value) {
      detail.value = { ...detail.value, likeCount, liked: !prev.liked }
    }
  } catch {
    // Keep the previous (optimistic) state on failure, but tell the user
    // the like did not register — a silent failure looks like a no-op.
    toast({ title: t('aiNews.likeFail'), tone: 'warning' })
  } finally {
    likeSending.value = false
  }
}

// ── Comments (FR-16..FR-20) — state + mutations in the composable ──────────
const {
  comments,
  commentText,
  commentSending,
  commentJustSent,
  replyTo,
  replyText,
  replySending,
  deletingId,
  commentSortOrder,
  commentsDrawerOpen,
  voteSendingIds,
  commentCount,
  loadComments,
  scrollToPendingComment,
  sendComment,
  sendReply,
  onHelpful,
  onDelete,
  reset: resetComments,
  dispose: disposeComments,
} = useAiNewsComments(selectedId)

// FR-07: links inside the email body open in a new tab (target=_blank rel=noopener).
function onBodyLinkClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement).closest('a')
  if (!anchor) return
  // Normalize: trim + strip control chars (e.g. "java\nscript:" would
  // otherwise slip past the scheme check below).
  const href = (anchor.getAttribute('href') ?? '').replace(/[\u0000-\u0020\u007f]/g, '').trim()
  if (!href || href.startsWith('#')) return
  event.preventDefault()
  // Defense in depth: block script/other dangerous schemes. The backend
  // already strips these via the Jsoup sanitizer, but never let a
  // javascript:/vbscript:/data: href execute from the rendered body.
  if (/^(javascript|vbscript|data):/i.test(href)) return
  if (href.startsWith('mailto:')) {
    // mailto: links must open the mail client, not a new tab.
    window.location.href = href
    return
  }
  window.open(href, '_blank', 'noopener,noreferrer')
}

// FR-07c: strip <style> blocks from the email body before v-html. The
// backend (HtmlSanitizer) already strips them at import time, so this is a
// safety net for legacy rows stored before that fix. The Samsung "cafe
// note" mail editor embeds a full stylesheet (e.g. `body li, body p {
// margin: 0 auto }`); once injected via v-html it becomes a GLOBAL
// stylesheet for the whole app — the `margin: 0 auto` on the comment <li>
// flex items overrides align-items:stretch, shrinking and centering every
// comment (and other app elements) on those news items. Layout comes from
// the preserved inline style attributes, so dropping the blocks does not
// visibly change the email. (Lazy match — a greedy .*? would backtrack
// quadratically on a multi-MB body.)
const bodyHtml = computed(() => (detail.value?.body ?? '').replace(/<style[\s\S]*?<\/style>/gi, ''))

// FR-07b: broken images in the email body are removed — the <img> error
// event does not bubble, so a capture-phase listener on the body container
// catches it. The body re-renders per news item (v-html), so re-attach on
// every detail change.
const bodyEl = ref<HTMLElement | null>(null)
watch(
  () => detailState.value,
  (state) => {
    if (state !== 'ok') return
    // The body div is created by v-else-if — wait for the render.
    void nextTick(() => {
      bodyEl.value?.addEventListener('error', onBodyImgError, true)
    })
  },
  { immediate: true },
)
function onBodyImgError(event: Event) {
  const target = event.target
  if (target instanceof HTMLImageElement) {
    target.remove()
  }
}

// ── Lifecycle ───────────────────────────────────────────────────────────────
watch(selectedId, () => {
  // Reset comment state so the previous item's comments/reply form never
  // leak into the newly selected item while its data is loading.
  resetComments()
  loadDetail()
})

// Back/forward + manual URL edits: keep the selection in sync with
// ?news={newsId} (ai-news_sumary.md §2). selectItem no-ops when the id
// equals the current selection, so this cannot fight the selectedId watcher.
watch(
  () => route.query.news,
  (raw) => {
    if (typeof raw !== 'string') return
    const id = Number(raw)
    if (Number.isFinite(id) && id > 0 && id !== selectedId.value) {
      selectItem(id)
    }
  },
)

// Bell deep-link while already on the same news item: re-scroll to the
// (possibly different) target comment.
watch(
  () => route.query.comment,
  () => {
    scrollToPendingComment()
  },
)

onMounted(async () => {
  // Ẩn scrollbar trang (vẫn scroll được) chỉ trong màn AI News
  document.documentElement.classList.add('ainews-page')
  // View timer only accumulates while the tab is visible.
  document.addEventListener('visibilitychange', onViewVisibilityChange)
  // Deep-link: ?sortBy=reputation sorts best-first (same as Skill/MCP).
  if (route.query.sortBy === 'reputation') {
    gridSort.value = 'reputation'
  }
  await loadList()
  // loadDetail is triggered by the selectedId watcher when loadList sets
  // the initial selection — no explicit call needed (would double-fetch).
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove('ainews-page')
  document.removeEventListener('visibilitychange', onViewVisibilityChange)
  detailAbort?.abort()
  disposeComments()
  stopViewTimer()
})
</script>

<template>
  <div class="ainews">
    <AiNewsImportDialog v-model="importOpen" @imported="loadList" />

    <!-- Advanced search box (collapsible) -->
    <div class="ainews-search" :class="{ 'is-open': searchOpen }">
      <button
        type="button"
        class="ainews-search-toggle"
        :aria-expanded="searchOpen"
        @click="toggleSearch"
      >
        <Search :size="15" aria-hidden="true" />
        <span v-if="!searchOpen">{{ t('aiNews.openAdvanceSearch') }}</span>
        <span v-else class="ainews-search-toggle-title">{{ t('aiNews.advanceSearch') }}</span>
        <span v-if="activeFilterCount > 0" class="ainews-search-badge">
          {{ t('aiNews.filtersActive', { n: activeFilterCount }) }}
        </span>
        <ChevronUp v-if="searchOpen" :size="16" aria-hidden="true" class="ainews-search-chevron" />
        <ChevronDown v-else :size="16" aria-hidden="true" class="ainews-search-chevron" />
      </button>

      <div v-if="searchOpen" class="ainews-search-body">
        <div class="ainews-search-row" @keyup.enter="runSearch">
          <PrismInput
            v-model="draft.title"
            :label="t('aiNews.searchTitle')"
            :placeholder="t('aiNews.searchTitlePh')"
          />
          <PrismInput
            v-model="draft.sender"
            :label="t('aiNews.searchSender')"
            :placeholder="t('aiNews.searchSenderPh')"
          />
          <PrismDatePicker
            v-model="draft.from"
            :label="t('aiNews.searchFrom')"
            :placeholder="t('app.common.selectDatePh')"
          />
          <PrismDatePicker
            v-model="draft.to"
            :label="t('aiNews.searchTo')"
            :placeholder="t('app.common.selectDatePh')"
          />
        </div>
        <div class="ainews-search-actions">
          <!-- Manual .mht import — admin only (FR-21), far left of the row -->
          <PrismButton
            v-if="auth.isAdmin"
            variant="secondary"
            class="ainews-import-btn"
            :disabled="listLoading"
            @click="importOpen = true"
          >
            <Upload :size="15" aria-hidden="true" /> {{ t('aiNews.import') }}
          </PrismButton>
          <PrismButton variant="secondary" :disabled="listLoading" @click="resetSearch">
            {{ t('aiNews.reset') }}
          </PrismButton>
          <PrismButton variant="primary" :disabled="listLoading" @click="runSearch">
            <Search :size="15" aria-hidden="true" /> {{ t('aiNews.search') }}
          </PrismButton>
        </div>
      </div>
    </div>

    <div
      class="ainews-grid"
      :class="{ 'is-empty-list': !listLoading && !listError && !items.length }"
    >
      <!-- List pane -->
      <PrismCard class="ainews-list-card">
        <template #header>
          <h3><span aria-hidden="true">📰</span> {{ t('app.aiNews') }}</h3>
          <!-- Grid sort dropdown -->
          <div v-if="!listLoading && items.length" class="ainews-grid-sort">
            <button
              type="button"
              class="ainews-grid-sort-btn"
              :aria-expanded="gridSortMenuOpen"
              @click="gridSortMenuOpen = !gridSortMenuOpen"
            >
              <span>{{ gridSortLabel }}</span>
              <ChevronDown :size="13" aria-hidden="true" />
            </button>
            <div v-if="gridSortMenuOpen" class="ainews-grid-sort-menu" @click.away="gridSortMenuOpen = false">
              <button type="button" :class="{ 'is-active': gridSort === 'newest' }" @click="gridSort = 'newest'; gridSortMenuOpen = false">{{ t('aiNews.gridSortNewest') }}</button>
              <button type="button" :class="{ 'is-active': gridSort === 'likes' }" @click="gridSort = 'likes'; gridSortMenuOpen = false">{{ t('aiNews.gridSortLikes') }}</button>
              <button type="button" :class="{ 'is-active': gridSort === 'comments' }" @click="gridSort = 'comments'; gridSortMenuOpen = false">{{ t('aiNews.gridSortComments') }}</button>
              <button type="button" :class="{ 'is-active': gridSort === 'reputation' }" @click="gridSort = 'reputation'; gridSortMenuOpen = false">Uy tín nhất</button>
            </div>
          </div>
        </template>
        <div v-if="listLoading" class="ai-news-skeleton" aria-busy="true">
          <div v-for="i in 5" :key="i" class="ai-news-skeleton-row">
            <PrismSkeleton height="14px" width="70%" />
            <PrismSkeleton height="12px" width="45%" />
          </div>
        </div>
        <PrismAlert v-else-if="listError" tone="warning" class="ainews-list-error">
          {{ listError }}
          <template #actions>
            <PrismButton variant="secondary" @click="loadList">{{ t('aiNews.retry') }}</PrismButton>
          </template>
        </PrismAlert>
        <PrismEmptyState
          v-else-if="!items.length"
          :title="searched ? t('aiNews.noResults') : t('aiNews.listEmpty')"
          :description="searched ? t('aiNews.noResultsDesc') : undefined"
        >
          <PrismButton v-if="searched" variant="secondary" @click="resetSearch">
            {{ t('aiNews.reset') }}
          </PrismButton>
        </PrismEmptyState>
        <ul
          v-else
          class="ai-news-list"
          role="list"
        >
          <li
            v-for="(item, idx) in gridSortedItems"
            :key="item.newsId"
            class="ai-news-item"
            :class="[`ai-news--${DOT_TONES[idx % DOT_TONES.length]}`, { selected: item.newsId === selectedId }]"
            :aria-current="item.newsId === selectedId ? 'true' : undefined"
            role="button"
            tabindex="0"
            @click="selectItem(item.newsId)"
            @keydown.enter="selectItem(item.newsId)"
            @keydown.space.prevent="selectItem(item.newsId)"
          >
            <span class="ai-news-dot" aria-hidden="true"></span>
            <div class="ai-news-body">
              <span class="ai-news-title" :title="item.title">{{ item.title }}</span>
              <p class="ai-news-desc">
                {{ item.sender ?? item.source }} · {{ formatDate(item.createdAt) }}
              </p>
              <div v-if="item.likeCount > 0 || item.commentCount > 0 || item.viewCount > 0" class="ai-news-stats">
                <span v-if="item.likeCount > 0" class="ai-news-stat"><Heart :size="11" aria-hidden="true" /> {{ item.likeCount }}</span>
                <span v-if="item.commentCount > 0" class="ai-news-stat"><MessageCircle :size="11" aria-hidden="true" /> {{ item.commentCount }}</span>
                <span v-if="item.viewCount > 0" class="ai-news-stat"><Eye :size="11" aria-hidden="true" /> {{ item.viewCount }}</span>
              </div>
              <div class="ai-news-reputation">
                <ReputationBadge
                  :tier="item.reputationTier"
                  :score="item.reputationScore"
                  new-hint="Mới: chưa đủ lượt xem để xếp hạng (cần 5)"
                />
              </div>
            </div>
          </li>
        </ul>
      </PrismCard>

      <!-- Detail pane -->
      <PrismCard class="ainews-detail-card" :aria-busy="detailLoading || undefined">
        <template #header>
          <h3 class="ainews-detail-title" :title="headerTitle">
            {{ headerTitle || t('aiNews.selectNews') }}
          </h3>
          <!-- FR-22: open the comment sidebar (right drawer) -->
          <button
            type="button"
            class="ainews-open-comments"
            :aria-label="t('aiNews.openComments')"
            :disabled="selectedId == null"
            @click="commentsDrawerOpen = true"
          >
            <ChevronLeft :size="16" aria-hidden="true" />
            <MessageCircle :size="16" aria-hidden="true" />
          </button>
        </template>
        <div v-if="headerMeta.sender" class="ainews-meta-row">
          <span class="ainews-sender">
            <Mail :size="14" aria-hidden="true" />
            {{ headerMeta.sender }}
          </span>
          <span class="ainews-meta-right">
            <!-- Reputation badge with explain tooltip (score from like/view + decay). -->
            <PrismTooltip v-if="reputation">
              <ReputationBadge
                :tier="reputation.tier"
                :score="reputation.score"
                new-hint="Mới: chưa đủ lượt xem để xếp hạng (cần 5)"
              />
              <template #content>
                <div v-for="reason in reputation.reasons" :key="reason">{{ reason }}</div>
              </template>
            </PrismTooltip>
            <span v-if="detail && detail.viewCount > 0" class="ainews-viewers" :title="t('aiNews.viewers', { n: detail.viewCount })">
              <Eye :size="13" aria-hidden="true" />
              {{ detail.viewCount }}
            </span>
            <time class="ainews-date"><Calendar :size="13" aria-hidden="true" />{{ formatDate(headerMeta.createdAt) }}</time>
          </span>
        </div>
        <div class="ainews-detail-scroll">
        <template v-if="selectedId == null">
          <PrismEmptyState
            :title="t('aiNews.selectNews')"
            :description="t('aiNews.selectNewsDesc')"
          />
        </template>

        <template v-else>
          <!-- Body states (FR-08..FR-11) -->
          <div v-if="detailState === 'loading'" class="ainews-body-state">
            <PrismSkeleton height="14px" width="60%" />
            <p class="ainews-muted">{{ t('aiNews.loading') }}</p>
          </div>
          <PrismAlert v-else-if="detailState === 'error'" tone="warning">
            {{ t('aiNews.loadFail') }}
          </PrismAlert>
          <PrismAlert v-else-if="detailState === 'notfound'" tone="warning">
            {{ t('aiNews.notFound') }}
          </PrismAlert>
          <PrismAlert v-else-if="detailState === 'empty'" tone="info">
            {{ t('aiNews.noBody') }}
          </PrismAlert>
          <div
            v-else-if="detailState === 'ok' && detail"
            ref="bodyEl"
            class="ainews-body"
            v-html="bodyHtml"
            @click="onBodyLinkClick"
          ></div>

          <!-- Like + comments (FR-15..FR-20) — shown when the detail loaded
               (both body-ok and empty-body states: FR-12 — like/comment are
               available regardless of body content). Shared component, also
               rendered in the comment sidebar (FR-22). -->
          <AiNewsComments
            v-if="detail && (detailState === 'ok' || detailState === 'empty')"
            v-model:comment-text="commentText"
            v-model:reply-to="replyTo"
            v-model:reply-text="replyText"
            v-model:sort-order="commentSortOrder"
            :comments="comments"
            :comment-sending="commentSending"
            :reply-sending="replySending"
            :vote-sending-ids="voteSendingIds"
            :deleting-id="deletingId"
            :is-admin="auth.isAdmin"
            :liked="detail.liked"
            :like-count="detail.likeCount"
            :like-sending="likeSending"
            form-position="bottom"
            :show-header="false"
            :just-sent="commentJustSent"
            @send="sendComment"
            @reply="sendReply"
            @helpful="onHelpful"
            @delete="onDelete"
            @like="onLike"
          />
        </template>
        </div>
      </PrismCard>
    </div>

    <!-- FR-22: comment sidebar (right drawer) — shares state with the block
         under the body, so both stay in sync. -->
    <PrismDrawer
      v-model="commentsDrawerOpen"
      placement="right"
      dialog-class="ainews-comments-drawer"
      :title="t('aiNews.commentCount', { n: commentCount })"
    >
      <AiNewsComments
        v-if="commentsDrawerOpen && selectedId != null && detail && (detailState === 'ok' || detailState === 'empty')"
        v-model:comment-text="commentText"
        v-model:reply-to="replyTo"
        v-model:reply-text="replyText"
        v-model:sort-order="commentSortOrder"
        :comments="comments"
        :comment-sending="commentSending"
        :reply-sending="replySending"
        :vote-sending-ids="voteSendingIds"
        :deleting-id="deletingId"
        :is-admin="auth.isAdmin"
        :liked="detail.liked"
        :like-count="detail.likeCount"
        :like-sending="likeSending"
        :just-sent="commentJustSent"
        @send="sendComment"
        @reply="sendReply"
        @helpful="onHelpful"
        @delete="onDelete"
        @like="onLike"
      />
    </PrismDrawer>
  </div>
</template>

<style scoped>
.ainews {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-4);
  /* Chiếm trọn vùng màn hình còn lại (trừ app header + padding main) —
     2 card list/detail bên dưới tự stretch vừa khít màn hình. */
  height: calc(100svh - var(--nx-app-header-height) - 2 * var(--ds-space-4));
}

.ainews-list-error {
  margin: 12px 16px;
}

/* ── Advanced search box (collapsible) ───────────────────────────────────── */
.ainews-search {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: rgb(9 20 45 / .25);
  overflow: hidden;
  flex-shrink: 0;
}
.ainews-search-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border: 0;
  background: none;
  color: var(--ds-color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  text-align: left;
}
.ainews-search-toggle:hover {
  background: rgb(154 182 219 / .08);
  color: var(--ds-color-text-primary);
}
.ainews-search-toggle > svg:first-child {
  color: var(--ds-color-accent);
  flex-shrink: 0;
}
.ainews-search-chevron {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--ds-color-text-muted);
}
.ainews-search-badge {
  font-size: 11px;
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .1);
  border: 1px solid rgb(103 232 249 / .25);
  border-radius: 999px;
  padding: 1px 8px;
  white-space: nowrap;
}
.ainews-search-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 14px 14px;
  border-top: 1px solid rgb(154 182 219 / .1);
}
/* Hàng 1: 4 search box chia đều 1 hàng (Title, Sender, From, To) */
.ainews-search-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
/* Hàng 2: nút Reset/Search — căn phải */
.ainews-search-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
/* Nút Import (admin) — ngoài cùng bên trái hàng actions */
.ainews-import-btn {
  margin-right: auto;
}
@media (max-width: 1199px) {
  .ainews-search-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 799px) {
  .ainews-search-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 2-pane layout (NFR-05) — chiếm trọn chiều cao còn lại của .ainews,
   2 card list/detail stretch vừa khít màn hình (không scroll trang). */
.ainews-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  grid-template-rows: 1fr;
  gap: 14px;
  flex: 1 1 auto;
  min-height: 0;
  align-items: stretch;
}
/* Card list: tự cuộn bên trong khi nhiều item (header card cố định). */
.ainews-list-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
/* Header card cố định — chỉ phần thân card cuộn */
.ainews-list-card :deep(> header) {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
.ainews-list-card :deep(> header h3) {
  flex: 1 1 auto;
  min-width: 0;
}
/* Grid sort dropdown */
.ainews-grid-sort {
  position: relative;
  flex-shrink: 0;
}
.ainews-grid-sort-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid rgb(154 182 219 / .2);
  border-radius: 999px;
  background: none;
  color: var(--ds-color-text-secondary);
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
}
.ainews-grid-sort-btn:hover {
  background: rgb(154 182 219 / .12);
  color: var(--ds-color-text-primary);
}
.ainews-grid-sort-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  min-width: 130px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: rgb(9 20 45 / .98);
  box-shadow: 0 6px 20px rgb(0 0 0 / .4);
}
.ainews-grid-sort-menu button {
  display: block;
  width: 100%;
  padding: 6px 10px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ds-color-text-secondary);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
}
.ainews-grid-sort-menu button:hover {
  background: rgb(154 182 219 / .12);
  color: var(--ds-color-text-primary);
}
.ainews-grid-sort-menu button.is-active {
  color: var(--ds-color-accent);
  font-weight: 600;
}
/* Danh sách: tự cuộn trong card khi nhiều item */
.ai-news-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}
/* List rỗng: 2 card cao bằng nhau — detail card stretch theo list card,
   empty state "Select a news item" căn giữa phần thân card */
.ainews-grid.is-empty-list {
  align-items: stretch;
}
/* Empty state "Select a news item" căn giữa phần thân card — chỉ dùng flex
   khi list rỗng (không có body content nên không lo vấn đề flex shrink). */
.ainews-grid.is-empty-list .ainews-detail-scroll {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.ainews-grid.is-empty-list .ainews-detail-card :deep(.p-empty) {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
@media (max-width: 999px) {
  .ainews {
    height: auto;
  }
  .ainews-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    flex: none;
  }
  /* Mobile: 1 cột — list/detail theo chiều cao tự nhiên, scroll ở mức trang */
  .ai-news-list {
    overflow: visible;
  }
  .ainews-detail-card {
    height: auto;
  }
  .ainews-detail-scroll {
    overflow: visible;
  }
}

/* ── List pane — timeline (demo style) ───────────────────────────────────── */
.ainews-list-card .ai-news-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: block;
}
.ainews-list-card .ai-news-item {
  align-items: start;
}
.ai-news-item {
  position: relative;
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: var(--ds-space-3);
  padding-bottom: var(--ds-space-5);
  cursor: pointer;
}
.ai-news-item:last-child {
  padding-bottom: 0;
}
.ai-news-item::before {
  content: '';
  position: absolute;
  left: 8px;
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
  left: 6px;
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
  font-size: 15px;
  font-weight: 500;
  color: var(--ds-color-text-primary);
  cursor: pointer;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-news-title:hover {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.ai-news-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  line-height: 1.5;
}
/* Like + comment stats badges on list items */
.ai-news-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}
.ai-news-stat {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--ds-color-text-muted);
}
/* Item đang được chọn (highlight) */
.ai-news-item.selected .ai-news-title {
  color: var(--ds-color-accent);
}
.ai-news-item.selected .ai-news-dot {
  box-shadow: 0 0 8px rgb(103 232 249 / .4);
}
.ai-news-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-2);
  padding: var(--ds-space-3) 16px;
}
.ai-news-skeleton-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ── Detail pane (demo: PrismCard) ───────────────────────────────────────── */
.ainews-detail-card {
  min-width: 0;
  /* Cao vừa khít màn hình (stretch theo grid row). Card là flex column:
     title + meta-row cố định trên, phần còn lại (.ainews-detail-scroll)
     tự cuộn bên dưới — body + like + comments cuộn cùng nhau. */
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
/* Title + meta-row (sender/date) cố định — không scroll */
.ainews-detail-card :deep(> header),
.ainews-detail-card > .ainews-meta-row {
  flex-shrink: 0;
}
/* FR-22: header card — title + nút mở comment sidebar cùng hàng.
   Title chiếm chỗ còn lại (ellipsis), nút căn ngoài cùng bên phải. */
.ainews-detail-card :deep(> header) {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
.ainews-detail-card :deep(> header .ainews-detail-title) {
  flex: 1 1 auto;
  min-width: 0;
}
.ainews-open-comments {
  margin-left: auto;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  padding: 4px;
  border-radius: var(--ds-radius-sm);
  transition: background .15s ease, color .15s ease;
}
.ainews-open-comments:hover:not(:disabled) {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}
.ainews-open-comments:disabled {
  opacity: .4;
  cursor: not-allowed;
}
/* Vùng cuộn: body + like + comments (comments nằm ở dưới cùng).
   KHÔNG dùng flex — để .ainews-body (block) tự mở đầy đủ theo chiều cao
   nội dung và cả vùng cuộn chung; nếu dùng flex, body (flex child) sẽ bị
   shrink lại và tự scroll riêng do overflow-x:auto làm overflow-y=auto. */
.ainews-detail-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  /* Smooth scrolling without the jank of JS-animated scrollIntoView.
     The browser handles it natively on the compositor thread. */
  scroll-behavior: smooth;
  /* Contain paint so the heavy email body (v-html) doesn't trigger
     layout thrashing on the rest of the page during scroll. */
  contain: layout paint;
  overscroll-behavior: contain;
}
.ainews-detail-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--ds-color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ainews-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  margin-bottom: 10px;
}
.ainews-meta-right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.ainews-viewers {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--ds-font-mono);
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.ainews-sender {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ds-color-accent);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ainews-date {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--ds-font-mono);
  font-size: 12px;
  color: var(--ds-color-text-muted);
  flex-shrink: 0;
}

/* Body container (FR-07): render the email exactly as the imported .mht —
   keep the email's own text color, background and layout (its inline styles
   are preserved by the backend sanitizer). Only the container background is
   set to the .mht's default white page color, and the size is constrained to
   fit the detail pane. No background/color overrides are forced here.
   Không giới hạn chiều cao riêng — body mở đầy đủ theo chiều cao nội dung,
   cuộn chung với detail pane; scroll xuống dưới cùng sẽ thấy comments. */
.ainews-body {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: #fff; /* the .mht's default page background (white) */
  color: #000;      /* email default text is black; inline styles override */
  font-size: 14px;
  line-height: 1.65;
  padding: 20px 22px;
  overflow-x: auto;
  word-break: break-word;
}
/* Fit the email to the pane width WITHOUT altering its own styling: images
   and tables shrink to the container; the email's inline background-color /
   color / font are left untouched.
   `height: auto` OVERRIDES the inline height (e.g. width:900px;height:900px
   from the Samsung mail editor): max-width alone would still keep the
   inline height, leaving a huge empty box that pushes the comments away.
   !important is required to beat the inline style attribute. */
.ainews-body :deep(img) {
  max-width: 100%;
  height: auto !important;
}
.ainews-body :deep(table) {
  max-width: 100%;
}

.ainews-body-state {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-2);
  padding: var(--ds-space-4);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-md);
}
.ainews-muted {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-muted);
  padding: 12px 0;
}

/* Like + comments (FR-15..FR-20) styles live in AiNewsComments.vue (shared
   between the block under the body and the comment sidebar). */

/* Ẩn thanh scroll nhưng vẫn scroll được (body, list, detail scroll) */
.ainews-body,
.ai-news-list,
.ainews-detail-scroll {
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge legacy */
}
.ainews-body::-webkit-scrollbar,
.ai-news-list::-webkit-scrollbar,
.ainews-detail-scroll::-webkit-scrollbar {
  display: none; /* Chrome/Safari */
}
</style>

<!-- Non-scoped: PrismDrawer teleport ra body nên scoped style không áp dụng
     vào <dialog>. Class `ainews-comments-drawer` được forward lên <dialog>
     (PrismDrawer/PrismDialog nhận prop `class`). -->
<style>
/* FR-22: comment sidebar — rộng hơn drawer mặc định (420px → 768px) */
.ainews-comments-drawer {
  width: min(768px, calc(100vw - 16px));
}
/* Flex column layout ONLY when the dialog is open — without [open],
   display:flex would override the UA's display:none for closed <dialog>,
   making the drawer visible even when commentsDrawerOpen is false. */
.ainews-comments-drawer[open] {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
/* Bỏ nhãn "PRISM / DRAWER" trong header của comment sidebar */
.ainews-comments-drawer .p-eyebrow {
  display: none;
}
/* Header cố định */
.ainews-comments-drawer .p-dialog-header {
  flex-shrink: 0;
}
/* Body là scroll container duy nhất — override display:grid → block
   (grid breaks position:sticky children). */
.ainews-comments-drawer .p-dialog-body {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  display: block;
  scroll-behavior: smooth;
  contain: layout paint;
  overscroll-behavior: contain;
  scrollbar-width: none;
}
.ainews-comments-drawer .p-dialog-body::-webkit-scrollbar {
  display: none;
}
</style>
