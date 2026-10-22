<script setup>defineProps({ label: { type: String, default: 'Đang tải…' } })</script>
<template><span class="p-loading" role="status"><span class="p-spinner" aria-hidden="true"></span><span>{{ label }}</span></span></template>
