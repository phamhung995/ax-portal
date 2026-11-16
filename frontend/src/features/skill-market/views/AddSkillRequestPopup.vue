<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ImagePlus, X } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { skillRequestApi } from '@/features/skill-market/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { SkillRequestSuggestions } from '@/features/skill-market/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'saved'])
const { t } = useI18nStore()

const HASHTAG_PATTERN = /^[A-Za-z0-9_-]+$/
const MAX_HASHTAGS = 10
const MAX_HASHTAG_LENGTH = 30
const MAX_FILES = 10
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
// Total upload budget: 10 files x 10MB. Checked before submit so the
// server (nginx/Tomcat) never rejects the whole request with a 413.
const MAX_TOTAL_SIZE = MAX_FILES * MAX_FILE_SIZE // 100MB

const title = ref('')
const description = ref('')
const hashtags = ref<string[]>([])
const tagInput = ref('')
const saving = ref(false)
const titleError = ref('')
const confirmCancel = ref(false)

// ── Attachments (all files as plain rows — no previews) ────────────────────
const fileInput = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
interface PendingFile {
  file: File
}
const pendingFiles = ref<PendingFile[]>([])

const totalSize = computed(() =>
  pendingFiles.value.reduce((sum, p) => sum + p.file.size, 0)
)

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function addFiles(fileList: FileList | File[]) {
  for (const f of Array.from(fileList)) {
    if (pendingFiles.value.length >= MAX_FILES) {
      toastError(t('app.skillMarket.maxAttachments', { n: MAX_FILES }))
      break
    }
    if (f.size > MAX_FILE_SIZE) {
      toastError(t('app.skillMarket.attachmentTooLarge', { name: f.name }))
      continue
    }
    if (totalSize.value + f.size > MAX_TOTAL_SIZE) {
      toastError(t('app.skillMarket.attachmentsTotalLimit'))
      continue
    }
    pendingFiles.value.push({ file: f })
  }
}

function removePending(index: number) {
  pendingFiles.value.splice(index, 1)
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files?.length) addFiles(input.files)
  input.value = ''
}

function openFilePicker() {
  fileInput.value?.click()
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  dragOver.value = false
  if (event.dataTransfer?.files?.length) {
    addFiles(event.dataTransfer.files)
  }
}

function clearPending() {
  pendingFiles.value = []
}

// ── Similar suggestions (option A): debounced hint while typing the title ──
const suggestions = ref<SkillRequestSuggestions | null>(null)
const suggestionsLoading = ref(false)
let suggestTimer: ReturnType<typeof setTimeout> | null = null

watch(title, (value) => {
  if (suggestTimer) clearTimeout(suggestTimer)
  const kw = value.trim()
  if (kw.length < 3) {
    suggestions.value = null
    suggestionsLoading.value = false
    return
  }
  suggestionsLoading.value = true
  suggestTimer = setTimeout(async () => {
    try {
      suggestions.value = await skillRequestApi.suggestions(kw)
    } catch {
      suggestions.value = null
    } finally {
      suggestionsLoading.value = false
    }
  }, 400)
})

const hasSuggestions = computed(() =>
  !!suggestions.value &&
  (suggestions.value.skills.length > 0 || suggestions.value.requests.length > 0)
)

function resetForm() {
  title.value = ''
  description.value = ''
  hashtags.value = []
  tagInput.value = ''
  titleError.value = ''
  suggestions.value = null
  suggestionsLoading.value = false
  clearPending()
  if (suggestTimer) clearTimeout(suggestTimer)
}

watch(
  () => props.open,
  (open) => {
    if (open) resetForm()
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

const titleCount = computed(() => title.value.length)
const titleCountWarn = computed(() => titleCount.value >= 90)

function formDirty() {
  return (
    title.value.trim() !== '' ||
    description.value.trim() !== '' ||
    hashtags.value.length > 0 ||
    pendingFiles.value.length > 0
  )
}

function validate(): boolean {
  titleError.value = ''
  // Fields sit inside a scrollable popup while Save is in the footer — a
  // field error alone is easy to miss → toast the first failing message.
  if (!title.value.trim()) {
    titleError.value = t('app.skillMarket.titleEmpty')
    toastError(t('app.skillMarket.titleEmpty'))
    return false
  }
  if (title.value.length > 100) {
    titleError.value = t('app.skillMarket.titleTooLong')
    toastError(t('app.skillMarket.titleTooLong'))
    return false
  }
  return true
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    await skillRequestApi.create({
      title: title.value.trim(),
      description: description.value.trim() || undefined,
      hashtags: hashtags.value,
      files: pendingFiles.value.map((p) => p.file),
    })
    toastSuccess(t('app.skillMarket.createdToast'))
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
    :title="t('app.skillMarket.registerTitle')"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div class="add">
      <PrismInput
        v-model="title"
        :label="t('app.skillMarket.title')"
        required
        :placeholder="t('app.skillMarket.titlePh')"
        :error="titleError"
        :maxlength="100"
      >
        <template #suffix>
          <small class="add-count" :class="{ 'is-warn': titleCountWarn }">{{ t('app.common.charCount', { count: titleCount, max: 100 }) }}</small>
        </template>
      </PrismInput>

      <PrismAlert v-if="hasSuggestions" tone="info" class="add-suggest">
        <div class="add-suggest-inner">
          <p v-if="suggestions!.skills.length" class="add-suggest-title">
            {{ t('app.skillMarket.similarSkills') }}
          </p>
          <ul class="add-suggest-list">
            <li v-for="sk in suggestions!.skills" :key="sk.id">
              <strong>{{ sk.name }}</strong>
              <span class="add-suggest-meta">{{ t('app.skillMarket.similarSkillsItem', { status: sk.status, creator: sk.creatorName ?? t('app.common.unknown') }) }}</span>
            </li>
          </ul>
          <p v-if="suggestions!.requests.length" class="add-suggest-title">
            {{ t('app.skillMarket.similarRequests') }}
          </p>
          <ul class="add-suggest-list">
            <li v-for="rq in suggestions!.requests" :key="rq.id">
              <strong>{{ rq.title }}</strong>
              <span class="add-suggest-meta">{{ t('app.skillMarket.similarVotes', { n: rq.voteCount }) }}</span>
            </li>
          </ul>
          <p class="add-suggest-hint">{{ t('app.skillMarket.similarConsolidate') }}</p>
        </div>
      </PrismAlert>
      <p v-else-if="suggestionsLoading" class="add-suggest-loading">{{ t('app.skillMarket.checkingSimilar') }}</p>

      <RichTextEditor
        v-model="description"
        :label="t('app.common.description')"
        :placeholder="t('app.skillMarket.descPh')"
        :rows="4"
      />

      <div class="add-field">
        <label class="p-label">
          {{ t('app.skillMarket.attachments') }}
          <small class="add-attach-hint">{{ t('app.skillMarket.attachmentsHint') }}</small>
        </label>
        <div
          class="add-attach-drop"
          :class="{ 'is-dragover': dragOver }"
          role="button"
          tabindex="0"
          :aria-label="t('app.skillMarket.addAttachmentsAria')"
          @click="openFilePicker"
          @keydown.enter.prevent="openFilePicker"
          @keydown.space.prevent="openFilePicker"
          @dragover.prevent="dragOver = true"
          @dragleave.prevent="dragOver = false"
          @drop="onDrop"
        >
          <ImagePlus :size="22" aria-hidden="true" />
          <span>{{ t('app.skillMarket.dropHere') }}</span>
          <small>{{ t('app.skillMarket.dropHint') }}</small>
        </div>
        <input
          ref="fileInput"
          type="file"
          multiple
          hidden
          accept="image/jpeg,image/png,image/webp,image/gif,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.zip,.7z,.txt,.md,.json,.csv,.log,.yaml,.yml,.py,.js,.ts"
          @change="onFileChange"
        />

        <div v-if="pendingFiles.length" class="add-attach-files">
          <div
            v-for="(p, i) in pendingFiles"
            :key="p.file.name + i"
            class="add-attach-file-row"
          >
            <span class="add-attach-file-name" :title="p.file.name">📄 {{ p.file.name }}</span>
            <span class="add-attach-file-size">{{ formatSize(p.file.size) }}</span>
            <button
              type="button"
              class="add-attach-remove"
              :aria-label="t('app.common.removeUserAria', { name: p.file.name })"
              @click="removePending(i)"
            >
              <X :size="12" aria-hidden="true" />
            </button>
          </div>
        </div>
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

      <PrismAlert tone="info">
        {{ t('app.skillMarket.postHint') }}
      </PrismAlert>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="saving" @click="save">{{ t('app.common.save') }}</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    :title="t('app.common.discardTitle')"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="add-confirm-text">{{ t('app.common.discardDescInfo') }}</p>
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
  min-width: 0;
}
.add-count {
  font-size: 12px;
  color: var(--ds-color-text-secondary);
  white-space: nowrap;
}
.add-count.is-warn {
  color: var(--ds-color-danger);
}
.add-suggest {
  min-width: 0;
}
.add-suggest-inner {
  display: grid;
  gap: var(--ds-space-1);
  min-width: 0;
}
.add-suggest-title {
  margin: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.add-suggest-list {
  margin: 0;
  padding-left: var(--ds-space-5);
  display: grid;
  gap: 2px;
  min-width: 0;
}
.add-suggest-list li {
  min-width: 0;
  overflow-wrap: anywhere;
}
.add-suggest-meta {
  color: var(--ds-color-text-secondary);
  font-size: 12px;
}
.add-suggest-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ds-color-text-secondary);
}
.add-suggest-loading {
  margin: 0;
  font-size: 12px;
  color: var(--ds-color-text-secondary);
}
.add-field {
  display: grid;
  gap: var(--ds-space-2);
}
.add-attach-hint {
  font-weight: 400;
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.add-attach-drop {
  display: grid;
  place-items: center;
  justify-items: center;
  gap: 4px;
  text-align: center;
  padding: var(--ds-space-5);
  border: 1.5px dashed var(--ds-color-border-strong);
  border-radius: var(--ds-radius-md);
  background: var(--ds-input-background);
  color: var(--ds-color-text-secondary);
  cursor: pointer;
  transition: all var(--ds-duration-fast) var(--ds-ease);
}
.add-attach-drop svg {
  color: var(--ds-color-accent);
}
.add-attach-drop small {
  color: var(--ds-color-text-muted);
  font-size: 11px;
}
@media (hover: hover) {
  .add-attach-drop:hover {
    background: var(--ds-input-hover-background);
  }
}
.add-attach-drop:focus-visible {
  outline: 2px solid var(--ds-color-accent);
  outline-offset: 2px;
}
.add-attach-drop.is-dragover {
  border-color: var(--ds-color-accent);
  background: rgb(94 106 210 / .08);
}
.add-attach-files {
  display: grid;
  gap: var(--ds-space-1);
}
.add-attach-file-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 6px var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  font-size: 13px;
}
.add-attach-file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.add-attach-file-size {
  font-size: 12px;
  color: var(--ds-color-text-muted);
  font-family: var(--ds-font-mono);
  flex-shrink: 0;
}
.add-attach-remove {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: none;
  color: var(--ds-color-text-muted);
  cursor: pointer;
  flex-shrink: 0;
}
@media (hover: hover) {
  .add-attach-remove:hover {
    color: var(--ds-color-danger);
  }
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
  background: rgb(94 106 210 / .06);
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
.add-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
