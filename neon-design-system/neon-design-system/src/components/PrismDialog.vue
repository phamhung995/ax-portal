<script setup>
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'
const props = defineProps({ modelValue: Boolean, title: { type: String, required: true }, description: String, size: { type: String, default: 'md' }, closeOnOverlay: { type: Boolean, default: true }, closeOnEscape: { type: Boolean, default: true }, preventClose: Boolean, initialFocus: String, placement: String })
const emit = defineEmits(['update:modelValue', 'close'])
const element = ref(null)
const id = useId()
let trigger
let pointerOutside = false
function requestClose() { if (props.preventClose) return; emit('update:modelValue', false); emit('close') }
function outside(event) { return event.target === element.value && (event.clientX < element.value.getBoundingClientRect().left || event.clientX > element.value.getBoundingClientRect().right || event.clientY < element.value.getBoundingClientRect().top || event.clientY > element.value.getBoundingClientRect().bottom) }
watch(() => props.modelValue, async open => {
  await nextTick()
  if (!element.value) return
  if (open && !element.value.open) {
    trigger = document.activeElement
    element.value.showModal()
    if (props.initialFocus) element.value.querySelector(props.initialFocus)?.focus()
  } else if (!open && element.value.open) {
    element.value.close()
    if (trigger?.isConnected) trigger.focus()
  }
}, { immediate: true })
function onNativeClose() { if (props.modelValue) emit('update:modelValue', false) }
onBeforeUnmount(() => { element.value?.close(); if (trigger?.isConnected) trigger.focus() })
</script>
<template>
  <Teleport to="body"><dialog ref="element" class="p-dialog p-glass" :class="[`p-dialog--${size}`, placement && `p-drawer--${placement}`]" :aria-labelledby="`${id}-title`" :aria-describedby="description ? `${id}-description` : undefined" @cancel.prevent="closeOnEscape && requestClose()" @close="onNativeClose" @pointerdown="pointerOutside = outside($event)" @click="pointerOutside && outside($event) && closeOnOverlay && requestClose()">
    <header class="p-dialog-header"><div><span class="p-eyebrow">PRISM / {{ placement ? 'DRAWER' : 'DIALOG' }}</span><h2 :id="`${id}-title`">{{ title }}</h2></div><slot name="title-extra" /><button type="button" class="p-icon-button" aria-label="Đóng" :disabled="preventClose" @click="requestClose"><X :size="18" /></button></header>
    <p v-if="description" :id="`${id}-description`" class="p-dialog-description">{{ description }}</p>
    <div class="p-dialog-body"><slot /></div><footer v-if="$slots.footer" class="p-dialog-footer"><slot name="footer" /></footer>
  </dialog></Teleport>
</template>
