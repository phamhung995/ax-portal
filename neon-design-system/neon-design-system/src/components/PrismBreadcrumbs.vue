<script setup>
defineProps({ items: { type: Array, default: () => [] }, label: { type: String, default: 'Điều hướng' } })
defineEmits(['navigate'])
</script>
<template>
  <nav :aria-label="label">
    <ol class="p-breadcrumbs">
      <li v-for="(item, i) in items" :key="i" class="p-breadcrumb" :class="{ 'is-current': i === items.length - 1 }">
        <button v-if="i < items.length - 1" type="button" class="p-breadcrumb-link" @click="$emit('navigate', i, item)">{{ item.label }}</button>
        <span v-else class="p-breadcrumb-current" aria-current="page">{{ item.label }}</span>
        <svg v-if="i < items.length - 1" width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true"><path d="m4.5 3 3 3-3 3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" /></svg>
      </li>
    </ol>
  </nav>
</template>
