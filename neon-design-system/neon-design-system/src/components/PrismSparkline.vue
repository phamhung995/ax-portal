<script setup>
import { computed, useId } from 'vue'
const uid = useId()
const props = defineProps({
  data: { type: Array, default: () => [] }, // [number, ...]
  width: { type: Number, default: 120 },
  height: { type: Number, default: 36 },
  ariaLabel: { type: String, default: 'Xu hướng' },
})
const chart = computed(() => {
  const values = props.data.map(v => Number(v) || 0)
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  const span = max - min || 1
  const n = Math.max(values.length, 1)
  const step = n > 1 ? props.width / (n - 1) : 0
  const pts = values.map((v, i) => ({ x: step * i, y: props.height - 3 - ((v - min) / span) * (props.height - 6) }))
  return { pts, line: pts.map(p => `${p.x},${p.y}`).join(' '), area: pts.length ? `0,${props.height} ${pts.map(p => `${p.x},${p.y}`).join(' ')} ${props.width},${props.height}` : '' }
})
</script>
<template>
<svg class="p-sparkline" :viewBox="`0 0 ${width} ${height}`" role="img" :aria-label="ariaLabel" preserveAspectRatio="none">
  <defs>
    <linearGradient :id="`p-spark-fill-${uid}`" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="var(--ds-color-accent)" stop-opacity=".25" />
      <stop offset="100%" stop-color="var(--ds-color-accent)" stop-opacity="0" />
    </linearGradient>
  </defs>
  <polygon v-if="chart.area" :points="chart.area" :fill="`url(#p-spark-fill-${uid})`" />
  <polyline v-if="chart.pts.length > 1" :points="chart.line" class="p-sparkline-path" />
</svg>
</template>
