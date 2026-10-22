<script setup lang="ts">
import { Check, Heart, Link2, MessageSquare, X } from 'lucide-vue-next'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import CommentEditor from '@/features/skill-requests/components/CommentEditor.vue'
import CommentTreeNode from './CommentTreeNode.vue'
import { sanitizeHtml } from '@/lib/sanitize'
import type { SkillRequestCommentItem } from '@/features/skill-requests/types'

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
  onResolve: (c: SkillRequestCommentItem) => void
  onSkillChipClick: (c: SkillRequestCommentItem) => void
  onReplyInput: (value: string) => void
  onPostReply: () => void
}>()

const commentHtml = (content: string) => sanitizeHtml(content)

const skillStatusLabel = (code: string) =>
  code === 'PENDING_APPROVAL' ? 'Pending Approval' : code === 'DRAFT' ? 'Draft' : code === 'APPROVED' ? 'Approved' : code === 'REJECTED' ? 'Rejected' : code
const skillStatusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'

function timeAgo(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'just now'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m} min ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} hour${h === 1 ? '' : 's'} ago`
  const d = Math.floor(h / 24)
  if (d <= 7) return `${d} day${d === 1 ? '' : 's'} ago`
  const dt = new Date(iso)
  const dd = String(dt.getDate()).padStart(2, '0')
  const mm = String(dt.getMonth() + 1).padStart(2, '0')
  return `${dd}/${mm}/${dt.getFullYear()}`
}
</script>

<template>
  <div class="ctn">
    <div class="comment" :class="{ 'is-reply': depth > 1 }">
      <div class="comment-head">
        <PrismAvatar :name="comment.userName ?? ''" :size="24" />
        <span class="comment-user">{{ comment.userName ?? 'Unknown' }}</span>
        <span class="comment-time">{{ timeAgo(comment.createdAt) }}</span>
        <span v-if="comment.resolvedYn === 'Y'" class="comment-resolved">
          <Check :size="12" aria-hidden="true" /> Resolved
        </span>
        <button
          v-if="comment.canDelete"
          type="button"
          class="comment-delete"
          aria-label="Delete comment"
          @click="onDelete(comment)"
        >
          <X :size="14" aria-hidden="true" />
        </button>
      </div>
      <p class="comment-body" v-html="commentHtml(comment.content)"></p>
      <div v-if="comment.linkedSkillName" class="comment-foot">
        <button
          v-if="comment.linkedSkillId"
          type="button"
          class="skill-chip"
          title="View skill detail"
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
          <Check :size="14" aria-hidden="true" /> Mark resolved
        </PrismButton>
      </div>
      <div class="comment-actions">
        <button
          type="button"
          class="comment-action"
          :class="{ 'is-liked': comment.likedByMe }"
          :aria-label="comment.likedByMe ? 'Unlike' : 'Like'"
          @click="onLike(comment)"
        >
          <Heart :size="14" :fill="comment.likedByMe ? 'currentColor' : 'none'" aria-hidden="true" />
          <span>{{ comment.likeCount }}</span>
        </button>
        <button
          v-if="depth < 3"
          type="button"
          class="comment-action"
          :class="{ 'is-active': replyParentId === comment.id }"
          @click="onReply(comment)"
        >
          <MessageSquare :size="14" aria-hidden="true" />
          <span>{{ replyParentId === comment.id ? 'Cancel' : 'Reply' }}</span>
        </button>
      </div>
      <!-- Reply form (only for the comment being replied to) -->
      <div v-if="replyParentId === comment.id" class="reply-form">
        <CommentEditor
          :model-value="replyDraft"
          @update:model-value="onReplyInput"
          placeholder="Write a reply…"
          :max-length="1000"
        />
        <div class="reply-actions">
          <PrismButton variant="secondary" @click="onReply(comment)">Cancel</PrismButton>
          <PrismButton :loading="replying" :disabled="!replyDraft.trim()" @click="onPostReply">
            Reply
          </PrismButton>
        </div>
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
        :on-resolve="onResolve"
        :on-skill-chip-click="onSkillChipClick"
        :on-reply-input="onReplyInput"
        :on-post-reply="onPostReply"
      />
    </div>
  </div>
</template>

<style scoped>
.ctn {
  display: grid;
  gap: var(--ds-space-1);
}
.ctn-replies {
  display: grid;
  gap: var(--ds-space-1);
  margin-left: var(--ds-space-5);
  padding-left: var(--ds-space-3);
  border-left: 2px solid var(--ds-color-border);
}
.comment {
  display: grid;
  gap: var(--ds-space-1);
  padding: var(--ds-space-2) var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
}
.comment.is-reply {
  background: rgb(255 255 255 / .02);
}
.comment-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.comment-user {
  font-weight: 600;
  font-size: 14px;
}
.comment-time {
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.comment-resolved {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--ds-color-success);
  font-weight: 600;
}
.comment-delete {
  margin-left: auto;
  border: none;
  background: none;
  color: var(--ds-color-text-muted);
  cursor: pointer;
  padding: 0;
  display: inline-flex;
}
@media (hover: hover) { .comment-delete:hover { color: var(--ds-color-danger); } }
.comment-body {
  margin: 0;
  font-size: 14px;
  color: var(--ds-color-text-primary);
  line-height: 1.5;
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
  font: 13px var(--ds-font-mono);
  padding: 5px 8px;
  border: 1px solid rgb(103 232 249 / .24);
  border-radius: 5px;
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .07);
  white-space: nowrap;
}
button.skill-chip {
  cursor: pointer;
  transition: border-color var(--ds-duration-fast) ease, background var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  button.skill-chip:hover {
    border-color: rgb(103 232 249 / .5);
    background: rgb(103 232 249 / .14);
  }
}
.resolve-btn {
  color: var(--ds-color-success);
  font-size: 13px;
  min-height: 32px;
  padding: 0 var(--ds-space-3);
}
.comment-actions {
  display: flex;
  align-items: center;
  gap: var(--ds-space-1);
}
.comment-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: none;
  color: var(--ds-color-text-muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  padding: 3px 8px;
  border-radius: var(--ds-radius-sm);
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .comment-action:hover {
    background: rgb(103 232 249 / .08);
    color: var(--ds-color-text-primary);
  }
}
.comment-action.is-liked {
  color: var(--ds-color-danger);
}
.comment-action.is-active {
  color: var(--ds-color-accent);
}
.reply-form {
  display: grid;
  gap: var(--ds-space-2);
}
.reply-actions {
  display: flex;
  gap: var(--ds-space-2);
  justify-content: flex-end;
}
</style>
