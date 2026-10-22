<script setup>
defineProps({ tone: { type: String, default: 'neutral' }, dot: Boolean })
</script>
<template><span class="p-badge" :class="`p-tone--${tone}`"><span v-if="dot" class="p-dot" aria-hidden="true"></span><slot /></span></template>
