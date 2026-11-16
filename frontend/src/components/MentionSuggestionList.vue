<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18nStore } from '@/stores/i18n'

const { t } = useI18nStore()

interface MentionItem {
  id: string
  label: string
}

const props = defineProps<{
  items: MentionItem[]
  command: (item: MentionItem) => void
  loading?: boolean
  /** Selected index (keyboard-driven by the parent renderer — the dropdown
   *  is mounted OUTSIDE the editor DOM, so keydown never reaches it). */
  selected?: number
  /** Hover preview: parent re-highlights its keyboard selection. */
  onHover?: (index: number) => void
}>()

function select(index: number) {
  const item = props.items[index]
  if (item) props.command(item)
}
</script>

<template>
  <div class="mention-suggestion" v-show="items.length > 0 || loading">
    <div class="mention-suggestion-list" role="listbox">
      <div v-if="loading" class="mention-suggestion-empty">{{ t('aiNews.mentionSearching') }}</div>
      <div v-else-if="items.length === 0" class="mention-suggestion-empty">{{ t('aiNews.mentionNoUsers') }}</div>
      <div
        v-for="(item, index) in items"
        :key="item.id"
        class="mention-suggestion-item"
        :class="{ 'is-selected': index === (selected ?? 0) }"
        role="option"
        :aria-selected="index === (selected ?? 0)"
        @mouseenter="onHover?.(index)"
        @click="select(index)"
      >
        <span class="mention-suggestion-name">{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mention-suggestion {
  z-index: 50;
  max-width: 280px;
}
.mention-suggestion-list {
  overflow-y: auto;
  max-height: 220px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-color-bg-primary, #14161a);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
  /* Ẩn thanh scroll nhưng vẫn scroll được (đồng bộ pattern .ainews-comments) */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge legacy */
}
.mention-suggestion-list::-webkit-scrollbar {
  display: none; /* Chrome/Safari */
}
.mention-suggestion-item {
  padding: 6px 10px;
  font-size: 13px;
  color: var(--ds-color-text-primary);
  cursor: pointer;
}
.mention-suggestion-item.is-selected {
  background: var(--ds-color-accent-soft, rgba(56, 189, 248, 0.16));
  color: var(--ds-color-accent);
}
.mention-suggestion-empty {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
}
</style>
