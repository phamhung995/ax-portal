<script setup>
import { computed, ref, watch } from 'vue'
const props = defineProps({ name: { type: String, default: '' }, src: String, size: { type: Number, default: 36 }, tone: { type: String, default: 'accent' } })
const initials = computed(() => props.name.trim().split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join(''))
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
</script>
<template>
  <span class="p-avatar" :class="`p-avatar--${tone}`" role="img" :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.38)}px` }" :aria-label="name || undefined" :title="name || undefined">
    <img v-if="src && !failed" :src="src" :alt="name" @error="failed = true" />
    <span v-else class="p-avatar-initials" aria-hidden="true">{{ initials || '?' }}</span>
  </span>
</template>
