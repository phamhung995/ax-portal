<script setup>
defineProps({ modelValue: Boolean, label: { type: String, required: true }, disabled: Boolean })
defineEmits(['update:modelValue'])
</script>
<template><label class="p-switch"><input type="checkbox" role="switch" :aria-checked="modelValue" :checked="modelValue" :disabled="disabled" @change="$emit('update:modelValue', $event.target.checked)" /><span class="p-switch-track" aria-hidden="true"></span><span>{{ label }}</span></label></template>
