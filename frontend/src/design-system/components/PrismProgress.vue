<script setup>
import { useId } from 'vue'
defineProps({ value: { type: Number, default: 0 }, max: { type: Number, default: 100 }, indeterminate: Boolean, label: String, showValue: { type: Boolean, default: true } })
const id = useId()
</script>
<template><div class="p-progress"><div><label :for="id">{{ label }}</label><span v-if="showValue && !indeterminate">{{ Math.round(Math.min(Math.max(value, 0), max) / max * 100) }}%</span><span v-else>Đang xử lý…</span></div><progress :id="id" :max="max" :value="indeterminate ? undefined : value" /></div></template>
