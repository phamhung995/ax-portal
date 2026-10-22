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
import { toastError, toastSuccess } from '@/lib/toast'
import { useUserListStore } from '@/features/users/store'
import type { UserListItem } from '@/features/users/types'

const list = useUserListStore()
const s = list.state

const ROLE_OPTIONS = [
  { label: 'Normal User', value: 'NORMAL_USER' },
  { label: 'Admin User', value: 'ADMIN_USER' },
]
const STATUS_OPTIONS = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Deactive', value: 'DEACTIVE' },
]
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
  code === 'ADMIN_USER' ? 'Admin User' : code === 'NORMAL_USER' ? 'Normal User' : code
const statusLabel = (code: string) =>
  code === 'DEACTIVE' ? 'Deactive' : code === 'ACTIVE' ? 'Active' : code
const orgLabel = (u: UserListItem) => {
  if (u.orgs.length === 0) return '—'
  return u.orgs.map((o) => `${o.part} > ${o.group}`).join('; ')
}
function formatDate(iso: string): string {
  const d = new Date(iso)
  const dd = String(d.getDate()).padStart(2, '0')
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  return `${dd}/${mm}/${d.getFullYear()}`
}

const dateRangeError = computed(() => {
  if (s.createdDateFrom && s.createdDateTo && s.createdDateFrom > s.createdDateTo) {
    return 'From Date cannot be later than To Date.'
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
    toastError('No data to export. Run a search first.')
    return
  }
  try {
    // Export only the data currently shown on screen:
    // the selected rows if any, otherwise the rows of the current page.
    const ids = selectedIds.value.size
      ? [...selectedIds.value]
      : s.items.map((u) => u.userId)
    await adminUserApi.exportUsers({ ...buildQuery(), ids })
    toastSuccess('User list exported successfully.')
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

const columns = computed(() => [
  { key: 'select', label: '' },
  { key: 'userName', label: 'User Name' },
  { key: 'knoxId', label: 'Knox ID' },
  { key: 'email', label: 'Email' },
  { key: 'organization', label: 'Organization' },
  { key: 'role', label: 'Role' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Created Date' },
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
    <section class="search p-glass" aria-label="Search" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput v-model="s.userName" label="User Name" placeholder="Contains…" />
        <PrismInput v-model="s.email" label="Email" placeholder="Contains…" />
        <PrismCombobox
          v-model="s.organization"
          label="Organization"
          placeholder="Select Organization"
          clearable
          :options="orgOptions"
        />
        <PrismCombobox
          v-model="s.roles"
          label="Role"
          placeholder="All Roles"
          multiple
          clearable
          :options="ROLE_OPTIONS"
        />
        <PrismDatePicker v-model="s.createdDateFrom" label="Created From" />
        <PrismDatePicker v-model="s.createdDateTo" label="Created To" />
        <PrismCombobox
          v-model="s.statuses"
          label="Status"
          placeholder="All Statuses"
          multiple
          clearable
          :options="STATUS_OPTIONS"
        />
      </div>
      <p v-if="dateRangeError" class="p-field-error" role="alert">{{ dateRangeError }}</p>
      <div class="search-actions">
        <PrismButton type="button" @click="createOpen = true">
          <Plus :size="16" aria-hidden="true" /> Create User
        </PrismButton>
        <PrismButton variant="secondary" type="button" @click="bulkOpen = true">
          <Upload :size="16" aria-hidden="true" /> Bulk Upload
        </PrismButton>
        <PrismButton variant="secondary" type="button" @click="onExport">
          <Download :size="16" aria-hidden="true" /> Export User List
        </PrismButton>
        <span class="search-actions-spacer" aria-hidden="true"></span>
        <PrismButton type="button" :loading="s.loading" @click="onSearch">Search</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">Reset</PrismButton>
      </div>
    </section>

    <!-- Grid -->
    <section class="grid p-glass" aria-label="User list">
      <PrismEmptyState
        v-if="!s.hasSearched && !s.loading && !s.error"
        title="No data found."
        description="Enter search criteria and click Search to load users."
      />
      <PrismEmptyState v-else-if="s.error" title="Unable to load user list.">
        <p class="grid-error-text">{{ s.error }}</p>
        <PrismButton variant="secondary" @click="onSearch">Retry</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <PrismTable
          :columns="columns"
          :rows="s.items"
          row-key="userId"
          :loading="s.loading"
          caption="User list"
          empty-text="No data found."
        >
          <template #header-select>
            <input
              type="checkbox"
              :checked="allSelected"
              aria-label="Select all rows on this page"
              @change="toggleAll()"
            />
          </template>
          <template #cell-select="{ row }">
            <input
              type="checkbox"
              :checked="selectedIds.has((row as UserListItem).userId)"
              :aria-label="`Select ${(row as UserListItem).userName}`"
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
          <label class="um-pagesize">
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
  padding: var(--ds-space-5);
  display: grid;
  gap: var(--ds-space-4);
}
.search-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--ds-space-4);
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
.um-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.um-pagesize select {
  background: var(--ds-input-background);
  border: 1px solid var(--ds-color-border);
  color: var(--ds-color-text-primary);
  border-radius: var(--ds-radius-sm);
  padding: 6px 8px;
}
</style>
