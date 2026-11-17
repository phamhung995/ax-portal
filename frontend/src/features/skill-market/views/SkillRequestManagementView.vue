<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import AddSkillRequestPopup from './AddSkillRequestPopup.vue'
import EditSkillRequestPopup from './EditSkillRequestPopup.vue'
import RequestFeedCard from './RequestFeedCard.vue'
import { skillRequestApi } from '@/features/skill-market/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useSkillRequestFeedStore } from '@/features/skill-market/store'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { SkillRequestStatsResult, SkillRequestFeedItem } from '@/features/skill-market/types'

const feed = useSkillRequestFeedStore()
const s = feed.state
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const { t } = useI18nStore()

const addOpen = ref(false)
const editItem = ref<SkillRequestFeedItem | null>(null)
const editOpen = ref(false)
const deleteItem = ref<SkillRequestFeedItem | null>(null)
const deleteConfirm = ref(false)
const deleting = ref(false)
const searchInput = ref('')
const sentinel = ref<HTMLElement | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null
let observer: IntersectionObserver | null = null

const viewerId = () => auth.user?.userId ?? -1

// ── Sidebar stats (top-10 cards) ────────────────────────────────────────────
const stats = ref<SkillRequestStatsResult | null>(null)

async function loadStats() {
  try {
    stats.value = await skillRequestApi.stats()
  } catch {
    /* non-critical: sidebar cards simply stay empty */
  }
}

// ── Feed paging ─────────────────────────────────────────────────────────────
async function loadPage(cursor: string | null) {
  if (s.loading) return
  s.loading = true
  s.error = ''
  try {
    const result = await skillRequestApi.feed(cursor, 10, s.mine, s.title || undefined,
      s.sortBy || undefined)
    feed.appendResult(result, viewerId())
    // Refresh sidebar stats: votes/comments changed.
    void loadStats()
  } catch (err) {
    s.error = toUserError(err).message
  } finally {
    s.loading = false
  }
}

function loadFirst() {
  feed.reset()
  void loadPage(null)
}

function loadNext() {
  if (!s.hasMore || s.loading) return
  void loadPage(s.nextCursor)
}

// ── Search (debounced) ──────────────────────────────────────────────────────
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (searchInput.value.trim() === s.title) return
    s.title = searchInput.value.trim()
    loadFirst()
  }, 400)
}

function clearSearch() {
  searchInput.value = ''
  if (searchTimer) clearTimeout(searchTimer)
  if (s.title !== '') {
    s.title = ''
    loadFirst()
  }
}

// "/" focuses the search box (ignored while typing in a field)
function onKeydown(event: KeyboardEvent) {
  if (event.key !== '/') return
  const target = event.target as HTMLElement | null
  if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return
  event.preventDefault()
  searchInputEl.value?.focus()
}
const searchInputEl = ref<HTMLElement | null>(null)

// ── Filter chips ────────────────────────────────────────────────────────────
function onChip(mine: boolean) {
  if (s.mine === mine) return
  s.mine = mine
  loadFirst()
}

// ── Sort: newest (default) / reputation best-first (GĐ5) ───────────────────
function onSortReputation() {
  s.sortBy = s.sortBy === 'reputation' ? '' : 'reputation'
  // Keep the choice in the URL so it survives reload / sharing (?sortBy=reputation).
  void router.replace({ query: { ...route.query, sortBy: s.sortBy || undefined } })
  loadFirst()
}

// Top Voted: open the request detail screen
function openRequestDetail(id: number) {
  void router.push({ name: 'skill-market-detail', params: { id } })
}

// Top Contributors: open the contributor profile screen
function openContributor(userId: number) {
  void router.push({ name: 'contributor-profile', params: { userId } })
}

// ── Infinite scroll ─────────────────────────────────────────────────────────
function onIntersect(entries: IntersectionObserverEntry[]) {
  if (entries.some((e) => e.isIntersecting)) loadNext()
}

function setupObserver() {
  observer?.disconnect()
  if (!sentinel.value) return
  observer = new IntersectionObserver(onIntersect, { rootMargin: '400px 0px' })
  observer.observe(sentinel.value)
}

watch(sentinel, setupObserver)

// ── Add request ─────────────────────────────────────────────────────────────
function onSaved() {
  loadFirst()
  void loadStats()
}

// ── Edit / Delete request (creator only) ────────────────────────────────────
function onEditRequest(item: SkillRequestFeedItem) {
  editItem.value = item
  editOpen.value = true
}
function onRequestUpdated() {
  editOpen.value = false
  editItem.value = null
  loadFirst()
  void loadStats()
}
function onDeleteRequest(item: SkillRequestFeedItem) {
  deleteItem.value = item
  deleteConfirm.value = true
}
async function confirmDelete() {
  if (!deleteItem.value) return
  deleting.value = true
  try {
    await skillRequestApi.delete(deleteItem.value.id)
    toastSuccess(t('app.skillMarket.deletedToast'))
    deleteConfirm.value = false
    deleteItem.value = null
    loadFirst()
    void loadStats()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  void loadStats()
  // Always reload the first page on mount: actions on the detail screen
  // (votes, comments, resolved skills) change the feed data, and the
  // cached store would otherwise show stale numbers on "Back to feed".
  if (s.loadedBy !== viewerId()) {
    s.mine = false
    s.title = ''
    searchInput.value = ''
  }
  // Deep-link: ?sortBy=reputation sorts best-first (same as Skill/MCP).
  if (route.query.sortBy === 'reputation') {
    s.sortBy = 'reputation'
  }
  loadFirst()
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  if (searchTimer) clearTimeout(searchTimer)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="frm">
    <div class="frm-cols">
      <!-- Skill request feed -->
      <div class="feed-col">
        <!-- Composer (top) -->
        <section class="composer p-glass" :aria-label="t('app.skillMarket.newRequestAria')">
          <PrismAvatar
            :name="auth.user?.userName ?? ''"
            :src="auth.user?.avatarUrl ?? undefined"
            :size="40"
          />
          <button type="button" class="composer-input" @click="addOpen = true">
            {{ t('app.skillMarket.feedPlaceholder') }}
          </button>
        </section>

        <!-- Toolbar -->
        <section class="toolbar" :aria-label="t('app.skillMarket.feedFiltersAria')">
          <div class="chips" role="group" :aria-label="t('app.skillMarket.feedFilterAria')">
            <button type="button" class="chip" :class="{ 'is-active': !s.mine }" @click="onChip(false)">
              {{ t('app.skillMarket.all') }}
            </button>
            <button type="button" class="chip" :class="{ 'is-active': s.mine }" @click="onChip(true)">
              {{ t('app.skillMarket.myRequests') }}
            </button>
            <button type="button" class="chip" :class="{ 'is-active': s.sortBy === 'reputation' }" @click="onSortReputation()">
              Uy tín nhất
            </button>
          </div>
          <div class="search-wrap">
            <Search class="search-icon" :size="16" aria-hidden="true" />
            <input
              ref="searchInputEl"
              v-model="searchInput"
              type="search"
              class="search-input"
              :placeholder="t('app.skillMarket.searchPh')"
              :aria-label="t('app.skillMarket.searchAria')"
              @input="onSearchInput"
            />
            <span class="search-kbd" aria-hidden="true">/</span>
            <button
              v-if="searchInput"
              type="button"
              class="search-clear"
              :aria-label="t('app.skillMarket.clearSearchAria')"
              @click="clearSearch"
            >
              ×
            </button>
          </div>
        </section>

        <!-- Feed -->
        <section class="feed" :aria-label="t('app.skillMarket.feedAria')">
          <PrismEmptyState
            v-if="s.error && !s.items.length"
            :title="t('app.skillMarket.loadError')"
            :description="s.error"
          >
            <PrismButton variant="secondary" @click="loadFirst">{{ t('app.common.retry') }}</PrismButton>
          </PrismEmptyState>
          <PrismEmptyState
            v-else-if="!s.loading && !s.items.length"
            :title="t('app.skillMarket.noRequests')"
            :description="t('app.skillMarket.noRequestsDesc')"
          />
          <RequestFeedCard
            v-for="item in s.items"
            :key="item.id"
            :item="item"
            @edit="onEditRequest"
            @deleted="onDeleteRequest"
          />
        </section>

        <!-- Sentinel / loader -->
        <div class="feed-end" aria-live="polite">
          <div v-if="s.loading" class="feed-end-spinner" :aria-label="t('app.skillMarket.loadingMore')"></div>
          <p v-else-if="s.error" class="feed-end-error">{{ s.error }}</p>
          <p v-else-if="!s.hasMore && s.items.length" class="feed-end-text">{{ t('app.skillMarket.allCaughtUp') }}</p>
          <PrismButton
            v-if="s.error && s.items.length"
            variant="secondary"
            @click="loadNext"
          >
            {{ t('app.skillMarket.loadMore') }}
          </PrismButton>
        </div>
      </div>

      <!-- Sidebar: 3 top-10 cards, one screen height, internal scroll -->
      <aside class="side-rail" :aria-label="t('app.skillMarket.highlightsAria')">
        <section class="widget p-glass" :aria-label="t('app.skillMarket.topVotedAria')">
          <h3>{{ t('app.skillMarket.topVoted') }}</h3>
          <div class="widget-list">
            <div v-for="(r, i) in stats?.topVoted ?? []" :key="r.id" class="trend-row">
              <span class="trend-rank">{{ i === 0 ? '🏆' : i === 1 ? '🥈' : i === 2 ? '🥉' : i + 1 }}</span>
              <div>
                <button type="button" class="trend-title" :title="r.title" @click="openRequestDetail(r.id)">
                  {{ r.title }}
                </button>
                <div class="trend-meta">{{ t('app.skillMarket.votesComments', { n: r.voteCount, m: r.commentCount }) }}</div>
              </div>
            </div>
            <p v-if="!stats?.topVoted?.length" class="widget-empty">{{ t('app.skillMarket.noRequests') }}</p>
          </div>
        </section>

        <section class="widget widget--fixed p-glass" :aria-label="t('app.skillMarket.topContributorsAria')">
          <h3>{{ t('app.skillMarket.topContributors') }}</h3>
          <div class="widget-list">
            <div v-for="(m, i) in stats?.topContributors ?? []" :key="m.userId" class="member-row">
              <span class="member-rank">{{ i === 0 ? '🏆' : i === 1 ? '🥈' : i === 2 ? '🥉' : i + 1 }}</span>
              <PrismAvatar :name="m.userName ?? ''" :src="m.userAvatarUrl ?? undefined" :size="32" />
              <div class="member-info">
                <button type="button" class="member-name member-name--link" @click="openContributor(m.userId)">
                  {{ m.userName ?? t('app.common.unknown') }}
                </button>
                <div class="member-role">{{ t('app.skillMarket.skillsLinked', { n: m.skillLinkCount }) }}</div>
              </div>
            </div>
            <p v-if="!stats?.topContributors?.length" class="widget-empty">{{ t('app.skillMarket.noContributions') }}</p>
          </div>
        </section>

        <section class="widget widget--tags p-glass" :aria-label="t('app.skillMarket.popularHashtagsAria')">
          <h3>{{ t('app.skillMarket.popularHashtags') }}</h3>
          <div class="widget-list">
            <div class="tag-cloud">
              <button
                v-for="h in stats?.popularHashtags ?? []"
                :key="h.tag"
                type="button"
                class="tag-chip"
                @click="searchInput = '#' + h.tag; onSearchInput()"
              >
                #{{ h.tag }} <span class="tag-count">{{ h.count }}</span>
              </button>
            </div>
            <p v-if="!stats?.popularHashtags?.length" class="widget-empty">{{ t('app.skillMarket.noHashtags') }}</p>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add request -->
    <AddSkillRequestPopup v-model:open="addOpen" @saved="onSaved" />

    <!-- Edit request -->
    <EditSkillRequestPopup v-model:open="editOpen" :item="editItem" @updated="onRequestUpdated" />

    <!-- Delete confirm -->
    <PrismDialog
      :model-value="deleteConfirm"
      :title="t('app.skillMarket.deleteTitle')"
      size="sm"
      :close-on-overlay="false"
      @update:model-value="deleteConfirm = $event"
    >
      <p class="frm-confirm-text">
        {{ t('app.skillMarket.deleteDesc', { title: deleteItem?.title ?? '' }) }}
      </p>
      <template #footer>
        <PrismButton variant="secondary" :disabled="deleting" @click="deleteConfirm = false">{{ t('app.common.cancel') }}</PrismButton>
        <PrismButton variant="danger" :loading="deleting" @click="confirmDelete">{{ t('app.common.delete') }}</PrismButton>
      </template>
    </PrismDialog>
  </div>
</template>

<style scoped>
.frm {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
}
.frm-confirm-text { color: var(--ds-color-text-primary); }
.frm-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--ds-space-4);
  align-items: start;
}
@media (max-width: 1199px) {
  .frm-cols {
    grid-template-columns: 1fr;
  }
}
.feed-col {
  display: grid;
  gap: var(--ds-space-4);
  min-width: 0;
}

/* composer */
.composer {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  padding: var(--ds-space-4);
}
.composer-input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--ds-color-border);
  background: var(--ds-input-background);
  color: var(--ds-color-text-muted);
  font: inherit;
  font-size: 15px;
  padding: 12px 16px;
  border-radius: 24px;
  cursor: pointer;
  text-align: left;
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease,
    border-color var(--ds-duration-fast) ease, box-shadow var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .composer-input:hover {
    background: rgb(103 232 249 / .08);
    border-color: var(--ds-color-border-strong);
    color: var(--ds-color-text-secondary);
    box-shadow: var(--ds-shadow-glow);
  }
}

/* toolbar */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  flex-wrap: wrap;
}
.chips {
  display: inline-flex;
  gap: var(--ds-space-2);
}
.chip {
  border: 1px solid var(--ds-color-border);
  background: var(--ds-input-background);
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 13px;
  padding: 6px 14px;
  border-radius: 20px;
  cursor: pointer;
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease,
    border-color var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .chip:hover {
    color: var(--ds-color-text-primary);
    border-color: var(--ds-color-border-strong);
  }
}
.chip.is-active {
  background: rgb(103 232 249 / .14);
  border-color: rgb(103 232 249 / .42);
  color: var(--ds-color-accent);
  font-weight: 600;
}
.search-wrap {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 220px;
  max-width: 460px;
}
.search-icon {
  position: absolute;
  left: 16px;
  color: var(--ds-color-text-muted);
  pointer-events: none;
  z-index: 1;
  transition: color var(--ds-duration-fast) ease;
}
.search-input {
  width: 100%;
  border: 1px solid var(--ds-color-border);
  background: rgb(16 13 43 / .55);
  backdrop-filter: blur(8px);
  color: var(--ds-color-text-primary);
  font: inherit;
  font-size: 14px;
  padding: 10px 76px 10px 42px;
  border-radius: 24px;
  outline: none;
  transition: border-color var(--ds-duration-fast) ease, box-shadow var(--ds-duration-fast) ease,
    background var(--ds-duration-fast) ease;
}
.search-input::placeholder {
  color: var(--ds-color-text-muted);
}
.search-input:focus {
  border-color: rgb(103 232 249 / .42);
  background: rgb(16 13 43 / .72);
  box-shadow: var(--ds-shadow-glow);
}
.search-wrap:focus-within .search-icon {
  color: var(--ds-color-accent);
}
.search-kbd {
  position: absolute;
  right: 40px;
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  border: 1px solid var(--ds-color-border);
  border-radius: 5px;
  padding: 2px 7px;
  pointer-events: none;
  transition: opacity var(--ds-duration-fast) ease;
}
.search-wrap:focus-within .search-kbd {
  opacity: 0;
}
.search-clear {
  position: absolute;
  right: 12px;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: none;
  background: rgb(255 255 255 / .06);
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  border-radius: 50%;
  z-index: 1;
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .search-clear:hover {
    background: rgb(255 255 255 / .12);
    color: var(--ds-color-text-primary);
  }
}

/* feed */
.feed {
  display: grid;
  gap: var(--ds-space-4);
}
.feed-end {
  display: grid;
  place-items: center;
  gap: var(--ds-space-3);
  padding: var(--ds-space-5) 0;
  min-height: 60px;
}
.feed-end-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--ds-color-border);
  border-top-color: var(--ds-color-accent);
  border-radius: 50%;
  animation: feed-spin 0.8s linear infinite;
}
@keyframes feed-spin {
  to { transform: rotate(360deg); }
}
.feed-end-text {
  margin: 0;
  color: var(--ds-color-text-muted);
  font-size: 14px;
}
.feed-end-error {
  margin: 0;
  color: var(--ds-color-danger);
  font-size: 13px;
  max-width: 480px;
  text-align: center;
}

/* side rail: 3 cards share exactly one screen height, each scrolls internally;
   sticky so the cards stay in view while the feed scrolls */
.side-rail {
  position: sticky;
  top: calc(var(--nx-app-header-height) + var(--ds-space-4));
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto auto;
  gap: var(--ds-space-4);
  height: calc(100vh - var(--nx-app-header-height) - 3 * var(--ds-space-4));
  min-height: 480px;
}
/* Top Contributors: sized to exactly 5 rows */
.widget--fixed {
  grid-template-rows: auto auto;
}
.widget--fixed .widget-list {
  overflow: visible;
}
/* Popular Hashtags: compact — only as much height as its chips need */
.widget--tags {
  grid-template-rows: auto minmax(0, auto);
}
.widget--tags .widget-list {
  max-height: 160px;
}
@media (max-width: 1199px) {
  .side-rail {
    position: static;
    height: auto;
    grid-template-rows: none;
  }
}
.widget {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--ds-space-3);
  padding: var(--ds-space-4) var(--ds-space-5);
  min-height: 0;
  overflow: hidden;
}
.widget h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.widget-list {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--ds-color-border-strong) transparent;
  padding-right: 4px;
  margin-right: -4px;
}
.widget-list::-webkit-scrollbar {
  width: 6px;
}
.widget-list::-webkit-scrollbar-track {
  background: transparent;
}
.widget-list::-webkit-scrollbar-thumb {
  background: var(--ds-color-border-strong);
  border-radius: 3px;
}
@media (max-width: 1199px) {
  .widget-list {
    max-height: 320px;
  }
}
.widget-empty {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-muted);
  padding: var(--ds-space-2) 0;
}

/* trending rows */
.trend-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: var(--ds-space-2);
  padding: var(--ds-space-2) 0;
}
.trend-rank {
  font: 700 15px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  text-align: center;
  align-self: start;
  line-height: 1.4;
}
.trend-title {
  width: 100%;
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-size: 14px;
  color: var(--ds-color-text-primary);
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (hover: hover) {
  .trend-title:hover {
    color: var(--ds-color-accent);
  }
}
.trend-meta {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  margin-top: 2px;
}

/* member rows */
.member-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  padding: var(--ds-space-2) 0;
}
.member-rank {
  flex: none;
  width: 20px;
  font: 700 14px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  text-align: center;
}
.member-info {
  flex: 1;
  min-width: 0;
}
.member-name {
  font-size: 14px;
  color: var(--ds-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-name--link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  text-align: left;
  cursor: pointer;
  display: block;
  width: 100%;
}
@media (hover: hover) {
  .member-name--link:hover {
    color: var(--ds-color-accent);
  }
}
.member-role {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}

/* tag cloud */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--ds-duration-fast), color var(--ds-duration-fast);
}
@media (hover: hover) {
  .tag-chip:hover {
    border-color: var(--ds-color-border-strong);
    color: var(--ds-color-text-primary);
  }
}
.tag-count {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
</style>
