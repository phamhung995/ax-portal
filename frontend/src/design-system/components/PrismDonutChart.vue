<script setup lang="ts">
import { computed } from 'vue'
interface DonutDatum { label: string; value: number; color?: string }
const props = defineProps<{
  data?: DonutDatum[]
  size?: number
  thickness?: number
  centerLabel?: string
  /** Render label+value lines inside the ring. */
  centerDetail?: boolean
  ariaLabel?: string
}>()
const COLORS = ['var(--ds-color-accent)', 'var(--ds-color-violet-400)', 'var(--ds-color-teal-400)', 'var(--ds-color-mint-300)', 'var(--ds-color-amber-300)', 'var(--ds-color-rose-300)']
const chart = computed(() => {
  const data = props.data ?? []
  const total = data.reduce((s, d) => s + (Number(d.value) || 0), 0) || 1
  const size = props.size ?? 180
  const thickness = props.thickness ?? 22
  const r = (size - thickness) / 2
  const C = 2 * Math.PI * r
  let offset = 0
  const segs = data.map((d, i) => {
    const v = Number(d.value) || 0
    const frac = v / total
    const seg = { label: d.label, value: v, frac, color: d.color || COLORS[i % COLORS.length], dash: `${frac * C} ${C}`, offset: -offset * C }
    offset += frac
    return seg
  })
  return { total, r, C, segs, cx: size / 2, size, thickness }
})
// Inner-circle text: total on top, then each status with its count.
const centerDetailLines = computed(() => {
  if (!props.centerDetail) return []
  const lines = [{ text: String(chart.value.total), strong: true }]
  for (const s of chart.value.segs) {
    lines.push({ text: `${s.label} ${s.value}`, strong: false })
  }
  return lines
})
</script>
<template>
<figure class="p-chart p-donut">
  <svg :viewBox="`0 0 ${chart.size} ${chart.size}`" role="img" :aria-label="ariaLabel ?? 'Biểu đồ tròn'">
    <g :transform="`rotate(-90 ${chart.cx} ${chart.cx})`">
      <circle :cx="chart.cx" :cy="chart.cx" :r="chart.r" fill="none" class="p-donut-track" :stroke-width="chart.thickness" />
      <circle v-for="(s, i) in chart.segs" :key="i" :cx="chart.cx" :cy="chart.cx" :r="chart.r" fill="none"
        :stroke="s.color" :stroke-width="chart.thickness" :stroke-dasharray="s.dash" :stroke-dashoffset="s.offset" class="p-donut-seg" />
    </g>
    <template v-if="centerDetail">
      <text v-for="(line, i) in centerDetailLines" :key="i"
        class="p-donut-center p-donut-center-line" :class="{ 'is-strong': line.strong }"
        :x="chart.cx" :y="chart.cx - 20 + i * 15" text-anchor="middle" dominant-baseline="central">{{ line.text }}</text>
    </template>
    <text v-else-if="centerLabel != null" class="p-donut-center" :x="chart.cx" :y="chart.cx" text-anchor="middle" dominant-baseline="central">{{ centerLabel }}</text>
  </svg>
  <ul class="p-donut-legend">
    <li v-for="(s, i) in chart.segs" :key="i"><span class="p-donut-swatch" :style="{ background: s.color }"></span><span class="p-donut-name">{{ s.label }}</span><span class="p-donut-val">{{ s.value }}</span></li>
  </ul>
</figure>
</template>
