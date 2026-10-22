<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import { feedbackApi } from '@/features/feedback/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import { sanitizeHtml } from '@/lib/sanitize'
import type { FeedbackDetailResponse } from '@/features/feedback/types'

const props = defineProps<{ open: boolean; feedbackId: number | null }>()
const emit = defineEmits(['update:open'])

const detail = ref<FeedbackDetailResponse | null>(null)
const loading = ref(false)

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

const isClosed = computed(() =>
  detail.value?.status === 'RESOLVED' || detail.value?.status === 'IGNORED')

async function load() {
  if (!props.feedbackId) return
  loading.value = true
  detail.value = null
  try {
    detail.value = await feedbackApi.detail(props.feedbackId)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) load()
  },
)

function close() {
  emit('update:open', false)
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    title="My Feedback"
    size="md"
    @update:model-value="(v) => !v && close()"
    @close="close"
  >
    <div v-if="loading || !detail" class="mf-loading">Loading…</div>
    <div v-else class="mf">
      <div class="mf-badges">
        <PrismBadge :tone="typeTone(detail.type)">{{ typeLabel(detail.type) }}</PrismBadge>
        <PrismBadge :tone="statusTone(detail.status)">{{ statusLabel(detail.status) }}</PrismBadge>
      </div>

      <div class="mf-meta">
        <span>Submitted: {{ formatDateTime(detail.createdAt) }}</span>
      </div>

      <div class="rich-content" v-html="sanitizeHtml(detail.content)"></div>

      <div v-if="detail.screenshot" class="mf-screenshot">
        <img :src="detail.screenshot" alt="Feedback screenshot" />
      </div>

      <div v-if="isClosed" class="mf-resolution">
        <p class="mf-resolution-label">
          {{ statusLabel(detail.status) }}
          <template v-if="detail.resolvedByName"> by {{ detail.resolvedByName }}</template>
          <template v-if="detail.resolvedAt"> on {{ formatDateTime(detail.resolvedAt) }}</template>
        </p>
        <p v-if="detail.resolutionNote" class="mf-resolution-note">{{ detail.resolutionNote }}</p>
      </div>
      <p v-else class="mf-pending">Your feedback is being reviewed. We will notify you once it is handled.</p>
    </div>

    <template #footer>
      <PrismButton variant="secondary" @click="close">Close</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.mf-loading {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-secondary);
}
.mf {
  display: grid;
  gap: var(--ds-space-3);
}
.mf-badges {
  display: flex;
  gap: var(--ds-space-2);
}
.mf-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-4);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.mf-screenshot img {
  max-width: 100%;
  max-height: 280px;
  border-radius: var(--ds-radius-sm);
  border: 1px solid var(--ds-color-border);
}
.mf-resolution {
  padding: var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-color-bg-secondary, rgba(255, 255, 255, 0.02));
}
.mf-resolution-label {
  margin: 0 0 var(--ds-space-1);
  font-size: 13px;
  font-weight: 600;
  color: var(--ds-color-text-primary);
}
.mf-resolution-note {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  white-space: pre-wrap;
}
.mf-pending {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
</style>
