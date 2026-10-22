<script setup>
import { useId } from 'vue'
defineProps({ value: [String, Number], id: String, label: String, name: String, checked: Boolean, disabled: Boolean })
defineEmits(['update:modelValue'])
const generatedId = useId()
</script>
<template>
  <label class="p-radio" :for="id || generatedId">
    <input :id="id || generatedId" type="radio" :name="name" :value="value" :checked="checked" :disabled="disabled" @change="$emit('update:modelValue', value)" />
    <span class="p-radio-dot" aria-hidden="true"></span>
    <span>{{ label }}<slot /></span>
  </label>
</template>
