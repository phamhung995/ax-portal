<script setup>
import { computed } from 'vue'
const props = defineProps({
  value: { type: Number, default: 0 },
  min: { type: Number, default: 0 },
  max: { type: Number, default: 100 },
  label: String,
  size: { type: Number, default: 220 },
  ariaLabel: { type: String, default: 'Chỉ số' },
})
const W = 480
const H = 260
const chart = computed(() => {
  const span = props.max - props.min || 1
  const frac = Math.min(Math.max((props.value - props.min) / span, 0), 1)
  const cx = W / 2
  const cy = 210
  const R = 170
  const start = Math.PI
  const end = Math.PI * (1 + frac)
  const arc = (a0, a1) => {
    const x0 = cx + Math.cos(a0) * R
    const y0 = cy + Math.sin(a0) * R
    const x1 = cx + Math.cos(a1) * R
    const y1 = cy + Math.sin(a1) * R
    return `M ${x0} ${y0} A ${R} ${R} 0 ${a1 - a0 > Math.PI ? 1 : 0} 1 ${x1} ${y1}`
  }
  const ticks = [0, 0.25, 0.5, 0.75, 1].map(t => {
    const a = Math.PI + Math.PI * t
    return {
      t,
      x1: cx + Math.cos(a) * (R - 10), y1: cy + Math.sin(a) * (R - 10),
      x2: cx + Math.cos(a) * (R + 4), y2: cy + Math.sin(a) * (R + 4),
      lx: cx + Math.cos(a) * (R + 20), ly: cy + Math.sin(a) * (R + 20),
      label: Math.round(props.min + span * t),
    }
  })
  return { frac, cx, cy, R, track: arc(start, 2 * Math.PI), value: frac > 0.001 ? arc(start, end) : '', ticks }
})
</script>
<template>
<figure class="p-chart p-gauge">
  <svg :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="`${ariaLabel}: ${value}/${max}`" preserveAspectRatio="xMidYMid meet">
    <path :d="chart.track" class="p-gauge-track" />
    <path v-if="chart.value" :d="chart.value" class="p-gauge-value" />
    <g v-for="(t, i) in chart.ticks" :key="i">
      <line :x1="t.x1" :y1="t.y1" :x2="t.x2" :y2="t.y2" class="p-gauge-tick" />
      <text :x="t.lx" :y="t.ly" class="p-gauge-tick-label" text-anchor="middle">{{ t.label }}</text>
    </g>
    <text class="p-gauge-number" :x="chart.cx" :y="chart.cy - 18" text-anchor="middle">{{ value }}</text>
    <text v-if="label" class="p-gauge-caption" :x="chart.cx" :y="chart.cy + 14" text-anchor="middle">{{ label }}</text>
  </svg>
</figure>
</template>
