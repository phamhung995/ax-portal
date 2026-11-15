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
import { useI18nStore } from '@/stores/i18n'
import type { FeedbackDetailResponse, FeedbackStatus } from '@/features/feedback/types'

const props = defineProps<{ open: boolean; feedbackId: number | null }>()
const emit = defineEmits(['update:open', 'saved'])
const { t, formatDateTime } = useI18nStore()

const detail = ref<FeedbackDetailResponse | null>(null)
const loading = ref(false)
const busy = ref(false)
const note = ref('')
const noteError = ref('')

const typeLabel = (code: string) =>
  code === 'BUG' ? '🐞 ' + t('app.feedback.typeBug') : code === 'SUGGESTION' ? '💡 ' + t('app.feedback.typeSuggestion') : code === 'QUESTION' ? '❓ ' + t('app.feedback.typeQuestion') : code
const typeTone = (code: string) =>
  code === 'BUG' ? 'danger' : code === 'SUGGESTION' ? 'info' : 'neutral'
const statusLabel = (code: string) =>
  code === 'NEW' ? t('app.feedback.statusNew') : code === 'IN_REVIEW' ? t('app.feedback.statusInReview') : code === 'RESOLVED' ? t('app.feedback.statusResolved') : code === 'IGNORED' ? t('app.feedback.statusIgnored') : code
const statusTone = (code: string) =>
  code === 'NEW' ? 'warning' : code === 'IN_REVIEW' ? 'info' : code === 'RESOLVED' ? 'success' : 'neutral'

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
    noteError.value = t('app.feedback.noteRequired')
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
      ? t('app.feedback.inReviewToast')
      : status === 'RESOLVED'
        ? t('app.feedback.resolvedToast')
        : t('app.feedback.ignoredToast'))
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
    :title="t('app.feedback.detailTitle')"
    size="lg"
    :close-on-overlay="!busy"
    :close-on-escape="!busy"
    @update:model-value="(v) => !v && requestClose()"
    @close="requestClose"
  >
    <div v-if="loading || !detail" class="fd-loading">{{ t('app.feedback.loading') }}</div>
    <div v-else class="fd">
      <div class="fd-badges">
        <PrismBadge :tone="typeTone(detail.type)">{{ typeLabel(detail.type) }}</PrismBadge>
        <PrismBadge :tone="statusTone(detail.status)">{{ statusLabel(detail.status) }}</PrismBadge>
      </div>

      <div class="fd-meta">
        <span>{{ t('app.feedback.metaUser') }} {{ detail.userName ?? '—' }} ({{ detail.userEmail ?? '—' }})</span>
        <span>{{ t('app.feedback.metaCreated') }} {{ formatDateTime(detail.createdAt) }}</span>
      </div>

      <div class="rich-content" v-html="sanitizeHtml(detail.content)"></div>

      <div v-if="detail.screenshot" class="fd-screenshot">
        <img :src="detail.screenshot" :alt="t('app.feedback.screenshotAlt')" />
      </div>

      <div v-if="detail.status === 'RESOLVED' || detail.status === 'IGNORED'" class="fd-resolution">
        <p class="fd-resolution-label">
          {{ statusLabel(detail.status) }} {{ t('app.feedback.by') }} {{ detail.resolvedByName ?? '—' }}
          <template v-if="detail.resolvedAt"> {{ t('app.feedback.on') }} {{ formatDateTime(detail.resolvedAt) }}</template>
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
          :placeholder="t('app.feedback.notePh')"
        />
        <div class="fd-actions-buttons">
          <PrismButton
            v-if="detail.status === 'NEW'"
            variant="secondary"
            :loading="busy"
            @click="changeStatus('IN_REVIEW', false)"
          >
            {{ t('app.feedback.markInReview') }}
          </PrismButton>
          <PrismButton :loading="busy" @click="changeStatus('RESOLVED', true)">{{ t('app.feedback.resolve') }}</PrismButton>
          <PrismButton variant="danger" :loading="busy" @click="changeStatus('IGNORED', true)">{{ t('app.feedback.ignore') }}</PrismButton>
        </div>
      </div>
    </div>

    <template #footer>
      <PrismButton variant="secondary" :disabled="busy" @click="requestClose">{{ t('app.common.close') }}</PrismButton>
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
