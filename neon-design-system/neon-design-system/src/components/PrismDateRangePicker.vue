<script setup>
import { computed, ref, useId } from 'vue'
import { CalendarRange } from 'lucide-vue-next'
import PrismFormField from './PrismFormField.vue'
import PrismCalendar from './PrismCalendar.vue'
import { useDatePopup } from './useDatePopup.js'

defineOptions({ inheritAttrs: false })
const props = defineProps({
  modelValue: { type: Array, default: () => [] }, // [startISO, endISO]
  id: String,
  label: String,
  hint: String,
  error: String,
  placeholder: { type: String, default: 'Chọn khoảng ngày…' },
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
const [startISO, endISO] = [computed(() => props.modelValue[0] || ''), computed(() => props.modelValue[1] || '')]
function initialMonth() {
  const d = startISO.value ? new Date(startISO.value + 'T00:00:00') : new Date()
  return new Date(d.getFullYear(), d.getMonth(), 1)
}
const viewMonth = ref(initialMonth())
const pendingStart = ref('')

const display = computed(() => {
  const fmt = iso => { if (!iso) return ''; const [y, m, d] = iso.split('-'); return `${d}/${m}/${y}` }
  if (startISO.value && endISO.value) return `${fmt(startISO.value)} → ${fmt(endISO.value)}`
  if (startISO.value) return `${fmt(startISO.value)} → …`
  return ''
})

function openPicker() {
  if (props.disabled) return
  if (startISO.value) {
    const d = new Date(startISO.value + 'T00:00:00')
    viewMonth.value = new Date(d.getFullYear(), d.getMonth(), 1)
  }
  pendingStart.value = ''
  openPopup()
}
function closePicker(refocus = true) { closePopup(refocus) }
function onKeydown(e) {
  if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker() }
  else if (e.key === 'Escape' && open.value) { e.stopPropagation(); closePicker() }
}
// Chọn lần 1 = start (hoặc đặt lại start nếu đã có cả start+end);
// chọn lần 2 = end (đảo nếu nhỏ hơn start).
function onSelect(iso) {
  if (!pendingStart.value || (startISO.value && endISO.value)) {
    pendingStart.value = iso
    emit('update:modelValue', [iso, ''])
    return
  }
  const s = iso < pendingStart.value ? iso : pendingStart.value
  const en = iso < pendingStart.value ? pendingStart.value : iso
  pendingStart.value = ''
  emit('update:modelValue', [s, en])
  emit('change', [s, en])
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
          @click="openPicker" @keydown="onKeydown" />
        <CalendarRange :size="16" class="p-date-icon" aria-hidden="true" />
      </div>
    </div>
  </PrismFormField>
  <Teleport :to="teleportTarget">
    <div v-if="open" ref="popupRef" class="p-date-popup p-glass" :class="{ 'p-date-popup--in-dialog': inDialog }" role="dialog" :aria-label="label || 'Chọn khoảng ngày'" :style="{ top: `${popupPos.top}px`, left: `${popupPos.left}px` }">
      <PrismCalendar :view-month="viewMonth" :range-start="pendingStart || startISO" :range-end="endISO" :min="min" :max="max" aria-label="Lịch chọn khoảng ngày"
        @select="onSelect" @prev-month="onPrevMonth" @next-month="onNextMonth" />
    </div>
  </Teleport>
</template>
