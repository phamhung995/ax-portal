<script setup>
import { computed } from 'vue'
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ date: 'YYYY-MM-DD', value }]
  weeks: { type: Number, default: 26 },
  max: Number,
  ariaLabel: { type: String, default: 'Biểu đồ nhiệt' },
})
const CELL = 14
const GAP = 3
const chart = computed(() => {
  const byDate = new Map(props.data.map(d => [d.date, Number(d.value) || 0]))
  const maxValue = props.max || Math.max(...props.data.map(d => Number(d.value) || 0), 1)
  const today = new Date()
  const end = new Date(today)
  const totalDays = props.weeks * 7
  const start = new Date(today)
  start.setDate(start.getDate() - (totalDays - 1))
  // căn cho cột đầu tiên bắt đầu từ Chủ nhật
  start.setDate(start.getDate() - start.getDay())
  const cols = []
  const cursor = new Date(start)
  // đủ cột để bao phủ đến hôm nay kể cả sau khi căn Chủ nhật
  const totalCols = Math.ceil((end - start) / (7 * 86400000)) + 1
  for (let c = 0; c < totalCols; c++) {
    const col = []
    for (let d = 0; d < 7; d++) {
      const iso = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}-${String(cursor.getDate()).padStart(2, '0')}`
      col.push({ date: iso, value: byDate.get(iso) ?? 0, future: cursor > end })
      cursor.setDate(cursor.getDate() + 1)
    }
    cols.push(col)
  }
  return { cols, maxValue }
})
const level = value => {
  if (!value) return 0
  const t = value / (chart.value.maxValue || 1)
  return t < 0.25 ? 1 : t < 0.5 ? 2 : t < 0.75 ? 3 : 4
}
const monthLabels = computed(() => {
  const labels = []
  let lastMonth = -1
  chart.value.cols.forEach((col, ci) => {
    const m = new Date(col[0].date).getMonth()
    if (m !== lastMonth) { labels.push({ x: 24 + ci * (CELL + GAP), month: m }); lastMonth = m }
  })
  return labels
})
const monthName = m => ['T1', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'T8', 'T9', 'T10', 'T11', 'T12'][m]
</script>
<template>
<figure class="p-chart p-heatmap">
  <svg :viewBox="`0 0 ${chart.cols.length * (CELL + GAP) + 30} ${7 * (CELL + GAP) + 22}`" role="img" :aria-label="ariaLabel" preserveAspectRatio="xMinYMid meet">
    <text v-for="(ml, i) in monthLabels" :key="i" class="p-heatmap-month" :x="ml.x" :y="10">{{ monthName(ml.month) }}</text>
    <g v-for="(col, ci) in chart.cols" :key="ci">
      <rect v-for="(cell, ri) in col" :key="ri" class="p-heatmap-cell" :class="`is-l${cell.future ? 0 : level(cell.value)}`"
        :x="24 + ci * (CELL + GAP)" :y="18 + ri * (CELL + GAP)" :width="CELL" :height="CELL" rx="3" />
    </g>
  </svg>
</figure>
</template>
