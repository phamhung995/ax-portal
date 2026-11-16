<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Link2, MessageSquare, Pencil, ThumbsUp, Trash2 } from 'lucide-vue-next'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import type { MentionSuggestionItem } from '@/components/RichTextEditor.vue'
import CommentTreeNode from './CommentTreeNode.vue'
import { sanitizeHtml, stripHtml } from '@/lib/sanitize'
import { useI18nStore } from '@/stores/i18n'
import type { SkillRequestCommentItem } from '@/features/skill-market/types'

const props = defineProps<{
  comment: SkillRequestCommentItem
  depth: number
  isRequestCreator: boolean
  replyParentId: number | null
  replyDraft: string
  replying: boolean
  onLike: (c: SkillRequestCommentItem) => void
  onReply: (c: SkillRequestCommentItem) => void
  onDelete: (c: SkillRequestCommentItem) => void
  onEdit: (c: SkillRequestCommentItem) => void
  onResolve: (c: SkillRequestCommentItem) => void
  onSkillChipClick: (c: SkillRequestCommentItem) => void
  onReplyInput: (value: string) => void
  onPostReply: () => void
  editCommentId: number | null
  editCommentDraft: string
  savingComment: boolean
  onEditInput: (value: string) => void
  onSaveEdit: (c: SkillRequestCommentItem) => void
  /** @mention autocomplete (same pipeline as AI News comments). */
  mentionSuggestion: (query: string, signal?: AbortSignal) => Promise<MentionSuggestionItem[]>
}>()

const { t } = useI18nStore()

// Rich text: the Reply/Save buttons are enabled by the PLAIN TEXT of the
// draft (HTML tags and embedded images don't count) — same as AI News.
const hasReplyText = computed(() => stripHtml(props.replyDraft).trim().length > 0)
const hasEditText = computed(() => stripHtml(props.editCommentDraft).trim().length > 0)
// The editor's maxLength counter is display-only — the buttons must also be
// disabled when the plain text exceeds the limit (otherwise an over-limit
// draft reaches the backend and 400s).
const replyEditor = ref<InstanceType<typeof RichTextEditor> | null>(null)
const editEditor = ref<InstanceType<typeof RichTextEditor> | null>(null)
const replyOverLimit = computed(() => replyEditor.value?.isOverLimit ?? false)
const editOverLimit = computed(() => editEditor.value?.isOverLimit ?? false)

const commentHtml = (content: string) => sanitizeHtml(content)

const skillStatusLabel = (code: string) =>
  code === 'PENDING_APPROVAL' ? t('app.common.statusPending') : code === 'DRAFT' ? t('app.common.statusDraft') : code === 'APPROVED' ? t('app.common.statusApproved') : code === 'REJECTED' ? t('app.common.statusRejected') : code
const skillStatusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'

function timeAgo(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return t('app.common.timeAgoNow')
  const m = Math.floor(s / 60)
  if (m < 60) return t('app.common.timeAgoMin', { n: m })
  const h = Math.floor(m / 60)
  if (h < 24) return t('app.common.timeAgoHour', { n: h })
  const d = Math.floor(h / 24)
  if (d <= 7) return t('app.common.timeAgoDay', { n: d })
  const dt = new Date(iso)
  const dd = String(dt.getDate()).padStart(2, '0')
  const mm = String(dt.getMonth() + 1).padStart(2, '0')
  return `${dd}/${mm}/${dt.getFullYear()}`
}
</script>

<template>
  <div class="ctn" :class="{ 'is-top': depth === 1 }">
    <div class="comment" :class="{ 'is-reply': depth > 1 }">
      <PrismAvatar :name="comment.userName ?? ''" :src="comment.userAvatarUrl ?? undefined" :size="26" />
      <div class="comment-col">
        <div class="comment-head">
          <span class="comment-user">{{ comment.userName ?? t('app.common.unknown') }}</span>
          <span class="comment-time">{{ timeAgo(comment.createdAt) }}</span>
          <span v-if="comment.resolvedYn === 'Y'" class="comment-resolved">
            <Check :size="12" aria-hidden="true" /> {{ t('app.skillMarket.resolved') }}
          </span>
        </div>

        <p class="comment-body" v-html="commentHtml(comment.content)"></p>
      <div v-if="comment.linkedSkillName" class="comment-foot">
        <button
          v-if="comment.linkedSkillId"
          type="button"
          class="skill-chip"
          :title="t('app.skillMarket.viewSkillDetail')"
          @click="onSkillChipClick(comment)"
        >
          <Link2 :size="12" aria-hidden="true" /> {{ comment.linkedSkillName }}
        </button>
        <span v-else class="skill-chip">
          <Link2 :size="12" aria-hidden="true" /> {{ comment.linkedSkillName }}
        </span>
        <PrismBadge v-if="comment.linkedSkillStatus" :tone="skillStatusTone(comment.linkedSkillStatus)">
          {{ skillStatusLabel(comment.linkedSkillStatus) }}
        </PrismBadge>
        <PrismButton
          v-if="comment.resolvedYn !== 'Y' && isRequestCreator && comment.linkedSkillStatus === 'APPROVED'"
          variant="ghost"
          class="resolve-btn"
          @click="onResolve(comment)"
        >
          <Check :size="14" aria-hidden="true" /> {{ t('app.skillMarket.markResolved') }}
        </PrismButton>
      </div>
      <div class="comment-actions">
        <button
          type="button"
          class="comment-action"
          :class="{ 'is-liked': comment.likedByMe }"
          :title="comment.likedByMe ? t('app.skillMarket.unlike') : t('app.skillMarket.like')"
          :aria-label="comment.likedByMe ? t('app.skillMarket.unlike') : t('app.skillMarket.like')"
          @click="onLike(comment)"
        >
          <ThumbsUp :size="14" :fill="comment.likedByMe ? 'currentColor' : 'none'" aria-hidden="true" />
          <span class="n">{{ comment.likeCount }}</span>
        </button>
        <button
          v-if="depth < 3"
          type="button"
          class="comment-action"
          :class="{ 'is-active': replyParentId === comment.id }"
          :title="replyParentId === comment.id ? t('app.skillMarket.cancelReply') : t('app.skillMarket.reply')"
          :aria-label="replyParentId === comment.id ? t('app.skillMarket.cancelReply') : t('app.skillMarket.reply')"
          @click="onReply(comment)"
        >
          <MessageSquare :size="14" aria-hidden="true" />
        </button>
        <button
          v-if="comment.canEdit"
          type="button"
          class="comment-action"
          :class="{ 'is-active': editCommentId === comment.id }"
          :title="editCommentId === comment.id ? t('app.skillMarket.cancelEdit') : t('app.common.edit')"
          :aria-label="editCommentId === comment.id ? t('app.skillMarket.cancelEdit') : t('app.common.edit')"
          @click="onEdit(comment)"
        >
          <Pencil :size="14" aria-hidden="true" />
        </button>
        <button
          v-if="comment.canDelete"
          type="button"
          class="comment-action comment-action--danger"
          :title="t('app.common.delete')"
          :aria-label="t('app.common.delete')"
          @click="onDelete(comment)"
        >
          <Trash2 :size="14" aria-hidden="true" />
        </button>
      </div>
      <!-- Reply form (only for the comment being replied to) -->
      <Transition name="ctn-form">
        <div v-if="replyParentId === comment.id" class="reply-form">
          <RichTextEditor
            ref="replyEditor"
            :model-value="replyDraft"
            @update:model-value="onReplyInput"
            :placeholder="t('app.skillMarket.writeReplyPh')"
            :rows="3"
            :max-length="1000"
            mention-enabled
            :mention-suggestion="mentionSuggestion"
          />
          <div class="reply-actions">
            <PrismButton variant="secondary" @click="onReply(comment)">{{ t('app.common.cancel') }}</PrismButton>
            <PrismButton :loading="replying" :disabled="!hasReplyText || replyOverLimit" @click="onPostReply">
              {{ t('app.skillMarket.reply') }}
            </PrismButton>
          </div>
        </div>
      </Transition>
      <!-- Edit form (only for the comment being edited) -->
      <Transition name="ctn-form">
        <div v-if="editCommentId === comment.id" class="reply-form">
          <RichTextEditor
            ref="editEditor"
            :model-value="editCommentDraft"
            @update:model-value="onEditInput"
            :placeholder="t('app.skillMarket.editCommentPh')"
            :rows="3"
            :max-length="1000"
            mention-enabled
            :mention-suggestion="mentionSuggestion"
          />
          <div class="reply-actions">
            <PrismButton variant="secondary" @click="onEdit(comment)">{{ t('app.common.cancel') }}</PrismButton>
            <PrismButton :loading="savingComment" :disabled="!hasEditText || editOverLimit" @click="onSaveEdit(comment)">
              {{ t('app.common.save') }}
            </PrismButton>
          </div>
        </div>
      </Transition>
      </div>
    </div>

    <!-- Replies (nested, max depth 3) -->
    <div v-if="(comment.replies ?? []).length" class="ctn-replies">
      <CommentTreeNode
        v-for="r in comment.replies ?? []"
        :key="r.id"
        :comment="r"
        :depth="depth + 1"
        :is-request-creator="isRequestCreator"
        :reply-parent-id="replyParentId"
        :reply-draft="replyDraft"
        :replying="replying"
        :on-like="onLike"
        :on-reply="onReply"
        :on-delete="onDelete"
        :on-edit="onEdit"
        :on-resolve="onResolve"
        :on-skill-chip-click="onSkillChipClick"
        :on-reply-input="onReplyInput"
        :on-post-reply="onPostReply"
        :edit-comment-id="editCommentId"
        :edit-comment-draft="editCommentDraft"
        :saving-comment="savingComment"
        :on-edit-input="onEditInput"
        :on-save-edit="onSaveEdit"
        :mention-suggestion="mentionSuggestion"
      />
    </div>
  </div>
</template>

<style scoped>
.ctn {
  display: grid;
  gap: 2px;
}
/* Hairline between top-level comments only (not between a parent and its replies) */
.ctn.is-top:not(:last-child) {
  border-bottom: 1px solid rgb(255 255 255 / .08);
}
.ctn-replies {
  display: grid;
  gap: var(--ds-space-2);
  margin-top: 0;
  margin-left: 28px;
  padding-left: var(--ds-space-2);
  border-left: 2px solid var(--ds-color-border);
}
/* Comment: timeline row — avatar left (variant B) */
.comment {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: var(--ds-space-3);
  align-items: start;
  padding: 10px 0;
}
.comment.is-reply {
  padding: 6px 0;
}
.comment-col {
  min-width: 0;
  display: grid;
  gap: 4px;
}
.comment-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.comment-user {
  font-weight: 600;
  font-size: 13.5px;
}
.comment-time {
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.comment-resolved {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--ds-color-success);
  font-weight: 600;
}
.comment-body {
  margin: 0;
  font-size: 14px;
  color: var(--ds-color-text-secondary);
  line-height: 1.55;
}
.comment-body :deep(p) {
  margin: 0 0 var(--ds-space-1);
}
.comment-body :deep(p:last-child) {
  margin-bottom: 0;
}
.comment-body :deep(a) {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
/* @mention chips (same style as AI News comments) */
.comment-body :deep(.mention),
.comment-body :deep(span[data-type="mention"]) {
  color: var(--ds-color-accent);
  background: rgb(94 106 210 / .12);
  border-radius: 3px;
  padding: 0 3px;
}
.comment-foot {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-wrap: wrap;
}
.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: 12px var(--ds-font-mono);
  padding: 3px 9px;
  border: 1px solid rgb(94 106 210 / .24);
  border-radius: 6px;
  color: var(--ds-color-accent);
  background: rgb(94 106 210 / .07);
  white-space: nowrap;
}
button.skill-chip {
  cursor: pointer;
  transition: border-color var(--ds-duration-fast) ease, background var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  button.skill-chip:hover {
    border-color: rgb(94 106 210 / .5);
    background: rgb(94 106 210 / .14);
  }
}
.resolve-btn {
  color: var(--ds-color-success);
  font-size: 13px;
  min-height: 32px;
  padding: 0 var(--ds-space-3);
}
/* Action bar: icon buttons (Like → Reply → Edit → Delete) — shifted left
   so the first icon aligns with the start of the comment text
   (offset = the button's own 10px left padding) */
.comment-actions {
  display: flex;
  align-items: center;
  gap: 0;
  margin-left: -10px;
}
.comment-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  color: var(--ds-color-text-muted);
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: var(--ds-radius-sm);
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease;
}
.comment-action .n {
  font: 11px var(--ds-font-mono);
}
@media (hover: hover) {
  .comment-action:hover {
    background: rgb(94 106 210 / .08);
    color: var(--ds-color-text-primary);
  }
}
.comment-action.is-liked {
  color: var(--ds-color-accent);
}
.comment-action.is-active {
  color: var(--ds-color-accent);
}
.comment-action--danger:hover {
  background: rgb(229 83 75 / .10);
  color: var(--ds-color-danger);
}
.reply-form {
  display: grid;
  gap: var(--ds-space-2);
  padding: var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: rgb(255 255 255 / .35);
  transition: border-color var(--ds-duration-fast) ease, box-shadow var(--ds-duration-fast) ease;
}
.reply-form:focus-within {
  border-color: rgb(94 106 210 / .42);
  box-shadow: var(--ds-shadow-card);
}
/* Merge the tiptap editor into the form (drop its own border/bg) */
.reply-form :deep(.rte) {
  border: none;
  background: transparent;
}
.reply-form :deep(.rte-content) {
  min-height: 44px;
  max-height: 160px;
  overflow-y: auto;
  font-size: 14px;
}
.reply-actions {
  display: flex;
  gap: var(--ds-space-2);
  justify-content: flex-end;
}
/* Compact buttons inside the inline reply/edit forms — the base .p-button
   (16px / 10px 18px) is too large for the 14px comment composer. */
.reply-actions :deep(.p-button) {
  padding: 5px 12px;
  font-size: 13px;
  min-height: 30px;
}
/* Reply / edit form enter-leave */
.ctn-form-enter-active {
  transition: opacity var(--ds-duration-normal) ease, transform var(--ds-duration-normal) ease;
}
.ctn-form-leave-active {
  transition: opacity var(--ds-duration-fast) ease;
}
.ctn-form-enter-from,
.ctn-form-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
