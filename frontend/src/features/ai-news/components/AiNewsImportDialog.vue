<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircle2, AlertTriangle, XCircle, Upload } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import { aiNewsApi } from '@/features/ai-news/api'
import { useI18nStore } from '@/stores/i18n'
import { toUserError } from '@/lib/api'
import { toast } from '@/lib/toast'
import type { MhtImportResult } from '@/features/ai-news/types'

const { t } = useI18nStore()

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'imported'): void
}>()

const MAX_FILES = 20
const MAX_FILE_BYTES = 20 * 1024 * 1024
const MAX_TOTAL_BYTES = 40 * 1024 * 1024

const files = ref<File[]>([])
const importing = ref(false)
const results = ref<MhtImportResult[] | null>(null)
const requestError = ref('')

const totalBytes = computed(() => files.value.reduce((s, f) => s + (f.size || 0), 0))
const overTotalLimit = computed(() => totalBytes.value > MAX_TOTAL_BYTES)
const insertedCount = computed(() => results.value?.filter((r) => r.status === 'INSERTED').length ?? 0)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      files.value = []
      results.value = null
      requestError.value = ''
    }
  },
)

function close() {
  if (importing.value) return
  emit('update:modelValue', false)
  if (results.value) emit('imported')
}

async function onImport() {
  if (files.value.length === 0 || importing.value || overTotalLimit.value) return
  importing.value = true
  requestError.value = ''
  try {
    results.value = await aiNewsApi.adminImport(files.value)
    const total = results.value.length
    const ok = insertedCount.value
    toast({
      title: t('aiNews.importDone', { n: ok, total }),
      tone: ok === total ? 'success' : 'warning',
    })
  } catch (error) {
    requestError.value = toUserError(error).message
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <PrismDialog
    :model-value="modelValue"
    :title="t('aiNews.importTitle')"
    size="lg"
    :close-on-overlay="!importing"
    :close-on-escape="!importing"
    @update:model-value="close"
  >
    <!-- Step 1: file selection -->
    <section v-if="!results" class="ainews-import-step">
      <PrismFileUpload
        v-model="files"
        :label="t('aiNews.importFileLabel')"
        accept=".mht"
        multiple
        :max-size="MAX_FILE_BYTES"
        :hint="t('aiNews.importHint')"
      />
      <p v-if="overTotalLimit" class="ainews-import-error">
        {{ t('aiNews.importTotalLimit') }}
      </p>
      <p v-if="requestError" class="ainews-import-error">{{ requestError }}</p>
    </section>

    <!-- Step 2: per-file results -->
    <section v-else class="ainews-import-step">
      <ul class="ainews-import-results">
        <li
          v-for="r in results"
          :key="r.index"
          class="ainews-import-result"
          :class="`is-${r.status.toLowerCase()}`"
        >
          <CheckCircle2 v-if="r.status === 'INSERTED'" :size="16" aria-hidden="true" />
          <AlertTriangle v-else-if="r.status === 'SKIPPED_DUPLICATE'" :size="16" aria-hidden="true" />
          <XCircle v-else :size="16" aria-hidden="true" />
          <span class="ainews-import-result-name" :title="r.fileName">{{ r.fileName }}</span>
          <span class="ainews-import-result-status">
            {{
              r.status === 'INSERTED'
                ? t('aiNews.importInserted')
                : r.status === 'SKIPPED_DUPLICATE'
                  ? t('aiNews.importSkippedDup')
                  : t('aiNews.importFailed')
            }}
          </span>
          <span v-if="r.error" class="ainews-import-result-error">{{ r.error }}</span>
        </li>
      </ul>
    </section>

    <template #footer>
      <PrismButton
        v-if="!results"
        variant="primary"
        :disabled="files.length === 0 || importing || overTotalLimit"
        @click="onImport"
      >
        <Upload :size="15" aria-hidden="true" />
        {{ importing ? t('aiNews.importing') : t('aiNews.importSubmit') }}
      </PrismButton>
      <PrismButton v-else variant="primary" @click="close">
        {{ t('aiNews.importClose') }}
      </PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.ainews-import-step {
  display: grid;
  gap: 12px;
}
.ainews-import-error {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger, #e5534b);
}
.ainews-import-results {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
  max-height: 50vh;
  overflow-y: auto;
}
.ainews-import-result {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--color-border, rgba(255, 255, 255, 0.12));
}
.ainews-import-result.is-inserted {
  color: var(--color-success, #34d399);
}
.ainews-import-result.is-skipped_duplicate {
  color: var(--color-warning, #e5a100);
}
.ainews-import-result.is-failed {
  color: var(--color-danger, #e5534b);
}
.ainews-import-result-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text, #e2e8f0);
}
.ainews-import-result-status {
  flex-shrink: 0;
  font-weight: 600;
}
.ainews-import-result-error {
  flex-shrink: 0;
  max-width: 40%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.8;
}
</style>
