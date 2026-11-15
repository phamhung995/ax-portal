<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismInput from '@/design-system/components/PrismInput.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import PrismFileUpload from '@/design-system/components/PrismFileUpload.vue'
import TaxonomyChipSelect from '@/features/skills/views/TaxonomyChipSelect.vue'
import { aiToolApi } from '@/features/ai-tools/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { useI18nStore } from '@/stores/i18n'
import type { AiToolFileItem } from '@/features/ai-tools/types'
import type { TaxonomyOption } from '@/features/skills/types'

const props = defineProps<{ open: boolean; toolId: number | null }>()
const emit = defineEmits(['update:open', 'saved'])
const { t } = useI18nStore()

const ALLOWED_EXTENSIONS = ['.zip', '.7z', '.json', '.yaml', '.yml', '.txt', '.md', '.py', '.js', '.ts']
const MAX_FILE_SIZE = 50 * 1024 * 1024

const name = ref('')
const description = ref('')
const tags = ref<string[]>([])
const tagInput = ref('')
const categoryIds = ref<number[]>([])
const stageIds = ref<number[]>([])
const files = ref<File[]>([])
const existingFile = ref<AiToolFileItem | null>(null)
const removeFile = ref(false)
const saving = ref(false)
const nameError = ref('')
const descriptionError = ref('')
const categoryError = ref('')
const stageError = ref('')
const fileError = ref('')

const categories = ref<TaxonomyOption[]>([])
const stages = ref<TaxonomyOption[]>([])

const isEdit = computed(() => props.toolId !== null)
const title = computed(() => (isEdit.value ? t('app.aiTools.editTitle') : t('app.aiTools.addTitle')))

function close() {
  emit('update:open', false)
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    // Reset the form
    name.value = ''
    description.value = ''
    tags.value = []
    tagInput.value = ''
    categoryIds.value = []
    stageIds.value = []
    files.value = []
    existingFile.value = null
    removeFile.value = false
    nameError.value = ''
    descriptionError.value = ''
    categoryError.value = ''
    stageError.value = ''
    fileError.value = ''
    try {
      const taxonomy = await aiToolApi.taxonomy()
      categories.value = taxonomy.categories
      stages.value = taxonomy.stages
    } catch {
      // non-fatal: the pickers show no options
    }
    if (isEdit.value && props.toolId) {
      try {
        const detail = await aiToolApi.detail(props.toolId)
        name.value = detail.name
        description.value = detail.description ?? ''
        tags.value = [...detail.tags]
        categoryIds.value = []
        stageIds.value = []
        // Map names back to ids via the taxonomy lists.
        for (const c of categories.value) {
          if (detail.categories.includes(c.name)) categoryIds.value.push(c.id)
        }
        for (const st of stages.value) {
          if (detail.stages.includes(st.name)) stageIds.value.push(st.id)
        }
        // Keep the existing file (not re-sent unless replaced).
        existingFile.value = detail.file
      } catch (err) {
        toastError(toUserError(err).message)
        close()
      }
    }
  }
)

// ── Tags (type + Enter, chip with ×) ───────────────────────────────────────
const MAX_TAGS = 10
const MAX_TAG_LENGTH = 30

function addTag() {
  const raw = tagInput.value.trim().replace(/^#/, '')
  tagInput.value = ''
  if (!raw) return
  const tag = raw.toLowerCase()
  if (tags.value.includes(tag)) return
  if (tags.value.length >= MAX_TAGS) {
    toastError(t('app.aiTools.tagsMax', { n: MAX_TAGS }))
    return
  }
  if (tag.length > MAX_TAG_LENGTH) {
    toastError(t('app.aiTools.tagTooLong', { n: MAX_TAG_LENGTH }))
    return
  }
  if (!/^[a-z0-9_-]+$/.test(tag)) {
    toastError(t('app.aiTools.tagInvalidChars'))
    return
  }
  tags.value = [...tags.value, tag]
}
function onTagKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    addTag()
  } else if (event.key === 'Backspace' && !tagInput.value && tags.value.length) {
    tags.value = tags.value.slice(0, -1)
  }
}
function removeTag(index: number) {
  tags.value = tags.value.filter((_, i) => i !== index)
}

// ── Existing file (edit mode) ─────────────────────────────────────────────
function removeExistingFile() {
  existingFile.value = null
  removeFile.value = true
}

function onNewFilePicked() {
  // A new file replaces the removed/old one — no need to send removeFile.
  removeFile.value = false
}

function formatSize(bytes: number): string {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

watch(files, () => onNewFilePicked())

// ── Save ───────────────────────────────────────────────────────────────────
async function onSave() {
  let ok = true
  const file = files.value[0]
  nameError.value = ''
  descriptionError.value = ''
  categoryError.value = ''
  stageError.value = ''
  fileError.value = ''
  if (!name.value.trim()) {
    nameError.value = t('app.aiTools.nameRequired')
    ok = false
  }
  if (!description.value.trim()) {
    descriptionError.value = t('app.aiTools.descRequired')
    ok = false
  }
  if (!categoryIds.value.length) {
    categoryError.value = t('app.common.selectCategory')
    ok = false
  }
  if (!stageIds.value.length) {
    stageError.value = t('app.common.selectStage')
    ok = false
  }
  if (!isEdit.value && !file) {
    fileError.value = t('app.aiTools.fileRequired')
    ok = false
  }
  if (file && file.size > MAX_FILE_SIZE) {
    fileError.value = t('app.aiTools.fileSizeError')
    ok = false
  }
  if (!ok) return

  saving.value = true
  try {
    if (isEdit.value && props.toolId) {
      await aiToolApi.update(props.toolId, {
        name: name.value.trim(),
        description: description.value.trim(),
        tags: tags.value,
        categoryIds: categoryIds.value,
        stageIds: stageIds.value,
        file,
        removeFile: removeFile.value && !file,
      })
      toastSuccess(t('app.aiTools.updatedToast'))
    } else {
      // file is guaranteed non-undefined here (validated above for create).
      await aiToolApi.create({
        name: name.value.trim(),
        description: description.value.trim(),
        tags: tags.value,
        categoryIds: categoryIds.value,
        stageIds: stageIds.value,
        file: file as File,
      })
      toastSuccess(t('app.aiTools.createdToast'))
    }
    emit('saved')
    close()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PrismDialog :model-value="open" :title="title" size="lg" dialog-class="ai-tool-form-dialog" :close-on-overlay="false" @update:model-value="emit('update:open', $event)">
    <div class="atf">
      <PrismInput
        v-model="name"
        :label="t('app.aiTools.name')"
        required
        :error="nameError"
        :placeholder="t('app.aiTools.namePh')"
      />
      <div class="add-desc">
        <RichTextEditor
          v-model="description"
          :label="t('app.common.description')"
          required
          :rows="4"
          :placeholder="t('app.aiTools.descPh')"
        />
        <p v-if="descriptionError" class="p-field-error" role="alert">{{ descriptionError }}</p>
      </div>
      <TaxonomyChipSelect
        v-model="categoryIds"
        :options="categories"
        tone="category"
        :label="t('app.common.category')"
        required
        :error="categoryError"
        :placeholder="t('app.aiTools.noCategories')"
      />
      <TaxonomyChipSelect
        v-model="stageIds"
        :options="stages"
        tone="stage"
        :label="t('app.common.processStage')"
        required
        :error="stageError"
        :placeholder="t('app.aiTools.noStages')"
      />
      <div class="atf-field">
        <label class="p-label">{{ t('app.common.tags') }}</label>
        <div class="atf-tags" role="group" :aria-label="t('app.common.tags')">
          <span v-for="(tag, i) in tags" :key="tag" class="atf-tag">
            #{{ tag }}
            <button type="button" class="atf-tag-remove" :aria-label="t('app.common.removeTag')" @click="removeTag(i)">×</button>
          </span>
          <input
            v-model="tagInput"
            class="atf-tag-input"
            :placeholder="t('app.common.typeTagPh')"
            @keydown="onTagKeydown"
          />
        </div>
      </div>
      <div v-if="isEdit && existingFile" class="atf-existing-file">
        <span class="atf-existing-file-name" :title="existingFile.fileName">
          📄 {{ existingFile.fileName }} ({{ formatSize(existingFile.fileSize) }})
        </span>
        <button type="button" class="atf-existing-file-remove" @click="removeExistingFile">{{ t('app.common.remove') }}</button>
      </div>
      <PrismFileUpload
        v-model="files"
        :label="isEdit ? t('app.aiTools.replaceFile') : t('app.aiTools.file')"
        :required="!isEdit"
        :error="fileError"
        :accept="ALLOWED_EXTENSIONS.join(',')"
        :max-size="MAX_FILE_SIZE"
        :hint="isEdit ? t('app.aiTools.keepFileHint') : undefined"
      />
    </div>
    <template #footer>
      <PrismButton variant="secondary" @click="close">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton :loading="saving" @click="onSave">{{ isEdit ? t('app.aiTools.saveChanges') : t('app.common.save') }}</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.atf {
  display: grid;
  gap: var(--ds-space-5);
}
.atf-field {
  display: grid;
  gap: var(--ds-space-2);
  align-content: start;
}
.atf-existing-file {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  padding: var(--ds-space-2) var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: rgb(103 232 249 / .04);
  margin-bottom: var(--ds-space-2);
}
.atf-existing-file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
.atf-existing-file-remove {
  border: 1px solid rgb(255 107 107 / .4);
  background: none;
  color: var(--ds-color-danger);
  border-radius: var(--ds-radius-sm);
  font-size: 12px;
  padding: 2px 10px;
  cursor: pointer;
  transition: background var(--ds-duration-fast), border-color var(--ds-duration-fast);
}
@media (hover: hover) {
  .atf-existing-file-remove:hover {
    background: rgb(255 107 107 / .1);
    border-color: var(--ds-color-danger);
  }
}
.atf-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 0 var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
  min-height: var(--ds-control-height);
  transition: border-color var(--ds-duration-fast), background var(--ds-duration-fast), box-shadow var(--ds-duration-fast);
}
.atf-tags:focus-within {
  border-color: var(--ds-color-accent);
  background: var(--ds-input-focus-background);
  box-shadow: 0 0 0 3px rgb(103 232 249 / .10), 0 0 14px rgb(103 232 249 / .05);
}
.atf-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  padding: 1px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 12px;
}
.atf-tag-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  font-size: 14px;
  line-height: 1;
  padding: 0;
}
@media (hover: hover) { .atf-tag-remove:hover { color: var(--ds-color-danger); } }
.atf-tag-input {
  flex: 1;
  min-width: 120px;
  min-height: 34px;
  border: none;
  background: none;
  color: var(--ds-color-text-primary);
  font: inherit;
  padding: 2px 0;
  outline: none;
}
.atf-tag-input::placeholder {
  color: var(--ds-color-text-muted);
}
</style>
