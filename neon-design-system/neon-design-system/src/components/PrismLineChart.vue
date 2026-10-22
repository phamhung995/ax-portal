<script setup>
import { computed, useId } from 'vue'
const uid = useId()
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ label, value }]
  height: { type: Number, default: 180 },
  showDots: { type: Boolean, default: true },
  ariaLabel: { type: String, default: 'Biểu đồ đường' },
})
const W = 480
const PAD = { top: 16, right: 12, bottom: 24, left: 12 }
const chart = computed(() => {
  const values = props.data.map(d => Number(d.value) || 0)
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  const span = max - min || 1
  const innerW = W - PAD.left - PAD.right
  const innerH = props.height - PAD.top - PAD.bottom
  const n = Math.max(props.data.length, 1)
  const step = n > 1 ? innerW / (n - 1) : 0
  const pts = props.data.map((d, i) => ({
    label: d.label, value: Number(d.value) || 0,
    x: PAD.left + step * i,
    y: PAD.top + innerH - ((Number(d.value) || 0) - min) / span * innerH,
  }))
  const line = pts.map(p => `${p.x},${p.y}`).join(' ')
  const area = pts.length ? `${PAD.left},${PAD.top + innerH} ${line} ${PAD.left + innerW},${PAD.top + innerH}` : ''
  return { pts, line, area, innerH, gridYs: [0, 0.5, 1].map(t => PAD.top + innerH * t), gridLabels: [0, 0.5, 1].map(t => Math.round(max - span * t)) }
})
</script>
<template>
<figure class="p-chart p-line-chart">
  <svg :viewBox="`0 0 ${W} ${height}`" role="img" :aria-label="ariaLabel" preserveAspectRatio="xMidYMid meet">
    <defs>
      <linearGradient :id="`p-line-fill-${uid}`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="var(--ds-color-accent)" stop-opacity=".22" />
        <stop offset="100%" stop-color="var(--ds-color-accent)" stop-opacity="0" />
      </linearGradient>
    </defs>
    <line v-for="(y, i) in chart.gridYs" :key="i" class="p-line-grid" :x1="PAD.left" :x2="W - PAD.right" :y1="y" :y2="y" />
    <text v-for="(y, i) in chart.gridYs" :key="'t' + i" class="p-line-grid-label" :x="W - PAD.right" :y="y - 4" text-anchor="end">{{ chart.gridLabels[i] }}</text>
    <polygon v-if="chart.area" :points="chart.area" :fill="`url(#p-line-fill-${uid})`" />
    <polyline v-if="chart.pts.length > 1" :points="chart.line" class="p-line-path" />
    <g v-for="(p, i) in chart.pts" :key="'p' + i">
      <circle v-if="showDots" :cx="p.x" :cy="p.y" r="3.5" class="p-line-dot" />
      <text class="p-line-label" :x="p.x" :y="height - 6" text-anchor="middle">{{ p.label }}</text>
    </g>
  </svg>
</figure>
</template>
