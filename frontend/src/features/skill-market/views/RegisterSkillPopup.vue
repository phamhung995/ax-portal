<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import TaxonomyChipSelect from '@/features/skills/views/TaxonomyChipSelect.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { TaxonomyOption } from '@/features/skills/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; saved: [skillId: number, skillName: string] }>()
const { t } = useI18nStore()

const SCOPE_OPTIONS = computed(() => [
  { label: t('app.common.scopePrivate'), value: 'PRIVATE' },
  { label: t('app.skill.scopeProject'), value: 'PROJECT' },
  { label: t('app.common.scopePart'), value: 'PART' },
  { label: t('app.common.scopeGroup'), value: 'GROUP' },
  { label: t('app.common.scopePublic'), value: 'PUBLIC' },
])
const HASHTAG_PATTERN = /^[A-Za-z0-9_-]+$/
const MAX_HASHTAGS = 10
const MAX_HASHTAG_LENGTH = 30
const ALLOWED_EXTENSIONS = ['.zip', '.7z', '.json', '.yaml', '.yml', '.txt', '.md', '.py', '.js', '.ts']
const MAX_FILE_SIZE = 10 * 1024 * 1024

const name = ref('')
const scope = ref('PRIVATE')
const systemKey = ref('')
const subSystemCode = ref('')
const description = ref('')
const hashtags = ref<string[]>([])
const categoryIds = ref<number[]>([])
const stageIds = ref<number[]>([])
const files = ref<File[]>([])
const tagInput = ref('')
const saving = ref(false)
const nameError = ref('')
const categoryError = ref('')
const stageError = ref('')
const fileError = ref('')
const confirmCancel = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const categories = ref<TaxonomyOption[]>([])
const stages = ref<TaxonomyOption[]>([])
const mySystems = ref<{ systemKey: string; systemName: string }[]>([])
const subSystems = ref<{ subSystemCode: string; subSystemName: string }[]>([])

const systemOptions = computed(() =>
  mySystems.value.map((s) => ({ label: s.systemName, value: s.systemKey }))
)
const subSystemOptions = computed(() =>
  subSystems.value.map((s) => ({ label: s.subSystemName, value: s.subSystemCode }))
)

async function loadTaxonomy() {
  try {
    const taxonomy = await skillApi.taxonomy()
    categories.value = taxonomy.categories
    stages.value = taxonomy.stages
  } catch {
    // non-fatal
  }
}

async function loadMySystems() {
  try {
    mySystems.value = await skillApi.mySystems()
  } catch {
    mySystems.value = []
  }
}

async function loadSubSystems() {
  if (!systemKey.value) {
    subSystems.value = []
    return
  }
  try {
    subSystems.value = await skillApi.subSystems(systemKey.value)
  } catch {
    subSystems.value = []
  }
}

watch(scope, (v) => {
  if (v === 'PROJECT') {
    if (!mySystems.value.length) loadMySystems()
  } else {
    systemKey.value = ''
    subSystemCode.value = ''
    subSystems.value = []
  }
})

watch(systemKey, (v, old) => {
  subSystemCode.value = ''
  if (v && v !== old) {
    loadSubSystems()
  } else {
    subSystems.value = []
  }
})

onMounted(loadTaxonomy)

function fileInputClick() {
  fileInputRef.value?.click()
}

const nameCount = computed(() => name.value.length)
const nameCountWarn = computed(() => nameCount.value >= 90)

function formDirty() {
  return (
    name.value.trim() !== '' ||
    description.value.trim() !== '' ||
    hashtags.value.length > 0 ||
    categoryIds.value.length > 0 ||
    stageIds.value.length > 0 ||
    files.value.length > 0
  )
}

function resetForm() {
  name.value = ''
  scope.value = 'PRIVATE'
  systemKey.value = ''
  subSystemCode.value = ''
  subSystems.value = []
  description.value = ''
  hashtags.value = []
  categoryIds.value = []
  stageIds.value = []
  tagInput.value = ''
  files.value = []
  nameError.value = ''
  categoryError.value = ''
  stageError.value = ''
  fileError.value = ''
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      resetForm()
      if (!categories.value.length) loadTaxonomy()
    }
  }
)

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
  files.value = [...files.value, selected]
}

function removeFile(index: number) {
  files.value = files.value.filter((_, i) => i !== index)
  fileError.value = ''
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function validate(): boolean {
  nameError.value = ''
  categoryError.value = ''
  stageError.value = ''
  // Fields sit inside a scrollable popup while Save is in the footer — a
  // field error alone is easy to miss → toast the first failing message.
  if (!name.value.trim()) {
    nameError.value = t('app.common.skillNameEmpty')
    toastError(t('app.common.skillNameEmpty'))
    return false
  }
  if (name.value.length > 100) {
    nameError.value = t('app.common.skillNameTooLong')
    toastError(t('app.common.skillNameTooLong'))
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
  return true
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    const skillId = await skillApi.create({
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      publicScope: scope.value,
      systemKey: scope.value === 'PROJECT' ? systemKey.value || undefined : undefined,
      subSystemCode: scope.value === 'PROJECT' ? subSystemCode.value || undefined : undefined,
      hashtags: hashtags.value,
      categoryIds: categoryIds.value,
      stageIds: stageIds.value,
      files: files.value.length ? files.value : undefined,
    })
    toastSuccess(t('app.skillMarket.registeredToast'))
    emit('saved', skillId, name.value.trim())
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
    :title="t('app.skillMarket.registerSkillTitle')"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div class="reg">
      <PrismInput
        v-model="name"
        :label="t('app.common.skillName')"
        required
        :placeholder="t('app.common.skillNamePh')"
        :error="nameError"
        :maxlength="100"
      >
        <template #suffix>
          <small class="reg-count" :class="{ 'is-warn': nameCountWarn }">{{ t('app.common.charCount', { count: nameCount, max: 100 }) }}</small>
        </template>
      </PrismInput>

      <PrismCombobox v-model="scope" :label="t('app.common.visibilityScope')" required :options="SCOPE_OPTIONS" />

      <template v-if="scope === 'PROJECT'">
        <PrismCombobox
          v-model="systemKey"
          :label="t('app.skills.system')"
          :options="systemOptions"
          :placeholder="t('app.skills.systemPh')"
        />
        <PrismCombobox
          v-model="subSystemCode"
          :label="t('app.skills.subSystem')"
          :options="subSystemOptions"
          :disabled="!systemKey"
          :placeholder="t('app.skills.subSystemPh')"
        />
      </template>

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

      <div class="reg-desc">
        <RichTextEditor
          v-model="description"
          :label="t('app.common.description')"
          :placeholder="t('app.common.skillDescPh')"
          :rows="4"
        />
      </div>

      <div class="reg-field">
        <label class="p-label">{{ t('app.common.hashtag') }}</label>
        <div class="reg-tags" role="group" :aria-label="t('app.common.hashtags')">
          <span v-for="(tag, i) in hashtags" :key="tag" class="reg-tag">
            #{{ tag }}
            <button type="button" class="reg-tag-remove" :aria-label="t('app.common.removeHashtag')" @click="removeTag(i)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="reg-tag-input"
            :placeholder="t('app.common.typeHashtagPh')"
            @keydown="onTagKeydown"
          />
        </div>
      </div>

      <div class="reg-field">
        <label class="p-label">{{ t('app.common.files') }}</label>
        <input
          ref="fileInputRef"
          type="file"
          class="reg-file-input"
          multiple
          :accept="ALLOWED_EXTENSIONS.join(',')"
          @change="onFileSelected"
        />
        <div v-if="files.length" class="reg-file-list">
          <div v-for="(f, i) in files" :key="i" class="reg-file-row">
            <span class="reg-file-name" :title="f.name">📄 {{ f.name }} ({{ formatSize(f.size) }})</span>
            <button type="button" class="reg-file-remove" :aria-label="t('app.common.removeFile')" @click="removeFile(i)">×</button>
          </div>
        </div>
        <div
          class="reg-file-drop"
          @dragover.prevent
          @drop.prevent="onDrop"
        >
          <button type="button" class="reg-file-drop-btn" @click="fileInputClick">
            {{ t('app.common.dragDropOr') }} <span class="reg-file-link">{{ t('app.common.clickToBrowse') }}</span>
          </button>
          <small class="reg-file-meta">{{ t('app.common.supportedSkillFiles') }}</small>
        </div>
        <p v-if="fileError" class="p-field-error" role="alert">{{ fileError }}</p>
      </div>

      <PrismAlert tone="info">
        {{ t('app.skillMarket.registerSkillHint') }}
      </PrismAlert>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="saving" @click="save">{{ t('app.skillMarket.registerBtn') }}</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.common.discardTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="reg-confirm-text">{{ t('app.common.discardDescInfo') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.keepEditing') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.discard') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.reg {
  display: grid;
  gap: var(--ds-space-4);
}
.reg-count {
  font-size: 12px;
  color: var(--ds-color-text-secondary);
  white-space: nowrap;
}
.reg-count.is-warn {
  color: var(--ds-color-danger);
}
.reg-desc {
  display: grid;
  gap: var(--ds-space-1);
  justify-items: start;
}
.reg-field {
  display: grid;
  gap: var(--ds-space-2);
}
.reg-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  padding: var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
}
.reg-tag {
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
.reg-tag-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
@media (hover: hover) { .reg-tag-remove:hover { color: var(--ds-color-danger); } }
.reg-tag-input {
  flex: 1;
  min-width: 160px;
  border: none;
  background: none;
  color: var(--ds-color-text-primary);
  font: inherit;
  padding: 2px 0;
  outline: none;
}
.reg-file-list {
  display: grid;
  gap: var(--ds-space-2);
}
.reg-file-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.reg-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}
.reg-file-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font-size: 16px;
}
.reg-file-drop {
  display: grid;
  gap: var(--ds-space-2);
  justify-items: center;
  padding: var(--ds-space-4);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  text-align: center;
}
.reg-file-input {
  display: none;
}
.reg-file-drop-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-primary);
  font: inherit;
}
.reg-file-link {
  color: var(--ds-color-accent);
}
.reg-file-meta {
  color: var(--ds-color-text-secondary);
  font-size: 12px;
}
.reg-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
