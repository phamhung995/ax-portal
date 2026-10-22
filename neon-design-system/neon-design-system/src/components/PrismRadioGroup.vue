<script setup>
import { nextTick, useId } from 'vue'
import PrismFormField from './PrismFormField.vue'
const props = defineProps({ modelValue: [String, Number], options: { type: Array, default: () => [] }, id: String, label: String, hint: String, error: String, required: Boolean, disabled: Boolean, inline: Boolean })
const emit = defineEmits(['update:modelValue'])
const generatedId = useId()
const groupName = useId()
function onKeydown(event) {
  const keys = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 }
  if (!(event.key in keys)) return
  const enabled = props.options.filter(o => !o.disabled)
  if (!enabled.length) return
  event.preventDefault()
  const idx = enabled.findIndex(o => (o.value ?? o) === props.modelValue)
  const next = enabled[(idx + keys[event.key] + enabled.length) % enabled.length]
  emit('update:modelValue', next.value ?? next)
  nextTick(() => document.querySelector(`input[type=radio][name=${groupName}][data-p-value="${String(next.value ?? next)}"]`)?.focus())
}
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div :id="id || generatedId" class="p-radio-group" :class="{ 'is-inline': inline }" role="radiogroup" :aria-label="label" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" @keydown="onKeydown">
      <label v-for="(option, i) in options" :key="i" class="p-radio">
        <input type="radio" :name="groupName" :value="option.value ?? option" :data-p-value="option.value ?? option" :checked="(option.value ?? option) === modelValue" :disabled="option.disabled || disabled" @change="emit('update:modelValue', option.value ?? option)" />
        <span class="p-radio-dot" aria-hidden="true"></span>
        <span>{{ option.label ?? option }}</span>
      </label>
    </div>
  </PrismFormField>
</template>
