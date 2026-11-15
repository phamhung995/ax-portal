<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import PrismTable from '@/design-system/components/PrismTable.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import { adminUserApi } from '@/features/users/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { BulkRowResult, BulkUploadResponse } from '@/features/users/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'saved'])
const { t } = useI18nStore()

const files = ref<File[]>([])
const uploading = ref(false)
const uploadError = ref('')
const fileName = ref('')

const result = ref<BulkUploadResponse | null>(null)
const saving = ref(false)
const saveError = ref('')
const confirmCancel = ref(false)

const successRows = computed(() =>
  (result.value?.rows ?? []).filter((r) => r.status === 'SUCCESS')
)
const hasResult = computed(() => result.value != null)

function resetForm() {
  files.value = []
  uploading.value = false
  uploadError.value = ''
  fileName.value = ''
  result.value = null
  saving.value = false
  saveError.value = ''
}

watch(
  () => props.open,
  (open) => {
    if (open) resetForm()
  }
)

async function onFileChange() {
  uploadError.value = ''
  result.value = null
  saveError.value = ''
  if (!files.value.length) {
    fileName.value = ''
    return
  }
  const file = files.value[0]
  if (!file) return
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    uploadError.value = t('app.users.fileTypeError')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    uploadError.value = t('app.users.fileSizeError')
    return
  }
  fileName.value = file.name
  uploading.value = true
  try {
    result.value = await adminUserApi.bulkUpload(file)
  } catch (err) {
    uploadError.value = toUserError(err).message
  } finally {
    uploading.value = false
  }
}

async function onSave() {
  if (!result.value) return
  saving.value = true
  saveError.value = ''
  try {
    const res = await adminUserApi.bulkSave(successRows.value as BulkRowResult[])
    if (res.savedCount > 0) emit('saved')
    toastSuccess(
      t('app.users.bulkDoneToast', { saved: res.savedCount, skipped: res.skippedCount })
    )
    emit('update:open', false)
  } catch (err) {
    saveError.value = toUserError(err).message
  } finally {
    saving.value = false
  }
}

function requestClose() {
  if (saving.value || uploading.value) return
  if (hasResult.value) {
    confirmCancel.value = true
  } else {
    emit('update:open', false)
  }
}

function confirmDiscard() {
  confirmCancel.value = false
  emit('update:open', false)
}

const columns = computed(() => [
  { key: 'rowNumber', label: t('app.users.colRow') },
  { key: 'email', label: t('app.users.bulkColEmail') },
  { key: 'status', label: t('app.users.colResult') },
  { key: 'errors', label: t('app.users.colErrorMessage') },
])
</script>

<template>
  <PrismDialog
    :model-value="open"
    :title="t('app.users.bulkTitle')"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <!-- Step 1: file selection -->
    <section v-if="!hasResult" class="bu-step">
      <PrismFileUpload
        :model-value="files"
        :label="t('app.users.bulkFileLabel')"
        accept=".xlsx,.xls"
        :max-size="10 * 1024 * 1024"
        :hint="t('app.users.bulkHint')"
        @update:model-value="files = $event as File[]; onFileChange()"
      />
      <p v-if="uploadError" class="p-field-error" role="alert">{{ uploadError }}</p>
      <PrismButton
        type="button"
        :loading="uploading"
        :disabled="!files.length"
        @click="onFileChange"
      >
        {{ t('app.users.upload') }}
      </PrismButton>
    </section>

    <!-- Step 2: validation result -->
    <section v-else class="bu-step">
      <div class="bu-summary">
        <p class="bu-file">{{ t('app.users.fileLabel', { name: fileName }) }}</p>
        <div class="bu-counts">
          <span>{{ t('app.users.totalRows') }} <strong>{{ (result as BulkUploadResponse).totalRows }}</strong></span>
          <span class="is-success">{{ t('app.users.successCount') }} <strong>{{ (result as BulkUploadResponse).successCount }}</strong></span>
          <span class="is-error">{{ t('app.users.errorCount') }} <strong>{{ (result as BulkUploadResponse).errorCount }}</strong></span>
        </div>
      </div>

      <p v-if="saveError" class="p-field-error" role="alert">{{ saveError }}</p>

      <PrismTable
        :columns="columns"
        :rows="(result as BulkUploadResponse).rows"
        row-key="rowNumber"
        :caption="t('app.users.bulkResultCaption')"
        :empty-text="t('app.users.bulkNoRows')"
      >
        <template #cell-status="{ row }">
          <PrismBadge :tone="(row as BulkRowResult).status === 'SUCCESS' ? 'success' : 'danger'">
            {{ (row as BulkRowResult).status }}
          </PrismBadge>
        </template>
        <template #cell-errors="{ row }">
          <span v-if="(row as BulkRowResult).errors" class="bu-error" :title="(row as BulkRowResult).errors ?? undefined">
            {{ (row as BulkRowResult).errors }}
          </span>
          <span v-else>—</span>
        </template>
      </PrismTable>
    </section>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving || uploading" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton
        v-if="hasResult"
        type="button"
        :loading="saving"
        :disabled="!successRows.length"
        @click="onSave"
      >
        {{ t('app.common.save') }}
      </PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.users.discardResultTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="bu-confirm-text">{{ t('app.users.discardResultDesc') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.stay') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.leave') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.bu-step {
  display: grid;
  gap: var(--ds-space-4);
}
.bu-summary {
  display: grid;
  gap: var(--ds-space-2);
}
.bu-file {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.bu-counts {
  display: flex;
  gap: var(--ds-space-5);
  font-size: 14px;
}
.bu-counts .is-success {
  color: var(--ds-color-success);
}
.bu-counts .is-error {
  color: var(--ds-color-danger);
}
.bu-error {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--ds-color-danger);
}
.bu-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
