<script setup>
import { Layers } from 'lucide-vue-next'
defineProps({ title: { type: String, default: 'Chưa có dữ liệu' }, description: String })
</script>
<template><div class="p-empty"><div class="p-empty-icon"><slot name="icon"><Layers :size="26" /></slot></div><h3>{{ title }}</h3><p>{{ description }}</p><slot /></div></template>
