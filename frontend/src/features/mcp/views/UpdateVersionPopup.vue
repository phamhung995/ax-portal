<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import TaxonomyChipSelect from '@/features/skills/views/TaxonomyChipSelect.vue'
import { mcpApi } from '@/features/mcp/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { McpDetailResponse } from '@/features/mcp/types'
import type { TaxonomyOption } from '@/features/skills/types'

const props = defineProps<{ open: boolean; mcpId: number | null }>()
const emit = defineEmits(['update:open', 'saved'])
const { t } = useI18nStore()

const HASHTAG_PATTERN = /^[A-Za-z0-9_-]+$/
const MAX_HASHTAGS = 10
const MAX_HASHTAG_LENGTH = 30
const ALLOWED_EXTENSIONS = ['.zip', '.7z', '.json', '.yaml', '.yml', '.txt', '.md', '.py', '.js', '.ts']
const MAX_FILE_SIZE = 10 * 1024 * 1024

const name = ref('')
const description = ref('')
const hashtags = ref<string[]>([])
const categoryIds = ref<number[]>([])
const stageIds = ref<number[]>([])
const tagInput = ref('')
const newFiles = ref<File[]>([])
const removeFileIds = ref<number[]>([])
const saving = ref(false)
const nameError = ref('')
const categoryError = ref('')
const stageError = ref('')
const fileError = ref('')
const descError = ref('')
const confirmCancel = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

// ── Confirm before a version update that goes to PENDING_APPROVAL ──────────
// An MCP version update ALWAYS goes to PENDING_APPROVAL (admin approval).
const versionConfirmOpen = ref(false)
let versionConfirmAction: (() => Promise<void>) | null = null

const categories = ref<TaxonomyOption[]>([])
const stages = ref<TaxonomyOption[]>([])

async function loadTaxonomy() {
  try {
    const taxonomy = await mcpApi.taxonomy()
    categories.value = taxonomy.categories
    stages.value = taxonomy.stages
  } catch {
    // non-fatal
  }
}

function categoryIdByName(name: string): number | undefined {
  return categories.value.find((c) => c.name === name)?.id
}
function stageIdByName(name: string): number | undefined {
  return stages.value.find((s) => s.name === name)?.id
}

// Snapshot of the values when the popup opened (for dirty check).
interface Snapshot {
  name: string
  description: string
  hashtags: string[]
  categoryIds: number[]
  stageIds: number[]
}
const snapshot = ref<Snapshot | null>(null)
const detail = ref<McpDetailResponse | null>(null)

const nameCount = computed(() => name.value.length)
const nameCountWarn = computed(() => nameCount.value >= 90)

function fileInputClick() {
  fileInputRef.value?.click()
}

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

function fillFrom(detailData: McpDetailResponse) {
  name.value = detailData.name
  description.value = detailData.description ?? ''
  hashtags.value = [...detailData.hashtags]
  categoryIds.value = detailData.categories
    .map((n) => categoryIdByName(n))
    .filter((id): id is number => id != null)
  stageIds.value = detailData.stages
    .map((n) => stageIdByName(n))
    .filter((id): id is number => id != null)
  tagInput.value = ''
  newFiles.value = []
  removeFileIds.value = []
  nameError.value = ''
  categoryError.value = ''
  stageError.value = ''
  fileError.value = ''
  descError.value = ''
  snapshot.value = {
    name: detailData.name,
    description: detailData.description ?? '',
    hashtags: [...detailData.hashtags],
    categoryIds: [...categoryIds.value],
    stageIds: [...stageIds.value],
  }
}

watch(
  () => props.open,
  async (open) => {
    if (!open || !props.mcpId) return
    detail.value = null
    snapshot.value = null
    versionConfirmOpen.value = false
    versionConfirmAction = null
    if (!categories.value.length) await loadTaxonomy()
    try {
      const d = await mcpApi.detail(props.mcpId)
      detail.value = d
      fillFrom(d)
    } catch {
      toastError(t('app.mcp.loadErrorDetail'))
      emit('update:open', false)
    }
  }
)

function formDirty(): boolean {
  const snap = snapshot.value
  if (!snap) return false
  return (
    name.value.trim() !== snap.name ||
    description.value.trim() !== snap.description ||
    JSON.stringify(hashtags.value) !== JSON.stringify(snap.hashtags) ||
    JSON.stringify(categoryIds.value) !== JSON.stringify(snap.categoryIds) ||
    JSON.stringify(stageIds.value) !== JSON.stringify(snap.stageIds) ||
    newFiles.value.length > 0 ||
    removeFileIds.value.length > 0
  )
}

function addTag() {
  const raw = tagInput.value.trim().replace(/^#/, '')
  tagInput.value = ''
  if (!raw) return
  if (raw.length > MAX_HASHTAG_LENGTH) {
    toastError(t('app.common.hashtagTooLong'))
    return
  }
  if (!HASHTAG_PATTERN.test(raw)) {
    toastError(t('app.common.hashtagInvalidChars'))
    return
  }
  if (hashtags.value.length >= MAX_HASHTAGS) {
    toastError(t('app.common.hashtagMax'))
    return
  }
  if (hashtags.value.some((h) => h.toLowerCase() === raw.toLowerCase())) {
    toastError(t('app.common.hashtagExists'))
    return
  }
  hashtags.value = [...hashtags.value, raw]
}

function onTagKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    addTag()
  } else if (event.key === 'Backspace' && !tagInput.value && hashtags.value.length) {
    hashtags.value = hashtags.value.slice(0, -1)
  }
}

function removeTag(index: number) {
  hashtags.value = hashtags.value.filter((_, i) => i !== index)
}

function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files ? Array.from(input.files) : []
  input.value = ''
  for (const f of selected) acceptFile(f)
}

function onDrop(event: DragEvent) {
  const dropped = event.dataTransfer?.files
  if (dropped) {
    for (const f of Array.from(dropped)) acceptFile(f)
  }
}

function acceptFile(selected: File) {
  const dot = selected.name.lastIndexOf('.')
  const ext = dot >= 0 ? selected.name.slice(dot).toLowerCase() : ''
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    fileError.value = t('app.common.fileFormatError', { name: selected.name })
    return
  }
  if (selected.size > MAX_FILE_SIZE) {
    fileError.value = t('app.common.fileSizeError10', { name: selected.name })
    return
  }
  fileError.value = ''
  newFiles.value = [...newFiles.value, selected]
}

function removeNewFile(index: number) {
  newFiles.value = newFiles.value.filter((_, i) => i !== index)
  fileError.value = ''
}

function toggleRemoveFile(fileId: number) {
  removeFileIds.value = removeFileIds.value.includes(fileId)
    ? removeFileIds.value.filter((id) => id !== fileId)
    : [...removeFileIds.value, fileId]
}

function validate(): boolean {
  nameError.value = ''
  categoryError.value = ''
  stageError.value = ''
  fileError.value = ''
  descError.value = ''
  // Fields sit inside a scrollable popup while Submit is in the footer — a
  // field error alone is easy to miss → toast the first failing message.
  if (!name.value.trim()) {
    nameError.value = t('app.mcp.nameEmpty')
    toastError(t('app.mcp.nameEmpty'))
    return false
  }
  if (name.value.length > 100) {
    nameError.value = t('app.mcp.nameTooLong')
    toastError(t('app.mcp.nameTooLong'))
    return false
  }
  if (!description.value.trim()) {
    descError.value = t('app.mcp.descRequired')
    toastError(t('app.mcp.descRequired'))
    return false
  }
  if (!categoryIds.value.length) {
    categoryError.value = t('app.common.selectCategory')
    toastError(t('app.common.selectCategory'))
    return false
  }
  if (!stageIds.value.length) {
    stageError.value = t('app.common.selectStage')
    toastError(t('app.common.selectStage'))
    return false
  }
  if (!newFiles.value.length) {
    fileError.value = t('app.mcp.updateFileRequired')
    toastError(t('app.mcp.updateFileRequired'))
    return false
  }
  return true
}

async function save() {
  if (!validate() || !props.mcpId) return
  // A version update always goes to PENDING_APPROVAL — confirm first.
  versionConfirmAction = doSave
  versionConfirmOpen.value = true
}

async function confirmVersionUpdate() {
  versionConfirmOpen.value = false
  const action = versionConfirmAction
  versionConfirmAction = null
  if (action) await action()
}

async function doSave() {
  if (!props.mcpId) return
  if (!validate()) return
  saving.value = true
  try {
    await mcpApi.updateVersion(props.mcpId, {
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      hashtags: hashtags.value,
      categoryIds: categoryIds.value,
      stageIds: stageIds.value,
      files: newFiles.value.length ? newFiles.value : undefined,
      removeFileIds: removeFileIds.value.length ? removeFileIds.value : undefined,
    })
    toastSuccess(t('app.mcp.updateSubmitted'))
    emit('saved')
    emit('update:open', false)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    saving.value = false
  }
}

function requestClose() {
  if (saving.value) return
  if (formDirty()) {
    confirmCancel.value = true
  } else {
    emit('update:open', false)
  }
}

function confirmDiscard() {
  confirmCancel.value = false
  emit('update:open', false)
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    :title="t('app.mcp.updateTitle')"
    size="lg"
    dialog-class="skill-form-dialog"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div v-if="!detail" class="uv-loading">{{ t('app.common.loading') }}</div>
    <div v-else class="uv">
      <PrismAlert tone="info">
        {{ t('app.mcp.updateHint') }}
      </PrismAlert>

      <PrismInput
        v-model="name"
        :label="t('app.mcp.name')"
        required
        :placeholder="t('app.mcp.namePh')"
        :error="nameError"
        :maxlength="100"
      >
        <template #suffix>
          <small class="uv-count" :class="{ 'is-warn': nameCountWarn }">{{ t('app.common.charCount', { count: nameCount, max: 100 }) }}</small>
        </template>
      </PrismInput>

      <TaxonomyChipSelect
        v-model="categoryIds"
        :label="t('app.common.category')"
        required
        tone="category"
        :options="categories"
        :error="categoryError"
        :hint="t('app.common.categoryHint')"
      />

      <TaxonomyChipSelect
        v-model="stageIds"
        :label="t('app.common.processStage')"
        required
        tone="stage"
        :options="stages"
        :error="stageError"
        :hint="t('app.common.processStageHint')"
      />

      <div class="uv-desc">
        <RichTextEditor
          v-model="description"
          :label="t('app.common.description')"
          :placeholder="t('app.mcp.descriptionPh')"
          :rows="4"
          required
        />
        <p v-if="descError" class="p-field-error" role="alert">{{ descError }}</p>
      </div>

      <div class="uv-field">
        <label class="p-label">{{ t('app.common.hashtag') }}</label>
        <div class="uv-tags" role="group" :aria-label="t('app.common.hashtags')">
          <span v-for="(tag, i) in hashtags" :key="tag" class="uv-tag">
            #{{ tag }}
            <button type="button" class="uv-tag-remove" :aria-label="t('app.common.removeHashtag')" @click="removeTag(i)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="uv-tag-input"
            :placeholder="t('app.common.typeHashtagPh')"
            @keydown="onTagKeydown"
          />
        </div>
      </div>

      <div class="uv-field">
        <label class="p-label">{{ t('app.common.files') }} <span class="p-required" aria-hidden="true">*</span></label>
        <p class="uv-file-hint">{{ t('app.mcp.updateFilesHint') }}</p>
        <div v-if="detail.files.length" class="uv-file-list">
          <div v-for="f in detail.files" :key="f.id" class="uv-file-row">
            <span class="uv-file-name" :class="{ 'is-removed': removeFileIds.includes(f.id) }" :title="f.fileName">
              📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})
            </span>
            <button type="button" class="uv-file-delete" @click="toggleRemoveFile(f.id)">
              {{ removeFileIds.includes(f.id) ? t('app.common.undo') : t('app.common.delete') }}
            </button>
          </div>
        </div>
        <div v-if="newFiles.length" class="uv-file-list">
          <div v-for="(f, i) in newFiles" :key="i" class="uv-file-row">
            <span class="uv-file-name" :title="f.name">📄 {{ f.name }} ({{ formatSize(f.size) }})</span>
            <button type="button" class="uv-file-delete" @click="removeNewFile(i)">{{ t('app.common.remove') }}</button>
          </div>
        </div>
        <div class="uv-file-drop" @dragover.prevent @drop.prevent="onDrop">
          <input
            ref="fileInputRef"
            type="file"
            class="uv-file-input"
            multiple
            :accept="ALLOWED_EXTENSIONS.join(',')"
            @change="onFileSelected"
          />
          <button type="button" class="uv-file-drop-btn" @click="fileInputClick">
            {{ t('app.mcp.dragDropNewOr') }} <span class="uv-file-link">{{ t('app.common.clickToBrowse') }}</span>
          </button>
          <small class="uv-file-meta">{{ t('app.common.supportedSkillFiles') }}</small>
        </div>
        <p v-if="fileError" class="p-field-error" role="alert">{{ fileError }}</p>
      </div>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="saving" :disabled="!detail" @click="save">{{ t('app.mcp.submitForReview') }}</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.common.discardTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="uv-confirm-text">{{ t('app.common.discardDescChanges') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.keepEditing') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.discard') }}</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="versionConfirmOpen"
    :title="t('app.mcp.versionConfirmTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="versionConfirmOpen = $event"
  >
    <p class="uv-confirm-text">{{ t('app.mcp.versionConfirmDesc') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="versionConfirmOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton @click="confirmVersionUpdate">{{ t('app.common.confirm') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.uv {
  display: grid;
  gap: var(--ds-space-4);
}
.uv-loading {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-secondary);
}
.uv-count {
  font-size: 12px;
  color: var(--ds-color-text-secondary);
  white-space: nowrap;
}
.uv-count.is-warn {
  color: var(--ds-color-danger);
}
.uv-desc {
  display: grid;
  gap: var(--ds-space-1);
}
.uv-field {
  display: grid;
  gap: var(--ds-space-2);
}
.uv-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  padding: var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
}
.uv-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 2px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.uv-tag-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
@media (hover: hover) { .uv-tag-remove:hover { color: var(--ds-color-danger); } }
.uv-tag-input {
  flex: 1;
  min-width: 160px;
  border: none;
  background: none;
  color: var(--ds-color-text-primary);
  font: inherit;
  padding: 2px 0;
  outline: none;
}
.uv-file-hint {
  margin: 0;
  color: var(--ds-color-text-muted);
  font-size: 12px;
}
.uv-file-list {
  display: grid;
  gap: var(--ds-space-2);
}
.uv-file-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.uv-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}
.uv-file-name.is-removed {
  text-decoration: line-through;
  color: var(--ds-color-text-muted);
}
.uv-file-delete {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-danger);
  font: inherit;
  font-size: 13px;
}
.uv-file-drop {
  display: grid;
  gap: var(--ds-space-2);
  justify-items: center;
  padding: var(--ds-space-4);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  text-align: center;
}
.uv-file-input {
  display: none;
}
.uv-file-drop-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-primary);
  font: inherit;
}
.uv-file-link {
  color: var(--ds-color-accent);
}
.uv-file-meta {
  color: var(--ds-color-text-secondary);
  font-size: 12px;
}
.uv-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
