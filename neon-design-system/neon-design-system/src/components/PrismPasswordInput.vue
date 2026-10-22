<script setup>
import { ref, useId } from 'vue'
import PrismFormField from './PrismFormField.vue'
defineOptions({ inheritAttrs: false })
defineProps({ modelValue: { type: String, default: '' }, id: String, label: String, hint: String, error: String, placeholder: String, disabled: Boolean, required: Boolean, strength: { type: Number, default: 0 } })
defineEmits(['update:modelValue'])
const generatedId = useId()
const visible = ref(false)
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div class="p-input-wrap p-password">
      <slot name="prefix" />
      <input v-bind="$attrs" :id="id || generatedId" :type="visible ? 'text' : 'password'" :value="modelValue" :placeholder="placeholder" :disabled="disabled" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" @input="$emit('update:modelValue', $event.target.value)" />
      <div v-if="strength > 0" class="p-strength" :aria-hidden="true">
        <i v-for="i in 4" :key="i" :class="{ 'is-on': i <= strength }"></i>
      </div>
      <button type="button" class="p-password-toggle" :aria-label="visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'" :aria-pressed="visible" :disabled="disabled" @click="visible = !visible">
        <svg v-if="!visible" width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M1.5 8s2.3-4.5 6.5-4.5S14.5 8 14.5 8 12.2 12.5 8 12.5 1.5 8 1.5 8Z" stroke="currentColor" stroke-width="1.4" /><circle cx="8" cy="8" r="2" stroke="currentColor" stroke-width="1.4" /></svg>
        <svg v-else width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true"><path d="M1.5 8s2.3-4.5 6.5-4.5S14.5 8 14.5 8 12.2 12.5 8 12.5 1.5 8 1.5 8Z" stroke="currentColor" stroke-width="1.4" /><circle cx="8" cy="8" r="2" stroke="currentColor" stroke-width="1.4" /><path d="m2.5 13.5 11-11" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" /></svg>
      </button>
      <slot name="suffix" />
    </div>
  </PrismFormField>
</template>
