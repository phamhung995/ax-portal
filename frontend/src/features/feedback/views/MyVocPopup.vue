<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import FeedbackForm from '@/features/feedback/views/FeedbackForm.vue'
import MyFeedbackPopup from '@/features/feedback/views/MyFeedbackPopup.vue'
import { feedbackApi } from '@/features/feedback/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import { stripHtml } from '@/lib/sanitize'
import type { FeedbackItem } from '@/features/feedback/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits(['update:open'])

type Tab = 'add' | 'list'
const activeTab = ref<Tab>('add')

// ── My VOCs list ────────────────────────────────────────────────────────────
const items = ref<FeedbackItem[]>([])
const loading = ref(false)
const listError = ref('')

const typeLabel = (code: string) =>
  code === 'BUG' ? '🐞 Bug' : code === 'SUGGESTION' ? '💡 Suggestion' : code === 'QUESTION' ? '❓ Question' : code
const typeTone = (code: string) =>
  code === 'BUG' ? 'danger' : code === 'SUGGESTION' ? 'info' : 'neutral'
const statusLabel = (code: string) =>
  code === 'NEW' ? 'New' : code === 'IN_REVIEW' ? 'In Review' : code === 'RESOLVED' ? 'Resolved' : code === 'IGNORED' ? 'Ignored' : code
const statusTone = (code: string) =>
  code === 'NEW' ? 'warning' : code === 'IN_REVIEW' ? 'info' : code === 'RESOLVED' ? 'success' : 'neutral'

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const dd = String(d.getDate()).padStart(2, '0')
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${dd}/${mm}/${d.getFullYear()} ${hh}:${mi}`
}

async function loadList() {
  loading.value = true
  listError.value = ''
  try {
    const data = await feedbackApi.mine(1, 50)
    items.value = data.items
  } catch (err) {
    listError.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      activeTab.value = 'add'
      loadList()
    }
  },
)

function switchTab(tab: Tab) {
  activeTab.value = tab
  if (tab === 'list') loadList()
}

function close() {
  emit('update:open', false)
}

// Detail popup (reused from the bell deep-link)
const detailId = ref<number | null>(null)
const detailOpen = computed({
  get: () => detailId.value !== null,
  set: (v: boolean) => {
    if (!v) detailId.value = null
  },
})
function openDetail(item: FeedbackItem) {
  detailId.value = item.id
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    title="VOC"
    size="md"
    @update:model-value="(v) => !v && close()"
    @close="close"
  >
    <div class="voc-tabs" role="tablist" aria-label="VOC">
      <button
        type="button"
        role="tab"
        class="voc-tab"
        :class="{ 'is-active': activeTab === 'add' }"
        :aria-selected="activeTab === 'add'"
        @click="switchTab('add')"
      >
        Add VOC
      </button>
      <button
        type="button"
        role="tab"
        class="voc-tab"
        :class="{ 'is-active': activeTab === 'list' }"
        :aria-selected="activeTab === 'list'"
        @click="switchTab('list')"
      >
        My VOCs
      </button>
    </div>

    <FeedbackForm v-if="activeTab === 'add'" @submitted="switchTab('list')" />

    <div v-else class="voc-list">
      <div v-if="loading" class="voc-list-state">Loading…</div>
      <PrismEmptyState
        v-else-if="!items.length"
        title="No VOCs yet"
        description="VOCs you submit will appear here."
      />
      <p v-else-if="listError" class="voc-list-error">{{ listError }}</p>
      <ul v-else class="voc-items">
        <li v-for="item in items" :key="item.id">
          <button type="button" class="voc-item" @click="openDetail(item)">
            <div class="voc-item-badges">
              <PrismBadge :tone="typeTone(item.type)">{{ typeLabel(item.type) }}</PrismBadge>
              <PrismBadge :tone="statusTone(item.status)">{{ statusLabel(item.status) }}</PrismBadge>
            </div>
            <p class="voc-item-text">{{ stripHtml(item.content).slice(0, 160) }}</p>
            <span class="voc-item-date">{{ formatDateTime(item.createdAt) }}</span>
          </button>
        </li>
      </ul>
    </div>

    <template #footer>
      <PrismButton variant="secondary" @click="close">Close</PrismButton>
    </template>
  </PrismDialog>

  <MyFeedbackPopup v-model:open="detailOpen" :feedback-id="detailId" />
</template>

<style scoped>
.voc-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--ds-color-border);
}
.voc-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 18px;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 14px;
}
.voc-tab:hover {
  color: var(--ds-color-text-primary);
}
.voc-tab.is-active {
  color: var(--ds-color-accent);
  border-bottom-color: var(--ds-color-accent);
}

.voc-list {
  display: grid;
  gap: var(--ds-space-2);
}
.voc-list-state {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-secondary);
}
.voc-list-error {
  margin: 0;
  padding: var(--ds-space-3);
  border-radius: var(--ds-radius-sm);
  border: 1px solid var(--ds-color-danger, #f87171);
  color: var(--ds-color-danger, #f87171);
  font-size: 13px;
}
.voc-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--ds-space-2);
}
.voc-item {
  width: 100%;
  display: grid;
  gap: 6px;
  padding: var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: none;
  text-align: left;
  cursor: pointer;
}
@media (hover: hover) {
  .voc-item:hover {
    border-color: var(--ds-color-border-strong);
    background: var(--ds-list-item-hover-accent);
  }
}
.voc-item-badges {
  display: flex;
  gap: var(--ds-space-2);
}
.voc-item-text {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.voc-item-date {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
</style>
