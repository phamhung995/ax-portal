<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Upload, Play } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import { hrSystemApi } from '@/features/orgs/api'
import { toUserError } from '@/lib/api'
import { toastSuccess, toastError } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type {
  HrListItem, HrSearchResult, HrImportStatus, SystemImportRowResult,
} from '@/features/orgs/types'

const i18n = useI18nStore()
const { t, formatDateTime } = i18n

const PAGE_SIZES = [10, 20, 50, 100]

// ── Search state ─────────────────────────────────────────────────────────────
const s = ref({
  knoxId: '',
  userName: '',
  part: '',
  systemKey: '',
  role: '',
  page: 1,
  pageSize: 20,
})
const loading = ref(false)
const error = ref('')
const hasSearched = ref(false)
const result = ref<HrSearchResult | null>(null)

const partOptions = ref<{ label: string; value: string }[]>([])
async function loadFilterOptions() {
  try {
    partOptions.value = (await hrSystemApi.partOptions()).map((v) => ({ label: v, value: v }))
  } catch {
    partOptions.value = []
  }
}

async function runSearch(resetPage = true) {
  if (resetPage) s.value.page = 1
  loading.value = true
  error.value = ''
  try {
    result.value = await hrSystemApi.search({
      knoxId: s.value.knoxId || undefined,
      userName: s.value.userName || undefined,
      part: s.value.part || undefined,
      systemKey: s.value.systemKey || undefined,
      role: s.value.role || undefined,
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
  s.value = { knoxId: '', userName: '', part: '', systemKey: '', role: '', page: 1, pageSize: 20 }
}
function onPage(page: number) {
  s.value.page = page
  runSearch(false)
}
function onPageSize(size: number) {
  s.value.pageSize = size
  runSearch(true)
}

// ── Upload + scheduled import ────────────────────────────────────────────────
const uploadOpen = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const uploadFiles = ref<File[]>([])
const importNowLoading = ref(false)
const errorDialogOpen = ref(false)

const status = ref<HrImportStatus | null>(null)

async function loadStatus() {
  try {
    status.value = await hrSystemApi.status()
  } catch {
    status.value = null
  }
}

function onOpenUpload() {
  uploadError.value = ''
  uploadFiles.value = []
  uploadOpen.value = true
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
    status.value = await hrSystemApi.uploadFile(file)
    toastSuccess(t('app.org.uploadedToast'))
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
    status.value = await hrSystemApi.importNow()
    const last = status.value.lastImport
    if (last?.status === 'SUCCESS') {
      toastSuccess(t('app.org.importedHrToast', { rows: last.rowsCount ?? 0, errors: last.errorCount ?? 0 }))
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

// ── Grid ─────────────────────────────────────────────────────────────────────
const columns = computed(() => [
  { key: 'knoxId', label: t('app.org.colKnoxId') },
  { key: 'userName', label: t('app.org.colUserName') },
  { key: 'employed', label: t('app.org.colEmployed') },
  { key: 'regularPartner', label: t('app.org.colRegularPartner') },
  { key: 'company', label: t('app.org.colCompany') },
  { key: 'part', label: t('app.org.colPart') },
  { key: 'employeeNumber', label: t('app.org.colEmployeeNumber') },
  { key: 'systemKey', label: t('app.org.colSystemKey') },
  { key: 'systemCode', label: t('app.org.colSystemCode') },
  { key: 'role', label: t('app.org.colRole') },
  { key: 'jan', label: t('app.org.colJan') },
  { key: 'feb', label: t('app.org.colFeb') },
  { key: 'mar', label: t('app.org.colMar') },
  { key: 'apr', label: t('app.org.colApr') },
  { key: 'may', label: t('app.org.colMay') },
  { key: 'jun', label: t('app.org.colJun') },
  { key: 'jul', label: t('app.org.colJul') },
  { key: 'aug', label: t('app.org.colAug') },
  { key: 'sep', label: t('app.org.colSep') },
  { key: 'oct', label: t('app.org.colOct') },
  { key: 'nov', label: t('app.org.colNov') },
  { key: 'dec', label: t('app.org.colDec') },
  { key: 'projectKey', label: t('app.org.colProjectKey') },
  { key: 'projectName', label: t('app.org.colProjectName') },
])

// PrismTable keys rows by a single field; build a stable unique id per row.
const gridRows = computed(() =>
  (result.value?.items ?? []).map((it, i) => ({
    ...it,
    id: `${it.knoxId}|${it.systemKey}|${i}`,
  }))
)

onMounted(() => {
  loadFilterOptions()
  loadStatus()
})

// ── Import error rows dialog ─────────────────────────────────────────────────
const errorKeyFilter = ref('')
const errorMessageFilter = ref('')
const errorSheetFilter = ref('')

const errorRows = computed<SystemImportRowResult[]>(() => status.value?.lastImport?.errorRows ?? [])

const errorKeyOptions = computed(() =>
  [...new Set(errorRows.value.map((r) => r.systemKey).filter(Boolean))].sort()
    .map((v) => ({ label: v, value: v }))
)

const errorMessageOptions = computed(() =>
  [...new Set(errorRows.value.flatMap((r) => r.errors))].sort()
    .map((v) => ({ label: v, value: v }))
)

const errorSheetOptions = computed(() =>
  [...new Set(errorRows.value.map((r) => r.sheet))].map((v) => ({ label: v, value: v }))
)

const filteredErrorRows = computed(() =>
  errorRows.value.filter((r) => {
    if (errorKeyFilter.value && r.systemKey !== errorKeyFilter.value) return false
    if (errorMessageFilter.value && !r.errors.includes(errorMessageFilter.value)) return false
    if (errorSheetFilter.value && r.sheet !== errorSheetFilter.value) return false
    return true
  })
)

const ERROR_PAGE_SIZES = [10, 20, 50, 100]
const errorPage = ref(1)
const errorPageSize = ref(20)

watch([errorKeyFilter, errorMessageFilter, errorSheetFilter, errorPageSize], () => {
  errorPage.value = 1
})

const pagedErrorRows = computed(() => {
  const start = (errorPage.value - 1) * errorPageSize.value
  return filteredErrorRows.value.slice(start, start + errorPageSize.value)
})

const errorColumns = computed(() => [
  { key: 'rowNumber', label: t('app.org.colRow') },
  { key: 'sheet', label: t('app.org.colSheet') },
  { key: 'systemKey', label: t('app.org.colSystemKey') },
  { key: 'errors', label: t('app.org.colErrorMessage') },
])
</script>

<template>
  <div class="hr">
    <!-- Search area -->
    <section class="search p-glass" :aria-label="t('app.org.hrSearchAria')" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput v-model="s.knoxId" :label="t('app.org.colKnoxId')" :placeholder="t('app.org.searchContains')" />
        <PrismInput v-model="s.userName" :label="t('app.org.colUserName')" :placeholder="t('app.org.searchContains')" />
        <PrismCombobox
          v-model="s.part"
          :label="t('app.org.colPart')"
          :placeholder="t('app.org.searchSelect')"
          clearable
          :options="partOptions"
        />
        <PrismInput v-model="s.systemKey" :label="t('app.org.colSystemKey')" :placeholder="t('app.org.searchContains')" />
        <PrismInput v-model="s.role" :label="t('app.org.colRole')" :placeholder="t('app.org.searchContains')" />
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
        <span class="search-actions-spacer"></span>
        <PrismButton type="button" :loading="loading" @click="onSearch">{{ t('app.org.search') }}</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">{{ t('app.org.reset') }}</PrismButton>
      </div>

      <!-- Import status -->
      <div v-if="status?.running" class="hr-status" role="status">
        <strong>{{ t('app.org.importing') }}</strong>
      </div>
      <div v-else-if="status?.pendingFile" class="hr-status" role="status">
        {{ t('app.org.pendingFileHr', {
          file: status.pendingFile.fileName,
          time: formatDateTime(status.pendingFile.uploadedAt) || '—',
          by: status.pendingFile.uploadedBy,
        }) }}
      </div>
      <div v-else-if="status?.lastImport" class="hr-status" role="status">
        {{ t('app.org.lastImport', { time: formatDateTime(status.lastImport.finishedAt) || '—' }) }}
        <template v-if="status.lastImport.status === 'SUCCESS'">
          {{ t('app.org.lastImportHrSuccess', { rows: status.lastImport.rowsCount ?? 0 }) }}
          <span v-if="(status.lastImport.errorCount ?? 0) > 0" class="is-error">
            <strong>{{ status.lastImport.errorCount }}</strong> {{ t('app.org.errors') }}
            <button type="button" class="hr-link" @click="errorDialogOpen = true">{{ t('app.org.viewErrors') }}</button>
          </span>
        </template>
        <template v-else>
          <span class="is-error">{{ t('app.org.failed', { message: status.lastImport.errorMessage ?? '' }) }}</span>
        </template>
      </div>
    </section>

    <!-- Grid -->
    <section class="grid p-glass" :aria-label="t('app.org.hrListAria')">
      <PrismEmptyState
        v-if="!hasSearched && !loading && !error"
        :title="t('app.org.noData')"
        :description="t('app.org.noDataDesc', { what: t('app.org.tabHr') })"
      />
      <PrismEmptyState v-else-if="error" :title="t('app.org.loadError', { what: t('app.org.tabHr') })">
        <p class="grid-error-text">{{ error }}</p>
        <PrismButton variant="secondary" type="button" @click="onSearch">{{ t('app.translations.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <div class="hr-table-wrap">
          <PrismTable
            :columns="columns"
            :rows="gridRows"
            row-key="id"
            :loading="loading"
            :caption="t('app.org.total', { count: result?.totalCount ?? 0 })"
            :empty-text="t('app.org.noData')"
          >
            <template #cell-employeeNumber="{ row }">
              {{ (row as HrListItem).employeeNumber ?? '—' }}
            </template>
            <template #cell-systemCode="{ row }">
              {{ (row as HrListItem).systemCode ?? '—' }}
            </template>
            <template #cell-role="{ row }">
              {{ (row as HrListItem).role ?? '—' }}
            </template>
            <template #cell-jan="{ row }">{{ (row as HrListItem).jan ?? '—' }}</template>
            <template #cell-feb="{ row }">{{ (row as HrListItem).feb ?? '—' }}</template>
            <template #cell-mar="{ row }">{{ (row as HrListItem).mar ?? '—' }}</template>
            <template #cell-apr="{ row }">{{ (row as HrListItem).apr ?? '—' }}</template>
            <template #cell-may="{ row }">{{ (row as HrListItem).may ?? '—' }}</template>
            <template #cell-jun="{ row }">{{ (row as HrListItem).jun ?? '—' }}</template>
            <template #cell-jul="{ row }">{{ (row as HrListItem).jul ?? '—' }}</template>
            <template #cell-aug="{ row }">{{ (row as HrListItem).aug ?? '—' }}</template>
            <template #cell-sep="{ row }">{{ (row as HrListItem).sep ?? '—' }}</template>
            <template #cell-oct="{ row }">{{ (row as HrListItem).oct ?? '—' }}</template>
            <template #cell-nov="{ row }">{{ (row as HrListItem).nov ?? '—' }}</template>
            <template #cell-dec="{ row }">{{ (row as HrListItem).dec ?? '—' }}</template>
            <template #cell-projectKey="{ row }">
              <span class="hr-truncate" :title="(row as HrListItem).projectKey ?? undefined">
                {{ (row as HrListItem).projectKey ?? '—' }}
              </span>
            </template>
            <template #cell-projectName="{ row }">
              <span class="hr-truncate" :title="(row as HrListItem).projectName ?? undefined">
                {{ (row as HrListItem).projectName ?? '—' }}
              </span>
            </template>
          </PrismTable>
        </div>

        <div class="hr-pager">
          <div class="hr-pagesize">
            <span class="hr-pagesize-label">{{ t('app.translations.rows') }}</span>
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

    <!-- Upload dialog -->
    <PrismDialog v-model="uploadOpen" :title="t('app.org.uploadTitle', { what: t('app.org.tabHr') })"
      :description="t('app.org.uploadDescHr')">
      <div class="hr-import-body">
        <PrismFileUpload
          :model-value="uploadFiles"
          :label="t('app.org.fileLabel', { what: t('app.org.tabHr') })"
          accept=".xlsx,.xls"
          :max-size="10 * 1024 * 1024"
          :hint="t('app.org.uploadHintHr')"
          @update:model-value="onFilePicked($event as File[])"
        />
        <p v-if="uploadError" class="p-field-error" role="alert">{{ uploadError }}</p>
        <p v-else-if="uploadFiles.length > 0 && !uploading" class="hr-import-ready" role="status">
          {{ t('app.org.fileReady', { file: uploadFiles[0]?.name ?? '' }) }}
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
    <PrismDialog v-model="errorDialogOpen" :title="t('app.org.errorDialogTitle')" size="lg" dialog-class="hr-dialog-xl"
      :description="t('app.org.errorDialogDesc')">
      <div class="hr-error-filters">
        <PrismCombobox
          v-model="errorKeyFilter"
          :label="t('app.org.colSystemKey')"
          :placeholder="t('app.org.errorAllKeys')"
          clearable
          :options="errorKeyOptions"
        />
        <PrismCombobox
          v-model="errorMessageFilter"
          :label="t('app.org.colErrorMessage')"
          :placeholder="t('app.org.errorAllMessages')"
          clearable
          :options="errorMessageOptions"
        />
        <PrismCombobox
          v-model="errorSheetFilter"
          :label="t('app.org.colSheet')"
          :placeholder="t('app.org.errorAllSheets')"
          clearable
          :options="errorSheetOptions"
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
          <span class="hr-error" :title="(row as SystemImportRowResult).errors.join(' ')">
            {{ (row as SystemImportRowResult).errors.join(' ') }}
          </span>
        </template>
      </PrismTable>
      <div class="hr-pager">
        <div class="hr-pagesize">
          <span class="hr-pagesize-label">{{ t('app.translations.rows') }}</span>
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
.hr {
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
.hr-table-wrap {
  overflow-x: auto;
}
.hr-truncate {
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
.hr-status {
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.hr-status .is-error {
  color: var(--ds-color-danger);
}
.hr-link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: var(--ds-color-accent);
  cursor: pointer;
  text-decoration: underline;
}
.hr-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
.hr-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-shrink: 0;
}
.hr-pagesize-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ds-color-text-secondary);
}
.hr-pagesize :deep(.p-combobox) {
  width: 84px;
  font-size: 12px;
}
.hr-pagesize :deep(.p-input-wrap),
.hr-pagesize :deep(.p-input-wrap) input {
  min-height: 30px;
  font-size: 12px;
  padding: 0 var(--ds-space-2);
}
.hr-import-body {
  display: grid;
  gap: var(--ds-space-3);
}
.hr-import-ready {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.hr-error {
  display: inline-block;
  max-width: 640px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--ds-color-danger);
}
.hr-error-filters {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--ds-space-4);
}
@media (max-width: 900px) {
  .hr-error-filters {
    grid-template-columns: 1fr;
  }
}
</style>

<!-- Non-scoped: the dialog is teleported to <body> via PrismDialog. -->
<style>
.hr-dialog-xl {
  width: min(1200px, calc(100vw - 32px));
}
</style>
