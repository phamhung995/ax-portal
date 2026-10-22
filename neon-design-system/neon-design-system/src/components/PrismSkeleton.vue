<script setup>defineProps({ width: { type: String, default: '100%' }, height: { type: String, default: '16px' } })</script>
<template><div class="p-skeleton" aria-hidden="true" :style="{ width, height }"></div></template>
