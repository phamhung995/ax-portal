<script setup>
import { computed, ref, useId } from 'vue'
import { CalendarDays } from 'lucide-vue-next'
import PrismFormField from './PrismFormField.vue'
import PrismCalendar from './PrismCalendar.vue'
import { useDatePopup } from './useDatePopup.js'

defineOptions({ inheritAttrs: false })
const props = defineProps({
  modelValue: { type: String, default: '' }, // ISO YYYY-MM-DD
  id: String,
  label: String,
  hint: String,
  error: String,
  placeholder: { type: String, default: 'Chọn ngày…' },
  min: { type: String, default: '' },
  max: { type: String, default: '' },
  disabled: Boolean,
  required: Boolean,
})
const emit = defineEmits(['update:modelValue', 'change'])

const generatedId = useId()
const fieldId = computed(() => props.id || generatedId)
const root = ref(null)
const inputRef = ref(null)
const { open, popupRef, popupPos, teleportTarget, inDialog, openPopup, closePopup } = useDatePopup(inputRef, root)
function initialMonth() {
  const d = props.modelValue ? new Date(props.modelValue + 'T00:00:00') : new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1)
}
const viewMonth = ref(initialMonth())

const display = computed(() => {
  if (!props.modelValue) return ''
  const [y, m, d] = props.modelValue.split('-')
  return `${d}/${m}/${y}`
})

function openPicker() {
  if (props.disabled) return
  if (props.modelValue) {
    const d = new Date(props.modelValue + 'T00:00:00')
    viewMonth.value = new Date(d.getFullYear(), d.getMonth(), 1)
  }
  openPopup()
}
function closePicker(refocus = true) { closePopup(refocus) }
function onInputClick() { openPicker() }
function onKeydown(e) {
  if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker() }
  else if (e.key === 'Escape' && open.value) { e.stopPropagation(); closePicker() }
}
function onSelect(iso) {
  emit('update:modelValue', iso)
  emit('change', iso)
  closePicker()
}
function onPrevMonth() { viewMonth.value = new Date(viewMonth.value.getFullYear(), viewMonth.value.getMonth() - 1, 1) }
function onNextMonth() { viewMonth.value = new Date(viewMonth.value.getFullYear(), viewMonth.value.getMonth() + 1, 1) }
</script>
<template>
  <PrismFormField :id="fieldId" :label="label" :hint="hint" :error="error" :required="required" :disabled="disabled" v-slot="field">
    <div ref="root" class="p-date" :class="{ 'is-open': open }">
      <div class="p-input-wrap p-date-input">
        <input ref="inputRef" v-bind="$attrs" :id="fieldId" type="text" role="combobox" :value="display" :placeholder="placeholder" :disabled="disabled" :readonly="true"
          :aria-expanded="open" aria-haspopup="dialog" :aria-invalid="field.invalid || undefined" :aria-describedby="field.describedby"
          @click="onInputClick" @keydown="onKeydown" />
        <CalendarDays :size="16" class="p-date-icon" aria-hidden="true" />
      </div>
    </div>
  </PrismFormField>
  <Teleport :to="teleportTarget">
    <div v-if="open" ref="popupRef" class="p-date-popup p-glass" :class="{ 'p-date-popup--in-dialog': inDialog }" role="dialog" :aria-label="label || 'Chọn ngày'" :style="{ top: `${popupPos.top}px`, left: `${popupPos.left}px` }">
      <PrismCalendar :view-month="viewMonth" :selected="modelValue" :min="min" :max="max" aria-label="Lịch chọn ngày"
        @select="onSelect" @prev-month="onPrevMonth" @next-month="onNextMonth" />
    </div>
  </Teleport>
</template>
