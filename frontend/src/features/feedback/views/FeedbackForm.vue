<script setup lang="ts">
import { ref } from 'vue'
import { ImagePlus, X } from 'lucide-vue-next'
import RichTextEditor from '@/components/RichTextEditor.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import { feedbackApi } from '@/features/feedback/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { stripHtml } from '@/lib/sanitize'
import { fileToDataUri } from '@/lib/imageCompress'
import type { FeedbackType } from '@/features/feedback/types'

const emit = defineEmits<{
  (e: 'submitted'): void
}>()

const TYPE_OPTIONS: { value: FeedbackType; label: string }[] = [
  { value: 'BUG', label: '🐞 Bug' },
  { value: 'SUGGESTION', label: '💡 Suggestion' },
  { value: 'QUESTION', label: '❓ Question' },
]

const type = ref<FeedbackType>('BUG')
const content = ref('')
const screenshot = ref('')
const screenshotName = ref('')
const submitting = ref(false)
const contentError = ref('')

const imageInput = ref<HTMLInputElement | null>(null)

function pickScreenshot() {
  imageInput.value?.click()
}

async function onScreenshotPicked(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const dataUri = await fileToDataUri(file)
  if (!dataUri) {
    toastError('Unsupported image. Allowed: .jpg, .jpeg, .png, .webp (max 5MB).')
    return
  }
  screenshot.value = dataUri
  screenshotName.value = file.name
}

function removeScreenshot() {
  screenshot.value = ''
  screenshotName.value = ''
}

async function onSubmit() {
  const text = stripHtml(content.value).trim()
  if (!text) {
    contentError.value = 'Please describe your feedback.'
    return
  }
  if (text.length > 1000) {
    contentError.value = 'Feedback text is at most 1000 characters.'
    return
  }
  contentError.value = ''
  submitting.value = true
  try {
    await feedbackApi.submit({
      type: type.value,
      content: content.value.trim(),
      screenshot: screenshot.value || undefined,
    })
    toastSuccess('Thank you for your feedback!')
    emit('submitted')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="fb-form">
    <p class="fb-intro">
      Found a bug or have a suggestion? Let us know — your feedback helps us improve the system.
    </p>

    <div class="fb-type" role="radiogroup" aria-label="Feedback type">
      <button
        v-for="opt in TYPE_OPTIONS"
        :key="opt.value"
        type="button"
        class="fb-type-btn"
        :class="{ 'is-active': type === opt.value }"
        role="radio"
        :aria-checked="type === opt.value"
        @click="type = opt.value"
      >
        {{ opt.label }}
      </button>
    </div>

    <RichTextEditor
      v-model="content"
      label="Description"
      :rows="5"
      :error="contentError"
      placeholder="Describe the issue or your suggestion…"
    />

    <div class="fb-shot">
      <template v-if="screenshot">
        <div class="fb-shot-preview">
          <img :src="screenshot" :alt="screenshotName" />
          <button type="button" class="fb-shot-remove" aria-label="Remove screenshot" @click="removeScreenshot">
            <X :size="14" />
          </button>
        </div>
        <span class="fb-shot-name" :title="screenshotName">{{ screenshotName }}</span>
      </template>
      <button v-else type="button" class="fb-shot-add" @click="pickScreenshot">
        <ImagePlus :size="16" aria-hidden="true" />
        Attach screenshot (optional)
      </button>
      <input
        ref="imageInput"
        type="file"
        accept=".jpg,.jpeg,.png,.webp"
        class="fb-file-input"
        @change="onScreenshotPicked"
        tabindex="-1"
        aria-hidden="true"
      />
    </div>

    <div class="fb-submit">
      <PrismButton :loading="submitting" :disabled="!stripHtml(content).trim()" @click="onSubmit">Submit</PrismButton>
    </div>
  </div>
</template>

<style scoped>
.fb-form {
  display: grid;
  gap: var(--ds-space-4);
}
.fb-intro {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.fb-type {
  display: flex;
  gap: var(--ds-space-2);
}
.fb-type-btn {
  flex: 1;
  padding: 8px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: transparent;
  color: var(--ds-color-text-secondary);
  font: 13px var(--ds-font-sans);
  cursor: pointer;
}
.fb-type-btn:hover {
  background: var(--ds-color-bg-hover, rgba(255, 255, 255, 0.06));
  color: var(--ds-color-text-primary);
}
.fb-type-btn.is-active {
  border-color: var(--ds-color-accent);
  color: var(--ds-color-accent);
  background: var(--ds-color-accent-soft, rgba(56, 189, 248, 0.12));
}
.fb-shot {
  display: grid;
  gap: var(--ds-space-2);
}
.fb-shot-add {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 8px var(--ds-space-3);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: none;
  color: var(--ds-color-text-secondary);
  font: 13px var(--ds-font-sans);
  cursor: pointer;
  justify-self: start;
}
.fb-shot-add:hover {
  color: var(--ds-color-accent);
  border-color: var(--ds-color-accent);
}
.fb-shot-preview {
  position: relative;
  display: inline-block;
}
.fb-shot-preview img {
  display: block;
  max-width: 320px;
  max-height: 200px;
  border-radius: var(--ds-radius-sm);
  border: 1px solid var(--ds-color-border);
}
.fb-shot-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: rgb(0 0 0 / 0.6);
  color: #fff;
  cursor: pointer;
}
.fb-shot-name {
  font-size: 12px;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fb-file-input {
  display: none;
}
.fb-submit {
  display: flex;
  justify-content: flex-end;
}
</style>
