<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Download, Plus, Search, X, Bell, LayoutGrid, GitBranch, SlidersHorizontal } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismDatePicker from '@/design-system/components/PrismDatePicker.vue'
import PrismRadioGroup from '@/design-system/components/PrismRadioGroup.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import AddSkillPopup from './AddSkillPopup.vue'
import SkillDetailPopup from './SkillDetailPopup.vue'
import SearchUserPopup from './SearchUserPopup.vue'
import ManageTaxonomyPopup from './ManageTaxonomyPopup.vue'
import BadgeStack from './BadgeStack.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { stripHtml } from '@/lib/sanitize'
import { toastError, toastSuccess } from '@/lib/toast'
import { useSkillListStore } from '@/features/skills/store'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { SearchUserItem, SkillListItem, TaxonomyOption } from '@/features/skills/types'

const list = useSkillListStore()
const s = list.state
const auth = useAuthStore()
const route = useRoute()
const { t, formatDate } = useI18nStore()

const STATUS_OPTIONS = computed(() => [
  { label: t('app.common.statusDraft'), value: 'DRAFT' },
  { label: t('app.common.statusPending'), value: 'PENDING_APPROVAL' },
  { label: t('app.common.statusApproved'), value: 'APPROVED' },
  { label: t('app.common.statusRejected'), value: 'REJECTED' },
])
const SCOPE_OPTIONS = computed(() => [
  { label: t('app.skill.scopePrivate'), value: 'PRIVATE' },
  { label: t('app.skill.scopeProject'), value: 'PROJECT' },
  { label: t('app.skill.scopePart'), value: 'PART' },
  { label: t('app.skill.scopeGroup'), value: 'GROUP' },
  { label: t('app.skill.scopePublic'), value: 'PUBLIC' },
])
const PAGE_SIZES = [10, 20, 50, 100]

const addOpen = ref(false)
const searchUserOpen = ref(false)
const manageOpen = ref(false)
const detailSkillId = ref<number | null>(null)
const deleteConfirm = ref(false)
const deleteDeniedOpen = ref(false)
const deleteDenied = ref<{ id: number; name: string; creatorName: string | null }[]>([])
const deleting = ref(false)
const tagInput = ref('')

// ── View tabs (Grid | Process Map) ─────────────────────────────────────────
// The active tab and selected stage live in the store so they survive
// navigation away and back (same cached-data behavior as the search state).
const activeView = computed({
  get: () => s.view,
  set: (v: 'grid' | 'map') => {
    s.view = v
  },
})

// ── Taxonomy (categories & stages) ─────────────────────────────────────────
const categories = ref<TaxonomyOption[]>([])
const stages = ref<TaxonomyOption[]>([])

const categoryOptions = computed(() =>
  categories.value.map((c) => ({ label: c.name, value: c.id }))
)
const stageOptions = computed(() =>
  stages.value.map((st) => ({ label: st.name, value: st.id }))
)

// ── System / Sub System filters ────────────────────────────────────────────
const filterSystems = ref<{ systemKey: string; systemName: string }[]>([])
const filterSubSystems = ref<{ systemKey: string; subSystemCode: string; subSystemName: string }[]>([])

const systemFilterOptions = computed(() =>
  filterSystems.value.map((x) => ({ label: x.systemName, value: x.systemKey }))
)
const subSystemFilterOptions = computed(() => {
  const list = s.systemKey
    ? filterSubSystems.value.filter((x) => x.systemKey === s.systemKey)
    : filterSubSystems.value
  return list.map((x) => ({ label: x.subSystemName, value: x.subSystemCode }))
})

watch(
  () => s.systemKey,
  (v, old) => {
    if (!v || v !== old) s.subSystemCode = ''
  }
)

async function loadTaxonomy() {
  try {
    const taxonomy = await skillApi.taxonomy()
    categories.value = taxonomy.categories
    stages.value = taxonomy.stages
  } catch {
    // non-fatal: filters/forms degrade gracefully
  }
}

async function loadFilterOptions() {
  try {
    const f = await skillApi.filterOptions()
    filterSystems.value = f.systems
    filterSubSystems.value = f.subSystems
  } catch {
    // non-fatal
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

/** Total skills mapped to a stage under the current search criteria (server-side). */
function stageCount(stageId: number): number {
  return s.stageCounts[stageId] ?? 0
}

const statusLabel = (code: string) =>
  code === 'PENDING_APPROVAL' ? t('app.common.statusPending') : code === 'DRAFT' ? t('app.common.statusDraft') : code === 'APPROVED' ? t('app.common.statusApproved') : code === 'REJECTED' ? t('app.common.statusRejected') : code
const statusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'
// A skill with a pending version update still serves its approved version to
// other users — show it as Approved for them (creator/admin see the real status).
// Same for a pending SHARE/SCOPE change (pending_reason set): the skill stays
// live for its current audience, so it displays as Approved to others.
const displayStatus = (row: SkillListItem): string =>
  (row.hasPendingVersion || row.pendingReason != null)
    && row.creatorId !== auth.user?.userId && !auth.isAdmin && !row.isCowork
    ? 'APPROVED' : row.status
const scopeLabel = (code: string) =>
  code === 'PRIVATE' ? t('app.skill.scopePrivate') : code === 'PROJECT' ? t('app.skill.scopeProject') : code === 'PART' ? t('app.skill.scopePart') : code === 'GROUP' ? t('app.skill.scopeGroup') : code === 'PUBLIC' ? t('app.skill.scopePublic') : code

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

// ── Selection ───────────────────────────────────────────────────────────────
const selectedIds = ref<Set<number>>(new Set())
const allSelected = computed(
  () => s.items.length > 0 && s.items.every((u) => selectedIds.value.has(u.id))
)
function toggleAll() {
  selectedIds.value = allSelected.value ? new Set() : new Set(s.items.map((u) => u.id))
}
function toggleRow(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

// ── Search ──────────────────────────────────────────────────────────────────
function buildQuery() {
  return {
    skillName: s.skillName || undefined,
    description: s.description || undefined,
    tags: s.tags.length ? s.tags : undefined,
    creatorId: s.creatorId ?? undefined,
    createdDateFrom: s.createdDateFrom || undefined,
    createdDateTo: s.createdDateTo || undefined,
    statuses: s.statuses.length ? s.statuses : undefined,
    scopes: s.scopes.length ? s.scopes : undefined,
    categoryIds: s.categoryIds.length ? s.categoryIds : undefined,
    stageId: s.stageId ?? undefined,
    subscribed: s.subscribed === 'ALL' ? undefined : s.subscribed,
    systemKey: s.systemKey || undefined,
    subSystemCode: s.subSystemCode || undefined,
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
    const result = await skillApi.search(buildQuery())
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

// ── Detail open (300ms delay + double-click cancel) ────────────────────────
let detailTimer: ReturnType<typeof setTimeout> | null = null
function openDetail(id: number) {
  detailSkillId.value = id
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
function onRowDblClick(row: SkillListItem) {
  if (detailTimer) clearTimeout(detailTimer)
  detailTimer = null
  openDetail(row.id)
}

// ── Search User (Creator field) ─────────────────────────────────────────────
function onUserSelected(user: SearchUserItem) {
  s.creatorId = user.id
  s.creatorName = user.name
}

// ── Subscribe (grid bell) ───────────────────────────────────────────────────
function canSubscribe(row: SkillListItem): boolean {
  return (row.status === 'APPROVED' || row.hasPendingVersion) && row.creatorId !== auth.user?.userId
}

async function toggleSubscribe(row: SkillListItem) {
  if (!canSubscribe(row)) return
  const wasSubscribed = row.subscribed
  try {
    if (wasSubscribed) {
      await skillApi.unsubscribe(row.id)
      row.subscribed = false
      toastSuccess(t('app.skills.unsubscribedToast'))
    } else {
      await skillApi.subscribe(row.id)
      row.subscribed = true
      toastSuccess(t('app.skills.subscribedToast'))
    }
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

// ── Export ──────────────────────────────────────────────────────────────────
async function onExport() {
  if (!s.items.length) {
    toastError(t('app.common.noDataToExport'))
    return
  }
  try {
    // Export only the data currently shown on screen:
    // the selected rows if any, otherwise the rows of the current page.
    const ids = selectedIds.value.size
      ? [...selectedIds.value]
      : s.items.map((i) => i.id)
    await skillApi.exportExcel(buildQuery(), ids)
    toastSuccess(t('app.skills.exportedToast'))
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

// ── Bulk delete ─────────────────────────────────────────────────────────────
async function onDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    const check = await skillApi.checkDelete(ids)
    if (!check.canDelete) {
      deleteDenied.value = check.deniedSkills
      deleteDeniedOpen.value = true
      return
    }
    deleteDenied.value = []
    deleteConfirm.value = true
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

async function confirmDelete() {
  deleteConfirm.value = false
  const ids = [...selectedIds.value]
  deleting.value = true
  try {
    await skillApi.deleteBatch(ids)
    toastSuccess(t('app.skills.deletedToast', { n: ids.length }))
    selectedIds.value = new Set()
    runSearch(false)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    deleting.value = false
  }
}

// ── Add ─────────────────────────────────────────────────────────────────────
function onSaved() {
  loadTaxonomy()
  runSearch(false)
}

function onTaxonomyChanged() {
  loadTaxonomy()
  runSearch(false)
}

const columns = computed(() => [
  { key: 'select', label: '' },
  { key: 'name', label: t('app.skills.colName') },
  { key: 'description', label: t('app.common.description') },
  { key: 'categories', label: t('app.common.category') },
  { key: 'stages', label: t('app.common.processStage') },
  { key: 'creatorName', label: t('app.skills.colCreator') },
  { key: 'createdAt', label: t('app.common.createdDate') },
  { key: 'status', label: t('app.common.status') },
  { key: 'viewCount', label: t('app.skills.colViews') },
  { key: 'downloadCount', label: t('app.skills.colDownloads') },
  { key: 'rating', label: t('app.skills.colRating') },
  { key: 'subscribe', label: t('app.skills.colSubscribe') },
])

onMounted(() => {
  loadTaxonomy()
  loadFilterOptions()
  // Deep-link filters from Home (KPI cards / View All buttons) arrive as
  // route query params — apply them to the search state before loading.
  const q = route.query
  // AC-HOME-14: opening a skill from Home carries ?detail={id} — open the
  // Skill Detail Popup (which records the view count on mount).
  if (typeof q.detail === 'string' && q.detail !== '') {
    const id = Number(q.detail)
    if (Number.isInteger(id) && id > 0) {
      detailSkillId.value = id
    }
  }
  if (Object.keys(q).length) {
    list.clear()
    if (q.creator === 'me' && auth.user) {
      s.creatorId = auth.user.userId
      s.creatorName = auth.user.userName
    }
    if (typeof q.status === 'string') s.statuses = [q.status]
    if (typeof q.scope === 'string') s.scopes = [q.scope]
    if (typeof q.createdFrom === 'string') s.createdDateFrom = q.createdFrom
    if (typeof q.createdTo === 'string') s.createdDateTo = q.createdTo
    // Home KPI deep-links: Total Views / Average Rating sort, Subscribed filter.
    if (q.subscribed === '1') s.subscribed = 'Y'
    if (q.sortBy === 'viewCount' || q.sortBy === 'avgRating') {
      s.sortBy = q.sortBy
      s.sortDirection = 'DESC'
    }
    runSearch(true)
    return
  }
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

// ?detail deep-link (Home KPI / notification bell): also handled on query
// change, so a bell click while already on /skills still opens the popup.
watch(
  () => route.query.detail,
  (raw) => {
    if (typeof raw !== 'string' || raw === '') return
    const id = Number(raw)
    if (Number.isInteger(id) && id > 0) {
      detailSkillId.value = id
    }
  },
)
</script>

<template>
  <div class="sm">
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
        <PrismInput v-model="s.skillName" :label="t('app.common.skillName')" :placeholder="t('app.common.containsPh')" />
        <PrismInput v-model="s.description" :label="t('app.common.description')" :placeholder="t('app.common.containsPh')" />
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
          <label class="p-label">{{ t('app.skills.colCreator') }}</label>
          <div class="sm-creator">
            <button type="button" class="sm-creator-search" :aria-label="t('app.common.searchUserAria')" @click="searchUserOpen = true">
              <Search :size="16" aria-hidden="true" />
            </button>
            <span v-if="s.creatorName" class="sm-creator-name">{{ s.creatorName }}</span>
            <span v-else class="sm-creator-placeholder">{{ t('app.skills.selectCreator') }}</span>
            <button
              v-if="s.creatorId"
              type="button"
              class="sm-creator-clear"
              :aria-label="t('app.skills.clearCreator')"
              @click="s.creatorId = null; s.creatorName = ''"
            >
              <X :size="14" aria-hidden="true" />
            </button>
          </div>
        </div>
        <PrismCombobox v-model="s.categoryIds" :label="t('app.common.category')" :placeholder="t('app.common.allCategories')" multiple clearable :options="categoryOptions" />
        <PrismCombobox :model-value="s.stageId ?? ''" :label="t('app.common.processStage')" :placeholder="t('app.common.allStages')" clearable :options="stageOptions" @update:model-value="s.stageId = $event ?? null; runSearch(true)" />
        <PrismCombobox v-model="s.statuses" :label="t('app.common.status')" :placeholder="t('app.common.allStatuses')" multiple clearable :options="STATUS_OPTIONS" />
        <PrismCombobox v-model="s.scopes" :label="t('app.skills.colScope')" :placeholder="t('app.common.allScopes')" multiple clearable :options="SCOPE_OPTIONS" />
        <PrismDatePicker v-model="s.createdDateFrom" :label="t('app.common.createdFrom')" :placeholder="t('app.common.selectDatePh')" />
        <PrismDatePicker v-model="s.createdDateTo" :label="t('app.common.createdTo')" :placeholder="t('app.common.selectDatePh')" />
        <PrismCombobox v-model="s.systemKey" :label="t('app.skill.searchSystem')" :placeholder="t('app.skill.searchAllSystems')" clearable :options="systemFilterOptions" />
        <PrismCombobox v-model="s.subSystemCode" :label="t('app.skill.searchSubSystem')" :placeholder="t('app.skill.searchAllSubSystems')" clearable :options="subSystemFilterOptions" />
        <PrismRadioGroup
          v-model="s.subscribed"
          :label="t('app.skills.colSubscribe')"
          inline
          :options="[
            { value: 'ALL', label: t('app.skills.subscribeAll') },
            { value: 'Y', label: t('app.skills.subscribeY') },
            { value: 'N', label: t('app.skills.subscribeN') },
          ]"
        />
      </div>
      <p v-if="dateRangeError" class="p-field-error" role="alert">{{ dateRangeError }}</p>
      <div class="search-actions">
        <PrismButton type="button" @click="addOpen = true">
          <Plus :size="16" aria-hidden="true" /> {{ t('app.skills.addBtn') }}
        </PrismButton>
        <PrismButton variant="secondary" type="button" @click="onExport">
          <Download :size="16" aria-hidden="true" /> {{ t('app.skills.downloadExcel') }}
        </PrismButton>
        <PrismButton
          v-if="selectedIds.size"
          variant="danger"
          type="button"
          @click="onDelete"
        >
          {{ t('app.skills.deleteBtn', { n: selectedIds.size }) }}
        </PrismButton>
        <PrismButton v-if="auth.isAdmin" variant="secondary" type="button" @click="manageOpen = true">
          <SlidersHorizontal :size="16" aria-hidden="true" /> {{ t('app.skills.manageTaxonomy') }}
          <span class="sm-admin-tag">{{ t('app.skills.adminBadge') }}</span>
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
            {{ t('app.skills.skillCount', { n: stageCount(stage.id) }) }}
          </span>
        </button>
      </div>
    </section>

    <!-- Grid (table view) -->
    <section v-if="activeView === 'grid'" class="grid p-glass" :aria-label="t('app.skills.listAria')">
      <PrismEmptyState
        v-if="!s.hasSearched && !s.loading && !s.error"
        :title="t('app.common.noDataFound')"
        :description="t('app.skills.emptyDesc')"
      />
      <PrismEmptyState v-else-if="s.error" :title="t('app.skills.loadError')">
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
              :aria-label="t('app.common.selectAllRows')"
              @change="toggleAll()"
            />
          </template>
          <template #cell-select="{ row }">
            <input
              type="checkbox"
              :checked="selectedIds.has((row as SkillListItem).id)"
              :aria-label="t('app.common.selectRow', { name: (row as SkillListItem).name })"
              @change="toggleRow((row as SkillListItem).id)"
            />
          </template>
          <template #cell-name="{ row }">
            <button
              type="button"
              class="sm-name-link sm-truncate"
              :title="(row as SkillListItem).name"
              @click="onNameClick((row as SkillListItem).id)"
            >
              {{ (row as SkillListItem).name }}
            </button>
          </template>
          <template #cell-description="{ row }">
            <span class="sm-truncate" :title="stripHtml((row as SkillListItem).description ?? '')">
              {{ stripHtml((row as SkillListItem).description ?? '') || '—' }}
            </span>
          </template>
          <template #cell-categories="{ row }">
            <BadgeStack :items="(row as SkillListItem).categories" tone="category" />
          </template>
          <template #cell-stages="{ row }">
            <BadgeStack :items="(row as SkillListItem).stages" tone="stage" />
          </template>
          <template #cell-creatorName="{ row }">
            <span class="sm-truncate" :title="(row as SkillListItem).creatorName ?? ''">
              {{ (row as SkillListItem).creatorName ?? '—' }}
            </span>
          </template>
          <template #cell-createdAt="{ row }">
            {{ formatDate((row as SkillListItem).createdAt) }}
          </template>
          <template #cell-status="{ row }">
            <div class="sm-status-cell">
              <PrismBadge :tone="statusTone(displayStatus(row as SkillListItem))">
                {{ statusLabel(displayStatus(row as SkillListItem)) }}
              </PrismBadge>
              <!-- The admin/creator sees how many shares are awaiting approval
                   on a SHARE-pending skill. -->
              <span
                v-if="(row as SkillListItem).status === 'PENDING_APPROVAL'
                  && (row as SkillListItem).pendingReason === 'SHARE'
                  && (row as SkillListItem).pendingShareCount > 0
                  && (auth.isAdmin || (row as SkillListItem).creatorId === auth.user?.userId)"
                class="sm-pending-count"
              >
                {{ t('app.skill.pendingSharesCount', { count: (row as SkillListItem).pendingShareCount }) }}
              </span>
            </div>
          </template>
          <template #cell-viewCount="{ row }">
            {{ (row as SkillListItem).viewCount }}
          </template>
          <template #cell-downloadCount="{ row }">
            {{ (row as SkillListItem).downloadCount }}
          </template>
          <template #cell-rating="{ row }">
            <span v-if="(row as SkillListItem).avgRating != null" class="sm-rating">
              ★ {{ (row as SkillListItem).avgRating!.toFixed(1) }} ({{ (row as SkillListItem).ratingCount }})
            </span>
            <span v-else class="sm-norating">{{ t('app.common.noRating') }}</span>
          </template>
          <template #cell-subscribe="{ row }">
            <button
              v-if="canSubscribe(row as SkillListItem)"
              type="button"
              class="sm-bell"
              :class="{ 'is-subscribed': (row as SkillListItem).subscribed }"
              :aria-label="t('app.skills.colSubscribe')"
              @click="toggleSubscribe(row as SkillListItem)"
            >
              <Bell :size="16" :fill="(row as SkillListItem).subscribed ? 'currentColor' : 'none'" aria-hidden="true" />
            </button>
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
    <section v-else class="grid p-glass" :aria-label="t('app.skills.cardsAria')">
      <PrismEmptyState
        v-if="!s.hasSearched && !s.loading && !s.error"
        :title="t('app.common.noDataFound')"
        :description="t('app.skills.emptyDesc')"
      />
      <PrismEmptyState v-else-if="s.error" :title="t('app.skills.loadError')">
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
              <PrismBadge :tone="statusTone(item.status)">{{ statusLabel(item.status) }}</PrismBadge>
            </div>
            <p class="sc-desc" :title="stripHtml(item.description ?? '')">
              {{ stripHtml(item.description ?? '') || '—' }}
            </p>
            <div class="sc-badges">
              <BadgeStack :items="item.categories" tone="category" />
              <BadgeStack :items="item.stages" tone="stage" />
            </div>
            <div class="sc-foot">
              <span>{{ item.creatorName ?? '—' }}</span>
              <span aria-hidden="true">·</span>
              <span>{{ t('app.common.views', { n: item.viewCount }) }}</span>
              <span v-if="item.avgRating != null" class="sc-rating">★ {{ item.avgRating.toFixed(1) }}</span>
              <span v-else class="sc-norating">{{ t('app.common.noRating') }}</span>
            </div>
          </div>
        </div>
        <PrismEmptyState v-else :title="t('app.common.noDataFound')" :description="t('app.skills.noMatchDesc')" />

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

    <!-- Add Skill -->
    <AddSkillPopup v-model:open="addOpen" @saved="onSaved" />

    <!-- Manage Categories & Stages (admin) -->
    <ManageTaxonomyPopup v-model:open="manageOpen" @changed="onTaxonomyChanged" />

    <!-- Search User (Creator field) -->
    <SearchUserPopup v-model:open="searchUserOpen" @selected="onUserSelected" />

    <!-- Skill Detail -->
    <SkillDetailPopup
      :skill-id="detailSkillId"
      @closed="detailSkillId = null"
      @changed="runSearch(false)"
    />

    <!-- Delete confirm -->
    <PrismDialog
      v-model="deleteConfirm"
      :title="t('app.skills.deleteTitle')"
      size="sm"
      :close-on-overlay="false"
    >
      <p class="sm-confirm-text">
        {{ t('app.skills.deleteDesc', { n: selectedIds.size }) }}
      </p>
      <template #footer>
        <PrismButton variant="secondary" @click="deleteConfirm = false">{{ t('app.common.cancel') }}</PrismButton>
        <PrismButton variant="danger" :loading="deleting" @click="confirmDelete">{{ t('app.common.delete') }}</PrismButton>
      </template>
    </PrismDialog>

    <!-- Delete denied alert -->
    <PrismDialog
      v-model="deleteDeniedOpen"
      :title="t('app.skills.cannotDeleteTitle')"
      size="md"
      :close-on-overlay="false"
    >
      <p class="sm-confirm-text">
        {{ t('app.skills.cannotDeleteDesc') }}
      </p>
      <p class="sm-denied-title">{{ t('app.skills.cannotDeleteList') }}</p>
      <ul class="sm-denied-list">
        <li v-for="d in deleteDenied" :key="d.id">
          {{ t('app.skills.cannotDeleteItem', { name: d.name, creator: d.creatorName ?? t('app.common.unknown') }) }}
        </li>
      </ul>
      <p class="sm-confirm-text">
        {{ t('app.skills.cannotDeleteTotal', { n: selectedIds.size, m: deleteDenied.length }) }}
      </p>
      <template #footer>
        <PrismButton @click="deleteDeniedOpen = false">{{ t('app.common.close') }}</PrismButton>
      </template>
    </PrismDialog>
  </div>
</template>

<style scoped>
.sm {
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
  /* 36px = 34px input + 2px border, matches .p-input-wrap in compact mode */
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
  box-shadow: 0 0 0 3px rgb(103 232 249 / .10), 0 0 14px rgb(103 232 249 / .05);
}
.sm-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 1px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
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
  /* 36px = 34px input + 2px border, matches .p-input-wrap in compact mode */
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
.sm-name-link {
  border: none;
  background: none;
  padding: 0;
  color: var(--ds-color-accent);
  font: inherit;
  cursor: pointer;
  text-align: left;
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.sm-name-link:hover {
  text-decoration: underline;
}
.sm-truncate {
  display: inline-block;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.sm-rating {
  color: var(--ds-color-accent);
}
.sm-norating {
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.grid-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}

.sm-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
/* Rows (page size) — compact: label + 30px combobox, narrow width. */
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
/* The combobox has no label of its own — PrismFormField still renders an
   empty <label> row; hide it so the control sits on the same line as "Rows". */
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
.sm-confirm-text {
  color: var(--ds-color-text-primary);
}
.sm-denied-title {
  font-weight: 600;
  color: var(--ds-color-text-primary);
}
.sm-denied-list {
  margin: 0;
  padding-left: var(--ds-space-5);
  color: var(--ds-color-text-primary);
  display: grid;
  gap: var(--ds-space-1);
}
.sm-bell {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  display: inline-flex;
  align-items: center;
  padding: 0;
}
.sm-bell.is-subscribed {
  color: var(--ds-color-accent);
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
.sm-admin-tag {
  font-size: 10px;
  letter-spacing: .06em;
  text-transform: uppercase;
  color: var(--ds-color-text-muted);
  border: 1px solid var(--ds-color-border);
  border-radius: 4px;
  padding: 1px 5px;
}

/* ── Process map pipeline ───────────────────────────────────────────────── */
.map-panel {
  padding: var(--ds-space-5);
}
.map-pipeline {
  display: grid;
  /* auto-fit: as many 150px+ columns as fit, items stretch to fill the row —
     7 stages share one full-width row; an 8th wraps when no column fits. */
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
  background: rgb(103 232 249 / .1);
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
  border-top: 1px solid rgb(133 234 255 / .08);
}
.sc-rating {
  margin-left: auto;
  color: var(--ds-color-accent);
}
.sc-norating {
  margin-left: auto;
  color: var(--ds-color-text-muted);
}
.sm-status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}
.sm-pending-count {
  font-size: 11px;
  color: var(--ds-color-warning, #b45309);
  white-space: nowrap;
}
</style>
