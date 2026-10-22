<script setup>
import { computed } from 'vue'
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ label, value }]
  height: { type: Number, default: 160 },
  showValues: { type: Boolean, default: true },
  ariaLabel: { type: String, default: 'Biểu đồ cột' },
})
const W = 480
const PAD = { top: 18, right: 8, bottom: 24, left: 8 }
const chart = computed(() => {
  const values = props.data.map(d => Number(d.value) || 0)
  const max = Math.max(...values, 1)
  const innerW = W - PAD.left - PAD.right
  const innerH = props.height - PAD.top - PAD.bottom
  const n = Math.max(props.data.length, 1)
  const step = innerW / n
  const barW = Math.min(step * 0.52, 44)
  return {
    max,
    bars: props.data.map((d, i) => {
      const v = Number(d.value) || 0
      const h = (v / max) * innerH
      return {
        label: d.label, value: v,
        x: PAD.left + step * i + (step - barW) / 2,
        y: PAD.top + innerH - h,
        w: barW, h,
        cx: PAD.left + step * i + step / 2,
      }
    }),
    innerH,
    gridYs: [0, 0.5, 1].map(t => PAD.top + innerH * t),
    gridLabels: [0, 0.5, 1].map(t => Math.round(max * t)),
  }
})
</script>
<template>
<figure class="p-chart p-bar-chart">
  <svg :viewBox="`0 0 ${W} ${height}`" role="img" :aria-label="ariaLabel" preserveAspectRatio="xMidYMid meet">
    <line v-for="(y, i) in chart.gridYs" :key="i" class="p-bar-grid" :x1="PAD.left" :x2="W - PAD.right" :y1="y" :y2="y" />
    <text v-for="(y, i) in chart.gridYs" :key="'t' + i" class="p-bar-grid-label" :x="W - PAD.right" :y="y - 4" text-anchor="end">{{ chart.gridLabels[i] }}</text>
    <g v-for="(b, i) in chart.bars" :key="i">
      <rect :x="b.x" :y="b.y" :width="b.w" :height="Math.max(b.h, 0)" rx="5" class="p-bar-rect" />
      <text v-if="showValues && b.h > 2" class="p-bar-value" :x="b.cx" :y="b.y - 6" text-anchor="middle">{{ b.value }}</text>
      <text class="p-bar-label" :x="b.cx" :y="height - 6" text-anchor="middle">{{ b.label }}</text>
    </g>
  </svg>
</figure>
</template>
