<script setup>
import { computed, ref, useId } from 'vue'
import { useI18nStore } from '@/stores/i18n'
import PrismFormField from './PrismFormField.vue'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, id: String, label: String, hint: String, error: String, accept: String, multiple: Boolean, disabled: Boolean, required: Boolean, maxSize: { type: Number, default: 5 * 1024 * 1024 } })
const emit = defineEmits(['update:modelValue'])
const generatedId = useId()
const inputRef = ref(null)
const dragOver = ref(false)
const { t } = useI18nStore()
const rejected = ref('')
const files = computed(() => props.modelValue)
const totalSize = computed(() => props.modelValue.reduce((s, f) => s + (f.size || 0), 0))
function matchesAccept(file) {
  if (!props.accept) return true
  return props.accept.split(',').some(a => {
    const t = a.trim().toLowerCase()
    if (!t) return false
    if (t.startsWith('.')) return file.name.toLowerCase().endsWith(t)
    if (t.endsWith('/*')) return file.type.startsWith(t.slice(0, -1))
    return file.type === t
  })
}
function addFiles(list) {
  if (props.disabled) return
  rejected.value = ''
  const incoming = [...list]
  const next = props.multiple ? [...props.modelValue] : []
  for (const file of incoming) {
    if (!matchesAccept(file)) { rejected.value = t('app.fileUpload.invalidType', { name: file.name }); continue }
    if (props.maxSize && file.size > props.maxSize) { rejected.value = t('app.fileUpload.tooLarge', { name: file.name }); continue }
    if (next.some(f => f.name === file.name && f.size === file.size)) continue
    next.push(file)
    if (!props.multiple) break
  }
  emit('update:modelValue', next)
  if (inputRef.value) inputRef.value.value = ''
}
function remove(index) { emit('update:modelValue', props.modelValue.filter((_, i) => i !== index)) }
function clear() { emit('update:modelValue', []); if (inputRef.value) inputRef.value.value = '' }
function openPicker() { if (!props.disabled) inputRef.value?.click() }
function onKeydown(event) { if ((event.key === 'Enter' || event.key === ' ') && !props.disabled) { event.preventDefault(); openPicker() } }
// dragleave also fires when the pointer moves onto a child element — only
// clear the dragover state when the pointer actually leaves the drop area.
function onDragLeave(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) dragOver.value = false
}
const formatSize = bytes => bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : bytes >= 1024 ? `${Math.round(bytes / 1024)} KB` : `${bytes} B`
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div class="p-fileupload" :class="{ 'is-dragover': dragOver, 'is-disabled': disabled }"
         @dragenter.prevent="dragOver = true" @dragover.prevent="dragOver = true"
         @dragleave.prevent="onDragLeave" @drop.prevent="dragOver = false; addFiles($event.dataTransfer.files)">
      <input v-bind="$attrs" ref="inputRef" :id="id || generatedId" type="file" class="p-fileupload-input" :accept="accept" :multiple="multiple" :disabled="disabled" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" @change="addFiles($event.target.files)" />
      <button type="button" class="p-fileupload-drop" draggable="false" :disabled="disabled" @click="openPicker" @keydown="onKeydown">
        <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden="true"><path d="M11 14V4m0 0L7.5 7.5M11 4l3.5 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /><path d="M3.5 14.5v2a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" /></svg>
        <span class="p-fileupload-text"><strong>{{ t('app.fileUpload.dragDrop') }}</strong> {{ t('app.fileUpload.orSelect') }} <span class="p-fileupload-link">{{ t('app.fileUpload.chooseFromDevice') }}</span></span>
        <span v-if="accept" class="p-fileupload-meta">{{ accept }}{{ maxSize ? ` · ${t('app.fileUpload.maxSize', { size: formatSize(maxSize) })}` : '' }}</span>
      </button>
      <ul v-if="files.length" class="p-fileupload-list">
        <li v-for="(file, i) in files" :key="file.name + file.size" class="p-fileupload-item">
          <span class="p-fileupload-name" :title="file.name">{{ file.name }}</span>
          <span class="p-fileupload-size">{{ formatSize(file.size || 0) }}</span>
          <button type="button" class="p-fileupload-remove" :aria-label="t('app.fileUpload.removeFile', { name: file.name })" :disabled="disabled" @click="remove(i)">
            <svg width="11" height="11" viewBox="0 0 11 11" fill="none" aria-hidden="true"><path d="m2.5 2.5 6 6m0-6-6 6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" /></svg>
          </button>
        </li>
      </ul>
      <p v-if="files.length" class="p-fileupload-total">{{ files.length }} {{ t('app.fileUpload.filesCount') }} · {{ formatSize(totalSize) }}
        <button type="button" class="p-fileupload-clear" :disabled="disabled" @click="clear">{{ t('app.fileUpload.clearAll') }}</button>
      </p>
      <p v-if="rejected" class="p-field-error">{{ rejected }}</p>
    </div>
  </PrismFormField>
</template>
