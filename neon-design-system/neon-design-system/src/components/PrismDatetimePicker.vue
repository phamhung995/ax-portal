<script setup>
import { computed, ref, useId } from 'vue'
import { CalendarClock } from 'lucide-vue-next'
import PrismFormField from './PrismFormField.vue'
import PrismCalendar from './PrismCalendar.vue'
import { useDatePopup } from './useDatePopup.js'

defineOptions({ inheritAttrs: false })
const props = defineProps({
  modelValue: { type: String, default: '' }, // ISO YYYY-MM-DDTHH:mm
  id: String,
  label: String,
  hint: String,
  error: String,
  placeholder: { type: String, default: 'Chọn ngày và giờ…' },
  min: { type: String, default: '' },
  max: { type: String, default: '' },
  minuteStep: { type: Number, default: 5 },
  disabled: Boolean,
  required: Boolean,
})
const emit = defineEmits(['update:modelValue', 'change'])

const generatedId = useId()
const fieldId = computed(() => props.id || generatedId)
const root = ref(null)
const inputRef = ref(null)
const { open, popupRef, popupPos, teleportTarget, inDialog, openPopup, closePopup } = useDatePopup(inputRef, root)

const datePart = computed(() => props.modelValue.slice(0, 10))
const timePart = computed(() => props.modelValue.slice(11, 16))
function initialMonth() {
  const d = datePart.value ? new Date(datePart.value + 'T00:00:00') : new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1)
}
const viewMonth = ref(initialMonth())

const display = computed(() => {
  if (!props.modelValue) return ''
  const [y, m, d] = datePart.value.split('-')
  return `${d}/${m}/${y} ${timePart.value || '00:00'}`
})

const hours = computed(() => Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0')))
const minutes = computed(() => {
  const step = Math.max(1, Math.min(60, Math.round(props.minuteStep)))
  return Array.from({ length: Math.floor(60 / step) }, (_, i) => String(i * step).padStart(2, '0'))
})

function currentDateTime() { return datePart.value ? `${datePart.value}T${timePart.value || '00:00'}` : '' }

function openPicker() {
  if (props.disabled) return
  if (datePart.value) {
    const d = new Date(datePart.value + 'T00:00:00')
    viewMonth.value = new Date(d.getFullYear(), d.getMonth(), 1)
  }
  openPopup()
}
function closePicker(refocus = true) { closePopup(refocus) }
function onKeydown(e) {
  if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker() }
  else if (e.key === 'Escape' && open.value) { e.stopPropagation(); closePicker() }
}
function onDateSelect(iso) {
  const t = timePart.value || '00:00'
  emit('update:modelValue', `${iso}T${t}`)
  emit('change', `${iso}T${t}`)
  closePicker()
}
function onTimeSelect(h, m) {
  const d = datePart.value
  if (!d) return
  const value = `${d}T${h}:${m}`
  emit('update:modelValue', value)
  emit('change', value)
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
          @click="openPicker" @keydown="onKeydown" />
        <CalendarClock :size="16" class="p-date-icon" aria-hidden="true" />
      </div>
    </div>
  </PrismFormField>
  <Teleport :to="teleportTarget">
    <div v-if="open" ref="popupRef" class="p-date-popup p-date-popup--dt p-glass" :class="{ 'p-date-popup--in-dialog': inDialog }" role="dialog" :aria-label="label || 'Chọn ngày và giờ'" :style="{ top: `${popupPos.top}px`, left: `${popupPos.left}px` }">
      <PrismCalendar :view-month="viewMonth" :selected="datePart" :min="min" :max="max" aria-label="Lịch chọn ngày"
        @select="onDateSelect" @prev-month="onPrevMonth" @next-month="onNextMonth" />
      <div class="p-time" role="group" aria-label="Chọn giờ">
        <div class="p-time-col">
          <span class="p-time-title">Giờ</span>
          <button v-for="h in hours" :key="h" type="button" class="p-time-btn" :class="{ 'is-active': timePart.startsWith(h + ':') }" :aria-label="'Giờ ' + Number(h)" @click="onTimeSelect(h, timePart.slice(3) || '00')">{{ h }}</button>
        </div>
        <div class="p-time-col">
          <span class="p-time-title">Phút</span>
          <button v-for="m in minutes" :key="m" type="button" class="p-time-btn" :class="{ 'is-active': timePart.endsWith(':' + m) }" :aria-label="'Phút ' + Number(m)" @click="onTimeSelect(timePart.slice(0, 2) || '00', m)">{{ m }}</button>
        </div>
        <button type="button" class="p-time-done" @click="closePicker">Xong</button>
      </div>
    </div>
  </Teleport>
</template>
