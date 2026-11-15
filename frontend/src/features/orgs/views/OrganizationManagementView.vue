<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Download, Upload, Play } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismTabs from '@/design-system/components/PrismTabs.vue'
import SystemsTab from './SystemsTab.vue'
import HrSystemsTab from './HrSystemsTab.vue'
import { organizationApi } from '@/features/orgs/api'
import { toUserError } from '@/lib/api'
import { toastSuccess, toastError } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { OrgListItem, OrgImportStatus, OrgImportRowResult, OrgSearchResult } from '@/features/orgs/types'

const i18n = useI18nStore()
const { t, formatDateTime } = i18n

const activeTab = ref('organization')

const EMPLOYED_OPTIONS = computed(() => [
  { label: t('app.org.employedYes'), value: 'Y' },
  { label: t('app.org.employedNo'), value: 'N' },
])
const PAGE_SIZES = [10, 20, 50, 100]

// ── Search state ─────────────────────────────────────────────────────────────
const s = ref({
  knoxId: '',
  userName: '',
  company: '',
  division: '',
  center: '',
  group: '',
  part: '',
  employed: '',
  page: 1,
  pageSize: 20,
})
const loading = ref(false)
const error = ref('')
const hasSearched = ref(false)
const result = ref<OrgSearchResult | null>(null)

// ── Filter options (Division / Center / Group / Part) ───────────────────────
const divisionOptions = ref<{ label: string; value: string }[]>([])
const centerOptions = ref<{ label: string; value: string }[]>([])
const groupOptions = ref<{ label: string; value: string }[]>([])
const partOptions = ref<{ label: string; value: string }[]>([])
async function loadFilterOptions() {
  try {
    const [divisions, centers, groups, parts] = await Promise.all([
      organizationApi.divisionOptions(),
      organizationApi.centerOptions(),
      organizationApi.groupOptions(),
      organizationApi.partOptions(),
    ])
    divisionOptions.value = divisions.map((v) => ({ label: v, value: v }))
    centerOptions.value = centers.map((v) => ({ label: v, value: v }))
    groupOptions.value = groups.map((v) => ({ label: v, value: v }))
    partOptions.value = parts.map((v) => ({ label: v, value: v }))
  } catch {
    divisionOptions.value = []
    centerOptions.value = []
    groupOptions.value = []
    partOptions.value = []
  }
}

async function runSearch(resetPage = true) {
  if (resetPage) s.value.page = 1
  loading.value = true
  error.value = ''
  try {
    result.value = await organizationApi.search({
      knoxId: s.value.knoxId || undefined,
      userName: s.value.userName || undefined,
      company: s.value.company || undefined,
      division: s.value.division || undefined,
      center: s.value.center || undefined,
      group: s.value.group || undefined,
      part: s.value.part || undefined,
      employed: s.value.employed || undefined,
      page: s.value.page,
      pageSize: s.value.pageSize,
    })
    hasSearched.value = true
  } catch (err) {
    error.value = toUserError(err).message
    result.value = null
  } finally {
    loading.value = false
  }
}

function onSearch() {
  runSearch(true)
}
function onSearchKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter') return
  const target = event.target as HTMLElement | null
  if (target?.closest?.('.p-combobox')?.classList.contains('is-open')) return
  onSearch()
}
function onReset() {
  s.value = { knoxId: '', userName: '', company: '', division: '', center: '', group: '', part: '', employed: '', page: 1, pageSize: 20 }
}
function onPage(page: number) {
  s.value.page = page
  runSearch(false)
}
function onPageSize(size: number) {
  s.value.pageSize = size
  runSearch(true)
}

onMounted(() => {
  loadFilterOptions()
  loadStatus()
})

// ── Upload + on-demand import (job pattern, same as Systems/HR) ─────────────
const uploadOpen = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const uploadFiles = ref<File[]>([])
const importNowLoading = ref(false)
const errorDialogOpen = ref(false)

const status = ref<OrgImportStatus | null>(null)

async function loadStatus() {
  try {
    status.value = await organizationApi.status()
  } catch {
    status.value = null
  }
}

function onOpenUpload() {
  uploadError.value = ''
  uploadFiles.value = []
  uploadOpen.value = true
}

async function onDownloadTemplate() {
  try {
    await organizationApi.downloadTemplate()
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

function onFilePicked(files: File[]) {
  uploadFiles.value = files
  uploadError.value = ''
  const file = files[0]
  if (!file) return
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    uploadError.value = t('app.org.fileTypeError')
  } else if (file.size > 10 * 1024 * 1024) {
    uploadError.value = t('app.org.fileSizeError')
  }
}

const canUpload = computed(() => uploadFiles.value.length > 0 && !uploading.value && !uploadError.value)

async function doUpload() {
  const file = uploadFiles.value[0]
  if (!file) return
  uploading.value = true
  uploadError.value = ''
  try {
    status.value = await organizationApi.uploadFile(file)
    toastSuccess(t('app.org.uploadedToastOrg'))
    uploadOpen.value = false
    await runSearch(true)
  } catch (err) {
    uploadError.value = toUserError(err).message
  } finally {
    uploading.value = false
  }
}

async function doImportNow() {
  if (!status.value?.pendingFile) {
    toastError(t('app.org.noPendingFile'))
    return
  }
  importNowLoading.value = true
  try {
    status.value = await organizationApi.importNow()
    const last = status.value.lastImport
    if (last?.status === 'SUCCESS') {
      toastSuccess(t('app.org.importedOrgToast', {
        success: last.successCount ?? 0,
        errors: last.errorCount ?? 0,
        matched: last.matchedCount ?? 0,
        deactivated: last.deactivatedCount ?? 0,
      }))
      if ((last.errorCount ?? 0) > 0) {
        errorDialogOpen.value = true
      }
      await runSearch(true)
    } else if (last?.status === 'FAILED') {
      toastError(t('app.org.importFailedToast', { message: last.errorMessage ?? 'unknown error' }))
    }
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    importNowLoading.value = false
  }
}

const errorRows = computed<OrgImportRowResult[]>(() => status.value?.lastImport?.errorRows ?? [])

// ── Error rows filters + pagination (same pattern as Systems/HR) ────────────
const errorKnoxFilter = ref('')
const errorMessageFilter = ref('')

const errorKnoxOptions = computed(() =>
  [...new Set(errorRows.value.map((r) => r.knoxId).filter(Boolean))].sort()
    .map((v) => ({ label: v, value: v }))
)

const errorMessageOptions = computed(() =>
  [...new Set(errorRows.value.flatMap((r) => r.errors))].sort()
    .map((v) => ({ label: v, value: v }))
)

const filteredErrorRows = computed(() =>
  errorRows.value.filter((r) => {
    if (errorKnoxFilter.value && r.knoxId !== errorKnoxFilter.value) return false
    if (errorMessageFilter.value && !r.errors.includes(errorMessageFilter.value)) return false
    return true
  })
)

const ERROR_PAGE_SIZES = [10, 20, 50, 100]
const errorPage = ref(1)
const errorPageSize = ref(20)

watch([errorKnoxFilter, errorMessageFilter, errorPageSize], () => {
  errorPage.value = 1
})

const pagedErrorRows = computed(() => {
  const start = (errorPage.value - 1) * errorPageSize.value
  return filteredErrorRows.value.slice(start, start + errorPageSize.value)
})

// ── Grid ────────────────────────────────────────────────────────────────────
const columns = computed(() => [
  { key: 'knoxId', label: t('app.org.colKnoxId') },
  { key: 'userName', label: t('app.org.colUserName') },
  { key: 'employed', label: t('app.org.colEmployed') },
  { key: 'regularPartner', label: t('app.org.colRegularPartner') },
  { key: 'company', label: t('app.org.colCompany') },
  { key: 'division', label: t('app.org.colDivision') },
  { key: 'center', label: t('app.org.colCenter') },
  { key: 'group', label: t('app.org.colGroup') },
  { key: 'part', label: t('app.org.colPart') },
  { key: 'employeeNumber', label: t('app.org.colEmployeeNumber') },
  { key: 'phone', label: t('app.org.colPhone') },
  { key: 'email', label: t('app.org.colEmail') },
  { key: 'position', label: t('app.org.colPosition') },
  { key: 'matchedUser', label: t('app.org.colMatchedUser') },
])

const errorColumns = computed(() => [
  { key: 'rowNumber', label: t('app.org.colRow') },
  { key: 'knoxId', label: t('app.org.colKnoxId') },
  { key: 'userName', label: t('app.org.colUserName') },
  { key: 'errors', label: t('app.org.colErrorMessage') },
])
</script>

<template>
  <div class="om">
    <PrismTabs v-model="activeTab" :items="[
      { value: 'organization', label: t('app.org.tabOrganization') },
      { value: 'systems', label: t('app.org.tabSystems') },
      { value: 'hr', label: t('app.org.tabHr') },
    ]">
      <!-- Organization tab (existing content) -->
      <template #organization>
      <div class="om-tab">
    <!-- Search area -->
    <section class="search p-glass" :aria-label="t('app.org.orgSearchAria')" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput v-model="s.knoxId" :label="t('app.org.filterKnoxId')" :placeholder="t('app.org.searchContains')" />
        <PrismInput v-model="s.userName" :label="t('app.org.filterUserName')" :placeholder="t('app.org.searchContains')" />
        <PrismInput v-model="s.company" :label="t('app.org.filterCompany')" :placeholder="t('app.org.searchContains')" />
        <PrismCombobox
          v-model="s.division"
          :label="t('app.org.filterDivision')"
          :placeholder="t('app.org.selectDivision')"
          clearable
          :options="divisionOptions"
        />
        <PrismCombobox
          v-model="s.center"
          :label="t('app.org.filterCenter')"
          :placeholder="t('app.org.selectCenter')"
          clearable
          :options="centerOptions"
        />
        <PrismCombobox
          v-model="s.group"
          :label="t('app.org.filterGroup')"
          :placeholder="t('app.org.selectGroup')"
          clearable
          :options="groupOptions"
        />
        <PrismCombobox
          v-model="s.part"
          :label="t('app.org.filterPart')"
          :placeholder="t('app.org.selectPart')"
          clearable
          :options="partOptions"
        />
        <PrismCombobox
          v-model="s.employed"
          :label="t('app.org.filterEmployed')"
          :placeholder="t('app.org.selectAll')"
          clearable
          :options="EMPLOYED_OPTIONS"
        />
      </div>
      <div class="search-actions">
        <PrismButton variant="secondary" type="button" @click="onOpenUpload">
          <Upload :size="16" aria-hidden="true" /> {{ t('app.org.uploadExcel') }}
        </PrismButton>
        <PrismButton
          variant="secondary"
          type="button"
          :loading="importNowLoading"
          :disabled="!status?.pendingFile || status?.running"
          @click="doImportNow"
        >
          <Play :size="16" aria-hidden="true" /> {{ t('app.org.importNow') }}
        </PrismButton>
        <PrismButton variant="secondary" type="button" @click="onDownloadTemplate">
          <Download :size="16" aria-hidden="true" /> {{ t('app.org.downloadTemplate') }}
        </PrismButton>
        <span class="search-actions-spacer" aria-hidden="true"></span>
        <PrismButton type="button" :loading="loading" @click="onSearch">{{ t('app.org.search') }}</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">{{ t('app.org.reset') }}</PrismButton>
      </div>

      <!-- Import status -->
      <div v-if="status?.running" class="om-status" role="status">
        <strong>{{ t('app.org.importing') }}</strong>
      </div>
      <div v-else-if="status?.pendingFile" class="om-status" role="status">
        {{ t('app.org.pendingFileOrg', {
          file: status.pendingFile.fileName,
          time: formatDateTime(status.pendingFile.uploadedAt) || '—',
          by: status.pendingFile.uploadedBy,
        }) }}
      </div>
      <div v-else-if="status?.lastImport" class="om-status" role="status">
        {{ t('app.org.lastImport', { time: formatDateTime(status.lastImport.finishedAt) || '—' }) }}
        <template v-if="status.lastImport.status === 'SUCCESS'">
          {{ t('app.org.lastImportOrgSuccess', {
            success: status.lastImport.successCount ?? 0,
            errors: status.lastImport.errorCount ?? 0,
            matched: status.lastImport.matchedCount ?? 0,
            deactivated: status.lastImport.deactivatedCount ?? 0,
          }) }}
          <span v-if="(status.lastImport.errorCount ?? 0) > 0" class="is-error">
            <strong>{{ status.lastImport.errorCount }}</strong> {{ t('app.org.errors') }}
            <button type="button" class="om-link" @click="errorDialogOpen = true">{{ t('app.org.viewErrors') }}</button>
          </span>
        </template>
        <template v-else>
          <span class="is-error">{{ t('app.org.failed', { message: status.lastImport.errorMessage ?? '' }) }}</span>
        </template>
      </div>
    </section>

    <!-- Grid -->
    <section class="grid p-glass" :aria-label="t('app.org.orgListAria')">
      <PrismEmptyState
        v-if="!hasSearched && !loading && !error"
        :title="t('app.org.noData')"
        :description="t('app.org.noDataDesc', { what: t('app.org.tabOrganization') })"
      />
      <PrismEmptyState v-else-if="error" :title="t('app.org.loadError', { what: t('app.org.tabOrganization') })">
        <p class="grid-error-text">{{ error }}</p>
        <PrismButton variant="secondary" type="button" @click="onSearch">{{ t('app.translations.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <PrismTable
          :columns="columns"
          :rows="result?.items ?? []"
          row-key="id"
          :loading="loading"
          :caption="t('app.org.total', { count: result?.totalCount ?? 0 })"
          :empty-text="t('app.org.noData')"
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
              {{ (row as OrgListItem).employed === 'Y' ? t('app.org.employedYes') : t('app.org.employedNo') }}
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
          <template #cell-division="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).division ?? undefined">
              {{ (row as OrgListItem).division ?? '—' }}
            </span>
          </template>
          <template #cell-center="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).center ?? undefined">
              {{ (row as OrgListItem).center ?? '—' }}
            </span>
          </template>
          <template #cell-group="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).group ?? undefined">
              {{ (row as OrgListItem).group ?? '—' }}
            </span>
          </template>
          <template #cell-part="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).part ?? undefined">
              {{ (row as OrgListItem).part ?? '—' }}
            </span>
          </template>
          <template #cell-employeeNumber="{ row }">
            {{ (row as OrgListItem).employeeNumber ?? '—' }}
          </template>
          <template #cell-phone="{ row }">
            {{ (row as OrgListItem).phone ?? '—' }}
          </template>
          <template #cell-email="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).email ?? undefined">
              {{ (row as OrgListItem).email ?? '—' }}
            </span>
          </template>
          <template #cell-position="{ row }">
            <span class="om-truncate" :title="(row as OrgListItem).position ?? undefined">
              {{ (row as OrgListItem).position ?? '—' }}
            </span>
          </template>
          <template #cell-matchedUser="{ row }">
            <span v-if="(row as OrgListItem).matchedUserEmail" class="om-truncate" :title="(row as OrgListItem).matchedUserEmail ?? undefined">
              {{ (row as OrgListItem).matchedUserEmail }}
            </span>
            <PrismBadge v-else tone="neutral">{{ t('app.org.notMatched') }}</PrismBadge>
          </template>
        </PrismTable>

        <div class="om-pager">
          <div class="om-pagesize">
            <span class="om-pagesize-label">{{ t('app.translations.rows') }}</span>
            <PrismCombobox
              :model-value="s.pageSize"
              readonly
              :aria-label="t('app.translations.rows')"
              :options="PAGE_SIZES.map((size) => ({ value: size, label: String(size) }))"
              @update:model-value="onPageSize(Number($event))"
            />
          </div>
          <PrismPagination
            :page="s.page"
            :page-size="s.pageSize"
            :total="result?.totalCount ?? 0"
            @update:page="onPage"
          />
        </div>
      </template>
    </section>
      </div>
      </template>

      <!-- Systems tab -->
      <template #systems>
        <SystemsTab />
      </template>

      <!-- Human Resource tab -->
      <template #hr>
        <HrSystemsTab />
      </template>
    </PrismTabs>

    <!-- Upload dialog -->
    <PrismDialog v-model="uploadOpen" :title="t('app.org.uploadDialogTitle')"
      :description="t('app.org.uploadDescOrg')">
      <div class="om-import-body">
        <PrismFileUpload
          :model-value="uploadFiles"
          :label="t('app.org.fileLabelOrg')"
          accept=".xlsx,.xls"
          :max-size="10 * 1024 * 1024"
          :hint="t('app.org.uploadHintOrg')"
          @update:model-value="onFilePicked($event as File[])"
        />
        <button type="button" class="om-template-link" @click="onDownloadTemplate">
          <Download :size="14" aria-hidden="true" /> {{ t('app.org.downloadTemplate') }}
        </button>
        <p v-if="uploadError" class="p-field-error" role="alert">{{ uploadError }}</p>
        <p v-else-if="uploadFiles.length > 0 && !uploading" class="om-import-ready" role="status">
          {{ t('app.org.fileReadyOrg', { file: uploadFiles[0]?.name ?? '' }) }}
        </p>
      </div>
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="uploadOpen = false">{{ t('app.org.close') }}</PrismButton>
        <PrismButton type="button" :loading="uploading" :disabled="!canUpload" @click="doUpload">
          {{ t('app.org.upload') }}
        </PrismButton>
      </template>
    </PrismDialog>

    <!-- Import error rows dialog -->
    <PrismDialog v-model="errorDialogOpen" :title="t('app.org.errorDialogTitle')" size="lg" dialog-class="om-dialog-xl"
      :description="t('app.org.errorDialogDesc')">
      <div class="om-error-filters">
        <PrismCombobox
          v-model="errorKnoxFilter"
          :label="t('app.org.colKnoxId')"
          :placeholder="t('app.org.errorAllKeys')"
          clearable
          :options="errorKnoxOptions"
        />
        <PrismCombobox
          v-model="errorMessageFilter"
          :label="t('app.org.colErrorMessage')"
          :placeholder="t('app.org.errorAllMessages')"
          clearable
          :options="errorMessageOptions"
        />
      </div>
      <PrismTable
        :columns="errorColumns"
        :rows="pagedErrorRows"
        row-key="rowNumber"
        :caption="t('app.org.total', { count: filteredErrorRows.length })"
        :empty-text="t('app.org.errorNoRows')"
      >
        <template #cell-errors="{ row }">
          <span class="om-error" :title="(row as OrgImportRowResult).errors.join(' ')">
            {{ (row as OrgImportRowResult).errors.join(' ') }}
          </span>
        </template>
      </PrismTable>
      <div class="om-pager">
        <div class="om-pagesize">
          <span class="om-pagesize-label">{{ t('app.translations.rows') }}</span>
          <PrismCombobox
            :model-value="errorPageSize"
            readonly
            :aria-label="t('app.translations.rows')"
            :options="ERROR_PAGE_SIZES.map((size) => ({ value: size, label: String(size) }))"
            @update:model-value="errorPageSize = Number($event)"
          />
        </div>
        <PrismPagination
          :page="errorPage"
          :page-size="errorPageSize"
          :total="filteredErrorRows.length"
          @update:page="errorPage = $event"
        />
      </div>
      <template #footer>
        <PrismButton variant="secondary" type="button" @click="errorDialogOpen = false">{{ t('app.org.close') }}</PrismButton>
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
/* Keep PrismTabs panel from stretching the page — the table scrolls inside the grid. */
.om :deep(.p-tabs),
.om :deep(.p-tabpanel) {
  min-width: 0;
}
.om-tab {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
  min-width: 0;
}

.search {
  padding: var(--ds-space-5);
  display: grid;
  gap: var(--ds-space-4);
  min-width: 0;
}
.search-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
  min-width: 0;
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
/* Rows (page size) — compact: label + 30px combobox, narrow width. */
.om-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-shrink: 0;
}
.om-pagesize-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ds-color-text-secondary);
}
.om-pagesize :deep(.p-combobox) {
  width: 84px;
  font-size: 12px;
}
.om-pagesize :deep(.p-input-wrap),
.om-pagesize :deep(.p-input-wrap) input {
  min-height: 30px;
  font-size: 12px;
  padding: 0 var(--ds-space-2);
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
.om-status {
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.om-status .is-error {
  color: var(--ds-color-danger);
}
.om-link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: var(--ds-color-accent);
  cursor: pointer;
  text-decoration: underline;
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
  max-width: 640px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--ds-color-danger);
}
.om-error-filters {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ds-space-4);
}
@media (max-width: 900px) {
  .om-error-filters {
    grid-template-columns: 1fr;
  }
}
</style>

<!-- Non-scoped: the dialog is teleported to <body> via PrismDialog. -->
<style>
.om-dialog-xl {
  width: min(1200px, calc(100vw - 32px));
}
</style>
