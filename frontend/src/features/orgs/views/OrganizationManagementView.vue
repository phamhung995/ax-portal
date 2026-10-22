<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download, Upload } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { organizationApi } from '@/features/orgs/api'
import { toUserError } from '@/lib/api'
import { toastSuccess, toastError } from '@/lib/toast'
import { useOrgListStore } from '@/features/orgs/store'
import type { OrgListItem, OrgImportResponse, OrgImportRowResult } from '@/features/orgs/types'

const list = useOrgListStore()
const s = list.state

const EMPLOYED_OPTIONS = [
  { label: 'Yes', value: 'Y' },
  { label: 'No', value: 'N' },
]
const PAGE_SIZES = [10, 20, 50, 100]

// ── Filter options (Part / Group) ───────────────────────────────────────────
const partOptions = ref<{ label: string; value: string }[]>([])
const groupOptions = ref<{ label: string; value: string }[]>([])
async function loadFilterOptions() {
  try {
    const [parts, groups] = await Promise.all([
      organizationApi.partOptions(),
      organizationApi.groupOptions(),
    ])
    partOptions.value = parts.map((v) => ({ label: v, value: v }))
    groupOptions.value = groups.map((v) => ({ label: v, value: v }))
  } catch {
    partOptions.value = []
    groupOptions.value = []
  }
}

// ── Search ──────────────────────────────────────────────────────────────────
function buildQuery() {
  return {
    knoxId: s.knoxId || undefined,
    userName: s.userName || undefined,
    company: s.company || undefined,
    part: s.part || undefined,
    group: s.group || undefined,
    employed: s.employed || undefined,
    page: s.page,
    pageSize: s.pageSize,
  }
}

async function runSearch(resetPage = true) {
  if (resetPage) s.page = 1
  s.loading = true
  s.error = ''
  try {
    list.applyResult(await organizationApi.search(buildQuery()))
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

onMounted(() => {
  loadFilterOptions()
})

// ── Import Excel ────────────────────────────────────────────────────────────
const importOpen = ref(false)
const importing = ref(false)
const importError = ref('')
const importResult = ref<OrgImportResponse | null>(null)
const importFiles = ref<File[]>([])
const errorDialogOpen = ref(false)

const errorRows = computed<OrgImportRowResult[]>(() =>
  (importResult.value?.rows ?? []).filter((r) => r.status === 'ERROR')
)

function onOpenImport() {
  importError.value = ''
  importResult.value = null
  importFiles.value = []
  importOpen.value = true
}

async function onDownloadTemplate() {
  try {
    await organizationApi.downloadTemplate()
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

/** Step 1: file picked — validate only, do not import yet. */
function onFilePicked(files: File[]) {
  importFiles.value = files
  importError.value = ''
  importResult.value = null
  const file = files[0]
  if (!file) return
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    importError.value = 'Only .xlsx and .xls files are supported.'
  } else if (file.size > 10 * 1024 * 1024) {
    importError.value = 'File size is at most 10MB.'
  }
}

const canImport = computed(() => importFiles.value.length > 0 && !importing.value && !importError.value)

/** Step 2: user clicks Import — run the import now. */
async function doImport() {
  const file = importFiles.value[0]
  if (!file) return
  importing.value = true
  importError.value = ''
  importResult.value = null
  try {
    const res = await organizationApi.importFile(file)
    importResult.value = res
    toastSuccess(
      `Imported: ${res.successCount} success, ${res.errorCount} errors, ` +
        `${res.matchedCount} matched, ${res.deactivatedCount} deactivated.`
    )
    if (res.errorCount > 0) {
      errorDialogOpen.value = true
    }
    // Refresh the grid with the current search criteria
    await runSearch(true)
    importOpen.value = false
  } catch (err) {
    importError.value = toUserError(err).message
  } finally {
    importing.value = false
  }
}

// ── Grid ────────────────────────────────────────────────────────────────────
const columns = computed(() => [
  { key: 'knoxId', label: 'Knox ID' },
  { key: 'userName', label: 'User Name' },
  { key: 'employed', label: 'Employed' },
  { key: 'regularPartner', label: 'Regular/Partner' },
  { key: 'company', label: 'Company' },
  { key: 'part', label: 'Part' },
  { key: 'employeeNumber', label: 'Employee Number' },
  { key: 'group', label: 'Group' },
  { key: 'matchedUser', label: 'Matched User' },
])

const errorColumns = [
  { key: 'rowNumber', label: 'Row' },
  { key: 'knoxId', label: 'Knox ID' },
  { key: 'userName', label: 'User Name' },
  { key: 'errors', label: 'Error Message' },
]
</script>

<template>
  <div class="om">
    <!-- Search area -->
    <section class="search p-glass" aria-label="Search" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput v-model="s.knoxId" label="Knox ID" placeholder="Contains…" />
        <PrismInput v-model="s.userName" label="User Name" placeholder="Contains…" />
        <PrismInput v-model="s.company" label="Company" placeholder="Contains…" />
        <PrismCombobox
          v-model="s.group"
          label="Group"
          placeholder="Select Group"
          clearable
          :options="groupOptions"
        />
        <PrismCombobox
          v-model="s.part"
          label="Part"
          placeholder="Select Part"
          clearable
          :options="partOptions"
        />
        <PrismCombobox
          v-model="s.employed"
          label="Employed"
          placeholder="All"
          clearable
          :options="EMPLOYED_OPTIONS"
        />
      </div>
      <div class="search-actions">
        <PrismButton variant="secondary" type="button" @click="onOpenImport">
          <Upload :size="16" aria-hidden="true" /> Upload Excel
        </PrismButton>
        <PrismButton variant="secondary" type="button" @click="onDownloadTemplate">
          <Download :size="16" aria-hidden="true" /> Download Template
        </PrismButton>
        <span class="search-actions-spacer" aria-hidden="true"></span>
        <PrismButton type="button" :loading="s.loading" @click="onSearch">Search</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">Reset</PrismButton>
      </div>
    </section>

    <!-- Grid -->
    <section class="grid p-glass" aria-label="Organization list">
      <PrismEmptyState
        v-if="!s.hasSearched && !s.loading && !s.error"
        title="No data found."
        description="Enter search criteria and click Search to load organization data."
      />
      <PrismEmptyState v-else-if="s.error" title="Unable to load organization list.">
        <p class="grid-error-text">{{ s.error }}</p>
        <PrismButton variant="secondary" @click="onSearch">Retry</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <PrismTable
          :columns="columns"
          :rows="s.items"
          row-key="id"
          :loading="s.loading"
          caption="Organization list"
          empty-text="No data found."
        >
          <template #cell-knoxId="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).knoxId">
              {{ (row as OrgListItem).knoxId }}
            </span>
          </template>
          <template #cell-userName="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).userName">
              {{ (row as OrgListItem).userName }}
            </span>
          </template>
          <template #cell-employed="{ row }">
            <PrismBadge :tone="(row as OrgListItem).employed === 'Y' ? 'success' : 'neutral'">
              {{ (row as OrgListItem).employed === 'Y' ? 'Yes' : 'No' }}
            </PrismBadge>
          </template>
          <template #cell-regularPartner="{ row }">
            {{ (row as OrgListItem).regularPartner }}
          </template>
          <template #cell-company="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).company ?? undefined">
              {{ (row as OrgListItem).company ?? '—' }}
            </span>
          </template>
          <template #cell-part="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).part ?? undefined">
              {{ (row as OrgListItem).part ?? '—' }}
            </span>
          </template>
          <template #cell-group="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).group ?? undefined">
              {{ (row as OrgListItem).group ?? '—' }}
            </span>
          </template>
          <template #cell-employeeNumber="{ row }">
            {{ (row as OrgListItem).employeeNumber ?? '—' }}
          </template>
          <template #cell-matchedUser="{ row }">
            <span v-if="(row as OrgListItem).matchedUserEmail" class="om-truncate" :title="(row as OrgListItem).matchedUserEmail ?? undefined">
              {{ (row as OrgListItem).matchedUserEmail }}
            </span>
            <PrismBadge v-else tone="neutral">Not matched</PrismBadge>
          </template>
        </PrismTable>

        <div class="om-pager">
          <label class="om-pagesize">
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

    <!-- Import dialog -->
    <PrismDialog v-model="importOpen" title="Upload Organization Excel"
      description="Template columns: Knox ID, User Name, Employed Y/N, Regular/ Partner, Company, Part, Employee Number, Group.">
      <div class="om-import-body">
        <PrismFileUpload
          :model-value="importFiles"
          label="Organization file"
          accept=".xlsx,.xls"
          :max-size="10 * 1024 * 1024"
          hint="One file per upload. Each import replaces only the rows whose Knox ID and Employee Number appear in the file; other organization rows are kept."
          @update:model-value="onFilePicked($event as File[])"
        />
        <button type="button" class="om-template-link" @click="onDownloadTemplate">
          <Download :size="14" aria-hidden="true" /> Download Template
        </button>
        <p v-if="importError" class="p-field-error" role="alert">{{ importError }}</p>
        <p v-else-if="importFiles.length > 0 && !importing" class="om-import-ready" role="status">
          File "{{ importFiles[0]?.name }}" đã sẵn sàng. Bấm <strong>Import</strong> để bắt đầu xử lý.
        </p>
        <p v-else-if="importing" class="om-import-ready" role="status">
          Đang xử lý file… vui lòng chờ.
        </p>
        <div v-if="importResult" class="om-import-summary">
          <span>Total: <strong>{{ importResult.totalRows }}</strong></span>
          <span class="is-success">Success: <strong>{{ importResult.successCount }}</strong></span>
          <span class="is-error">Error: <strong>{{ importResult.errorCount }}</strong></span>
          <span>Matched: <strong>{{ importResult.matchedCount }}</strong></span>
          <span>Deactivated: <strong>{{ importResult.deactivatedCount }}</strong></span>
        </div>
      </div>
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="importOpen = false">Close</PrismButton>
        <PrismButton type="button" :loading="importing" :disabled="!canImport" @click="doImport">
          Import
        </PrismButton>
      </template>
    </PrismDialog>

    <!-- Import error rows dialog -->
    <PrismDialog v-model="errorDialogOpen" title="Import errors"
      description="The rows below were not imported. Fix the file and upload again.">
      <PrismTable
        :columns="errorColumns"
        :rows="errorRows"
        row-key="rowNumber"
        caption="Import error rows"
        empty-text="No error rows."
      >
        <template #cell-errors="{ row }">
          <span class="om-error" :title="(row as OrgImportRowResult).errors.join(' ')">
            {{ (row as OrgImportRowResult).errors.join(' ') }}
          </span>
        </template>
      </PrismTable>
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="errorDialogOpen = false">Close</PrismButton>
      </template>
    </PrismDialog>
  </div>
</template>

<style scoped>
.om {
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
.om-truncate {
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

.om-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
.om-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.om-pagesize select {
  background: var(--ds-input-background);
  border: 1px solid var(--ds-color-border);
  color: var(--ds-color-text-primary);
  border-radius: var(--ds-radius-sm);
  padding: 6px 8px;
}

.om-import-body {
  display: grid;
  gap: var(--ds-space-3);
}
.om-template-link {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  justify-self: start;
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-size: 13px;
  color: var(--ds-color-accent);
  cursor: pointer;
}
.om-template-link:hover {
  text-decoration: underline;
}
.om-import-ready {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.om-import-summary {
  display: flex;
  gap: var(--ds-space-4);
  flex-wrap: wrap;
  font-size: 13px;
}
.om-import-summary .is-success {
  color: var(--ds-color-success);
}
.om-import-summary .is-error {
  color: var(--ds-color-danger);
}
.om-error {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--ds-color-danger);
}
</style>
