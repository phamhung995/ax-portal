<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Download, LayoutGrid, Plus, Search, X, GitBranch } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismDatePicker from '@/design-system/components/PrismDatePicker.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import BadgeStack from '@/features/skills/views/BadgeStack.vue'
import SearchUserPopup from '@/features/skills/views/SearchUserPopup.vue'
import AiToolFormPopup from './AiToolFormPopup.vue'
import AiToolDetailPopup from './AiToolDetailPopup.vue'
import { aiToolApi } from '@/features/ai-tools/api'
import { toUserError } from '@/lib/api'
import { stripHtml } from '@/lib/sanitize'
import { toastError, toastSuccess } from '@/lib/toast'
import { useAiToolListStore } from '@/features/ai-tools/store'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { SearchUserItem, TaxonomyOption } from '@/features/skills/types'
import type { AiToolListItem } from '@/features/ai-tools/types'

const list = useAiToolListStore()
const s = list.state
const auth = useAuthStore()
const { t, formatDate } = useI18nStore()

const PAGE_SIZES = [10, 20, 50, 100]

const formOpen = ref(false)
const editingToolId = ref<number | null>(null)
const searchUserOpen = ref(false)
const detailToolId = ref<number | null>(null)
const deleteConfirm = ref(false)
const deleting = ref(false)
const tagInput = ref('')
const downloadingId = ref<number | null>(null)

// ── View tabs (Grid | Process Map) ─────────────────────────────────────────
const activeView = computed({
  get: () => s.view,
  set: (v: 'grid' | 'map') => {
    s.view = v
  },
})

// ── Taxonomy (categories & stages — shared with Skill Management) ─────────
const categories = ref<TaxonomyOption[]>([])
const stages = ref<TaxonomyOption[]>([])

const categoryOptions = computed(() =>
  categories.value.map((c) => ({ label: c.name, value: c.id }))
)
const stageOptions = computed(() =>
  stages.value.map((st) => ({ label: st.name, value: st.id }))
)

async function loadTaxonomy() {
  try {
    const taxonomy = await aiToolApi.taxonomy()
    categories.value = taxonomy.categories
    stages.value = taxonomy.stages
  } catch {
    // non-fatal: filters/forms degrade gracefully
  }
}

function switchView(view: 'grid' | 'map') {
  activeView.value = view
}

function onStageClick(stage: TaxonomyOption) {
  if (s.stageId === stage.id) {
    s.stageId = null
  } else {
    s.stageId = stage.id
  }
  runSearch(true)
}

/** Total tools mapped to a stage under the current search criteria (server-side, stageId excluded). */
function stageCount(stageId: number): number {
  return s.stageCounts[stageId] ?? 0
}

const dateRangeError = computed(() => {
  if (s.createdDateFrom && s.createdDateTo && s.createdDateFrom > s.createdDateTo) {
    return t('app.common.dateRangeError')
  }
  return ''
})

// ── Tag search input (multi, Enter to add) ─────────────────────────────────
function addSearchTag() {
  const raw = tagInput.value.trim().replace(/^#/, '')
  tagInput.value = ''
  if (!raw) return
  if (!s.tags.includes(raw)) s.tags = [...s.tags, raw]
}
function onTagKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    addSearchTag()
  } else if (event.key === 'Backspace' && !tagInput.value && s.tags.length) {
    s.tags = s.tags.slice(0, -1)
  }
}
function removeSearchTag(index: number) {
  s.tags = s.tags.filter((_, i) => i !== index)
}

// ── Selection (only rows the current user may edit/delete) ────────────────
const editableItems = computed(() => s.items.filter((u) => u.canEdit))
const selectedIds = ref<Set<number>>(new Set())
const allSelected = computed(
  () => editableItems.value.length > 0 && editableItems.value.every((u) => selectedIds.value.has(u.id))
)
function toggleAll() {
  selectedIds.value = allSelected.value ? new Set() : new Set(editableItems.value.map((u) => u.id))
}
function toggleRow(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

// ── Search ─────────────────────────────────────────────────────────────────
function buildQuery() {
  return {
    name: s.name || undefined,
    tags: s.tags.length ? s.tags : undefined,
    uploaderId: s.uploaderId ?? undefined,
    createdDateFrom: s.createdDateFrom || undefined,
    createdDateTo: s.createdDateTo || undefined,
    categoryIds: s.categoryIds.length ? s.categoryIds : undefined,
    stageId: s.stageId ?? undefined,
    page: s.page,
    pageSize: s.pageSize,
    sortBy: s.sortBy,
    sortDirection: s.sortDirection,
  }
}

async function runSearch(resetPage = true) {
  if (dateRangeError.value) return
  if (resetPage) s.page = 1
  s.loading = true
  s.error = ''
  selectedIds.value = new Set()
  try {
    const result = await aiToolApi.search(buildQuery())
    list.applyResult(result, auth.user?.userId ?? -1, result.stageCounts)
  } catch (err) {
    s.error = toUserError(err).message
    s.items = []
    s.totalCount = 0
  } finally {
    s.loading = false
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
  // Date picker: Enter opens the calendar — do not search mid-selection.
  if (target?.closest?.('.p-date')) return
  onSearch()
}
function onReset() {
  list.clear()
  s.pageSize = 20
  tagInput.value = ''
}
function onPage(page: number) {
  s.page = page
  runSearch(false)
}
function onPageSize(size: number) {
  s.pageSize = size
  runSearch(true)
}

// ── Sorting (clickable headers) ────────────────────────────────────────────
function onSort(field: 'name' | 'uploader' | 'createdAt' | 'downloadCount') {
  if (s.sortBy === field) {
    s.sortDirection = s.sortDirection === 'ASC' ? 'DESC' : 'ASC'
  } else {
    s.sortBy = field
    s.sortDirection = field === 'name' || field === 'uploader' ? 'ASC' : 'DESC'
  }
  runSearch(true)
}
function sortClass(field: string) {
  if (s.sortBy !== field) return ''
  return s.sortDirection === 'ASC' ? 'asc' : 'desc'
}

// ── Detail open (300ms delay + double-click cancel) ────────────────────────
let detailTimer: ReturnType<typeof setTimeout> | null = null
function openDetail(id: number) {
  detailToolId.value = id
}
function onNameClick(id: number) {
  if (detailTimer) clearTimeout(detailTimer)
  detailTimer = setTimeout(() => openDetail(id), 300)
}
function onGridDblClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  const rowEl = target.closest('tr')
  if (!rowEl) return
  const tbody = rowEl.parentElement
  if (!tbody) return
  const index = Array.prototype.indexOf.call(tbody.children, rowEl)
  const item = s.items[index]
  if (item) {
    if (detailTimer) clearTimeout(detailTimer)
    detailTimer = null
    openDetail(item.id)
  }
}

// ── Search User (Uploader field) ───────────────────────────────────────────
function onUserSelected(user: SearchUserItem) {
  s.uploaderId = user.id
  s.uploaderName = user.name
}

// ── Download (grid button) ─────────────────────────────────────────────────
async function onDownload(row: AiToolListItem) {
  if (!row.fileId) {
    toastError(t('app.aiTools.noFileToast'))
    return
  }
  downloadingId.value = row.id
  try {
    await aiToolApi.download(row.id, row.fileId)
    toastSuccess(t('app.common.downloadStarted'))
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    downloadingId.value = null
  }
}

// ── Bulk delete (admin only) ───────────────────────────────────────────────
async function onDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  deleteConfirm.value = true
}

async function confirmDelete() {
  const ids = [...selectedIds.value]
  deleteConfirm.value = false
  deleting.value = true
  try {
    const result = await aiToolApi.deleteBatch(ids)
    if (result.skippedNames.length) {
      toastError(t('app.aiTools.deletedSkippedToast', {
        n: result.deleted,
        m: result.skippedNames.length,
        names: result.skippedNames.join(', '),
      }))
    } else {
      toastSuccess(t('app.aiTools.deletedToast', { n: result.deleted }))
    }
    selectedIds.value = new Set()
    runSearch(false)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    deleting.value = false
  }
}

// ── Add / Edit ─────────────────────────────────────────────────────────────
function onAdd() {
  editingToolId.value = null
  formOpen.value = true
}
function onEdit(id: number) {
  editingToolId.value = id
  formOpen.value = true
}
function onSaved() {
  loadTaxonomy()
  runSearch(false)
}

const columns = computed(() => [
  { key: 'select', label: '' },
  { key: 'name', label: t('app.aiTools.name') },
  { key: 'description', label: t('app.common.description') },
  { key: 'categories', label: t('app.common.category') },
  { key: 'stages', label: t('app.common.processStage') },
  { key: 'tags', label: t('app.common.tags') },
  { key: 'uploaderName', label: t('app.aiTools.uploader') },
  { key: 'createdAt', label: t('app.users.colCreatedDate') },
  { key: 'downloadCount', label: t('app.aiTools.colDownloads') },
  { key: 'download', label: t('app.common.download') },
])

onMounted(() => {
  loadTaxonomy()
  // Results cached from a different user (e.g. after logout + login as
  // another account) are not valid — clear and re-run the search.
  if (s.hasSearched && s.searchedBy !== auth.user?.userId) {
    list.clear()
    runSearch(true)
    return
  }
  if (s.hasSearched && s.needsRefresh) {
    runSearch(false)
  }
})
</script>

<template>
  <div class="at">
    <!-- View tabs: Grid | Process Map -->
    <div class="sm-view-tabs" role="tablist" :aria-label="t('app.common.view')">
      <button
        type="button"
        role="tab"
        class="sm-view-tab"
        :class="{ 'is-active': activeView === 'grid' }"
        :aria-selected="activeView === 'grid'"
        @click="switchView('grid')"
      >
        <LayoutGrid :size="15" aria-hidden="true" /> {{ t('app.common.grid') }}
      </button>
      <button
        type="button"
        role="tab"
        class="sm-view-tab"
        :class="{ 'is-active': activeView === 'map' }"
        :aria-selected="activeView === 'map'"
        @click="switchView('map')"
      >
        <GitBranch :size="15" aria-hidden="true" /> {{ t('app.common.processMap') }}
      </button>
    </div>

    <!-- Search area -->
    <section class="search p-glass" :aria-label="t('app.common.searchAria')" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput v-model="s.name" :label="t('app.aiTools.name')" :placeholder="t('app.common.containsPh')" />
        <div class="sm-field">
          <label class="p-label">{{ t('app.common.tag') }}</label>
          <div class="sm-tags" role="group" :aria-label="t('app.common.tags')">
            <span v-for="(tag, i) in s.tags" :key="tag" class="sm-tag">
              #{{ tag }}
              <button type="button" class="sm-tag-remove" :aria-label="t('app.common.removeTag')" @click="removeSearchTag(i)">×</button>
            </span>
            <input
              v-model="tagInput"
              class="sm-tag-input"
              :placeholder="t('app.common.typeTagPh')"
              @keydown="onTagKeydown"
            />
          </div>
        </div>
        <div class="sm-field">
          <label class="p-label">{{ t('app.aiTools.uploader') }}</label>
          <div class="sm-creator">
            <button type="button" class="sm-creator-search" :aria-label="t('app.common.searchUserAria')" @click="searchUserOpen = true">
              <Search :size="16" aria-hidden="true" />
            </button>
            <span v-if="s.uploaderName" class="sm-creator-name">{{ s.uploaderName }}</span>
            <span v-else class="sm-creator-placeholder">{{ t('app.aiTools.selectUploader') }}</span>
            <button
              v-if="s.uploaderId"
              type="button"
              class="sm-creator-clear"
              :aria-label="t('app.aiTools.clearUploader')"
              @click="s.uploaderId = null; s.uploaderName = ''"
            >
              <X :size="14" aria-hidden="true" />
            </button>
          </div>
        </div>
        <PrismCombobox v-model="s.categoryIds" :label="t('app.common.category')" :placeholder="t('app.common.allCategories')" multiple clearable :options="categoryOptions" />
        <PrismCombobox :model-value="s.stageId ?? ''" :label="t('app.common.processStage')" :placeholder="t('app.common.allStages')" clearable :options="stageOptions" @update:model-value="s.stageId = $event ?? null; runSearch(true)" />
        <PrismDatePicker v-model="s.createdDateFrom" :label="t('app.common.createdFrom')" :placeholder="t('app.common.selectDatePh')" />
        <PrismDatePicker v-model="s.createdDateTo" :label="t('app.common.createdTo')" :placeholder="t('app.common.selectDatePh')" />
      </div>
      <p v-if="dateRangeError" class="p-field-error" role="alert">{{ dateRangeError }}</p>
      <div class="search-actions">
        <PrismButton type="button" @click="onAdd">
          <Plus :size="16" aria-hidden="true" /> {{ t('app.aiTools.addTool') }}
        </PrismButton>
        <PrismButton
          v-if="selectedIds.size"
          variant="danger"
          type="button"
          @click="onDelete"
        >
          {{ t('app.aiTools.deleteBtn', { n: selectedIds.size }) }}
        </PrismButton>
        <span class="search-actions-spacer" aria-hidden="true"></span>
        <PrismButton type="button" :loading="s.loading" @click="onSearch">{{ t('app.common.search') }}</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">{{ t('app.common.reset') }}</PrismButton>
      </div>
    </section>

    <!-- Process map pipeline (map view only) -->
    <section v-if="activeView === 'map'" class="map-panel p-glass" :aria-label="t('app.common.processMapAria')">
      <div class="map-pipeline">
        <button
          v-for="(stage, i) in stages"
          :key="stage.id"
          type="button"
          class="map-stage"
          :class="{ 'is-active': s.stageId === stage.id, 'is-gap': stageCount(stage.id) === 0 }"
          @click="onStageClick(stage)"
        >
          <span class="map-stage-head">
            <span class="map-stage-num">{{ i + 1 }}</span>
            <span class="map-stage-name">{{ stage.name }}</span>
          </span>
          <span v-if="stage.description" class="map-stage-desc">{{ stage.description }}</span>
          <span class="map-stage-count">
            <span class="map-stage-count-num">{{ stageCount(stage.id) }}</span>
            {{ t('app.aiTools.countSuffix', { n: stageCount(stage.id) }) }}
          </span>
        </button>
      </div>
    </section>

    <!-- Grid (table view) -->
    <section v-if="activeView === 'grid'" class="grid p-glass" :aria-label="t('app.aiTools.listAria')">
      <PrismEmptyState
        v-if="!s.hasSearched && !s.loading && !s.error"
        :title="t('app.common.noData')"
        :description="t('app.aiTools.emptyDesc')"
      />
      <PrismEmptyState v-else-if="s.error" :title="t('app.aiTools.loadError')">
        <p class="grid-error-text">{{ s.error }}</p>
        <PrismButton variant="secondary" @click="onSearch">{{ t('app.common.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <div @dblclick="onGridDblClick">
        <PrismTable
          :columns="columns"
          :rows="s.items"
          row-key="id"
          :loading="s.loading"
          :caption="t('app.common.total', { n: s.totalCount })"
          :empty-text="t('app.common.noDataFound')"
        >
          <template #header-select>
            <input
              type="checkbox"
              :checked="allSelected"
              :aria-label="t('app.aiTools.selectEditableRows')"
              @change="toggleAll()"
            />
          </template>
          <template #cell-select="{ row }">
            <input
              type="checkbox"
              :disabled="!(row as AiToolListItem).canEdit"
              :checked="selectedIds.has((row as AiToolListItem).id)"
              :aria-label="t('app.common.selectRow', { name: (row as AiToolListItem).name })"
              @change="toggleRow((row as AiToolListItem).id)"
            />
          </template>
          <template #header-name>
            <span class="sortable" :class="sortClass('name')" @click="onSort('name')">{{ t('app.aiTools.name') }} <span class="sort-ind" aria-hidden="true"></span></span>
          </template>
          <template #header-uploaderName>
            <span class="sortable" :class="sortClass('uploader')" @click="onSort('uploader')">{{ t('app.aiTools.uploader') }} <span class="sort-ind" aria-hidden="true"></span></span>
          </template>
          <template #header-createdAt>
            <span class="sortable" :class="sortClass('createdAt')" @click="onSort('createdAt')">{{ t('app.users.colCreatedDate') }} <span class="sort-ind" aria-hidden="true"></span></span>
          </template>
          <template #header-downloadCount>
            <span class="sortable" :class="sortClass('downloadCount')" @click="onSort('downloadCount')">{{ t('app.aiTools.colDownloads') }} <span class="sort-ind" aria-hidden="true"></span></span>
          </template>
          <template #cell-name="{ row }">
            <button
              type="button"
              class="at-name-link"
              :title="(row as AiToolListItem).name"
              @click="onNameClick((row as AiToolListItem).id)"
            >
              {{ (row as AiToolListItem).name }}
            </button>
          </template>
          <template #cell-description="{ row }">
            <span class="at-truncate" :title="stripHtml((row as AiToolListItem).description ?? '')">
              {{ stripHtml((row as AiToolListItem).description ?? '') || '—' }}
            </span>
          </template>
          <template #cell-categories="{ row }">
            <BadgeStack :items="(row as AiToolListItem).categories" tone="category" />
          </template>
          <template #cell-stages="{ row }">
            <BadgeStack :items="(row as AiToolListItem).stages" tone="stage" />
          </template>
          <template #cell-tags="{ row }">
            <span v-for="tag in (row as AiToolListItem).tags" :key="tag" class="at-tag-pill">#{{ tag }}</span>
            <span v-if="!(row as AiToolListItem).tags.length" class="at-empty-cell">—</span>
          </template>
          <template #cell-uploaderName="{ row }">
            <span class="at-truncate" :title="(row as AiToolListItem).uploaderName ?? ''">
              {{ (row as AiToolListItem).uploaderName ?? '—' }}
            </span>
          </template>
          <template #cell-createdAt="{ row }">
            {{ formatDate((row as AiToolListItem).createdAt) }}
          </template>
          <template #cell-downloadCount="{ row }">
            {{ (row as AiToolListItem).downloadCount }}
          </template>
          <template #cell-download="{ row }">
            <PrismButton
              class="at-dl-btn"
              type="button"
              :loading="downloadingId === (row as AiToolListItem).id"
              @click="onDownload(row as AiToolListItem)"
            >
              <Download :size="14" aria-hidden="true" /> {{ t('app.common.download') }}
            </PrismButton>
          </template>
        </PrismTable>
        </div>

        <div class="sm-pager">
          <div class="sm-pagesize">
            <span class="sm-pagesize-label">{{ t('app.common.rows') }}</span>
            <PrismCombobox
              :model-value="s.pageSize"
              readonly
              :aria-label="t('app.common.rowsPerPage')"
              :options="PAGE_SIZES.map((size) => ({ value: size, label: String(size) }))"
              @update:model-value="onPageSize(Number($event))"
            />
          </div>
          <PrismPagination
            :page="s.page"
            :page-size="s.pageSize"
            :total="s.totalCount"
            @update:page="onPage"
          />
        </div>
      </template>
    </section>

    <!-- Card grid (Process Map view) -->
    <section v-else class="grid p-glass" :aria-label="t('app.aiTools.cardsAria')">
      <PrismEmptyState
        v-if="!s.hasSearched && !s.loading && !s.error"
        :title="t('app.common.noData')"
        :description="t('app.aiTools.emptyDesc')"
      />
      <PrismEmptyState v-else-if="s.error" :title="t('app.aiTools.loadError')">
        <p class="grid-error-text">{{ s.error }}</p>
        <PrismButton variant="secondary" @click="onSearch">{{ t('app.common.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <div v-if="s.items.length" class="card-grid">
          <div
            v-for="item in s.items"
            :key="item.id"
            class="skill-card"
            role="button"
            tabindex="0"
            @click="openDetail(item.id)"
            @keydown.enter="openDetail(item.id)"
          >
            <div class="sc-top">
              <span class="sc-name" :title="item.name">{{ item.name }}</span>
            </div>
            <p class="sc-desc" :title="stripHtml(item.description ?? '')">
              {{ stripHtml(item.description ?? '') || '—' }}
            </p>
            <div class="sc-badges">
              <BadgeStack :items="item.categories" tone="category" />
              <BadgeStack :items="item.stages" tone="stage" />
            </div>
            <div class="sc-foot">
              <span>{{ item.uploaderName ?? '—' }}</span>
              <span aria-hidden="true">·</span>
              <span>{{ t('app.common.downloads', { n: item.downloadCount }) }}</span>
            </div>
          </div>
        </div>
        <PrismEmptyState v-else :title="t('app.common.noData')" :description="t('app.aiTools.noMatchDesc')" />

        <div class="sm-pager">
          <div class="sm-pagesize">
            <span class="sm-pagesize-label">{{ t('app.common.rows') }}</span>
            <PrismCombobox
              :model-value="s.pageSize"
              readonly
              :aria-label="t('app.common.rowsPerPage')"
              :options="PAGE_SIZES.map((size) => ({ value: size, label: String(size) }))"
              @update:model-value="onPageSize(Number($event))"
            />
          </div>
          <PrismPagination
            :page="s.page"
            :page-size="s.pageSize"
            :total="s.totalCount"
            @update:page="onPage"
          />
        </div>
      </template>
    </section>

    <!-- Add / Edit Tool -->
    <AiToolFormPopup
      v-model:open="formOpen"
      :tool-id="editingToolId"
      @saved="onSaved"
    />

    <!-- Search User (Uploader field) -->
    <SearchUserPopup v-model:open="searchUserOpen" @selected="onUserSelected" />

    <!-- Tool Detail -->
    <AiToolDetailPopup
      :tool-id="detailToolId"
      @closed="detailToolId = null"
      @edit="onEdit"
      @changed="runSearch(false)"
    />

    <!-- Delete confirm -->
    <PrismDialog
      v-model="deleteConfirm"
      :title="t('app.aiTools.deleteTitle')"
      size="sm"
      :close-on-overlay="false"
    >
      <p class="at-confirm-text">
        {{ t('app.aiTools.deleteDesc', { n: selectedIds.size }) }}
      </p>
      <template #footer>
        <PrismButton variant="secondary" @click="deleteConfirm = false">{{ t('app.common.cancel') }}</PrismButton>
        <PrismButton variant="danger" :loading="deleting" @click="confirmDelete">{{ t('app.common.delete') }}</PrismButton>
      </template>
    </PrismDialog>
  </div>
</template>

<style scoped>
.at {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
  /* Pull the tab bar up against the app header (main padding is 16px) */
  margin-top: calc(-1 * var(--ds-space-2));
}

.search {
  padding: var(--ds-space-4);
  display: grid;
  gap: var(--ds-space-3);
}
.search-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--ds-space-3);
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
.sm-field {
  display: grid;
  gap: var(--ds-space-1);
  font-size: 13px;
  min-width: 0;
  align-content: start;
}
.sm-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 0 var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  min-height: 36px;
  box-sizing: border-box;
  font-size: 13px;
  transition: border-color var(--ds-duration-fast), background var(--ds-duration-fast), box-shadow var(--ds-duration-fast);
}
@media (hover: hover) {
  .sm-tags:hover:not(:focus-within),
  .sm-creator:hover {
    border-color: var(--ds-color-border-strong);
    background: var(--ds-input-hover-background);
  }
}
.sm-tags:focus-within,
.sm-creator:focus-within {
  border-color: var(--ds-color-accent);
  background: var(--ds-input-focus-background);
  box-shadow: 0 0 0 3px rgb(94 106 210 / .10), 0 0 14px rgb(94 106 210 / .05);
}
.sm-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 1px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(94 106 210 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 12px;
}
.sm-tag-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
@media (hover: hover) { .sm-tag-remove:hover { color: var(--ds-color-danger); } }
.sm-tag-input {
  flex: 1;
  min-width: 120px;
  min-height: 34px;
  border: none;
  background: none;
  color: var(--ds-color-text-primary);
  font: inherit;
  padding: 2px 0;
  outline: none;
}
.sm-tag-input::placeholder {
  color: var(--ds-color-text-muted);
}
.sm-creator {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 0 10px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  color: var(--ds-color-text-primary);
  min-height: 36px;
  box-sizing: border-box;
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--ds-duration-fast), background var(--ds-duration-fast), box-shadow var(--ds-duration-fast);
}
.sm-creator-placeholder {
  color: var(--ds-color-text-secondary);
}
.sm-creator-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sm-creator-search,
.sm-creator-clear {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-accent);
  display: inline-flex;
  align-items: center;
}
.sm-creator-clear {
  color: var(--ds-color-text-secondary);
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

.grid {
  padding: var(--ds-space-4);
}
.at-name-link {
  border: none;
  background: none;
  padding: 0;
  color: var(--ds-color-accent);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.at-name-link:hover {
  text-decoration: underline;
}
.at-truncate {
  display: inline-block;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.at-tag-pill {
  display: inline-block;
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  background: rgb(9 20 45 / .5);
  border: 1px solid var(--ds-color-border);
  border-radius: 999px;
  padding: 1px 8px;
  margin: 1px 2px 1px 0;
}
.at-empty-cell {
  color: var(--ds-color-text-muted);
}
.at-dl-btn {
  min-height: 34px;
  padding: 0 var(--ds-space-3);
  font-size: 14px;
}
.grid-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}
.at-confirm-text {
  color: var(--ds-color-text-primary);
}

.sm-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
.sm-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-shrink: 0;
}
.sm-pagesize-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ds-color-text-secondary);
}
.sm-pagesize :deep(.p-field) {
  gap: 0;
}
.sm-pagesize :deep(.p-field > label:empty) {
  display: none;
}
.sm-pagesize :deep(.p-combobox) {
  width: 84px;
  font-size: 12px;
}
.sm-pagesize :deep(.p-input-wrap),
.sm-pagesize :deep(.p-input-wrap) input {
  min-height: 30px;
  font-size: 12px;
  padding: 0 var(--ds-space-2);
}

/* ── View tabs (Grid | Process Map) ─────────────────────────────────────── */
.sm-view-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--ds-color-border);
}
.sm-view-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 18px;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 14px;
}
.sm-view-tab:hover {
  color: var(--ds-color-text-primary);
}
.sm-view-tab.is-active {
  color: var(--ds-color-accent);
  border-bottom-color: var(--ds-color-accent);
}

/* ── Sortable headers ───────────────────────────────────────────────────── */
.sortable {
  cursor: pointer;
  user-select: none;
}
.sortable:hover {
  color: var(--ds-color-text-primary);
}
.sortable .sort-ind::after {
  content: '↕';
  opacity: .4;
  margin-left: 4px;
}
.sortable.asc .sort-ind::after {
  content: '↑';
  opacity: 1;
  color: var(--ds-color-accent);
}
.sortable.desc .sort-ind::after {
  content: '↓';
  opacity: 1;
  color: var(--ds-color-accent);
}

/* ── Process map pipeline ───────────────────────────────────────────────── */
.map-panel {
  padding: var(--ds-space-5);
}
.map-pipeline {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}
.map-stage {
  display: grid;
  gap: 4px;
  justify-items: start;
  text-align: left;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: var(--ds-input-background);
  padding: 14px 12px;
  cursor: pointer;
  color: var(--ds-color-text-primary);
  font: inherit;
  transition: border-color var(--ds-duration-fast) var(--ds-ease),
    background var(--ds-duration-fast) var(--ds-ease);
}
.map-stage:hover {
  border-color: var(--ds-color-border-strong);
}
.map-stage.is-active {
  border-color: var(--ds-color-accent);
  background: rgb(94 106 210 / .1);
  box-shadow: var(--ds-shadow-glow);
}
.map-stage.is-gap {
  border-style: dashed;
}
.map-stage-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.map-stage-num {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 20px;
  font-weight: 700;
  color: var(--ds-color-accent);
  border: 1px solid var(--ds-color-border-strong);
}
.map-stage.is-active .map-stage-num {
  background: var(--ds-color-accent);
  color: var(--ds-color-space-950);
}
.map-stage-name {
  font-weight: 600;
  font-size: 20px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.map-stage-desc {
  font-size: 14px;
  line-height: 1.4;
  color: var(--ds-color-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}
.map-stage-count {
  font-size: 14px;
  color: var(--ds-color-text-muted);
}
.map-stage-count-num {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  color: var(--ds-color-text-primary);
}
.map-stage.is-active .map-stage-count-num {
  color: var(--ds-color-accent);
}
.map-stage.is-gap .map-stage-count {
  color: var(--ds-color-warning);
}
.map-stage.is-gap .map-stage-count-num {
  color: var(--ds-color-warning);
}

/* ── Card grid (Process Map view) ───────────────────────────────────────── */
.card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--ds-space-4);
}
@media (max-width: 1199px) { .card-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 799px) { .card-grid { grid-template-columns: 1fr; } }
.skill-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: var(--ds-input-background);
  padding: var(--ds-space-4);
  cursor: pointer;
  transition: border-color var(--ds-duration-fast) var(--ds-ease),
    transform var(--ds-duration-fast) var(--ds-ease);
}
.skill-card:hover {
  border-color: var(--ds-color-border-strong);
  transform: translateY(-2px);
}
.sc-top {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.sc-name {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  color: var(--ds-color-accent);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sc-desc {
  margin: 0;
  font-size: 12px;
  color: var(--ds-color-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sc-badges {
  display: grid;
  gap: 6px;
}
.sc-badges .bs {
  max-width: none;
}
.sc-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ds-color-text-muted);
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid rgb(255 255 255 / .08);
}
</style>
