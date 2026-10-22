<script setup>
import { computed } from 'vue'
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ label, value }]
  size: { type: Number, default: 240 },
  levels: { type: Number, default: 4 },
  max: Number,
  ariaLabel: { type: String, default: 'Biểu đồ radar' },
})
const chart = computed(() => {
  const n = Math.max(props.data.length, 3)
  const cx = props.size / 2
  const cy = props.size / 2
  const R = props.size / 2 - 30
  const values = props.data.map(d => Number(d.value) || 0)
  const maxValue = props.max || Math.max(...values, 1)
  const angle = i => (Math.PI * 2 * i) / n - Math.PI / 2
  const point = (i, r) => ({ x: cx + Math.cos(angle(i)) * r, y: cy + Math.sin(angle(i)) * r })
  const axes = props.data.map((d, i) => {
    const outer = point(i, R)
    const labelPos = point(i, R + 16)
    return { label: d.label, x: outer.x, y: outer.y, lx: labelPos.x, ly: labelPos.y, value: values[i] }
  })
  const rings = Array.from({ length: props.levels }, (_, ring) => {
    const r = (R * (ring + 1)) / props.levels
    return Array.from({ length: n }, (_, i) => { const p = point(i, r); return `${p.x},${p.y}` }).join(' ')
  })
  const shape = props.data.map((d, i) => {
    const r = (values[i] / maxValue) * R
    const p = point(i, r)
    return `${p.x},${p.y}`
  }).join(' ')
  const dots = props.data.map((d, i) => {
    const r = (values[i] / maxValue) * R
    return point(i, r)
  })
  return { cx, cy, R, axes, rings, shape, dots, maxValue }
})
</script>
<template>
<figure class="p-chart p-radar">
  <svg :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="ariaLabel">
    <polygon v-for="(ring, i) in chart.rings" :key="i" :points="ring" class="p-radar-ring" :class="{ 'is-outer': i === levels - 1 }" />
    <line v-for="(a, i) in chart.axes" :key="'a' + i" :x1="chart.cx" :y1="chart.cy" :x2="a.x" :y2="a.y" class="p-radar-axis" />
    <polygon :points="chart.shape" class="p-radar-shape" />
    <circle v-for="(d, i) in chart.dots" :key="'d' + i" :cx="d.x" :cy="d.y" r="3" class="p-radar-dot" />
    <text v-for="(a, i) in chart.axes" :key="'t' + i" class="p-radar-label" :x="a.lx" :y="a.ly" :text-anchor="a.lx > chart.cx + 8 ? 'start' : a.lx < chart.cx - 8 ? 'end' : 'middle'" :dy="a.ly > chart.cy + 8 ? '0.8em' : a.ly < chart.cy - 8 ? '0' : '0.35em'">{{ a.label }}</text>
  </svg>
</figure>
</template>
