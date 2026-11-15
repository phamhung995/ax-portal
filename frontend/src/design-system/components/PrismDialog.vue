<script setup>
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'
const props = defineProps({ modelValue: Boolean, title: { type: String, required: true }, description: String, size: { type: String, default: 'md' }, closeOnOverlay: { type: Boolean, default: true }, closeOnEscape: { type: Boolean, default: true }, preventClose: Boolean, initialFocus: String, placement: String, dialogClass: String })
const emit = defineEmits(['update:modelValue', 'close'])
const element = ref(null)
const id = useId()
let trigger
let pointerOutside = false
// While a native file picker (input[type=file]) is open, cancelling it
// (Escape) makes the browser deliver a `cancel` event to the modal dialog
// — treat that as a picker dismissal, NOT as "user pressed Escape".
let filePickerOpen = false
let lastPickerReturnAt = 0
function isFileInput(t) {
  return t?.tagName === 'INPUT' && t.type === 'file' && element.value?.contains(t)
}
function onFilePickerClick(ev) {
  if (isFileInput(ev.target)) filePickerOpen = true
}
function onFilePickerFocusin(ev) {
  // focus returned to the file input → the picker has closed
  if (isFileInput(ev.target)) {
    filePickerOpen = false
    lastPickerReturnAt = Date.now()
  }
}
function requestClose() { if (props.preventClose) return; emit('update:modelValue', false); emit('close') }
function onDialogCancel() {
  // Swallow the cancel that belongs to the file picker (picker still open,
  // or focus just returned to the file input).
  if (filePickerOpen || Date.now() - lastPickerReturnAt < 500) return
  if (closeOnEscape) requestClose()
}
function outside(event) { return event.target === element.value && (event.clientX < element.value.getBoundingClientRect().left || event.clientX > element.value.getBoundingClientRect().right || event.clientY < element.value.getBoundingClientRect().top || event.clientY > element.value.getBoundingClientRect().bottom) }
watch(() => props.modelValue, async open => {
  await nextTick()
  if (!element.value) return
  if (open && !element.value.open) {
    trigger = document.activeElement
    element.value.showModal()
    element.value.addEventListener('wheel', onDialogWheel, { passive: false })
    document.addEventListener('click', onFilePickerClick, true)
    document.addEventListener('focusin', onFilePickerFocusin, true)
    if (props.initialFocus) element.value.querySelector(props.initialFocus)?.focus()
  } else if (!open && element.value.open) {
    element.value.close()
    element.value.removeEventListener('wheel', onDialogWheel)
    document.removeEventListener('click', onFilePickerClick, true)
    document.removeEventListener('focusin', onFilePickerFocusin, true)
    filePickerOpen = false
    if (trigger?.isConnected) trigger.focus()
  }
}, { immediate: true })
function onNativeClose() { if (props.modelValue) emit('update:modelValue', false) }
// ── Scroll chaining guard ────────────────────────────────────────────────────
// When the wheel reaches the end of the dialog's (or a nested area's)
// scrollable range, the browser chains the scroll to the page behind
// (the background scrolled while the user was "inside" the popup).
// Walk up from the event target to the nearest scrollable element and
// block the default when that element cannot scroll further in the
// wheel direction.
function onDialogWheel(e) {
  const el = element.value
  if (!el) return
  let node = e.target instanceof Element ? e.target : null
  while (node && node !== el) {
    const st = getComputedStyle(node)
    if (/auto|scroll/.test(st.overflowY) && node.scrollHeight > node.clientHeight) {
      const atTop = node.scrollTop <= 0
      const atBottom = node.scrollTop + node.clientHeight >= node.scrollHeight - 1
      // The element can still scroll in this direction — let it.
      if ((e.deltaY > 0 && !atBottom) || (e.deltaY < 0 && !atTop)) return
      break // at a boundary → the wheel would chain to the background
    }
    node = node.parentElement
  }
  e.preventDefault()
}
onBeforeUnmount(() => {
  document.removeEventListener('click', onFilePickerClick, true)
  document.removeEventListener('focusin', onFilePickerFocusin, true)
  element.value?.removeEventListener('wheel', onDialogWheel)
  element.value?.close(); if (trigger?.isConnected) trigger.focus()
})
</script>
<template>
  <Teleport to="body"><dialog ref="element" class="p-dialog p-glass" :class="[`p-dialog--${size}`, placement && `p-drawer--${placement}`, dialogClass]" :aria-labelledby="`${id}-title`" :aria-describedby="description ? `${id}-description` : undefined" @cancel.prevent="onDialogCancel()" @close="onNativeClose" @pointerdown="pointerOutside = outside($event)" @click="pointerOutside && outside($event) && closeOnOverlay && requestClose()">
    <header class="p-dialog-header"><div><span class="p-eyebrow">PRISM / {{ placement ? 'DRAWER' : 'DIALOG' }}</span><h2 :id="`${id}-title`">{{ title }}</h2></div><slot name="title-extra" /><button type="button" class="p-icon-button" aria-label="Đóng" :disabled="preventClose" @click="requestClose"><X :size="18" /></button></header>
    <p v-if="description" :id="`${id}-description`" class="p-dialog-description">{{ description }}</p>
    <div class="p-dialog-body"><slot /></div><footer v-if="$slots.footer" class="p-dialog-footer"><slot name="footer" /></footer>
  </dialog></Teleport>
</template>
