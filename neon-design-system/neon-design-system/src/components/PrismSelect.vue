<script setup>
import { useId } from 'vue'
import PrismFormField from './PrismFormField.vue'
defineOptions({ inheritAttrs: false })
defineProps({ modelValue: [String, Number], options: { type: Array, default: () => [] }, id: String, label: String, hint: String, error: String, placeholder: String, disabled: Boolean, required: Boolean })
const emit = defineEmits(['update:modelValue'])
const generatedId = useId()
function update(event) { const option = event.target.selectedOptions[0]; emit('update:modelValue', option._value ?? option.value) }
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" v-slot="field">
    <select v-bind="$attrs" :id="id || generatedId" class="p-control" :value="modelValue" :disabled="disabled" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" @change="update">
      <option v-if="placeholder" value="" disabled>{{ placeholder }}</option>
      <option v-for="option in options" :key="option.value ?? option" :value="option.value ?? option" :disabled="option.disabled">{{ option.label ?? option }}</option>
    </select>
  </PrismFormField>
</template>
