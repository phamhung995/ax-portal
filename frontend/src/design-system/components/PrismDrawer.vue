<script setup>
import PrismDialog from './PrismDialog.vue'
defineProps({ modelValue: Boolean, title: { type: String, required: true }, description: String, placement: { type: String, default: 'right' }, closeOnOverlay: { type: Boolean, default: true }, closeOnEscape: { type: Boolean, default: true } })
defineEmits(['update:modelValue', 'close'])
</script>
<template><PrismDialog :model-value="modelValue" :title="title" :description="description" :placement="placement" :close-on-overlay="closeOnOverlay" :close-on-escape="closeOnEscape" @update:model-value="$emit('update:modelValue', $event)" @close="$emit('close')"><slot /><template v-if="$slots.footer" #footer><slot name="footer" /></template></PrismDialog></template>
