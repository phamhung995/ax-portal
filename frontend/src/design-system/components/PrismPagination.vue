<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
const props = defineProps({ page: { type: Number, default: 1 }, pageSize: { type: Number, default: 5 }, total: { type: Number, default: 0 } })
defineEmits(['update:page'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
</script>
<template><nav class="p-pagination" aria-label="Phân trang"><span role="status">Trang {{ page }} / {{ pages }} · {{ total }} mục</span><div><button type="button" class="p-icon-button" :disabled="page <= 1" aria-label="Trang trước" @click="$emit('update:page', page - 1)"><ChevronLeft :size="18" /></button><span aria-current="page">{{ page }}</span><button type="button" class="p-icon-button" :disabled="page >= pages" aria-label="Trang sau" @click="$emit('update:page', page + 1)"><ChevronRight :size="18" /></button></div></nav></template>
