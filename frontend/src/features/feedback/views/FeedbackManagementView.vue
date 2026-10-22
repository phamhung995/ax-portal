<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import FeedbackDetailPopup from './FeedbackDetailPopup.vue'
import { adminFeedbackApi } from '@/features/feedback/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { stripHtml } from '@/lib/sanitize'
import { Download, Search, X } from 'lucide-vue-next'
import SearchUserPopup from '@/features/skills/views/SearchUserPopup.vue'
import type { FeedbackItem, FeedbackStatus, FeedbackType } from '@/features/feedback/types'
import type { SearchUserItem } from '@/features/skills/types'
import type { UserListItem } from '@/features/users/types'

const TYPE_OPTIONS = [
  { label: 'Bug', value: 'BUG' },
  { label: 'Suggestion', value: 'SUGGESTION' },
  { label: 'Question', value: 'QUESTION' },
]
const STATUS_OPTIONS = [
  { label: 'New', value: 'NEW' },
  { label: 'In Review', value: 'IN_REVIEW' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Ignored', value: 'IGNORED' },
]
const PAGE_SIZES = [10, 20, 50, 100]

const s = ref({
  type: '' as string,
  status: '' as string,
  content: '' as string,
  requesterId: null as number | null,
  requesterName: '' as string,
  resolvedById: '' as string | number,
  page: 1,
  pageSize: 20,
  loading: false,
  error: '',
  hasSearched: false,
  totalCount: 0,
  items: [] as FeedbackItem[],
})

// ── Requester (search popup, like Creator in Skill Management) ──────────────
const searchUserOpen = ref(false)

function onUserSelected(user: SearchUserItem) {
  s.value.requesterId = user.id
  s.value.requesterName = user.name
}

// ── Handler (admin users only) ───────────────────────────────────────────────
const adminOptions = ref<UserListItem[]>([])

async function loadAdminOptions() {
  try {
    adminOptions.value = await adminFeedbackApi.userOptions(undefined, 'ADMIN_USER')
  } catch {
    // Non-blocking: filters still work, options just won't show
  }
}

const handlerOptions = computed(() =>
  adminOptions.value.map((u) => ({ label: u.userName, value: u.userId }))
)

const typeLabel = (code: string) =>
  code === 'BUG' ? '🐞 Bug' : code === 'SUGGESTION' ? '💡 Suggestion' : code === 'QUESTION' ? '❓ Question' : code
const typeTone = (code: string) =>
  code === 'BUG' ? 'danger' : code === 'SUGGESTION' ? 'info' : 'neutral'
const statusLabel = (code: string) =>
  code === 'NEW' ? 'New' : code === 'IN_REVIEW' ? 'In Review' : code === 'RESOLVED' ? 'Resolved' : code === 'IGNORED' ? 'Ignored' : code
const statusTone = (code: string) =>
  code === 'NEW' ? 'warning' : code === 'IN_REVIEW' ? 'info' : code === 'RESOLVED' ? 'success' : 'neutral'

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const dd = String(d.getDate()).padStart(2, '0')
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${dd}/${mm}/${d.getFullYear()} ${hh}:${mi}`
}

async function runSearch(resetPage = true) {
  if (resetPage) s.value.page = 1
  s.value.loading = true
  s.value.error = ''
  try {
    const result = await adminFeedbackApi.search({
      type: s.value.type || undefined,
      status: s.value.status || undefined,
      content: s.value.content.trim() || undefined,
      requesterId: s.value.requesterId ?? undefined,
      resolvedById: s.value.resolvedById ? Number(s.value.resolvedById) : undefined,
      page: s.value.page,
      pageSize: s.value.pageSize,
    })
    s.value.totalCount = result.totalCount
    s.value.items = result.items
    s.value.hasSearched = true
  } catch (err) {
    s.value.error = toUserError(err).message
    s.value.items = []
    s.value.totalCount = 0
  } finally {
    s.value.loading = false
  }
}

function onSearch() {
  runSearch(true)
}
function onSearchKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') return
  const target = event.target as HTMLElement | null
  // Combobox with open dropdown: Enter picks the highlighted option — do not search.
  if (target?.closest?.('.p-combobox')?.classList.contains('is-open')) return
  onSearch()
}
function onReset() {
  s.value.type = ''
  s.value.status = ''
  s.value.content = ''
  s.value.requesterId = null
  s.value.requesterName = ''
  s.value.resolvedById = ''
  s.value.page = 1
}
function onPage(page: number) {
  s.value.page = page
  runSearch(false)
}
function onPageSize(size: number) {
  s.value.pageSize = size
  runSearch(true)
}

// ── Export ──────────────────────────────────────────────────────────────────
async function onExport() {
  if (!s.value.items.length) {
    toastError('No data to export. Run a search first.')
    return
  }
  try {
    await adminFeedbackApi.exportExcel({
      type: s.value.type || undefined,
      status: s.value.status || undefined,
      content: s.value.content.trim() || undefined,
      requesterId: s.value.requesterId ?? undefined,
      resolvedById: s.value.resolvedById ? Number(s.value.resolvedById) : undefined,
    })
    toastSuccess('Feedback list exported successfully.')
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

const columns = computed(() => [
  { key: 'type', label: 'Type' },
  { key: 'content', label: 'Content' },
  { key: 'user', label: 'Requester' },
  { key: 'resolvedByName', label: 'Handler' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Created' },
])

// ── Detail popup ────────────────────────────────────────────────────────────
const detailId = ref<number | null>(null)
const detailOpen = computed({
  get: () => detailId.value !== null,
  set: (v: boolean) => {
    if (!v) detailId.value = null
  },
})

function onPopupSaved() {
  if (s.value.hasSearched) runSearch(false)
}

onMounted(() => {
  loadAdminOptions()
  runSearch(true)
})
</script>

<template>
  <div class="fbm">
    <!-- Search area -->
    <section class="search p-glass" aria-label="Search" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput
          v-model="s.content"
          label="Content"
          placeholder="Search in content..."
        />
        <PrismCombobox
          v-model="s.type"
          label="Type"
          placeholder="All Types"
          clearable
          :options="TYPE_OPTIONS"
        />
        <PrismCombobox
          v-model="s.status"
          label="Status"
          placeholder="All Statuses"
          clearable
          :options="STATUS_OPTIONS"
        />
        <div class="fbm-field">
          <label class="p-label">Requester</label>
          <div class="fbm-creator">
            <button type="button" class="fbm-creator-search" aria-label="Search requester" @click="searchUserOpen = true">
              <Search :size="16" aria-hidden="true" />
            </button>
            <span v-if="s.requesterName" class="fbm-creator-name">{{ s.requesterName }}</span>
            <span v-else class="fbm-creator-placeholder">Select requester</span>
            <button
              v-if="s.requesterId"
              type="button"
              class="fbm-creator-clear"
              aria-label="Clear requester"
              @click="s.requesterId = null; s.requesterName = ''"
            >
              <X :size="14" aria-hidden="true" />
            </button>
          </div>
        </div>
        <PrismCombobox
          :model-value="s.resolvedById"
          label="Handler"
          placeholder="All Handlers"
          clearable
          :options="handlerOptions"
          @update:model-value="s.resolvedById = $event ?? ''"
        />
      </div>
      <div class="search-actions">
        <PrismButton variant="secondary" type="button" @click="onExport">
          <Download :size="16" aria-hidden="true" /> Download Excel
        </PrismButton>
        <span class="search-actions-spacer" aria-hidden="true"></span>
        <PrismButton type="button" :loading="s.loading" @click="onSearch">Search</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">Reset</PrismButton>
      </div>
    </section>

    <!-- Grid -->
    <section class="grid p-glass" aria-label="Feedback list">
      <PrismEmptyState v-if="s.error" title="Unable to load feedback list.">
        <p class="grid-error-text">{{ s.error }}</p>
        <PrismButton variant="secondary" @click="onSearch">Retry</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <PrismTable
          :columns="columns"
          :rows="s.items"
          row-key="id"
          :loading="s.loading"
          caption="Feedback list"
          empty-text="No feedback found."
        >
          <template #cell-type="{ row }">
            <PrismBadge :tone="typeTone((row as FeedbackItem).type)">
              {{ typeLabel((row as FeedbackItem).type) }}
            </PrismBadge>
          </template>
          <template #cell-content="{ row }">
            <button
              type="button"
              class="fbm-content-link"
              @click="detailId = (row as FeedbackItem).id"
            >
              <span class="fbm-content-text" :title="stripHtml((row as FeedbackItem).content)">
                {{ stripHtml((row as FeedbackItem).content) }}
              </span>
              <span v-if="(row as FeedbackItem).hasScreenshot" class="fbm-shot-icon" title="Has screenshot">📷</span>
            </button>
          </template>
          <template #cell-user="{ row }">
            <div class="fbm-user">
              <span>{{ (row as FeedbackItem).userName ?? '—' }}</span>
              <span class="fbm-user-email">{{ (row as FeedbackItem).userEmail ?? '' }}</span>
            </div>
          </template>
          <template #cell-resolvedByName="{ row }">
            {{ (row as FeedbackItem).resolvedByName ?? '—' }}
          </template>
          <template #cell-status="{ row }">
            <PrismBadge :tone="statusTone((row as FeedbackItem).status)">
              {{ statusLabel((row as FeedbackItem).status) }}
            </PrismBadge>
          </template>
          <template #cell-createdAt="{ row }">
            {{ formatDateTime((row as FeedbackItem).createdAt) }}
          </template>
        </PrismTable>

        <div class="fbm-pager">
          <label class="fbm-pagesize">
            Rows
            <select :value="s.pageSize" @change="onPageSize(Number(($event.target as HTMLSelectElement).value))">
              <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }}</option>
            </select>
          </label>
          <PrismPagination
            :page="s.page"
            :page-size="s.pageSize"
            :total="s.totalCount"
            @update:page="onPage"
          />
        </div>
      </template>
    </section>

    <FeedbackDetailPopup v-model:open="detailOpen" :feedback-id="detailId" @saved="onPopupSaved" />

    <!-- Search User (Requester field, same as Creator in Skill Management) -->
    <SearchUserPopup v-model:open="searchUserOpen" @selected="onUserSelected" />
  </div>
</template>

<style scoped>
.fbm {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
}

.search {
  padding: var(--ds-space-5);
  display: grid;
  gap: var(--ds-space-4);
}
.search-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--ds-space-4);
}
@media (max-width: 1399px) {
  .search-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 1199px) {
  .search-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 899px) {
  .search-grid {
    grid-template-columns: 1fr;
  }
}
.search-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--ds-space-3);
  flex-wrap: wrap;
}
.search-actions-spacer {
  flex: 1;
}
.fbm-field {
  display: grid;
  gap: var(--ds-space-2);
}
.fbm-creator {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 8px 10px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  color: var(--ds-color-text-primary);
}
.fbm-creator-placeholder {
  color: var(--ds-color-text-secondary);
}
.fbm-creator-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fbm-creator-search,
.fbm-creator-clear {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-accent);
  display: inline-flex;
  align-items: center;
}
.fbm-creator-clear {
  color: var(--ds-color-text-secondary);
}

.grid {
  padding: var(--ds-space-4);
}
.fbm-content-link {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  border: none;
  background: none;
  padding: 0;
  color: var(--ds-color-accent);
  font: inherit;
  cursor: pointer;
  text-align: left;
}
.fbm-content-link:hover .fbm-content-text {
  text-decoration: underline;
}
.fbm-content-text {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.fbm-shot-icon {
  flex-shrink: 0;
}
.fbm-user {
  display: grid;
  gap: 2px;
}
.fbm-user-email {
  font-size: 12px;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
}
.grid-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}

.fbm-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
.fbm-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.fbm-pagesize select {
  background: var(--ds-input-background);
  border: 1px solid var(--ds-color-border);
  color: var(--ds-color-text-primary);
  border-radius: var(--ds-radius-sm);
  padding: 6px 8px;
}
</style>
