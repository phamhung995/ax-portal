<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ImagePlus, X } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import PrismTextarea from '@/design-system/components/PrismTextarea.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import { skillRequestApi } from '@/features/skill-requests/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import type { SkillRequestSuggestions } from '@/features/skill-requests/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open', 'saved'])

const HASHTAG_PATTERN = /^[A-Za-z0-9_-]+$/
const MAX_HASHTAGS = 10
const MAX_HASHTAG_LENGTH = 30
const MAX_FILES = 10
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
const IMAGE_EXT = new Set(['jpg', 'jpeg', 'png', 'webp', 'gif'])

const title = ref('')
const description = ref('')
const hashtags = ref<string[]>([])
const tagInput = ref('')
const saving = ref(false)
const titleError = ref('')
const confirmCancel = ref(false)

// ── Attachments (images + files) ──────────────────────────────────────────
const fileInput = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
interface PendingFile {
  file: File
  url: string | null // object URL for image previews
}
const pendingFiles = ref<PendingFile[]>([])

const pendingImages = computed(() => pendingFiles.value.filter((p) => p.url))
const pendingNonImages = computed(() => pendingFiles.value.filter((p) => !p.url))

function extOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
}

function isImageFile(name: string): boolean {
  return IMAGE_EXT.has(extOf(name))
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function addFiles(fileList: FileList | File[]) {
  for (const f of Array.from(fileList)) {
    if (pendingFiles.value.length >= MAX_FILES) {
      toastError(`Maximum ${MAX_FILES} attachments.`)
      break
    }
    if (f.size > MAX_FILE_SIZE) {
      toastError(`"${f.name}" is larger than 10 MB.`)
      continue
    }
    const url = isImageFile(f.name) ? URL.createObjectURL(f) : null
    pendingFiles.value.push({ file: f, url })
  }
}

function removePending(index: number) {
  const [removed] = pendingFiles.value.splice(index, 1)
  if (removed && removed.url) URL.revokeObjectURL(removed.url)
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
  for (const p of pendingFiles.value) {
    if (p.url) URL.revokeObjectURL(p.url)
  }
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
  if (!title.value.trim()) {
    titleError.value = 'Request title cannot be empty.'
    return false
  }
  if (title.value.length > 100) {
    titleError.value = 'Request title is at most 100 characters.'
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
    toastSuccess('Skill request created successfully.')
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
    title="Register new skill request"
    size="lg"
    :close-on-overlay="false"
    :close-on-escape="false"
    @update:model-value="requestClose"
    @close="requestClose"
  >
    <div class="add">
      <PrismInput
        v-model="title"
        label="Title"
        required
        placeholder="What skill do you need? e.g. Export Tibero report to Excel..."
        :error="titleError"
        :maxlength="100"
      >
        <template #suffix>
          <small class="add-count" :class="{ 'is-warn': titleCountWarn }">{{ titleCount }}/100</small>
        </template>
      </PrismInput>

      <PrismAlert v-if="hasSuggestions" tone="info" class="add-suggest">
        <div class="add-suggest-inner">
          <p v-if="suggestions!.skills.length" class="add-suggest-title">
            Similar skills may already exist:
          </p>
          <ul class="add-suggest-list">
            <li v-for="sk in suggestions!.skills" :key="sk.id">
              <strong>{{ sk.name }}</strong>
              <span class="add-suggest-meta">({{ sk.status }}, by {{ sk.creatorName ?? 'unknown' }})</span>
            </li>
          </ul>
          <p v-if="suggestions!.requests.length" class="add-suggest-title">
            Similar requests already submitted:
          </p>
          <ul class="add-suggest-list">
            <li v-for="rq in suggestions!.requests" :key="rq.id">
              <strong>{{ rq.title }}</strong>
              <span class="add-suggest-meta">({{ rq.voteCount }} votes)</span>
            </li>
          </ul>
          <p class="add-suggest-hint">You can still post this request; the community will consolidate duplicates.</p>
        </div>
      </PrismAlert>
      <p v-else-if="suggestionsLoading" class="add-suggest-loading">Checking similar skills and requests…</p>

      <PrismTextarea
        v-model="description"
        label="Description"
        placeholder="Describe the idea, the problem it solves, expected behavior..."
        :rows="4"
        :maxlength="2000"
      />

      <div class="add-field">
        <label class="p-label">
          Attachments
          <small class="add-attach-hint">(images + files, max 10, each ≤ 10 MB)</small>
        </label>
        <div
          class="add-attach-drop"
          :class="{ 'is-dragover': dragOver }"
          role="button"
          tabindex="0"
          aria-label="Add attachments"
          @click="openFilePicker"
          @keydown.enter.prevent="openFilePicker"
          @keydown.space.prevent="openFilePicker"
          @dragover.prevent="dragOver = true"
          @dragleave.prevent="dragOver = false"
          @drop="onDrop"
        >
          <ImagePlus :size="22" aria-hidden="true" />
          <span>Click or drop files here</span>
          <small>Images (JPG/PNG/WEBP/GIF) show in the post · other files appear as download links</small>
        </div>
        <input
          ref="fileInput"
          type="file"
          multiple
          hidden
          accept="image/jpeg,image/png,image/webp,image/gif,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.zip,.7z,.txt,.md,.json,.csv,.log,.yaml,.yml,.py,.js,.ts"
          @change="onFileChange"
        />

        <div v-if="pendingImages.length" class="add-attach-thumbs">
          <figure
            v-for="(p, i) in pendingImages"
            :key="p.file.name + i"
            class="add-attach-thumb"
          >
            <img :src="p.url ?? undefined" :alt="p.file.name" />
            <button
              type="button"
              class="add-attach-remove"
              :aria-label="`Remove ${p.file.name}`"
              @click="removePending(pendingFiles.indexOf(p))"
            >
              <X :size="12" aria-hidden="true" />
            </button>
          </figure>
        </div>

        <div v-if="pendingNonImages.length" class="add-attach-files">
          <div
            v-for="(p, i) in pendingNonImages"
            :key="p.file.name + i"
            class="add-attach-file-row"
          >
            <span class="add-attach-file-name" :title="p.file.name">📄 {{ p.file.name }}</span>
            <span class="add-attach-file-size">{{ formatSize(p.file.size) }}</span>
            <button
              type="button"
              class="add-attach-remove"
              :aria-label="`Remove ${p.file.name}`"
              @click="removePending(pendingFiles.indexOf(p))"
            >
              <X :size="12" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>

      <div class="add-field">
        <label class="p-label">Hashtag</label>
        <div class="add-tags" role="group" aria-label="Hashtags">
          <span v-for="(tag, i) in hashtags" :key="tag" class="add-tag">
            #{{ tag }}
            <button type="button" class="add-tag-remove" aria-label="Remove hashtag" @click="removeTag(i)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="add-tag-input"
            placeholder="Type a hashtag and press Enter..."
            @keydown="onTagKeydown"
          />
        </div>
      </div>

      <PrismAlert tone="info">
        Your request will be posted to the feed immediately.
      </PrismAlert>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="saving" @click="requestClose">Cancel</PrismButton>
      <PrismButton :loading="saving" @click="save">Save</PrismButton>
    </template>
  </PrismDialog>

  <PrismDialog
    :model-value="confirmCancel"
    title="Discard changes?"
    size="sm"
    :close-on-overlay="false"
    @update:model-value="confirmCancel = $event"
  >
    <p class="add-confirm-text">Are you sure you want to cancel? The information you entered will not be saved.</p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmCancel = false">Keep editing</PrismButton>
      <PrismButton @click="confirmDiscard">Discard</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.add {
  display: grid;
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
.add-suggest-inner {
  display: grid;
  gap: var(--ds-space-1);
}
.add-suggest-title {
  margin: 0;
  font-weight: 600;
}
.add-suggest-list {
  margin: 0;
  padding-left: var(--ds-space-5);
  display: grid;
  gap: 2px;
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
  background: rgb(103 232 249 / .08);
}
.add-attach-thumbs {
  display: grid;
  gap: var(--ds-space-2);
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
}
.add-attach-thumb {
  position: relative;
  margin: 0;
  aspect-ratio: 1;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  overflow: hidden;
  background: var(--ds-input-background);
}
.add-attach-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
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
.add-confirm-text {
  color: var(--ds-color-text-primary);
}
</style>
