<script setup lang="ts">
import PrismBadge from '@/design-system/components/PrismBadge.vue'

/**
 * Compact badge stack for Category / Process Stage cells:
 * shows at most 2 badges + a "+N" overflow marker.
 */
const props = defineProps<{
  items: string[]
  tone: 'category' | 'stage'
}>()

const toneMap = { category: 'neutral', stage: 'info' } as const
const shown = () => props.items.slice(0, 2)
const extra = () => Math.max(0, props.items.length - 2)
</script>

<template>
  <span class="bs" :class="`bs--${tone}`">
    <template v-if="items.length">
      <PrismBadge v-for="item in shown()" :key="item" :tone="toneMap[tone]">{{ item }}</PrismBadge>
      <span v-if="extra()" class="bs-more" :title="items.slice(2).join(', ')">+{{ extra() }}</span>
    </template>
    <span v-else class="bs-empty">—</span>
  </span>
</template>

<style scoped>
.bs {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  max-width: 190px;
}
.bs--category :deep(.p-badge) {
  color: var(--ds-color-violet-400, #a58aef);
  background: rgb(165 138 239 / .08);
  border-color: rgb(165 138 239 / .35);
}
.bs-more {
  color: var(--ds-color-text-muted);
  font-size: 11px;
  cursor: default;
}
.bs-empty {
  color: var(--ds-color-text-muted);
}
</style>
