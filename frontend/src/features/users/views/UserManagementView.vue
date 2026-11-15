<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download, Plus, Upload } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismDatePicker from '@/design-system/components/PrismDatePicker.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import CreateUserPopup from '@/features/users/views/CreateUserPopup.vue'
import BulkUploadPopup from '@/features/users/views/BulkUploadPopup.vue'
import UserDetailPopup from '@/features/users/views/UserDetailPopup.vue'
import { adminUserApi } from '@/features/users/api'
import { toUserError } from '@/lib/api'
import { orgsLabel } from '@/lib/types'
import { toastError, toastSuccess } from '@/lib/toast'
import { useUserListStore } from '@/features/users/store'
import type { UserListItem } from '@/features/users/types'
import { useI18nStore } from '@/stores/i18n'

const i18n = useI18nStore()
const { t, formatDate } = i18n
const list = useUserListStore()
const s = list.state

const ROLE_OPTIONS = computed(() => [
  { label: t('app.common.normalUser'), value: 'NORMAL_USER' },
  { label: t('app.common.adminUser'), value: 'ADMIN_USER' },
])
const STATUS_OPTIONS = computed(() => [
  { label: t('app.common.active'), value: 'ACTIVE' },
  { label: t('app.common.deactive'), value: 'DEACTIVE' },
])
const PAGE_SIZES = [10, 20, 50, 100]

const orgOptions = ref<{ label: string; value: string }[]>([])
async function loadOrgOptions() {
  try {
    const values = await adminUserApi.organizationOptions()
    orgOptions.value = values.map((v) => ({ label: v, value: v }))
  } catch {
    orgOptions.value = []
  }
}

const roleLabel = (code: string) =>
  code === 'ADMIN_USER' ? t('app.common.adminUser') : code === 'NORMAL_USER' ? t('app.common.normalUser') : code
const statusLabel = (code: string) =>
  code === 'DEACTIVE' ? t('app.common.deactive') : code === 'ACTIVE' ? t('app.common.active') : code
const orgLabel = (u: UserListItem) => orgsLabel(u.orgs)

const dateRangeError = computed(() => {
  if (s.createdDateFrom && s.createdDateTo && s.createdDateFrom > s.createdDateTo) {
    return t('app.common.dateRangeError')
  }
  return ''
})

const selectedIds = ref<Set<number>>(new Set())
const allSelected = computed(
  () => s.items.length > 0 && s.items.every((u) => selectedIds.value.has(u.userId))
)
function toggleAll() {
  selectedIds.value = allSelected.value ? new Set() : new Set(s.items.map((u) => u.userId))
}
function toggleRow(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

function buildQuery() {
  return {
    userName: s.userName || undefined,
    email: s.email || undefined,
    organization: s.organization || undefined,
    roles: s.roles.length ? s.roles : undefined,
    statuses: s.statuses.length ? s.statuses : undefined,
    createdDateFrom: s.createdDateFrom || undefined,
    createdDateTo: s.createdDateTo || undefined,
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
    list.applyResult(await adminUserApi.search(buildQuery()))
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
}
function onPage(page: number) {
  s.page = page
  runSearch(false)
}
function onPageSize(size: number) {
  s.pageSize = size
  runSearch(true)
}

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
      : s.items.map((u) => u.userId)
    await adminUserApi.exportUsers({ ...buildQuery(), ids })
    toastSuccess(t('app.users.exportedToast'))
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

const columns = computed(() => [
  { key: 'select', label: '' },
  { key: 'userName', label: t('app.users.colUserName') },
  { key: 'knoxId', label: t('app.users.colKnoxId') },
  { key: 'email', label: t('app.users.colEmail') },
  { key: 'organization', label: t('app.users.colOrganization') },
  { key: 'role', label: t('app.users.colRole') },
  { key: 'status', label: t('app.users.colStatus') },
  { key: 'createdAt', label: t('app.users.colCreatedDate') },
])

const knoxId = (email: string | null) => (email ? email.split('@')[0] ?? '' : '')

// ── Create User / Bulk Upload / User Detail popups ──────────────────────────
const createOpen = ref(false)
const bulkOpen = ref(false)
const detailUserId = ref<number | null>(null)
const detailOpen = computed({
  get: () => detailUserId.value !== null,
  set: (v: boolean) => {
    if (!v) detailUserId.value = null
  },
})

function onPopupSaved() {
  // A user was created/uploaded/updated: silently re-run the same search.
  if (s.hasSearched) runSearch(false)
}

onMounted(() => {
  // Do NOT auto-search (SRS §4.8). Grid stays empty until Search is clicked.
  loadOrgOptions()
  // A user was modified on the detail screen while we were away:
  // silently re-run the same search so the grid shows fresh data.
  if (s.hasSearched && s.needsRefresh) {
    runSearch(false)
  }
})
</script>

<template>
  <div class="um">
    <!-- Search area -->
    <section class="search p-glass" :aria-label="t('app.common.searchAria')" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput v-model="s.userName" :label="t('app.users.searchUserName')" :placeholder="t('app.common.containsPh')" />
        <PrismInput v-model="s.email" :label="t('app.users.searchEmail')" :placeholder="t('app.common.containsPh')" />
        <PrismCombobox
          v-model="s.organization"
          :label="t('app.common.organization')"
          :placeholder="t('app.common.selectOrganization')"
          clearable
          :options="orgOptions"
        />
        <PrismCombobox
          v-model="s.roles"
          :label="t('app.common.role')"
          :placeholder="t('app.common.allRoles')"
          multiple
          clearable
          :options="ROLE_OPTIONS"
        />
        <PrismDatePicker v-model="s.createdDateFrom" :label="t('app.common.createdFrom')" />
        <PrismDatePicker v-model="s.createdDateTo" :label="t('app.common.createdTo')" />
        <PrismCombobox
          v-model="s.statuses"
          :label="t('app.common.status')"
          :placeholder="t('app.common.allStatuses')"
          multiple
          clearable
          :options="STATUS_OPTIONS"
        />
      </div>
      <p v-if="dateRangeError" class="p-field-error" role="alert">{{ dateRangeError }}</p>
      <div class="search-actions">
        <PrismButton type="button" @click="createOpen = true">
          <Plus :size="16" aria-hidden="true" /> {{ t('app.users.createBtn') }}
        </PrismButton>
        <PrismButton variant="secondary" type="button" @click="bulkOpen = true">
          <Upload :size="16" aria-hidden="true" /> {{ t('app.users.bulkUploadBtn') }}
        </PrismButton>
        <PrismButton variant="secondary" type="button" @click="onExport">
          <Download :size="16" aria-hidden="true" /> {{ t('app.users.exportBtn') }}
        </PrismButton>
        <span class="search-actions-spacer" aria-hidden="true"></span>
        <PrismButton type="button" :loading="s.loading" @click="onSearch">{{ t('app.common.search') }}</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">{{ t('app.common.reset') }}</PrismButton>
      </div>
    </section>

    <!-- Grid -->
    <section class="grid p-glass" :aria-label="t('app.users.listAria')">
      <PrismEmptyState
        v-if="!s.hasSearched && !s.loading && !s.error"
        :title="t('app.common.noData')"
        :description="t('app.users.emptyDesc')"
      />
      <PrismEmptyState v-else-if="s.error" :title="t('app.users.loadError')">
        <p class="grid-error-text">{{ s.error }}</p>
        <PrismButton variant="secondary" @click="onSearch">{{ t('app.common.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <PrismTable
          :columns="columns"
          :rows="s.items"
          row-key="userId"
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
              :checked="selectedIds.has((row as UserListItem).userId)"
              :aria-label="t('app.common.selectRow', { name: (row as UserListItem).userName })"
              @change="toggleRow((row as UserListItem).userId)"
            />
          </template>
          <template #cell-userName="{ row }">
            <button
              type="button"
              class="um-name-link"
              @click="detailUserId = (row as UserListItem).userId"
            >
              {{ (row as UserListItem).userName }}
            </button>
          </template>
          <template #cell-knoxId="{ row }">
            <span class="um-truncate" :title="knoxId((row as UserListItem).email)">
              {{ knoxId((row as UserListItem).email) }}
            </span>
          </template>
          <template #cell-email="{ row }">
            <span class="um-truncate" :title="(row as UserListItem).email">
              {{ (row as UserListItem).email }}
            </span>
          </template>
          <template #cell-organization="{ row }">
            <span class="um-truncate" :title="orgLabel(row as UserListItem)">
              {{ orgLabel(row as UserListItem) }}
            </span>
          </template>
          <template #cell-role="{ row }">{{ roleLabel((row as UserListItem).role) }}</template>
          <template #cell-status="{ row }">
            <PrismBadge :tone="(row as UserListItem).status === 'ACTIVE' ? 'success' : 'neutral'">
              {{ statusLabel((row as UserListItem).status) }}
            </PrismBadge>
          </template>
          <template #cell-createdAt="{ row }">
            {{ formatDate((row as UserListItem).createdAt) }}
          </template>
        </PrismTable>

        <div class="um-pager">
          <div class="um-pagesize">
            <span class="um-pagesize-label">{{ t('app.common.rows') }}</span>
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

    <CreateUserPopup v-model:open="createOpen" @saved="onPopupSaved" />
    <BulkUploadPopup v-model:open="bulkOpen" @saved="onPopupSaved" />
    <UserDetailPopup v-model:open="detailOpen" :user-id="detailUserId" @saved="onPopupSaved" />
  </div>
</template>

<style scoped>
.um {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
}

.search {
  padding: var(--ds-space-4);
  display: grid;
  gap: var(--ds-space-3);
}
.search-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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
.um-name-link {
  border: none;
  background: none;
  padding: 0;
  color: var(--ds-color-accent);
  font: inherit;
  cursor: pointer;
  text-align: left;
}
.um-name-link:hover {
  text-decoration: underline;
}
.um-truncate {
  display: inline-block;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.grid-error-text {
  color: var(--ds-color-danger);
  font-size: 13px;
}

.um-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
/* Rows (page size) — compact: label + 30px combobox, narrow width. */
.um-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-shrink: 0;
}
.um-pagesize-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ds-color-text-secondary);
}
/* The combobox has no label of its own — PrismFormField still renders an
   empty <label> row; hide it so the control sits on the same line as "Rows". */
.um-pagesize :deep(.p-field) {
  gap: 0;
}
.um-pagesize :deep(.p-field > label:empty) {
  display: none;
}
.um-pagesize :deep(.p-combobox) {
  width: 84px;
  font-size: 12px;
}
.um-pagesize :deep(.p-input-wrap),
.um-pagesize :deep(.p-input-wrap) input {
  min-height: 30px;
  font-size: 12px;
  padding: 0 var(--ds-space-2);
}
</style>
