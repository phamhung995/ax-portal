<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismCombobox from '@/design-system/components/PrismCombobox.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import TaxonomyChipSelect from './TaxonomyChipSelect.vue'
import CoworkUserSelect from './CoworkUserSelect.vue'
import { skillApi } from '@/features/skills/api'
import { useMcpListStore } from '@/features/mcp/store'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { TaxonomyOption } from '@/features/skills/types'
import McpDependencySection from '@/features/mcp/views/McpDependencySection.vue'
import type { McpDependencyRow } from '@/features/mcp/views/McpDependencySection.vue'
import AddMcpPopup from '@/features/mcp/views/AddMcpPopup.vue'
import BrowseAllMcpPopup from '@/features/mcp/views/BrowseAllMcpPopup.vue'
import type { McpDetailResponse } from '@/features/mcp/types'

const props = withDefaults(defineProps<{
  open: boolean
  /** Show the Cowork user selector (Skill Management). */
  showCowork?: boolean
  /** Require at least one file (Skill Management). */
  filesRequired?: boolean
  /** i18n key for the dialog title. */
  titleKey?: string
  /** i18n key for the save button label. */
  saveLabelKey?: string
  /** i18n key for the info hint at the bottom. */
  hintKey?: string
  /** i18n key for the success toast. */
  successKey?: string
}>(), {
  showCowork: true,
  filesRequired: true,
  titleKey: 'app.skills.addTitle',
  saveLabelKey: 'app.common.save',
  hintKey: 'app.skills.draftHint',
  successKey: 'app.skills.createdToast',
})
const emit = defineEmits(['update:open', 'saved'])
const { t } = useI18nStore()
const mcpList = useMcpListStore()

const SCOPE_OPTIONS = computed(() => [
  { label: t('app.skill.scopePrivate'), value: 'PRIVATE' },
  { label: t('app.skill.scopeProject'), value: 'PROJECT' },
  { label: t('app.skill.scopePart'), value: 'PART' },
  { label: t('app.skill.scopeGroup'), value: 'GROUP' },
  { label: t('app.skill.scopePublic'), value: 'PUBLIC' },
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
const coworkUsers = ref<{ id: number; name: string }[]>([])
const files = ref<File[]>([])
const tagInput = ref('')
const saving = ref(false)
const nameError = ref('')
const categoryError = ref('')
const stageError = ref('')
const fileError = ref('')
const mcpError = ref('')
const descError = ref('')
const confirmCancel = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const mcpDependencies = ref<McpDependencyRow[]>([])
const mcpSectionRef = ref<InstanceType<typeof McpDependencySection> | null>(null)
const showAddMcpPopup = ref(false)
// v1.27 (FR-MCP-021): Browse All MCPs Popup — opened from the "Browse all
// MCPs" link in the MCP Dependencies dropdown.
const showBrowsePopup = ref(false)

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

watch(scope, () => {
  if (!mySystems.value.length) loadMySystems()
})

watch(systemKey, (v, old) => {
  subSystemCode.value = ''
  if (v && v !== old) {
    loadSubSystems()
  } else {
    subSystems.value = []
  }
})

onMounted(() => {
  loadTaxonomy()
  loadMySystems()
})

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
    coworkUsers.value.length > 0 ||
    files.value.length > 0 ||
    mcpDependencies.value.length > 0
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
  coworkUsers.value = []
  tagInput.value = ''
  files.value = []
  nameError.value = ''
  categoryError.value = ''
  stageError.value = ''
  fileError.value = ''
  mcpError.value = ''
  mcpDependencies.value = []
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

// Clear the required-field error as soon as the user adds a dependency
watch(mcpDependencies, (rows) => {
  if (rows.length) mcpError.value = ''
})

// v1.25 (Q-18 exception) + v1.27: refresh MCP dependency section after the
// Add MCP popup closes (a new MCP may be pinnable) and on tab focus (30s
// throttle inside refresh — Q-30 pattern), so chip status colors stay current.
function onAddMcpPopupClosed() {
  showAddMcpPopup.value = false
  mcpSectionRef.value?.refresh()
}

// v1.27 (FR-MCP-021, AC-MCP-072): after Save Draft from the Register popup
// (opened via Browse's "Add new MCP") → force-refresh suggestions (new MCP
// may match the fuzzy match) + Browse popup reopens (the new DRAFT MCP is
// findable there — Q-18 exception).
function onAddMcpPopupSaved() {
  showAddMcpPopup.value = false
  mcpList.markDirty()
  mcpSectionRef.value?.refresh(true)
  showBrowsePopup.value = true
}

// v1.27 (FR-MCP-021): Browse All MCPs Popup handlers.
// 2026-11-05: multi-select — OK emits an array of selected MCP details.
// 2026-11-06: single batch emit (stale-props fix).
function onBrowseSelected(details: McpDetailResponse[]) {
  showBrowsePopup.value = false
  mcpSectionRef.value?.addMcpsFromDetails(details)
}
function onBrowseAddNewMcp() {
  showBrowsePopup.value = false
  showAddMcpPopup.value = true
}

function onMcpVisibilityChange() {
  if (props.open && document.visibilityState === 'visible') {
    mcpSectionRef.value?.refresh()
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', onMcpVisibilityChange)
})
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onMcpVisibilityChange)
})

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
  mcpError.value = ''
  descError.value = ''
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
  fileError.value = ''
  if (props.filesRequired && !files.value.length) {
    fileError.value = t('app.skills.fileRequired')
    toastError(t('app.skills.fileRequired'))
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
      systemKey: systemKey.value || undefined,
      subSystemCode: systemKey.value ? subSystemCode.value || undefined : undefined,
      hashtags: hashtags.value,
      categoryIds: categoryIds.value,
      stageIds: stageIds.value,
      coworkUserIds: props.showCowork && coworkUsers.value.length ? coworkUsers.value.map((u) => u.id) : undefined,
      files: files.value.length ? files.value : undefined,
      mcpDependencies: mcpDependencies.value.length ? mcpDependencies.value.map(d => ({
        mcpId: d.mcpId, mcpVersion: d.mcpVersion,
      })) : undefined,
    })
    toastSuccess(t(props.successKey))
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
    :title="t(props.titleKey)"
    size="lg"
    dialog-class="skill-form-dialog"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div class="add">
      <PrismInput
        v-model="name"
        :label="t('app.common.skillName')"
        required
        :placeholder="t('app.common.skillNamePh')"
        :error="nameError"
        :maxlength="100"
      >
        <template #suffix>
          <small class="add-count" :class="{ 'is-warn': nameCountWarn }">{{ t('app.common.charCount', { count: nameCount, max: 100 }) }}</small>
        </template>
      </PrismInput>

      <!-- FR-MCP-014: MCP Dependencies section (right under Skill Name — Q-12) -->
      <McpDependencySection
        ref="mcpSectionRef"
        v-model="mcpDependencies"
        :error="mcpError"
        @open-browse="showBrowsePopup = true"
      />

      <PrismCombobox v-model="scope" :label="t('app.common.visibilityScope')" required :options="SCOPE_OPTIONS" />

      <div class="add-row">
        <PrismCombobox
          v-model="systemKey"
          :label="t('app.skill.formSystem')"
          :options="systemOptions"
          :placeholder="t('app.skill.formSystemOptional')"
        />
        <PrismCombobox
          v-model="subSystemCode"
          :label="t('app.skill.formSubSystem')"
          :options="subSystemOptions"
          :disabled="!systemKey"
          :placeholder="t('app.skill.formSubSystemOptional')"
        />
      </div>
      <CoworkUserSelect
        v-if="showCowork"
        v-model="coworkUsers"
        :label="t('app.common.cowork')"
        :hint="t('app.common.coworkHint')"
      />

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

      <div class="add-desc">
        <RichTextEditor
          v-model="description"
          :label="t('app.common.description')"
          :placeholder="t('app.common.skillDescPh')"
          :rows="4"
          required
        />
        <p v-if="descError" class="p-field-error" role="alert">{{ descError }}</p>
      </div>

      <div class="add-field">
        <label class="p-label">{{ t('app.common.hashtag') }}</label>
        <div class="add-tags" role="group" :aria-label="t('app.common.hashtags')">
          <span v-for="(tag, i) in hashtags" :key="tag" class="add-tag">
            #{{ tag }}
            <button type="button" class="add-tag-remove" :aria-label="t('app.common.removeHashtag')" @click="removeTag(i)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="add-tag-input"
            :placeholder="t('app.common.typeHashtagPh')"
            @keydown="onTagKeydown"
          />
        </div>
      </div>

      <div class="add-field">
        <label class="p-label">{{ t('app.common.files') }}<template v-if="filesRequired"> <span class="p-required" aria-hidden="true">*</span></template></label>
        <input
          ref="fileInputRef"
          type="file"
          class="add-file-input"
          multiple
          :accept="ALLOWED_EXTENSIONS.join(',')"
          @change="onFileSelected"
        />
        <div v-if="files.length" class="add-file-list">
          <div v-for="(f, i) in files" :key="i" class="add-file-row">
            <span class="add-file-name" :title="f.name">📄 {{ f.name }} ({{ formatSize(f.size) }})</span>
            <button type="button" class="add-file-remove" :aria-label="t('app.common.removeFile')" @click="removeFile(i)">×</button>
          </div>
        </div>
        <div
          class="add-file-drop"
          @dragover.prevent
          @drop.prevent="onDrop"
        >
          <button type="button" class="add-file-drop-btn" @click="fileInputClick">
            {{ t('app.common.dragDropOr') }} <span class="add-file-link">{{ t('app.common.clickToBrowse') }}</span>
          </button>
          <small class="add-file-meta">{{ t('app.common.supportedSkillFiles') }}</small>
        </div>
        <p v-if="fileError" class="p-field-error" role="alert">{{ fileError }}</p>
      </div>

      <PrismAlert tone="info">
        {{ t(props.hintKey) }}
      </PrismAlert>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="saving" @click="save">{{ t(props.saveLabelKey) }}</PrismButton>
    </template>
  </PrismDialog>

  <!-- FR-MCP-021 (v1.27): Browse All MCPs Popup — opened from the
       "Browse all MCPs" link in the MCP Dependencies dropdown. -->
  <BrowseAllMcpPopup
    v-if="showBrowsePopup"
    ref="browsePopupRef"
    :pinned-mcp-ids="mcpDependencies.map((d) => d.mcpId)"
    @close="showBrowsePopup = false"
    @add-new-mcp="onBrowseAddNewMcp"
    @selected="onBrowseSelected"
  />

  <!-- FR-MCP-004: Add MCP Popup — opened from "Add new MCP" in the Browse popup -->
  <AddMcpPopup v-if="showAddMcpPopup" :open="true" @update:open="onAddMcpPopupClosed" @saved="onAddMcpPopupSaved" />

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.common.discardTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="add-confirm-text">{{ t('app.common.discardDescChanges') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.keepEditing') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.discard') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.add {
  display: grid;
  gap: var(--ds-space-4);
}
.add-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ds-space-4);
}
.add-count {
  font-size: 12px;
  color: var(--ds-color-text-secondary);
  white-space: nowrap;
}
.add-count.is-warn {
  color: var(--ds-color-danger);
}
.add-desc {
  display: grid;
  gap: var(--ds-space-1);
}
.add-field {
  display: grid;
  gap: var(--ds-space-2);
}
.add-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
  padding: var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
}
.add-tag {
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
.add-tag-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
@media (hover: hover) { .add-tag-remove:hover { color: var(--ds-color-danger); } }
.add-tag-input {
  flex: 1;
  min-width: 160px;
  border: none;
  background: none;
  color: var(--ds-color-text-primary);
  font: inherit;
  padding: 2px 0;
  outline: none;
}
.add-file-list {
  display: grid;
  gap: var(--ds-space-2);
}
.add-file-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.add-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}
.add-file-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font-size: 16px;
}
.add-file-drop {
  display: grid;
  gap: var(--ds-space-2);
  justify-items: center;
  padding: var(--ds-space-4);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  text-align: center;
}
.add-file-input {
  display: none;
}
.add-file-drop-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-primary);
  font: inherit;
}
.add-file-link {
  color: var(--ds-color-accent);
}
.add-file-meta {
  color: var(--ds-color-text-secondary);
  font-size: 12px;
}
.add-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
