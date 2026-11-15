<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { useI18nStore } from '@/stores/i18n'
const props = defineProps({ page: { type: Number, default: 1 }, pageSize: { type: Number, default: 5 }, total: { type: Number, default: 0 } })
defineEmits(['update:page'])
const { t } = useI18nStore()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const statusText = computed(() => t('app.pagination.status', { page: props.page, pages: pages.value, total: props.total }))
</script>
<template><nav class="p-pagination" :aria-label="t('app.pagination.ariaLabel')"><span role="status">{{ statusText }}</span><div><button type="button" class="p-icon-button" :disabled="page <= 1" :aria-label="t('app.pagination.prev')" @click="$emit('update:page', page - 1)"><ChevronLeft :size="18" /></button><span aria-current="page">{{ page }}</span><button type="button" class="p-icon-button" :disabled="page >= pages" :aria-label="t('app.pagination.next')" @click="$emit('update:page', page + 1)"><ChevronRight :size="18" /></button></div></nav></template>
