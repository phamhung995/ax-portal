<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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

const props = withDefaults(defineProps<{ open: boolean; mcpId: number | null; canManageFiles?: boolean }>(), {
  canManageFiles: true,
})
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

const nameReadOnly = computed(
  () => detail.value?.status === 'APPROVED' && !detail.value?.isAdmin
)
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
  fileError.value = ''
}

function validate(): boolean {
  nameError.value = ''
  categoryError.value = ''
  stageError.value = ''
  descError.value = ''
  // Fields sit inside a scrollable popup while Save is in the footer — a
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
  if (props.canManageFiles && detail.value && (detail.value.files.length - removeFileIds.value.length + newFiles.value.length) <= 0) {
    fileError.value = t('app.mcp.fileRequired')
    toastError(t('app.mcp.fileRequired'))
    return false
  }
  return true
}

async function save() {
  if (!validate() || !props.mcpId || !detail.value) return
  saving.value = true
  try {
    await mcpApi.update(props.mcpId, {
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      hashtags: hashtags.value,
      categoryIds: categoryIds.value,
      stageIds: stageIds.value,
      files: props.canManageFiles && newFiles.value.length ? newFiles.value : undefined,
      removeFileIds: props.canManageFiles && removeFileIds.value.length ? removeFileIds.value : undefined,
      updatedAt: detail.value.updatedAt,
    })
    toastSuccess(t('app.mcp.updatedToast'))
    emit('saved')
    emit('update:open', false)
  } catch (err) {
    const userError = toUserError(err)
    if (userError.code === 'MCP_OPTIMISTIC_LOCK') {
      toastError(userError.message)
      emit('update:open', false)
      return
    }
    toastError(userError.message)
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
    :title="t('app.mcp.editTitle')"
    size="lg"
    dialog-class="skill-form-dialog"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div v-if="!detail" class="edt-loading">{{ t('app.common.loading') }}</div>
    <div v-else class="edt">
      <PrismAlert v-if="nameReadOnly" tone="info">
        {{ t('app.mcp.nameLocked') }}
      </PrismAlert>

      <PrismInput
        v-model="name"
        :label="t('app.mcp.name')"
        required
        :placeholder="t('app.mcp.namePh')"
        :error="nameError"
        :maxlength="100"
        :readonly="nameReadOnly"
      >
        <template #suffix>
          <small class="edt-count" :class="{ 'is-warn': nameCountWarn }">{{ t('app.common.charCount', { count: nameCount, max: 100 }) }}</small>
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

      <div class="edt-desc">
        <RichTextEditor
          v-model="description"
          :label="t('app.common.description')"
          :placeholder="t('app.mcp.descriptionPh')"
          :rows="4"
          required
        />
        <p v-if="descError" class="p-field-error" role="alert">{{ descError }}</p>
      </div>

      <div class="edt-field">
        <label class="p-label">{{ t('app.common.hashtag') }}</label>
        <div class="edt-tags" role="group" :aria-label="t('app.common.hashtags')">
          <span v-for="(tag, i) in hashtags" :key="tag" class="edt-tag">
            #{{ tag }}
            <button type="button" class="edt-tag-remove" :aria-label="t('app.common.removeHashtag')" @click="removeTag(i)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="edt-tag-input"
            :placeholder="t('app.common.typeHashtagPh')"
            @keydown="onTagKeydown"
          />
        </div>
      </div>

      <div v-if="!props.canManageFiles" class="edt-field">
        <label class="p-label">{{ t('app.common.files') }}</label>
        <div v-if="detail.files.length" class="edt-file-list">
          <div v-for="f in detail.files" :key="f.id" class="edt-file-row">
            <span class="edt-file-name" :title="f.fileName">
              📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})
            </span>
          </div>
        </div>
        <p v-else class="edt-file-empty">{{ t('app.common.noFilesAttached') }}</p>
        <p class="edt-file-locked">
          {{ t('app.mcp.approvedFilesHint') }}
        </p>
      </div>

      <div v-else class="edt-field">
        <label class="p-label">{{ t('app.common.files') }}</label>
        <input
          ref="fileInputRef"
          type="file"
          class="edt-file-input"
          multiple
          :accept="ALLOWED_EXTENSIONS.join(',')"
          @change="onFileSelected"
        />
        <div v-if="detail.files.length" class="edt-file-list">
          <div v-for="f in detail.files" :key="f.id" class="edt-file-row">
            <span class="edt-file-name" :class="{ 'is-removed': removeFileIds.includes(f.id) }" :title="f.fileName">
              📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})
            </span>
            <button type="button" class="edt-file-delete" @click="toggleRemoveFile(f.id)">
              {{ removeFileIds.includes(f.id) ? t('app.common.undo') : t('app.common.delete') }}
            </button>
          </div>
        </div>
        <div v-if="newFiles.length" class="edt-file-list">
          <div v-for="(f, i) in newFiles" :key="i" class="edt-file-row">
            <span class="edt-file-name" :title="f.name">📄 {{ f.name }} ({{ formatSize(f.size) }})</span>
            <button type="button" class="edt-file-delete" @click="removeNewFile(i)">{{ t('app.common.remove') }}</button>
          </div>
        </div>
        <div
          class="edt-file-drop"
          @dragover.prevent
          @drop.prevent="onDrop"
        >
          <button type="button" class="edt-file-drop-btn" @click="fileInputClick">
            {{ t('app.common.dragDropOr') }} <span class="edt-file-link">{{ t('app.common.clickToBrowse') }}</span>
          </button>
          <small class="edt-file-meta">{{ t('app.common.supportedSkillFiles') }}</small>
        </div>
        <p v-if="fileError" class="p-field-error" role="alert">{{ fileError }}</p>
      </div>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="saving" :disabled="!detail" @click="save">{{ t('app.common.save') }}</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.common.discardTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="edt-confirm-text">{{ t('app.common.discardDescChanges') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.keepEditing') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.discard') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.edt {
  display: grid;
  gap: var(--ds-space-4);
}
.edt-loading {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-secondary);
}
.edt-count {
  font-size: 12px;
  color: var(--ds-color-text-secondary);
  white-space: nowrap;
}
.edt-count.is-warn {
  color: var(--ds-color-danger);
}
.edt-desc {
  display: grid;
  gap: var(--ds-space-1);
}
.edt-field {
  display: grid;
  gap: var(--ds-space-2);
}
.edt-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  padding: var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
}
.edt-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 2px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(94 106 210 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.edt-tag-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
@media (hover: hover) { .edt-tag-remove:hover { color: var(--ds-color-danger); } }
.edt-tag-input {
  flex: 1;
  min-width: 160px;
  border: none;
  background: none;
  color: var(--ds-color-text-primary);
  font: inherit;
  padding: 2px 0;
  outline: none;
}
.edt-file-list {
  display: grid;
  gap: var(--ds-space-2);
}
.edt-file-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.edt-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}
.edt-file-name.is-removed {
  text-decoration: line-through;
  color: var(--ds-color-text-muted);
}
.edt-file-delete {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-danger);
  font: inherit;
  font-size: 13px;
}
.edt-file-drop {
  display: grid;
  gap: var(--ds-space-2);
  justify-items: center;
  padding: var(--ds-space-4);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  text-align: center;
}
.edt-file-input {
  display: none;
}
.edt-file-drop-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-primary);
  font: inherit;
}
.edt-file-link {
  color: var(--ds-color-accent);
}
.edt-file-meta {
  color: var(--ds-color-text-secondary);
  font-size: 12px;
}
.edt-file-empty {
  margin: 0;
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.edt-file-locked {
  margin: 0;
  color: var(--ds-color-text-muted);
  font-size: 12px;
}
.edt-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
