<script setup>
import { useId } from 'vue'
defineProps({ modelValue: { type: Number, default: 0 }, min: { type: Number, default: 0 }, max: { type: Number, default: 100 }, step: { type: Number, default: 1 }, label: String, unit: { type: String, default: '' }, disabled: Boolean })
defineEmits(['update:modelValue'])
const id = useId()
</script>
<template><div class="p-slider"><div><label :for="id">{{ label }}</label><output :for="id">{{ modelValue }}{{ unit }}</output></div><input :id="id" type="range" :min="min" :max="max" :step="step" :value="modelValue" :disabled="disabled" @input="$emit('update:modelValue', Number($event.target.value))" /></div></template>
