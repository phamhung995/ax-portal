<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import TaxonomyChipSelect from './TaxonomyChipSelect.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import type { SkillDetailResponse, TaxonomyOption } from '@/features/skills/types'

const props = defineProps<{ open: boolean; skillId: number | null }>()
const emit = defineEmits(['update:open', 'saved'])

const SCOPE_OPTIONS = [
  { label: 'Private', value: 'PRIVATE' },
  { label: 'Part', value: 'PART' },
  { label: 'Group', value: 'GROUP' },
  { label: 'Public', value: 'PUBLIC' },
]
const HASHTAG_PATTERN = /^[A-Za-z0-9_-]+$/
const MAX_HASHTAGS = 10
const MAX_HASHTAG_LENGTH = 30
const ALLOWED_EXTENSIONS = ['.zip', '.7z', '.json', '.yaml', '.yml', '.txt', '.md', '.py', '.js', '.ts']
const MAX_FILE_SIZE = 10 * 1024 * 1024

const name = ref('')
const scope = ref('PRIVATE')
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
const confirmCancel = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const categories = ref<TaxonomyOption[]>([])
const stages = ref<TaxonomyOption[]>([])

async function loadTaxonomy() {
  try {
    const t = await skillApi.taxonomy()
    categories.value = t.categories
    stages.value = t.stages
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
  scope: string
  description: string
  hashtags: string[]
  categoryIds: number[]
  stageIds: number[]
}
const snapshot = ref<Snapshot | null>(null)
const detail = ref<SkillDetailResponse | null>(null)

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

function fillFrom(detailData: SkillDetailResponse) {
  name.value = detailData.name
  scope.value = detailData.scope
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
  snapshot.value = {
    name: detailData.name,
    scope: detailData.scope,
    description: detailData.description ?? '',
    hashtags: [...detailData.hashtags],
    categoryIds: [...categoryIds.value],
    stageIds: [...stageIds.value],
  }
}

watch(
  () => props.open,
  async (open) => {
    if (!open || !props.skillId) return
    detail.value = null
    snapshot.value = null
    if (!categories.value.length) await loadTaxonomy()
    try {
      const d = await skillApi.detail(props.skillId)
      detail.value = d
      fillFrom(d)
    } catch {
      toastError('Unable to load skill information.')
      emit('update:open', false)
    }
  }
)

function formDirty(): boolean {
  const snap = snapshot.value
  if (!snap) return false
  return (
    name.value.trim() !== snap.name ||
    scope.value !== snap.scope ||
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
    toastError('Hashtag is at most 30 characters.')
    return
  }
  if (!HASHTAG_PATTERN.test(raw)) {
    toastError('Hashtag contains only letters, digits, "-" and "_".')
    return
  }
  if (hashtags.value.length >= MAX_HASHTAGS) {
    toastError('Maximum 10 hashtags.')
    return
  }
  if (hashtags.value.some((t) => t.toLowerCase() === raw.toLowerCase())) {
    toastError('Hashtag already exists.')
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
    fileError.value = `"${selected.name}": file format is not supported.`
    return
  }
  if (selected.size > MAX_FILE_SIZE) {
    fileError.value = `"${selected.name}": file size is at most 10MB.`
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
  if (!name.value.trim()) {
    nameError.value = 'Skill name cannot be empty.'
    return false
  }
  if (name.value.length > 100) {
    nameError.value = 'Skill name is at most 100 characters.'
    return false
  }
  if (!categoryIds.value.length) {
    categoryError.value = 'Select at least one category.'
    return false
  }
  if (!stageIds.value.length) {
    stageError.value = 'Select at least one process stage.'
    return false
  }
  if (!newFiles.value.length) {
    fileError.value = 'Upload at least one new file to update the version.'
    return false
  }
  return true
}

async function save() {
  if (!validate() || !props.skillId) return
  saving.value = true
  try {
    await skillApi.updateVersion(props.skillId, {
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      publicScope: scope.value,
      hashtags: hashtags.value,
      categoryIds: categoryIds.value,
      stageIds: stageIds.value,
      files: newFiles.value.length ? newFiles.value : undefined,
      removeFileIds: removeFileIds.value.length ? removeFileIds.value : undefined,
    })
    toastSuccess('Version update submitted for review.')
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
    title="Update version"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div v-if="!detail" class="uv-loading">Loading…</div>
    <div v-else class="uv">
      <PrismAlert tone="info">
        This update will be reviewed before going live. The current version
        stays available until it is approved.
      </PrismAlert>

      <PrismInput
        v-model="name"
        label="Skill name"
        required
        placeholder="Enter skill name..."
        :error="nameError"
        :maxlength="100"
      >
        <template #suffix>
          <small class="uv-count" :class="{ 'is-warn': nameCountWarn }">{{ nameCount }}/100</small>
        </template>
      </PrismInput>

      <PrismCombobox v-model="scope" label="Visibility scope" required :options="SCOPE_OPTIONS" />

      <TaxonomyChipSelect
        v-model="categoryIds"
        label="Category"
        required
        tone="category"
        :options="categories"
        :error="categoryError"
        hint="A skill can belong to several categories."
      />

      <TaxonomyChipSelect
        v-model="stageIds"
        label="Process Stage"
        required
        tone="stage"
        :options="stages"
        :error="stageError"
        hint="Pick every stage this skill applies to."
      />

      <div class="uv-desc">
        <RichTextEditor
          v-model="description"
          label="Description"
          placeholder="Enter skill description..."
          :rows="4"
        />
      </div>

      <div class="uv-field">
        <label class="p-label">Hashtag</label>
        <div class="uv-tags" role="group" aria-label="Hashtags">
          <span v-for="(tag, i) in hashtags" :key="tag" class="uv-tag">
            #{{ tag }}
            <button type="button" class="uv-tag-remove" aria-label="Remove hashtag" @click="removeTag(i)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="uv-tag-input"
            placeholder="Type a hashtag and press Enter..."
            @keydown="onTagKeydown"
          />
        </div>
      </div>

      <div class="uv-field">
        <label class="p-label">Files <span class="p-required" aria-hidden="true">*</span></label>
        <p class="uv-file-hint">At least one new file is required. Click Delete on a current file to remove it in the new version.</p>
        <div v-if="detail.files.length" class="uv-file-list">
          <div v-for="f in detail.files" :key="f.id" class="uv-file-row">
            <span class="uv-file-name" :class="{ 'is-removed': removeFileIds.includes(f.id) }" :title="f.fileName">
              📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})
            </span>
            <button type="button" class="uv-file-delete" @click="toggleRemoveFile(f.id)">
              {{ removeFileIds.includes(f.id) ? 'Undo' : 'Delete' }}
            </button>
          </div>
        </div>
        <div v-if="newFiles.length" class="uv-file-list">
          <div v-for="(f, i) in newFiles" :key="i" class="uv-file-row">
            <span class="uv-file-name" :title="f.name">📄 {{ f.name }} ({{ formatSize(f.size) }})</span>
            <button type="button" class="uv-file-delete" @click="removeNewFile(i)">Remove</button>
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
            Drag &amp; drop new files here or <span class="uv-file-link">click to browse</span>
          </button>
          <small class="uv-file-meta">Supported: .zip, .7z, .json, .yaml, .yml, .txt, .md, .py, .js, .ts · max 10MB each</small>
        </div>
        <p v-if="fileError" class="p-field-error" role="alert">{{ fileError }}</p>
      </div>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">Cancel</PrismButton>
      <PrismButton :loading="saving" :disabled="!detail" @click="save">Submit for Review</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    title="Discard changes?"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="uv-confirm-text">Are you sure you want to cancel? The changes you made will not be saved.</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">Keep editing</PrismButton>
      <PrismButton @click="confirmDiscard">Discard</PrismButton>
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
  justify-items: start;
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
