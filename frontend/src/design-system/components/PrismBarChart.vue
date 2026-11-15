<script setup>
import { computed } from 'vue'
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ label, value, sub? }]
  height: { type: Number, default: 160 },
  // ViewBox width. The SVG renders at 100% of its container, so a wider
  // viewBox in a full-width container keeps text/bars at the same pixel
  // size as the same chart in a narrower (grid-cell) container.
  width: { type: Number, default: 480 },
  showValues: { type: Boolean, default: true },
  // Optional suffix appended to the value labels (e.g. "/5" for ratings).
  valueSuffix: { type: String, default: '' },
  ariaLabel: { type: String, default: 'Biểu đồ cột' },
})
const PAD = { top: 18, right: 8, left: 8 }
const chart = computed(() => {
  const W = props.width
  const values = props.data.map(d => Number(d.value) || 0)
  const max = Math.max(...values, 1)
  // Extra bottom room when a sub line (e.g. "2/3") renders between the
  // x-axis labels and the bars — keeps the sub from overlapping the bars.
  const hasSub = props.data.some((d) => d.sub)
  const bottom = hasSub ? 40 : 24
  const innerW = W - PAD.left - PAD.right
  const innerH = props.height - PAD.top - bottom
  const n = Math.max(props.data.length, 1)
  const step = innerW / n
  const barW = Math.min(step * 0.52, 44)
  return {
    W,
    max,
    bars: props.data.map((d, i) => {
      const v = Number(d.value) || 0
      const h = (v / max) * innerH
      return {
        label: d.label, value: v, sub: d.sub ?? '',
        x: PAD.left + step * i + (step - barW) / 2,
        y: PAD.top + innerH - h,
        w: barW, h,
        cx: PAD.left + step * i + step / 2,
      }
    }),
    innerH,
    gridYs: [0, 0.5, 1].map(t => PAD.top + innerH * t),
    gridLabels: [0, 0.5, 1].map((t, i) => Math.round(max * t) + (i === 2 ? props.valueSuffix : '')),
  }
})
</script>
<template>
<figure class="p-chart p-bar-chart">
  <svg :viewBox="`0 0 ${chart.W} ${height}`" role="img" :aria-label="ariaLabel" preserveAspectRatio="xMidYMid meet">
    <line v-for="(y, i) in chart.gridYs" :key="i" class="p-bar-grid" :x1="PAD.left" :x2="chart.W - PAD.right" :y1="y" :y2="y" />
    <text v-for="(y, i) in chart.gridYs" :key="'t' + i" class="p-bar-grid-label" :x="chart.W - PAD.right" :y="y - 4" text-anchor="end">{{ chart.gridLabels[i] }}</text>
    <g v-for="(b, i) in chart.bars" :key="i">
      <rect :x="b.x" :y="b.y" :width="b.w" :height="Math.max(b.h, 0)" rx="5" class="p-bar-rect" />
      <text v-if="showValues && b.h > 2" class="p-bar-value" :x="b.cx" :y="b.y - 6" text-anchor="middle">{{ b.value }}{{ valueSuffix }}</text>
      <text v-if="b.sub" class="p-bar-sub" :x="b.cx" :y="height - 21" text-anchor="middle">{{ b.sub }}</text>
      <text class="p-bar-label" :x="b.cx" :y="height - 6" text-anchor="middle">{{ b.label }}</text>
    </g>
  </svg>
</figure>
</template>
