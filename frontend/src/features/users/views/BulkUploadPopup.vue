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
import type { BulkRowResult, BulkUploadResponse } from '@/features/users/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'saved'])

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
    uploadError.value = 'Only .xlsx and .xls files are supported.'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    uploadError.value = 'File size is at most 10MB.'
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
      `Bulk upload completed: ${res.savedCount} saved, ${res.skippedCount} skipped.`
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

const columns = [
  { key: 'rowNumber', label: 'Row' },
  { key: 'email', label: 'Email' },
  { key: 'status', label: 'Result' },
  { key: 'errors', label: 'Error Message' },
]
</script>

<template>
  <PrismDialog
    :model-value="open"
    title="Bulk Upload"
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
        label="User file"
        accept=".xlsx,.xls"
        :max-size="10 * 1024 * 1024"
        hint="One file per upload. Template columns: User Name, Email, Password, Confirm Password. Max 500 rows."
        @update:model-value="files = $event as File[]; onFileChange()"
      />
      <p v-if="uploadError" class="p-field-error" role="alert">{{ uploadError }}</p>
      <PrismButton
        type="button"
        :loading="uploading"
        :disabled="!files.length"
        @click="onFileChange"
      >
        Upload
      </PrismButton>
    </section>

    <!-- Step 2: validation result -->
    <section v-else class="bu-step">
      <div class="bu-summary">
        <p class="bu-file">File: {{ fileName }}</p>
        <div class="bu-counts">
          <span>Total Rows: <strong>{{ (result as BulkUploadResponse).totalRows }}</strong></span>
          <span class="is-success">Success: <strong>{{ (result as BulkUploadResponse).successCount }}</strong></span>
          <span class="is-error">Error: <strong>{{ (result as BulkUploadResponse).errorCount }}</strong></span>
        </div>
      </div>

      <p v-if="saveError" class="p-field-error" role="alert">{{ saveError }}</p>

      <PrismTable
        :columns="columns"
        :rows="(result as BulkUploadResponse).rows"
        row-key="rowNumber"
        caption="Bulk upload validation result"
        empty-text="No rows."
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
      <PrismButton variant="secondary" :disabled="saving || uploading" @click="requestClose">Cancel</PrismButton>
      <PrismButton
        v-if="hasResult"
        type="button"
        :loading="saving"
        :disabled="!successRows.length"
        @click="onSave"
      >
        Save
      </PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    title="Discard validation result"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="bu-confirm-text">You have an unsaved validation result. Continue and discard it?</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">Stay</PrismButton>
      <PrismButton @click="confirmDiscard">Leave</PrismButton>
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
