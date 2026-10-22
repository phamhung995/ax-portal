<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

// Internal calendar grid (không export public). Parent picker điều khiển
// viewMonth + trạng thái chọn; calendar chỉ render + emit sự kiện.
const props = defineProps({
  viewMonth: { type: Date, required: true }, // Date tại ngày 1 của tháng hiển thị
  selected: { type: String, default: '' }, // ISO YYYY-MM-DD
  rangeStart: { type: String, default: '' },
  rangeEnd: { type: String, default: '' },
  min: { type: String, default: '' },
  max: { type: String, default: '' },
  ariaLabel: { type: String, default: 'Lịch' },
})
const emit = defineEmits(['select', 'prev-month', 'next-month'])

const WEEKDAYS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const MONTHS = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6', 'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12']

function toISO(d) {
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const todayISO = toISO(new Date())

const label = computed(() => `${MONTHS[props.viewMonth.getMonth()]} ${props.viewMonth.getFullYear()}`)

const cells = computed(() => {
  const first = new Date(props.viewMonth)
  const startDow = (first.getDay() + 6) % 7 // Monday = 0
  const gridStart = new Date(first)
  gridStart.setDate(1 - startDow)
  const out = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(gridStart)
    d.setDate(gridStart.getDate() + i)
    const iso = toISO(d)
    const inMonth = d.getMonth() === props.viewMonth.getMonth()
    const disabled = Boolean((props.min && iso < props.min) || (props.max && iso > props.max))
    const inRange = props.rangeStart && props.rangeEnd && iso > props.rangeStart && iso < props.rangeEnd
    out.push({ iso, day: d.getDate(), inMonth, disabled, today: iso === todayISO, selected: iso === props.selected, inRange, isStart: iso === props.rangeStart, isEnd: iso === props.rangeEnd })
  }
  return out
})
</script>
<template>
<div class="p-calendar" role="grid" :aria-label="ariaLabel">
  <div class="p-calendar-head">
    <button type="button" class="p-calendar-nav" aria-label="Tháng trước" @click="emit('prev-month')"><ChevronLeft :size="16" /></button>
    <span class="p-calendar-label">{{ label }}</span>
    <button type="button" class="p-calendar-nav" aria-label="Tháng sau" @click="emit('next-month')"><ChevronRight :size="16" /></button>
  </div>
  <div class="p-calendar-week" aria-hidden="true"><span v-for="w in WEEKDAYS" :key="w">{{ w }}</span></div>
  <div class="p-calendar-grid" role="rowgroup">
    <button
      v-for="c in cells" :key="c.iso" type="button" role="gridcell"
      class="p-calendar-day"
      :class="{ 'is-out': !c.inMonth, 'is-today': c.today, 'is-selected': c.selected || c.isStart || c.isEnd, 'is-range': c.inRange, 'is-disabled': c.disabled }"
      :disabled="c.disabled"
      :aria-selected="c.selected || undefined"
      :aria-label="c.iso"
      @click="emit('select', c.iso)"
    >{{ c.day }}</button>
  </div>
</div>
</template>
