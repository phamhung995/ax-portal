<script setup>
import { Info, CircleCheck, TriangleAlert, X } from 'lucide-vue-next'
defineProps({ tone: { type: String, default: 'info' }, title: String, dismissible: Boolean, role: { type: String, default: 'status' } })
defineEmits(['dismiss'])
</script>
<template><div class="p-alert" :class="`p-tone--${tone}`" :role="role"><component :is="tone === 'success' ? CircleCheck : ['danger','warning'].includes(tone) ? TriangleAlert : Info" :size="19" aria-hidden="true" /><div><strong v-if="title">{{ title }}</strong><div class="p-alert-content"><slot /></div><slot name="actions" /></div><button v-if="dismissible" type="button" class="p-icon-button" aria-label="Đóng thông báo" @click="$emit('dismiss')"><X :size="16" /></button></div></template>
