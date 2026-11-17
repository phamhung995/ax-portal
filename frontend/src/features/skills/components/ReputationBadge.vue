<script setup lang="ts">
import PrismBadge from '@/design-system/components/PrismBadge.vue'

/**
 * Reputation tier badge (guide §2.3): the tier labels the CONTENT (skill),
 * not the author. NEW = not enough ratings yet, TRUSTED = score ≥ 70,
 * POPULAR = score ≥ 85 with 20+ downloads.
 *
 * `newHint` overrides the NEW tooltip for types whose evidence is not
 * star ratings (AI Tools: downloads; AI News: views).
 */
const props = defineProps<{
  tier: string
  score?: number | null
  newHint?: string
}>()

const label = props.tier === 'TRUSTED' ? 'Đáng tin' : props.tier === 'POPULAR' ? 'Phổ biến' : 'Mới'
const tone = props.tier === 'TRUSTED' ? 'info' : props.tier === 'POPULAR' ? 'success' : 'neutral'
const title =
  props.tier === 'NEW'
    ? (props.newHint ?? 'Mới: chưa đủ lượt đánh giá để xếp hạng (cần 5)')
    : `Điểm uy tín ${props.score != null ? props.score.toFixed(0) : '—'}/100`
</script>

<template>
  <PrismBadge :tone="tone" :title="title"> {{ label }} </PrismBadge>
</template>
