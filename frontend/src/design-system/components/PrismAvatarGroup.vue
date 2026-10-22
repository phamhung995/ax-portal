<script setup>
import { computed } from 'vue'
import PrismAvatar from './PrismAvatar.vue'
const props = defineProps({ items: { type: Array, default: () => [] }, max: { type: Number, default: 4 }, size: { type: Number, default: 32 }, label: { type: String, default: 'Nhóm thành viên' } })
const visible = computed(() => props.items.slice(0, props.max))
const overflow = computed(() => Math.max(props.items.length - props.max, 0))
</script>
<template>
  <div class="p-avatar-group" role="group" :aria-label="label">
    <PrismAvatar v-for="(item, i) in visible" :key="i" :name="item.name ?? item" :src="item.src" :size="size" :tone="item.tone" />
    <span v-if="overflow > 0" class="p-avatar p-avatar--overflow" :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.34)}px` }">+{{ overflow }}</span>
  </div>
</template>
