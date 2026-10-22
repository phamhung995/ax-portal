<script setup>
import { computed } from 'vue'
const props = defineProps({
  data: { type: Array, default: () => [] }, // [{ label, value, color? }]
  size: { type: Number, default: 180 },
  thickness: { type: Number, default: 22 },
  centerLabel: String,
  ariaLabel: { type: String, default: 'Biểu đồ tròn' },
})
const COLORS = ['var(--ds-color-accent)', 'var(--ds-color-violet-400)', 'var(--ds-color-teal-400)', 'var(--ds-color-mint-300)', 'var(--ds-color-amber-300)', 'var(--ds-color-rose-300)']
const chart = computed(() => {
  const total = props.data.reduce((s, d) => s + (Number(d.value) || 0), 0) || 1
  const r = (props.size - props.thickness) / 2
  const C = 2 * Math.PI * r
  let offset = 0
  const segs = props.data.map((d, i) => {
    const v = Number(d.value) || 0
    const frac = v / total
    const seg = { label: d.label, value: v, frac, color: d.color || COLORS[i % COLORS.length], dash: `${frac * C} ${C}`, offset: -offset * C }
    offset += frac
    return seg
  })
  return { total, r, C, segs, cx: props.size / 2 }
})
</script>
<template>
<figure class="p-chart p-donut">
  <svg :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="ariaLabel">
    <g :transform="`rotate(-90 ${chart.cx} ${chart.cx})`">
      <circle :cx="chart.cx" :cy="chart.cx" :r="chart.r" fill="none" class="p-donut-track" :stroke-width="thickness" />
      <circle v-for="(s, i) in chart.segs" :key="i" :cx="chart.cx" :cy="chart.cx" :r="chart.r" fill="none"
        :stroke="s.color" :stroke-width="thickness" :stroke-dasharray="s.dash" :stroke-dashoffset="s.offset" class="p-donut-seg" />
    </g>
    <text v-if="centerLabel != null" class="p-donut-center" :x="chart.cx" :y="chart.cx" text-anchor="middle" dominant-baseline="central">{{ centerLabel }}</text>
  </svg>
  <ul class="p-donut-legend">
    <li v-for="(s, i) in chart.segs" :key="i"><span class="p-donut-swatch" :style="{ background: s.color }"></span><span class="p-donut-name">{{ s.label }}</span><span class="p-donut-val">{{ s.value }}</span></li>
  </ul>
</figure>
</template>
