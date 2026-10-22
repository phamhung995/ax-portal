<script setup>
import { useId } from 'vue'
defineProps({ modelValue: Boolean, id: String, label: String, disabled: Boolean, indeterminate: Boolean })
defineEmits(['update:modelValue'])
const generatedId = useId()
</script>
<template><label class="p-check" :for="id || generatedId"><input :id="id || generatedId" type="checkbox" :checked="modelValue" :indeterminate="indeterminate" :disabled="disabled" @change="$emit('update:modelValue', $event.target.checked)" /><span>{{ label }}<slot /></span></label></template>
