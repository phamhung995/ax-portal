<script setup>
import { computed, nextTick, ref, useId, watch } from 'vue'
import PrismFormField from './PrismFormField.vue'
defineOptions({ inheritAttrs: false })
const props = defineProps({ modelValue: [String, Number], id: String, label: String, hint: String, error: String, min: Number, max: Number, step: { type: Number, default: 1 }, placeholder: String, disabled: Boolean, readonly: Boolean, required: Boolean, unit: String })
const emit = defineEmits(['update:modelValue'])
const generatedId = useId()
const inputRef = ref(null)
const clamp = (value, min, max) => Math.min(Math.max(value, min ?? -Infinity), max ?? Infinity)
function parse(raw) {
  if (raw === '' || raw == null) return null
  const n = Number(String(raw).replace(',', '.'))
  return Number.isFinite(n) ? n : null
}
// Draft cục bộ: cho gõ tự do khi đang focus (gõ "4.", xóa hết, gõ số âm...),
// chỉ normalize (parse + clamp) khi blur/Enter hoặc khi stepper đổi giá trị.
const draft = ref(props.modelValue == null ? '' : String(props.modelValue))
let editing = false
function syncFromModel() {
  if (editing) return
  draft.value = props.modelValue == null ? '' : String(props.modelValue)
}
watch(() => props.modelValue, syncFromModel)
function commit() {
  const n = parse(draft.value)
  if (n == null) {
    if (draft.value !== '') emit('update:modelValue', draft.value.trim())
    draft.value = props.modelValue == null ? '' : String(props.modelValue)
    return
  }
  const next = clamp(n, props.min, props.max)
  emit('update:modelValue', next)
  draft.value = String(next)
}
function onInput(event) {
  if (props.readonly) return
  draft.value = event.target.value
}
function onFocus() { editing = true }
function onBlur() {
  editing = false
  commit()
}
function onKeydown(event) {
  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    event.preventDefault()
    nudge(event.key === 'ArrowUp' ? 1 : -1)
    return
  }
  if (event.key === 'Enter') { commit(); return }
  if (event.key === 'Escape') {
    draft.value = props.modelValue == null ? '' : String(props.modelValue)
    nextTick(() => { if (inputRef.value) inputRef.value.value = draft.value })
  }
}
function nudge(delta) {
  if (props.disabled || props.readonly) return
  // Ưu tiên giá trị đang gõ; rỗng thì nhảy về min/max (giống input number native)
  const n = parse(draft.value.trim())
  let next
  if (n == null) next = delta > 0 ? (props.min ?? 0) : (props.max ?? 0)
  else next = Math.round((n + delta * props.step) * 1e6) / 1e6
  next = clamp(next, props.min, props.max)
  emit('update:modelValue', next)
  draft.value = String(next)
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.value = draft.value
      inputRef.value.setSelectionRange(draft.value.length, draft.value.length)
    }
  })
}
const display = computed(() => draft.value)
const stepperDisabled = computed(() => props.disabled || props.readonly)
</script>
<template>
  <PrismFormField :id="id || generatedId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div class="p-input-wrap p-number">
      <input v-bind="$attrs" ref="inputRef" :id="id || generatedId" type="text" inputmode="decimal" :value="display" :placeholder="placeholder" :disabled="disabled" :readonly="readonly" :required="required" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby" @input="onInput" @focus="onFocus" @blur="onBlur" @keydown="onKeydown" />
      <span v-if="unit" class="p-number-unit">{{ unit }}</span>
      <div v-if="!readonly" class="p-number-stepper" :aria-hidden="!!disabled">
        <button type="button" class="p-number-step" :aria-label="'Tăng' + (label ? ' ' + label : '')" :disabled="stepperDisabled" @click="nudge(1)">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M3 7.5 6 4.5l3 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
        <button type="button" class="p-number-step" :aria-label="'Giảm' + (label ? ' ' + label : '')" :disabled="stepperDisabled" @click="nudge(-1)">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M3 4.5 6 7.5l3-3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>
      </div>
    </div>
  </PrismFormField>
</template>
