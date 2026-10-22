<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { CircleCheck, Info, X } from 'lucide-vue-next'
const entries = ref([])
let serial = 0
const timers = new Map()
function dismiss(id) { clearTimeout(timers.get(id)); timers.delete(id); entries.value = entries.value.filter(entry => entry.id !== id) }
function pause(entry) { if (!timers.has(entry.id)) return; clearTimeout(timers.get(entry.id)); entry.remaining = Math.max(0, entry.remaining - (Date.now() - entry.started)); timers.delete(entry.id) }
function resume(entry) { if (entry.duration === 0 || timers.has(entry.id)) return; entry.started = Date.now(); timers.set(entry.id, setTimeout(() => dismiss(entry.id), entry.remaining)) }
function push({ title, message = '', tone = 'success', duration = 5000 }) { const entry = { id: ++serial, title, message, tone, duration, remaining: duration, started: 0 }; entries.value.push(entry); resume(entry); return entry.id }
onBeforeUnmount(() => timers.forEach(clearTimeout))
defineExpose({ push, dismiss })
</script>
<template><Teleport to="body"><div class="p-toasts" aria-live="polite" aria-relevant="additions text"><div v-for="entry in entries" :key="entry.id" class="p-toast" :class="`p-tone--${entry.tone}`" @mouseenter="pause(entry)" @mouseleave="!$event.currentTarget.contains($event.currentTarget.ownerDocument.activeElement) && resume(entry)" @focusin="pause(entry)" @focusout="!$event.currentTarget.contains($event.relatedTarget) && resume(entry)"><component :is="entry.tone === 'success' ? CircleCheck : Info" :size="20" aria-hidden="true" /><div><strong>{{ entry.title }}</strong><p v-if="entry.message">{{ entry.message }}</p></div><button type="button" class="p-icon-button" aria-label="Đóng thông báo" @click="dismiss(entry.id)"><X :size="16" /></button></div></div></Teleport></template>
