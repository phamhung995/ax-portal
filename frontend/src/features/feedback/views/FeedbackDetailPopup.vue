<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismTextarea from '@/design-system/components/PrismTextarea.vue'
import { adminFeedbackApi } from '@/features/feedback/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { sanitizeHtml } from '@/lib/sanitize'
import type { FeedbackDetailResponse, FeedbackStatus } from '@/features/feedback/types'

const props = defineProps<{ open: boolean; feedbackId: number | null }>()
const emit = defineEmits(['update:open', 'saved'])

const detail = ref<FeedbackDetailResponse | null>(null)
const loading = ref(false)
const busy = ref(false)
const note = ref('')
const noteError = ref('')

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

async function load() {
  if (!props.feedbackId) return
  loading.value = true
  detail.value = null
  note.value = ''
  noteError.value = ''
  try {
    detail.value = await adminFeedbackApi.detail(props.feedbackId)
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

function requestClose() {
  if (busy.value) return
  emit('update:open', false)
}

const isClosed = computed(() =>
  detail.value?.status === 'RESOLVED' || detail.value?.status === 'IGNORED')

async function changeStatus(status: FeedbackStatus, requireNote: boolean) {
  if (!props.feedbackId || !detail.value) return
  if (requireNote && !note.value.trim()) {
    noteError.value = 'A note is required.'
    return
  }
  noteError.value = ''
  busy.value = true
  try {
    await adminFeedbackApi.changeStatus(props.feedbackId, {
      status,
      resolutionNote: note.value.trim() || undefined,
    })
    toastSuccess(status === 'IN_REVIEW'
      ? 'Feedback marked as in review.'
      : status === 'RESOLVED'
        ? 'Feedback resolved.'
        : 'Feedback ignored.')
    emit('saved')
    emit('update:open', false)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <PrismDialog
    :model-value="open"
    title="Feedback Detail"
    size="lg"
    :close-on-overlay="!busy"
    :close-on-escape="!busy"
    @update:model-value="(v) => !v && requestClose()"
    @close="requestClose"
  >
    <div v-if="loading || !detail" class="fd-loading">Loading…</div>
    <div v-else class="fd">
      <div class="fd-badges">
        <PrismBadge :tone="typeTone(detail.type)">{{ typeLabel(detail.type) }}</PrismBadge>
        <PrismBadge :tone="statusTone(detail.status)">{{ statusLabel(detail.status) }}</PrismBadge>
      </div>

      <div class="fd-meta">
        <span>User: {{ detail.userName ?? '—' }} ({{ detail.userEmail ?? '—' }})</span>
        <span>Created: {{ formatDateTime(detail.createdAt) }}</span>
      </div>

      <div class="rich-content" v-html="sanitizeHtml(detail.content)"></div>

      <div v-if="detail.screenshot" class="fd-screenshot">
        <img :src="detail.screenshot" alt="Feedback screenshot" />
      </div>

      <div v-if="detail.status === 'RESOLVED' || detail.status === 'IGNORED'" class="fd-resolution">
        <p class="fd-resolution-label">
          {{ statusLabel(detail.status) }} by {{ detail.resolvedByName ?? '—' }}
          <template v-if="detail.resolvedAt"> on {{ formatDateTime(detail.resolvedAt) }}</template>
        </p>
        <p v-if="detail.resolutionNote" class="fd-resolution-note">{{ detail.resolutionNote }}</p>
      </div>

      <!-- Action area (admin) -->
      <div v-if="!isClosed" class="fd-actions">
        <PrismTextarea
          v-model="note"
          :rows="3"
          :maxlength="500"
          :error="noteError"
          placeholder="Note (required for Resolve / Ignore)…"
        />
        <div class="fd-actions-buttons">
          <PrismButton
            v-if="detail.status === 'NEW'"
            variant="secondary"
            :loading="busy"
            @click="changeStatus('IN_REVIEW', false)"
          >
            Mark In Review
          </PrismButton>
          <PrismButton :loading="busy" @click="changeStatus('RESOLVED', true)">Resolve</PrismButton>
          <PrismButton variant="danger" :loading="busy" @click="changeStatus('IGNORED', true)">Ignore</PrismButton>
        </div>
      </div>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="busy" @click="requestClose">Close</PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.fd-loading {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-secondary);
}
.fd {
  display: grid;
  gap: var(--ds-space-3);
}
.fd-badges {
  display: flex;
  gap: var(--ds-space-2);
}
.fd-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-4);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.fd-screenshot img {
  max-width: 100%;
  max-height: 320px;
  border-radius: var(--ds-radius-sm);
  border: 1px solid var(--ds-color-border);
}
.fd-resolution {
  padding: var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-color-bg-secondary, rgba(255, 255, 255, 0.02));
}
.fd-resolution-label {
  margin: 0 0 var(--ds-space-1);
  font-size: 13px;
  font-weight: 600;
  color: var(--ds-color-text-primary);
}
.fd-resolution-note {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  white-space: pre-wrap;
}
.fd-actions {
  display: grid;
  gap: var(--ds-space-3);
}
.fd-actions-buttons {
  display: flex;
  justify-content: flex-end;
  gap: var(--ds-space-2);
}
</style>
