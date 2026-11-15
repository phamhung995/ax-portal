<script setup>
import { useI18nStore } from '@/stores/i18n'
import { computed } from 'vue'
const props = defineProps({ columns: { type: Array, default: () => [] }, rows: { type: Array, default: () => [] }, caption: { type: String, required: true }, rowKey: { type: String, default: 'id' }, loading: Boolean, emptyText: { type: String, default: '' } })
// Optional per-column `width` (px) → <colgroup>. Only rendered when at least
// one column declares a width, so existing callers are unaffected.
const hasWidths = computed(() => props.columns.some((c) => c.width != null))
const { t } = useI18nStore()
</script>
<template><div class="p-table-scroll" role="region" :aria-label="caption" tabindex="0" :aria-busy="loading || undefined"><table class="p-table"><caption>{{ caption }}</caption><colgroup v-if="hasWidths"><col v-for="column in columns" :key="column.key" :style="column.width ? `width: ${column.width}px` : undefined" /></colgroup><thead><tr><th v-for="column in columns" :key="column.key" scope="col"><slot :name="`header-${column.key}`">{{ column.label }}</slot></th></tr></thead><tbody><tr v-if="loading"><td :colspan="columns.length" role="status">{{ t('app.table.loading') }}</td></tr><template v-else><tr v-for="row in rows" :key="row[rowKey]"><td v-for="column in columns" :key="column.key"><slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">{{ row[column.key] }}</slot></td></tr><tr v-if="!rows.length"><td :colspan="columns.length">{{ emptyText || t('app.table.empty') }}</td></tr></template></tbody></table></div></template>
