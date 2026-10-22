<script setup>
defineProps({ label: String, orientation: { type: String, default: 'horizontal' } })
</script>
<template>
  <div class="p-divider" :class="{ 'is-vertical': orientation === 'vertical', 'has-label': !!label }" :role="label ? 'separator' : undefined" :aria-orientation="label ? orientation : undefined">
    <span v-if="label" class="p-divider-label">{{ label }}</span>
  </div>
</template>
