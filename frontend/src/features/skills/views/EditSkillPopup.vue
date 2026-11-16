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
import McpDependencySection from '@/features/mcp/views/McpDependencySection.vue'
import type { McpDependencyRow } from '@/features/mcp/views/McpDependencySection.vue'
import AddMcpPopup from '@/features/mcp/views/AddMcpPopup.vue'
import BrowseAllMcpPopup from '@/features/mcp/views/BrowseAllMcpPopup.vue'
import type { McpDetailResponse } from '@/features/mcp/types'
import { skillApi } from '@/features/skills/api'
import { useMcpListStore } from '@/features/mcp/store'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { SkillDetailResponse, TaxonomyOption } from '@/features/skills/types'

const props = withDefaults(defineProps<{ open: boolean; skillId: number | null; canManageFiles?: boolean }>(), {
  canManageFiles: true,
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
const tagInput = ref('')
const newFiles = ref<File[]>([])
const removeFileIds = ref<number[]>([])
const saving = ref(false)
const nameError = ref('')
const categoryError = ref('')
const stageError = ref('')
const fileError = ref('')
const mcpError = ref('')
const descError = ref('')
const mcpDependencies = ref<McpDependencyRow[]>([])
const mcpSectionRef = ref<InstanceType<typeof McpDependencySection> | null>(null)
const showAddMcpPopup = ref(false)
const showBrowsePopup = ref(false)
const confirmCancel = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

// ── Confirm before a scope widening (moves the skill to PENDING_APPROVAL) ──
const scopeConfirmOpen = ref(false)
let scopeConfirmAction: (() => void) | null = null
const AUTO_APPROVE_SCOPES = ['PRIVATE', 'PROJECT']

function scopeWidening(): boolean {
  const d = detail.value
  if (!d || d.status !== 'APPROVED') return false
  // A reviewed skill (admin approved it at least once) applies the widening
  // immediately — no confirm, no pending (V91).
  if (d.adminReviewed) return false
  return (
    scope.value !== d.scope
    && AUTO_APPROVE_SCOPES.includes(d.scope)
    && !AUTO_APPROVE_SCOPES.includes(scope.value)
  )
}

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
  if (props.skillId == null) return
  try {
    mySystems.value = await skillApi.formSystems(props.skillId)
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
  if (filling.value) return
  subSystemCode.value = ''
  if (v && v !== old) {
    loadSubSystems()
  } else {
    subSystems.value = []
  }
})

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
  systemKey: string
  subSystemCode: string
  description: string
  hashtags: string[]
  categoryIds: number[]
  stageIds: number[]
  coworkIds: number[]
  mcpDeps: Array<{ mcpId: number; mcpVersion: number }>
}
const snapshot = ref<Snapshot | null>(null)
const detail = ref<SkillDetailResponse | null>(null)
/** True while fillFrom() populates the form — suppresses the systemKey watcher. */
const filling = ref(false)

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

function fillFrom(detailData: SkillDetailResponse) {
  filling.value = true
  name.value = detailData.name
  scope.value = detailData.scope
  systemKey.value = detailData.systemKey ?? ''
  subSystemCode.value = detailData.subSystemCode ?? ''
  if (!mySystems.value.length) loadMySystems()
  if (systemKey.value) loadSubSystems()
  description.value = detailData.description ?? ''
  hashtags.value = [...detailData.hashtags]
  categoryIds.value = detailData.categories
    .map((n) => categoryIdByName(n))
    .filter((id): id is number => id != null)
  stageIds.value = detailData.stages
    .map((n) => stageIdByName(n))
    .filter((id): id is number => id != null)
  coworkUsers.value = detailData.coworkUsers
    .filter((u) => u.name != null)
    .map((u) => ({ id: u.id, name: u.name as string }))
  tagInput.value = ''
  newFiles.value = []
  removeFileIds.value = []
  nameError.value = ''
  categoryError.value = ''
  stageError.value = ''
  fileError.value = ''
  mcpError.value = ''
  mcpDependencies.value = []
  snapshot.value = {
    name: detailData.name,
    scope: detailData.scope,
    systemKey: detailData.systemKey ?? '',
    subSystemCode: detailData.subSystemCode ?? '',
    description: detailData.description ?? '',
    hashtags: [...detailData.hashtags],
    categoryIds: [...categoryIds.value],
    stageIds: [...stageIds.value],
    coworkIds: coworkUsers.value.map((u) => u.id),
    mcpDeps: detailData.mcpDependencies.map((d) => ({ mcpId: d.mcpId, mcpVersion: d.mcpVersion })),
  }
  filling.value = false
}

// 2026-11: restore the skill's existing MCP dependencies once the section
// has rendered (fillFrom runs before the section ref exists). The section
// re-fetches each MCP detail for availableVersions (async, non-blocking).
// Save is disabled until init finishes so an in-flight init cannot be
// saved as an empty list (which would clear the dependencies).
const mcpInitLoading = ref(false)
watch([detail, mcpSectionRef], async ([d, section]) => {
  if (!d || !section || filling.value || mcpDependencies.value.length) return
  mcpInitLoading.value = true
  try {
    await section.initFromExisting(
      d.mcpDependencies.map((m) => ({ mcpId: m.mcpId, mcpVersion: m.mcpVersion, status: m.mcpStatus, name: m.mcpName }))
    )
  } finally {
    mcpInitLoading.value = false
  }
})

// Clear the MCP error as soon as the user adds a dependency.
watch(mcpDependencies, (rows) => {
  if (rows.length) mcpError.value = ''
})

// v1.27 (FR-MCP-021): refresh the MCP dependency section after the Add MCP
// popup closes (a new MCP may be pinnable) and on tab focus (30s throttle
// inside refresh — Q-30 pattern), so chip status colors stay current.
function onAddMcpPopupClosed() {
  showAddMcpPopup.value = false
  mcpSectionRef.value?.refresh()
}

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

watch(
  () => props.open,
  async (open) => {
    if (!open || !props.skillId) return
    detail.value = null
    snapshot.value = null
    scopeConfirmOpen.value = false
    scopeConfirmAction = null
    if (!categories.value.length) await loadTaxonomy()
    try {
      const d = await skillApi.detail(props.skillId)
      detail.value = d
      fillFrom(d)
    } catch {
      toastError(t('app.skills.loadErrorDetail'))
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
    systemKey.value !== snap.systemKey ||
    subSystemCode.value !== snap.subSystemCode ||
    description.value.trim() !== snap.description ||
    JSON.stringify(hashtags.value) !== JSON.stringify(snap.hashtags) ||
    JSON.stringify(categoryIds.value) !== JSON.stringify(snap.categoryIds) ||
    JSON.stringify(stageIds.value) !== JSON.stringify(snap.stageIds) ||
    JSON.stringify(coworkUsers.value.map((u) => u.id)) !== JSON.stringify(snap.coworkIds) ||
    newFiles.value.length > 0 ||
    removeFileIds.value.length > 0 ||
    JSON.stringify(mcpDependencies.value.map((d) => ({ mcpId: d.mcpId, mcpVersion: d.mcpVersion })))
      !== JSON.stringify(snap.mcpDeps)
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
  if (props.canManageFiles && detail.value && (detail.value.files.length - removeFileIds.value.length + newFiles.value.length) <= 0) {
    fileError.value = t('app.skills.fileRequired')
    toastError(t('app.skills.fileRequired'))
    return false
  }
  return true
}

async function save() {
  if (!validate() || !props.skillId || !detail.value || mcpInitLoading.value) return
  // A scope widening on an APPROVED skill moves it to PENDING_APPROVAL —
  // confirm first so the user knows it will wait for admin approval.
  if (scopeWidening()) {
    scopeConfirmAction = doSave
    scopeConfirmOpen.value = true
    return
  }
  await doSave()
}

async function confirmScopeChange() {
  scopeConfirmOpen.value = false
  const action = scopeConfirmAction
  scopeConfirmAction = null
  if (action) action()
}

async function doSave() {
  if (!props.skillId || !detail.value) return
  saving.value = true
  try {
    const widened = scopeWidening()
    await skillApi.update(props.skillId, {
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      publicScope: scope.value,
      systemKey: systemKey.value || undefined,
      subSystemCode: systemKey.value ? subSystemCode.value || undefined : undefined,
      hashtags: hashtags.value,
      categoryIds: categoryIds.value,
      stageIds: stageIds.value,
      coworkUserIds: coworkUsers.value.length ? coworkUsers.value.map((u) => u.id) : [],
      files: props.canManageFiles && newFiles.value.length ? newFiles.value : undefined,
      removeFileIds: props.canManageFiles && removeFileIds.value.length ? removeFileIds.value : undefined,
      // Full MCP dependency list — always sent (even []) to replace/clear.
      mcpDependencies: mcpDependencies.value.map((d) => ({ mcpId: d.mcpId, mcpVersion: d.mcpVersion })),
      updatedAt: detail.value.updatedAt,
    })
    toastSuccess(widened ? t('app.skill.scopePendingToast') : t('app.skills.updatedToast'))
    emit('saved')
    emit('update:open', false)
  } catch (err) {
    const userError = toUserError(err)
    if (userError.code === 'SKILL_OPTIMISTIC_LOCK') {
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
    :title="t('app.skills.editTitle')"
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
        {{ t('app.skills.nameLocked') }}
      </PrismAlert>

      <PrismInput
        v-model="name"
        :label="t('app.common.skillName')"
        required
        :placeholder="t('app.common.skillNamePh')"
        :error="nameError"
        :maxlength="100"
        :readonly="nameReadOnly"
      >
        <template #suffix>
          <small class="edt-count" :class="{ 'is-warn': nameCountWarn }">{{ t('app.common.charCount', { count: nameCount, max: 100 }) }}</small>
        </template>
      </PrismInput>

      <!-- FR-MCP-014: MCP Dependencies section (right under Skill Name — Q-12).
           2026-11-06: read-only for APPROVED skills (same rule as files) —
           the dependency set is managed through Update Version. -->
      <McpDependencySection
        ref="mcpSectionRef"
        v-model="mcpDependencies"
        :error="mcpError"
        :readonly="!props.canManageFiles"
        @open-browse="showBrowsePopup = true"
      />
      <p v-if="!props.canManageFiles" class="edt-file-locked">
        {{ t('app.skills.approvedMcpDepsHint') }}
      </p>

      <PrismCombobox v-model="scope" :label="t('app.common.visibilityScope')" required :options="SCOPE_OPTIONS" />

      <div class="edt-row">
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
        v-model="coworkUsers"
        :label="t('app.common.cowork')"
        :disabled="!detail.isCreator && !detail.isAdmin"
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

      <div class="edt-desc">
        <RichTextEditor
          v-model="description"
          :label="t('app.common.description')"
          :placeholder="t('app.common.skillDescPh')"
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
          {{ t('app.skills.approvedFilesHint') }}
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
      <PrismButton :loading="saving" :disabled="!detail || mcpInitLoading" @click="save">{{ t('app.common.save') }}</PrismButton>
    </template>
  </PrismDialog>

  <!-- FR-MCP-021 (v1.27): Browse All MCPs Popup — opened from the
       "Browse all MCPs" link in the MCP Dependencies dropdown. -->
  <BrowseAllMcpPopup
    v-if="showBrowsePopup"
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
    <p class="edt-confirm-text">{{ t('app.common.discardDescChanges') }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">{{ t('app.common.keepEditing') }}</PrismButton>
      <PrismButton @click="confirmDiscard">{{ t('app.common.discard') }}</PrismButton>
    </template>
  </PrismDialog>

  <!-- Confirm: a scope widening moves the skill to PENDING_APPROVAL -->
  <PrismDialog
    :model-value="scopeConfirmOpen"
    :title="t('app.skill.scopeConfirmTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="scopeConfirmOpen = $event"
  >
    <p class="edt-confirm-text">{{ t('app.skill.scopeConfirmDesc', { scope: SCOPE_OPTIONS.find((o) => o.value === scope)?.label ?? scope }) }}</p>
    <template #footer>
      <PrismButton variant="secondary" @click="scopeConfirmOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton @click="confirmScopeChange">{{ t('app.common.confirm') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.edt {
  display: grid;
  gap: var(--ds-space-4);
}
.edt-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
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
