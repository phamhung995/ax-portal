<script setup>
defineProps({ columns: { type: Number, default: 3 }, responsive: Boolean, gap: { type: String, default: 'var(--ds-space-4)' } })
</script>
<template><div class="p-grid" :class="{ 'p-grid--responsive': responsive }" :style="{ '--p-grid-cols': columns, '--p-grid-gap': gap }"><slot /></div></template>
