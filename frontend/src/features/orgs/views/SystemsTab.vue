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
import { systemApi } from '@/features/orgs/api'
import { toUserError } from '@/lib/api'
import { toastSuccess, toastError } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type {
  SystemListItem, SystemSearchResult, SystemImportStatus, SystemImportRowResult,
} from '@/features/orgs/types'

const i18n = useI18nStore()
const { t, formatDateTime } = i18n

const PAGE_SIZES = [10, 20, 50, 100]

// ── Search state ─────────────────────────────────────────────────────────────
const s = ref({
  systemKey: '',
  systemName: '',
  part: '',
  phase: '',
  page: 1,
  pageSize: 20,
})
const loading = ref(false)
const error = ref('')
const hasSearched = ref(false)
const result = ref<SystemSearchResult | null>(null)

const partOptions = ref<{ label: string; value: string }[]>([])
async function loadFilterOptions() {
  try {
    partOptions.value = (await systemApi.partOptions()).map((v) => ({ label: v, value: v }))
  } catch {
    partOptions.value = []
  }
}

async function runSearch(resetPage = true) {
  if (resetPage) s.value.page = 1
  loading.value = true
  error.value = ''
  try {
    result.value = await systemApi.search({
      systemKey: s.value.systemKey || undefined,
      systemName: s.value.systemName || undefined,
      part: s.value.part || undefined,
      phase: s.value.phase || undefined,
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
  s.value = { systemKey: '', systemName: '', part: '', phase: '', page: 1, pageSize: 20 }
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

// ── Error rows pagination ────────────────────────────────────────────────────
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

const status = ref<SystemImportStatus | null>(null)

async function loadStatus() {
  try {
    status.value = await systemApi.status()
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
    status.value = await systemApi.uploadFile(file)
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
    status.value = await systemApi.importNow()
    const last = status.value.lastImport
    if (last?.status === 'SUCCESS') {
      toastSuccess(
        t('app.org.importedSystemsToast', {
          systems: last.systemsCount ?? 0,
          subSystems: last.subSystemsCount ?? 0,
          errors: last.errorCount ?? 0,
        }),
      )
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
  { key: 'systemKey', label: t('app.org.colSystemKey') },
  { key: 'systemName', label: t('app.org.colSystemName') },
  { key: 'currentPhase', label: t('app.org.colCurrentPhase') },
  { key: 'clientOfSds', label: t('app.org.colClientOfSds') },
  { key: 'gdcGroup', label: t('app.org.colGdcGroup') },
  { key: 'gdcPart', label: t('app.org.colGdcPart') },
  { key: 'gdcTpmMain', label: t('app.org.colGdcTpmMain') },
  { key: 'projectKey', label: t('app.org.colProjectKey') },
  { key: 'projectName', label: t('app.org.colProjectName') },
  { key: 'subSystemCode', label: t('app.org.colSubSystemCode') },
  { key: 'subSystemName', label: t('app.org.colSubSystemName') },
  { key: 'subProjectKey', label: t('app.org.colSubProjectKey') },
])

const errorColumns = computed(() => [
  { key: 'rowNumber', label: t('app.org.colRow') },
  { key: 'sheet', label: t('app.org.colSheet') },
  { key: 'systemKey', label: t('app.org.colSystemKey') },
  { key: 'errors', label: t('app.org.colErrorMessage') },
])

// PrismTable keys rows by a single field; build a stable unique id per row.
const gridRows = computed(() =>
  (result.value?.items ?? []).map((it, i) => ({
    ...it,
    id: `${it.systemKey}|${it.subSystemCode ?? ''}|${i}`,
  }))
)

onMounted(() => {
  loadFilterOptions()
  loadStatus()
})
</script>

<template>
  <div class="sys">
    <!-- Search area -->
    <section class="search p-glass" :aria-label="t('app.org.sysSearchAria')" @keydown="onSearchKeydown">
      <div class="search-grid">
        <PrismInput v-model="s.systemKey" :label="t('app.org.colSystemKey')" :placeholder="t('app.org.searchContains')" />
        <PrismInput v-model="s.systemName" :label="t('app.org.colSystemName')" :placeholder="t('app.org.searchContains')" />
        <PrismInput v-model="s.phase" :label="t('app.org.colCurrentPhase')" :placeholder="t('app.org.searchExact')" />
        <PrismCombobox
          v-model="s.part"
          :label="t('app.org.colGdcPart')"
          :placeholder="t('app.org.searchSelect')"
          clearable
          :options="partOptions"
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
        <span class="search-actions-spacer"></span>
        <PrismButton type="button" :loading="loading" @click="onSearch">{{ t('app.org.search') }}</PrismButton>
        <PrismButton variant="secondary" type="button" @click="onReset">{{ t('app.org.reset') }}</PrismButton>
      </div>

      <!-- Import status -->
      <div v-if="status?.running" class="sys-status" role="status">
        <strong>{{ t('app.org.importing') }}</strong>
      </div>
      <div v-else-if="status?.pendingFile" class="sys-status" role="status">
        {{ t('app.org.pendingFile', {
          file: status.pendingFile.fileName,
          time: formatDateTime(status.pendingFile.uploadedAt) || '—',
          by: status.pendingFile.uploadedBy,
        }) }}
      </div>
      <div v-else-if="status?.lastImport" class="sys-status" role="status">
        {{ t('app.org.lastImport', { time: formatDateTime(status.lastImport.finishedAt) || '—' }) }}
        <template v-if="status.lastImport.status === 'SUCCESS'">
          {{ t('app.org.lastImportSuccess', {
            systems: status.lastImport.systemsCount ?? 0,
            subSystems: status.lastImport.subSystemsCount ?? 0,
          }) }}
          <span v-if="(status.lastImport.errorCount ?? 0) > 0" class="is-error">
            <strong>{{ status.lastImport.errorCount }}</strong> {{ t('app.org.errors') }}
            <button type="button" class="sys-link" @click="errorDialogOpen = true">{{ t('app.org.viewErrors') }}</button>
          </span>
        </template>
        <template v-else>
          <span class="is-error">{{ t('app.org.failed', { message: status.lastImport.errorMessage ?? '' }) }}</span>
        </template>
      </div>
    </section>

    <!-- Grid -->
    <section class="grid p-glass" :aria-label="t('app.org.sysListAria')">
      <PrismEmptyState
        v-if="!hasSearched && !loading && !error"
        :title="t('app.org.noData')"
        :description="t('app.org.noDataDesc', { what: t('app.org.tabSystems') })"
      />
      <PrismEmptyState v-else-if="error" :title="t('app.org.loadError', { what: t('app.org.tabSystems') })">
        <p class="grid-error-text">{{ error }}</p>
        <PrismButton variant="secondary" type="button" @click="onSearch">{{ t('app.translations.retry') }}</PrismButton>
      </PrismEmptyState>
      <template v-else>
        <PrismTable
          :columns="columns"
          :rows="gridRows"
          row-key="id"
          :loading="loading"
          :caption="t('app.org.total', { count: result?.totalCount ?? 0 })"
          :empty-text="t('app.org.noData')"
        >
          <template #cell-subSystemCode="{ row }">
            {{ (row as SystemListItem).subSystemCode ?? '—' }}
          </template>
          <template #cell-subSystemName="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).subSystemName ?? undefined">
              {{ (row as SystemListItem).subSystemName ?? '—' }}
            </span>
          </template>
          <template #cell-subProjectKey="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).subProjectKey ?? undefined">
              {{ (row as SystemListItem).subProjectKey ?? '—' }}
            </span>
          </template>
          <template #cell-systemKey="{ row }">
            {{ (row as SystemListItem).systemKey }}
          </template>
          <template #cell-systemName="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).systemName">
              {{ (row as SystemListItem).systemName }}
            </span>
          </template>
          <template #cell-currentPhase="{ row }">
            {{ (row as SystemListItem).currentPhase ?? '—' }}
          </template>
          <template #cell-clientOfSds="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).clientOfSds ?? undefined">
              {{ (row as SystemListItem).clientOfSds ?? '—' }}
            </span>
          </template>
          <template #cell-gdcGroup="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).gdcGroup ?? undefined">
              {{ (row as SystemListItem).gdcGroup ?? '—' }}
            </span>
          </template>
          <template #cell-gdcPart="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).gdcPart ?? undefined">
              {{ (row as SystemListItem).gdcPart ?? '—' }}
            </span>
          </template>
          <template #cell-gdcTpmMain="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).gdcTpmMain ?? undefined">
              {{ (row as SystemListItem).gdcTpmMain ?? '—' }}
            </span>
          </template>
          <template #cell-projectKey="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).projectKey ?? undefined">
              {{ (row as SystemListItem).projectKey ?? '—' }}
            </span>
          </template>
          <template #cell-projectName="{ row }">
            <span class="sys-truncate" :title="(row as SystemListItem).projectName ?? undefined">
              {{ (row as SystemListItem).projectName ?? '—' }}
            </span>
          </template>
        </PrismTable>

        <div class="sys-pager">
          <div class="sys-pagesize">
            <span class="sys-pagesize-label">{{ t('app.translations.rows') }}</span>
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
    <PrismDialog v-model="uploadOpen" :title="t('app.org.uploadTitle', { what: t('app.org.tabSystems') })"
      :description="t('app.org.uploadDescSystems')">
      <div class="sys-import-body">
        <PrismFileUpload
          :model-value="uploadFiles"
          :label="t('app.org.fileLabel', { what: t('app.org.tabSystems') })"
          accept=".xlsx,.xls"
          :max-size="10 * 1024 * 1024"
          :hint="t('app.org.uploadHint')"
          @update:model-value="onFilePicked($event as File[])"
        />
        <p v-if="uploadError" class="p-field-error" role="alert">{{ uploadError }}</p>
        <p v-else-if="uploadFiles.length > 0 && !uploading" class="sys-import-ready" role="status">
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
    <PrismDialog v-model="errorDialogOpen" :title="t('app.org.errorDialogTitle')" size="lg" dialog-class="sys-dialog-xl"
      :description="t('app.org.errorDialogDesc')">
      <div class="sys-error-filters">
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
          <span class="sys-error" :title="(row as SystemImportRowResult).errors.join(' ')">
            {{ (row as SystemImportRowResult).errors.join(' ') }}
          </span>
        </template>
      </PrismTable>
      <div class="sys-pager">
        <div class="sys-pagesize">
          <span class="sys-pagesize-label">{{ t('app.translations.rows') }}</span>
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
.sys {
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
.sys-truncate {
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
.sys-status {
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.sys-status .is-error {
  color: var(--ds-color-danger);
}
.sys-link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: var(--ds-color-accent);
  cursor: pointer;
  text-decoration: underline;
}
.sys-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  margin-top: var(--ds-space-4);
  flex-wrap: wrap;
}
.sys-pagesize {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-shrink: 0;
}
.sys-pagesize-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--ds-color-text-secondary);
}
.sys-pagesize :deep(.p-combobox) {
  width: 84px;
  font-size: 12px;
}
.sys-pagesize :deep(.p-input-wrap),
.sys-pagesize :deep(.p-input-wrap) input {
  min-height: 30px;
  font-size: 12px;
  padding: 0 var(--ds-space-2);
}
.sys-import-body {
  display: grid;
  gap: var(--ds-space-3);
}
.sys-import-ready {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.sys-import-summary {
  display: flex;
  gap: var(--ds-space-4);
  flex-wrap: wrap;
  font-size: 13px;
}
.sys-import-summary .is-error {
  color: var(--ds-color-danger);
}
.sys-error {
  display: inline-block;
  max-width: 640px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--ds-color-danger);
}
.sys-error-filters {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--ds-space-4);
}
@media (max-width: 900px) {
  .sys-error-filters {
    grid-template-columns: 1fr;
  }
}
</style>

<!-- Non-scoped: the dialog is teleported to <body> via PrismDialog. -->
<style>
.sys-dialog-xl {
  width: min(1200px, calc(100vw - 32px));
}
</style>
