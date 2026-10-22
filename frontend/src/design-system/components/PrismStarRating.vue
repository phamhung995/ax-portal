<script setup lang="ts">
import { computed } from 'vue'
import { Star } from 'lucide-vue-next'

/**
 * Read-only 5-star rating with half-star fill + score text
 * (ported from the AI-VibeCoding-Neon demo StarRating).
 */
const props = withDefaults(
  defineProps<{
    /** Average rating 0..5. */
    modelValue: number
    size?: number
    /** Total number of ratings — shown as "4.6 (5)". */
    total?: number | null
    /** Text shown when there is no rating (i18n — no default). */
    noRatingText: string
  }>(),
  { size: 13, total: null },
)

const score = computed(() =>
  props.modelValue
    ? props.modelValue.toFixed(1) + (props.total != null ? ` (${props.total})` : '')
    : props.noRatingText,
)

function starFill(i: number): 'full' | 'half' | '' {
  const v = props.modelValue - i
  if (v >= 1) return 'full'
  if (v >= 0.5) return 'half'
  return ''
}
</script>

<template>
  <span class="p-stars">
    <span v-for="i in 5" :key="i" class="p-star">
      <Star class="p-star-empty" :size="size" aria-hidden="true" />
      <span v-if="starFill(i - 1)" class="p-star-fill" :class="starFill(i - 1)">
        <Star :size="size" fill="currentColor" aria-hidden="true" />
      </span>
    </span>
    <span class="p-stars-score">{{ score }}</span>
  </span>
</template>
