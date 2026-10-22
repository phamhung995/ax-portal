<script setup>
import { useId } from 'vue'
import PrismFormField from './PrismFormField.vue'
defineOptions({ inheritAttrs: false })
defineProps({ modelValue: [String, Number], id: String, label: String, hint: String, error: String, type: { type: String, default: 'text' }, disabled: Boolean, readonly: Boolean, required: Boolean })
defineEmits(['update:modelValue'])
const generatedId = useId()
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div class="p-input-wrap"><slot name="prefix" /><input v-bind="$attrs" :id="id || generatedId" :value="modelValue" :type="type" :disabled="disabled" :readonly="readonly" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" @input="$emit('update:modelValue', $event.target.value)" /><slot name="suffix" /></div>
  </PrismFormField>
</template>
