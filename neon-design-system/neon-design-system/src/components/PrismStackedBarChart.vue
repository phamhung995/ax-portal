<script setup>
import { computed } from 'vue'
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ label, series: { name: value, ... } }]
  height: { type: Number, default: 200 },
  ariaLabel: { type: String, default: 'Biểu đồ cột chồng' },
})
const COLORS = ['var(--ds-color-accent)', 'var(--ds-color-violet-400)', 'var(--ds-color-teal-400)', 'var(--ds-color-amber-300)', 'var(--ds-color-rose-300)']
const W = 480
const PAD = { top: 18, right: 8, bottom: 24, left: 8 }
const chart = computed(() => {
  const names = [...new Set(props.data.flatMap(d => Object.keys(d.series ?? {})))]
  const totals = props.data.map(d => names.reduce((s, n) => s + (Number(d.series?.[n]) || 0), 0))
  const max = Math.max(...totals, 1)
  const innerW = W - PAD.left - PAD.right
  const innerH = props.height - PAD.top - PAD.bottom
  const n = Math.max(props.data.length, 1)
  const step = innerW / n
  const barW = Math.min(step * 0.5, 46)
  const bars = props.data.map((d, i) => {
    let y = PAD.top + innerH
    const segments = names.map((name, si) => {
      const v = Number(d.series?.[name]) || 0
      const h = (v / max) * innerH
      y -= h
      return { name, value: v, x: PAD.left + step * i + (step - barW) / 2, y, w: barW, h, color: COLORS[si % COLORS.length] }
    })
    return { label: d.label, total: totals[i], segments, cx: PAD.left + step * i + step / 2 }
  })
  return { names, bars, max, innerH, gridYs: [0, 0.5, 1].map(t => PAD.top + innerH * t), gridLabels: [0, 0.5, 1].map(t => Math.round(max * t)) }
})
</script>
<template>
<figure class="p-chart p-stacked-bar">
  <svg :viewBox="`0 0 ${W} ${height}`" role="img" :aria-label="ariaLabel" preserveAspectRatio="xMidYMid meet">
    <line v-for="(y, i) in chart.gridYs" :key="i" class="p-bar-grid" :x1="PAD.left" :x2="W - PAD.right" :y1="y" :y2="y" />
    <text v-for="(y, i) in chart.gridYs" :key="'t' + i" class="p-bar-grid-label" :x="W - PAD.right" :y="y - 4" text-anchor="end">{{ chart.gridLabels[i] }}</text>
    <g v-for="(b, i) in chart.bars" :key="i">
      <rect v-for="(s, si) in b.segments" :key="si" :x="s.x" :y="s.y" :width="s.w" :height="Math.max(s.h - 1, 0)" rx="3" class="p-stacked-rect" :style="{ fill: s.color }" />
      <text class="p-bar-value" :x="b.cx" :y="b.segments.length ? b.segments[b.segments.length - 1].y - 6 : PAD.top + 10" text-anchor="middle">{{ b.total }}</text>
      <text class="p-bar-label" :x="b.cx" :y="height - 6" text-anchor="middle">{{ b.label }}</text>
    </g>
  </svg>
  <ul class="p-donut-legend">
    <li v-for="(name, i) in chart.names" :key="i"><span class="p-donut-swatch" :style="{ background: COLORS[i % COLORS.length] }"></span><span class="p-donut-name">{{ name }}</span></li>
  </ul>
</figure>
</template>
