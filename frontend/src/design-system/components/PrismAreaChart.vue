<script setup>
import { computed, useId } from 'vue'
const uid = useId()
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ label, value }]
  height: { type: Number, default: 180 },
  color: { type: String, default: 'var(--ds-color-accent)' },
  showDots: { type: Boolean, default: false },
  ariaLabel: { type: String, default: 'Biểu đồ vùng' },
})
const W = 480
const PAD = { top: 16, right: 12, bottom: 24, left: 12 }
const chart = computed(() => {
  const values = props.data.map(d => Number(d.value) || 0)
  const max = Math.max(...values, 1)
  const innerW = W - PAD.left - PAD.right
  const innerH = props.height - PAD.top - PAD.bottom
  const n = Math.max(props.data.length, 1)
  const step = n > 1 ? innerW / (n - 1) : 0
  const pts = props.data.map((d, i) => ({
    label: d.label, value: Number(d.value) || 0,
    x: PAD.left + step * i,
    y: PAD.top + innerH - (Number(d.value) || 0) / max * innerH,
  }))
  const line = pts.map(p => `${p.x},${p.y}`).join(' ')
  const area = pts.length ? `${PAD.left},${PAD.top + innerH} ${line} ${PAD.left + innerW},${PAD.top + innerH}` : ''
  return { pts, line, area, gridYs: [0, 0.5, 1].map(t => PAD.top + innerH * t), gridLabels: [0, 0.5, 1].map(t => Math.round(max * t)) }
})
</script>
<template>
<figure class="p-chart p-area-chart">
  <svg :viewBox="`0 0 ${W} ${height}`" role="img" :aria-label="ariaLabel" preserveAspectRatio="xMidYMid meet">
    <defs>
      <linearGradient :id="`p-area-fill-${uid}`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" :stop-color="color" stop-opacity=".30" />
        <stop offset="100%" :stop-color="color" stop-opacity=".02" />
      </linearGradient>
    </defs>
    <line v-for="(y, i) in chart.gridYs" :key="i" class="p-area-grid" :x1="PAD.left" :x2="W - PAD.right" :y1="y" :y2="y" />
    <text v-for="(y, i) in chart.gridYs" :key="'t' + i" class="p-area-grid-label" :x="W - PAD.right" :y="y - 4" text-anchor="end">{{ chart.gridLabels[i] }}</text>
    <polygon v-if="chart.area" :points="chart.area" :fill="`url(#p-area-fill-${uid})`" />
    <polyline v-if="chart.pts.length > 1" :points="chart.line" class="p-area-line" :style="{ stroke: color }" />
    <g v-for="(p, i) in chart.pts" :key="'p' + i">
      <circle v-if="showDots" :cx="p.x" :cy="p.y" r="3" class="p-area-dot" :style="{ fill: color }" />
      <text class="p-area-label" :x="p.x" :y="height - 6" text-anchor="middle">{{ p.label }}</text>
    </g>
  </svg>
</figure>
</template>
