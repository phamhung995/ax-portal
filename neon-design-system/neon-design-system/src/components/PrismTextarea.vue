<script setup>
import { useId } from 'vue'
import PrismFormField from './PrismFormField.vue'
defineOptions({ inheritAttrs: false })
defineProps({ modelValue: String, id: String, label: String, hint: String, error: String, rows: { type: Number, default: 3 }, maxlength: Number, disabled: Boolean, readonly: Boolean, required: Boolean })
defineEmits(['update:modelValue'])
const generatedId = useId()
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" v-slot="field">
    <textarea v-bind="$attrs" :id="id || generatedId" class="p-control" :value="modelValue" :rows="rows" :maxlength="maxlength" :disabled="disabled" :readonly="readonly" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" @input="$emit('update:modelValue', $event.target.value)" />
    <small v-if="maxlength" class="p-counter">{{ modelValue?.length || 0 }} / {{ maxlength }} ký tự</small>
  </PrismFormField>
</template>
