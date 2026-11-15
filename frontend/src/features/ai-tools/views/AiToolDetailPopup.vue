<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import { aiToolApi } from '@/features/ai-tools/api'
import { toUserError } from '@/lib/api'
import { sanitizeHtml } from '@/lib/sanitize'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { AiToolDetailResponse } from '@/features/ai-tools/types'

const props = defineProps<{ toolId: number | null }>()
const emit = defineEmits(['closed', 'edit', 'changed'])
const { t, formatDate } = useI18nStore()

const detail = ref<AiToolDetailResponse | null>(null)
const loading = ref(false)
const downloading = ref(false)
const deleteConfirm = ref(false)
const deleting = ref(false)

/** The uploader (creator) or an admin may edit/delete this tool. */
const canEdit = computed(() => detail.value?.canEdit ?? false)
const descriptionHtml = computed(() => sanitizeHtml(detail.value?.description ?? ''))

function close() {
  emit('closed')
}

async function loadDetail() {
  if (!props.toolId) return
  loading.value = true
  detail.value = null
  try {
    detail.value = await aiToolApi.detail(props.toolId)
  } catch (err) {
    toastError(toUserError(err).message)
    close()
  } finally {
    loading.value = false
  }
}

watch(
  () => props.toolId,
  (id) => {
    if (id) loadDetail()
  },
  { immediate: true }
)

function formatSize(bytes: number): string {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

async function onDownload() {
  if (!detail.value?.file) return
  downloading.value = true
  try {
    await aiToolApi.download(detail.value.id, detail.value.file.id)
    toastSuccess(t('app.common.downloadStarted'))
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    downloading.value = false
  }
}

function onEdit() {
  if (!detail.value) return
  const id = detail.value.id
  close()
  emit('edit', id)
}

async function confirmDelete() {
  if (!detail.value) return
  const id = detail.value.id
  deleteConfirm.value = false
  deleting.value = true
  try {
    await aiToolApi.deleteBatch([id])
    toastSuccess(t('app.aiTools.deletedToast2'))
    close()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <PrismDialog :model-value="toolId !== null" :title="t('app.aiTools.detailTitle')" size="lg" dialog-class="ai-tool-form-dialog" @update:model-value="!$event && close()">
    <div v-if="detail" class="dtl">
      <!-- Row 1: Uploader + Created Date -->
      <div class="dtl-meta">
        <span>{{ t('app.aiTools.uploaderMeta', { name: detail.uploaderName ?? '—' }) }}</span>
        <span>{{ t('app.common.created', { date: formatDate(detail.createdAt) }) }}</span>
      </div>

      <!-- Row 2: Category -->
      <div class="dtl-badges">
        <span class="dtl-badges-label">{{ t('app.skills.categories') }}</span>
        <span class="dtl-badges-row">
          <span v-for="c in detail.categories" :key="c" class="dtl-badge dtl-badge--cat">{{ c }}</span>
          <span v-if="!detail.categories.length" class="dtl-empty">{{ t('app.aiTools.noCategory') }}</span>
        </span>
      </div>

      <!-- Row 3: Process Stage -->
      <div class="dtl-badges">
        <span class="dtl-badges-label">{{ t('app.common.processStage') }}</span>
        <span class="dtl-badges-row">
          <span v-for="st in detail.stages" :key="st" class="dtl-badge dtl-badge--stage">{{ st }}</span>
          <span v-if="!detail.stages.length" class="dtl-empty">{{ t('app.aiTools.noStage') }}</span>
        </span>
      </div>

      <!-- Row 4: Tags -->
      <div class="dtl-hashtags">
        <span v-for="tag in detail.tags" :key="tag" class="dtl-hashtag">#{{ tag }}</span>
        <span v-if="!detail.tags.length" class="dtl-empty">{{ t('app.aiTools.noTags') }}</span>
      </div>

      <!-- Row 5: Download (file + count) -->
      <div class="dtl-files">
        <div v-if="detail.file" class="dtl-file">
          <PrismButton
            class="dtl-file-download-btn"
            type="button"
            :loading="downloading"
            @click="onDownload"
          >
            {{ t('app.common.download') }}
          </PrismButton>
          <span class="dtl-file-name" :title="detail.file.fileName">
            📄 {{ detail.file.fileName }} ({{ formatSize(detail.file.fileSize) }})
          </span>
          <span class="dtl-download-count">↓ {{ t('app.common.downloads', { n: detail.downloadCount }) }}</span>
        </div>
        <div v-else class="dtl-file">
          <span class="dtl-empty">{{ t('app.aiTools.noFile') }}</span>
          <span class="dtl-download-count">↓ {{ t('app.common.downloads', { n: detail.downloadCount }) }}</span>
        </div>
      </div>

      <hr class="dtl-divider" />

      <!-- Row 6: Description (rich text, same as Skill Detail) -->
      <div v-if="descriptionHtml" class="dtl-desc" v-html="descriptionHtml"></div>
      <div v-else class="dtl-desc">{{ t('app.aiTools.noDescription') }}</div>
    </div>
    <div v-else-if="loading" class="dtl-loading">{{ t('app.common.loading') }}</div>

    <template #footer>
      <PrismButton v-if="canEdit" variant="secondary" @click="onEdit">{{ t('app.common.edit') }}</PrismButton>
      <PrismButton v-if="canEdit" variant="danger" @click="deleteConfirm = true">{{ t('app.common.delete') }}</PrismButton>
      <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
    </template>

    <!-- Delete confirm -->
    <PrismDialog
      v-model="deleteConfirm"
      :title="t('app.aiTools.deleteToolTitle')"
      size="sm"
      :close-on-overlay="false"
    >
      <p class="at-confirm-text">
        {{ t('app.aiTools.deleteToolDesc', { name: detail?.name ?? '' }) }}
      </p>
      <template #footer>
        <PrismButton variant="secondary" @click="deleteConfirm = false">{{ t('app.common.cancel') }}</PrismButton>
        <PrismButton variant="danger" :loading="deleting" @click="confirmDelete">{{ t('app.common.delete') }}</PrismButton>
      </template>
    </PrismDialog>
  </PrismDialog>
</template>

<style scoped>
.dtl {
  display: grid;
  gap: var(--ds-space-3);
}
.dtl-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-4);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.dtl-badges {
  display: flex;
  align-items: baseline;
  gap: var(--ds-space-3);
  flex-wrap: wrap;
}
.dtl-badges-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--ds-color-text-muted);
}
.dtl-badges-row {
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
}
.dtl-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 11px;
  white-space: nowrap;
}
.dtl-badge--cat {
  color: var(--ds-color-violet-400, #a58aef);
  background: rgb(165 138 239 / .08);
  border: 1px solid rgb(165 138 239 / .35);
}
.dtl-badge--stage {
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .08);
  border: 1px solid rgb(103 232 249 / .35);
}
.dtl-hashtags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
}
.dtl-hashtag {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.dtl-files {
  display: grid;
  gap: var(--ds-space-2);
}
.dtl-file-download-btn {
  min-height: 34px;
  padding: 0 var(--ds-space-3);
  font-size: 13px;
}
.dtl-download-count {
  color: var(--ds-color-text-muted);
  font: 12px var(--ds-font-mono);
  white-space: nowrap;
}
.dtl-divider {
  border: none;
  border-top: 1px solid var(--ds-color-border);
  margin: 0;
}
.dtl-file {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
.dtl-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}
.dtl-desc {
  overflow-y: auto;
  max-height: 420px;
  color: var(--ds-color-text-primary);
  line-height: 1.6;
  /* Hide the scrollbar but keep wheel/drag scrolling. */
  scrollbar-width: none;
}
.dtl-desc::-webkit-scrollbar {
  display: none;
}
/* Rich text (Tiptap) — same rules as SkillDetailPopup. */
.dtl-desc :deep(p) {
  margin: 0 0 var(--ds-space-2);
}
.dtl-desc :deep(p:last-child) {
  margin-bottom: 0;
}
.dtl-desc :deep(h2),
.dtl-desc :deep(h3) {
  margin: var(--ds-space-3) 0 var(--ds-space-2);
  color: var(--ds-color-text-primary);
}
.dtl-desc :deep(h1) {
  font-size: 20px;
}
.dtl-desc :deep(h2) {
  font-size: 16px;
}
.dtl-desc :deep(h3) {
  font-size: 14px;
}
.dtl-desc :deep(ul),
.dtl-desc :deep(ol) {
  margin: 0 0 var(--ds-space-2);
  padding-left: 22px;
}
.dtl-desc :deep(a) {
  color: var(--ds-color-accent);
}
.dtl-desc :deep(blockquote) {
  margin: 0 0 var(--ds-space-2);
  padding-left: var(--ds-space-3);
  border-left: 3px solid var(--ds-color-border-strong);
  color: var(--ds-color-text-secondary);
}
.dtl-desc :deep(pre) {
  margin: 0 0 var(--ds-space-2);
  padding: var(--ds-space-2) var(--ds-space-3);
  background: var(--ds-input-background);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  overflow-x: auto;
}
.dtl-desc :deep(code) {
  font-family: ui-monospace, monospace;
  font-size: 13px;
}
.dtl-desc :deep(pre code) {
  font-size: inherit;
}
.dtl-desc :deep(mark) {
  background: rgba(250, 204, 21, 0.35);
  color: inherit;
  border-radius: 2px;
}
.dtl-desc :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--ds-radius-sm);
}
.dtl-desc :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0 0 var(--ds-space-2);
  table-layout: fixed;
}
.dtl-desc :deep(th),
.dtl-desc :deep(td) {
  border: 1px solid var(--ds-color-border);
  padding: 4px 8px;
  vertical-align: top;
  text-align: left;
}
.dtl-desc :deep(th) {
  background: var(--ds-color-bg-secondary, rgba(255, 255, 255, 0.04));
  font-weight: 600;
}
.dtl-desc :deep(ul[data-type='taskList']) {
  list-style: none;
  padding-left: 4px;
}
.dtl-desc :deep(li[data-type='taskItem']) {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.dtl-desc :deep(li[data-type='taskItem'] input[type='checkbox']) {
  accent-color: var(--ds-color-accent);
}
.dtl-desc :deep(li[data-type='taskItem'][data-checked='true']) {
  text-decoration: line-through;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
}
.dtl-desc :deep(hr) {
  border: none;
  border-top: 1px solid var(--ds-color-border);
  margin: var(--ds-space-3) 0;
}
.dtl-empty {
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.dtl-loading {
  color: var(--ds-color-text-secondary);
  padding: var(--ds-space-4);
}
.at-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
