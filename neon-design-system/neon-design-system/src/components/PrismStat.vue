<script setup>
defineProps({ label: String, value: [String, Number], delta: [String, Number], deltaTone: { type: String, default: 'auto' }, hint: String, tone: { type: String, default: 'accent' } })
</script>
<template>
  <div class="p-stat" :class="`p-stat--${tone}`">
    <span class="p-stat-label">{{ label }}</span>
    <span class="p-stat-value">{{ value }}</span>
    <span v-if="delta != null" class="p-stat-delta" :class="`p-stat-delta--${deltaTone === 'auto' ? (Number(delta) >= 0 ? 'up' : 'down') : deltaTone}`">
      <svg width="11" height="11" viewBox="0 0 11 11" fill="none" aria-hidden="true"><path v-if="Number(delta) >= 0" d="M2.5 6.5 5.5 3.5l3 3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /><path v-else d="M2.5 4.5 5.5 7.5l3-3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
      {{ delta }}
    </span>
    <span v-if="hint" class="p-stat-hint">{{ hint }}</span>
  </div>
</template>
