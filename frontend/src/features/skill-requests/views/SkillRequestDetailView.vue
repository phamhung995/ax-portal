<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ThumbsUp, MessageSquare, Link2, Check, X } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import CommentEditor from '@/features/skill-requests/components/CommentEditor.vue'
import CommentTreeNode from '@/features/skill-requests/views/CommentTreeNode.vue'
import ImageLightbox from '@/features/skill-requests/components/ImageLightbox.vue'
import LinkSkillPopup from './LinkSkillPopup.vue'
import SkillDetailPopup from '@/features/skills/views/SkillDetailPopup.vue'
import { skillRequestApi } from '@/features/skill-requests/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { sanitizeHtml } from '@/lib/sanitize'
import { useAuthStore } from '@/stores/auth'
import type {
  SkillRequestFeedItem,
  SkillRequestCommentItem,
} from '@/features/skill-requests/types'
import type { SkillListItem } from '@/features/skills/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const viewerId = auth.user?.userId ?? -1

const requestId = computed(() => Number(route.params.id))
const validId = computed(() => Number.isInteger(requestId.value) && requestId.value > 0)

const item = ref<SkillRequestFeedItem | null>(null)
const loading = ref(false)
const loadError = ref('')

// ── Comments (sidebar, paged, ranked by backend) ──────────────────────────
const COMMENT_PAGE_SIZE = 10
const comments = ref<SkillRequestCommentItem[]>([])
const commentsLoading = ref(false)
const commentsLoaded = ref(false)
const commentPage = ref(1)
const commentsTotal = ref(0)
const commentsHasMore = ref(false)
const commentListEl = ref<HTMLElement | null>(null)

// ── Comment form (pinned at the bottom of the sidebar) ────────────────────
const commentDraft = ref('')
const posting = ref(false)
const pendingSkill = ref<SkillListItem | null>(null)
const linkPopupOpen = ref(false)
const detailSkillId = ref<number | null>(null)

// ── Reply state (one open reply form at a time) ───────────────────────────
const replyParentId = ref<number | null>(null)
const replyDraft = ref('')
const replying = ref(false)

// ── Attachments ───────────────────────────────────────────────────────────
const imageFiles = computed(() => item.value?.files.filter((f) => f.isImage && f.url) ?? [])
const nonImageFiles = computed(() => item.value?.files.filter((f) => !f.isImage) ?? [])
const lightboxOpen = ref(false)
const lightboxIndex = ref(0)

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

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const descHtml = computed(() => sanitizeHtml(item.value?.description ?? ''))

// ── Load ──────────────────────────────────────────────────────────────────
async function loadDetail() {
  if (!validId.value) return
  loading.value = true
  loadError.value = ''
  item.value = null
  comments.value = []
  commentsLoaded.value = false
  commentPage.value = 1
  try {
    item.value = await skillRequestApi.detail(requestId.value)
    void loadComments(true)
  } catch (err) {
    loadError.value = toUserError(err).message
  } finally {
    loading.value = false
  }
}

async function loadComments(reset = false) {
  if (commentsLoading.value || !validId.value) return
  commentsLoading.value = true
  try {
    const page = reset ? 1 : commentPage.value + 1
    const result = await skillRequestApi.comments(requestId.value, page, COMMENT_PAGE_SIZE)
    comments.value = reset ? result.items : [...comments.value, ...result.items]
    commentsTotal.value = result.totalCount
    commentsHasMore.value = comments.value.length < result.totalCount
    commentPage.value = page
    commentsLoaded.value = true
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    commentsLoading.value = false
  }
}

function onSkillChipClick(c: SkillRequestCommentItem) {
  if (c.linkedSkillStatus && c.linkedSkillStatus !== 'APPROVED') {
    toastError('This skill has not been approved yet. Please contact an admin.')
    return
  }
  detailSkillId.value = c.linkedSkillId
}

function onSkillSelected(skill: SkillListItem) {
  pendingSkill.value = skill
}

// ── Vote (toggle) ─────────────────────────────────────────────────────────
async function onVote() {
  const it = item.value
  if (!it) return
  try {
    if (it.hasVoted) {
      await skillRequestApi.unvote(it.id)
      it.hasVoted = false
      it.voteCount = Math.max(0, it.voteCount - 1)
    } else {
      await skillRequestApi.vote(it.id)
      it.hasVoted = true
      it.voteCount += 1
    }
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

// ── Comments: post / like / delete / resolve / reply ──────────────────────
async function postComment() {
  const content = commentDraft.value.trim()
  if (!content || posting.value || !validId.value) return
  posting.value = true
  const linkedSkill = pendingSkill.value
  try {
    await skillRequestApi.addComment(requestId.value, content, linkedSkill?.id)
    commentDraft.value = ''
    pendingSkill.value = null
    if (item.value) item.value.commentCount += 1
    await loadComments(true)
    // Keep the newest view: scroll the sidebar to the top (ranked list).
    commentListEl.value?.scrollTo({ top: 0 })
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    posting.value = false
  }
}

async function onLike(comment: SkillRequestCommentItem) {
  if (!validId.value) return
  try {
    if (comment.likedByMe) {
      await skillRequestApi.unlikeComment(requestId.value, comment.id)
      comment.likedByMe = false
      comment.likeCount = Math.max(0, comment.likeCount - 1)
    } else {
      await skillRequestApi.likeComment(requestId.value, comment.id)
      comment.likedByMe = true
      comment.likeCount += 1
    }
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

function openReply(comment: SkillRequestCommentItem) {
  if (replyParentId.value === comment.id) {
    replyParentId.value = null
    replyDraft.value = ''
    return
  }
  replyParentId.value = comment.id
  replyDraft.value = ''
}

async function postReply() {
  const content = replyDraft.value.trim()
  if (!content || !replyParentId.value || replying.value || !validId.value) return
  replying.value = true
  try {
    await skillRequestApi.addComment(requestId.value, content, undefined, replyParentId.value)
    replyParentId.value = null
    replyDraft.value = ''
    if (item.value) item.value.commentCount += 1
    await loadComments(true)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    replying.value = false
  }
}

async function onResolve(comment: SkillRequestCommentItem) {
  if (!validId.value) return
  try {
    await skillRequestApi.resolveComment(requestId.value, comment.id)
    comment.resolvedYn = 'Y'
    comment.resolvedAt = new Date().toISOString()
    if (item.value) item.value.hasResolvedComment = true
    toastSuccess('Comment marked as resolved.')
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

async function onDeleteComment(comment: SkillRequestCommentItem) {
  if (!validId.value) return
  try {
    await skillRequestApi.deleteComment(requestId.value, comment.id)
    if (item.value) item.value.commentCount = Math.max(0, item.value.commentCount - 1)
    await loadComments(true)
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

async function onDownloadFile(fileId: number) {
  if (!validId.value) return
  try {
    await skillRequestApi.downloadFile(requestId.value, fileId)
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

function openLightbox(index: number) {
  lightboxIndex.value = index
  lightboxOpen.value = true
}

function goBack() {
  void router.push({ name: 'skill-requests' })
}

// Reload when the route param changes (e.g. navigating between details).
watch(requestId, (id, oldId) => {
  if (id !== oldId && Number.isInteger(id)) {
    void loadDetail()
  }
})

onMounted(() => {
  void loadDetail()
})

onBeforeUnmount(() => {
  /* nothing to clean up */
})
</script>

<template>
  <div class="srd">
    <div v-if="!validId" class="srd-wrap">
      <PrismEmptyState title="Invalid request." description="The request id in the URL is not valid." />
      <PrismButton variant="secondary" @click="goBack">Back to feed</PrismButton>
    </div>

    <div v-else class="srd-wrap">
      <button type="button" class="srd-back" @click="goBack">
        <ArrowLeft :size="16" aria-hidden="true" /> Back to feed
      </button>

      <div v-if="loading" class="srd-loading">Loading request…</div>
      <PrismEmptyState
        v-else-if="loadError"
        title="Unable to load the request."
        :description="loadError"
      >
        <PrismButton variant="secondary" @click="loadDetail">Retry</PrismButton>
      </PrismEmptyState>

      <template v-else-if="item">
        <div class="srd-cols">
          <!-- Main column: the post -->
          <article class="srd-post p-glass">
            <header class="srd-head">
              <PrismAvatar :name="item.creatorName ?? ''" :size="44" />
              <div class="srd-who">
                <span class="srd-name">{{ item.creatorName ?? 'Unknown' }}</span>
                <span class="srd-time">{{ timeAgo(item.createdAt) }}</span>
              </div>
              <PrismBadge v-if="item.hasResolvedComment" tone="success" class="srd-resolved">
                <Check :size="12" aria-hidden="true" /> Resolved
              </PrismBadge>
            </header>

            <h1 class="srd-title">{{ item.title }}</h1>
            <div v-if="item.description" class="srd-desc" v-html="descHtml"></div>
            <div v-if="item.hashtags.length" class="srd-tags">
              <PrismBadge v-for="tag in item.hashtags" :key="tag" tone="info">#{{ tag }}</PrismBadge>
            </div>

            <!-- Attachments: image grid (click → lightbox) -->
            <div
              v-if="imageFiles.length"
              class="attach-grid"
              :class="{ 'attach-grid--single': imageFiles.length === 1 }"
            >
              <button
                v-for="(img, i) in imageFiles"
                :key="img.id"
                type="button"
                class="attach-tile"
                :aria-label="`View ${img.fileName}`"
                @click="openLightbox(i)"
              >
                <img :src="img.url ?? undefined" :alt="img.fileName" loading="lazy" />
                <span v-if="imageFiles.length > 1 && i === imageFiles.length - 1" class="attach-tile-count">
                  {{ imageFiles.length }} photos
                </span>
              </button>
            </div>

            <!-- Attachments: non-image files (download links) -->
            <div v-if="nonImageFiles.length" class="attach-files">
              <div v-for="f in nonImageFiles" :key="f.id" class="attach-file">
                <span class="attach-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
                <button
                  type="button"
                  class="attach-file-download"
                  :aria-label="`Download ${f.fileName}`"
                  @click="onDownloadFile(f.id)"
                >
                  Download
                </button>
              </div>
            </div>

            <!-- Action bar -->
            <div class="srd-actions">
              <button
                type="button"
                class="srd-action"
                :class="{ 'is-voted': item.hasVoted }"
                :aria-label="item.hasVoted ? 'Unvote' : 'Vote'"
                @click="onVote"
              >
                <ThumbsUp :size="16" :fill="item.hasVoted ? 'currentColor' : 'none'" aria-hidden="true" />
                <span>{{ item.voteCount }}</span>
              </button>
              <span class="srd-action srd-action--static">
                <MessageSquare :size="16" aria-hidden="true" />
                <span>{{ item.commentCount }}</span>
              </span>
            </div>
          </article>

          <!-- Sidebar: comments with internal scroll + pinned composer -->
          <aside class="srd-side p-glass" aria-label="Comments">
            <div class="srd-side-head">
              <h3>Comments</h3>
              <span class="srd-side-count">{{ item.commentCount }}</span>
            </div>

            <div ref="commentListEl" class="srd-comments">
              <div v-if="commentsLoading && !comments.length" class="srd-comments-loading">
                Loading comments…
              </div>
              <p v-else-if="commentsLoaded && !comments.length" class="srd-comments-empty">
                No comments yet. Be the first to share your thoughts.
              </p>
              <div v-else class="srd-comments-list">
                <CommentTreeNode
                  v-for="c in comments"
                  :key="c.id"
                  :comment="c"
                  :depth="1"
                  :is-request-creator="item.isRequestCreator"
                  :reply-parent-id="replyParentId"
                  :on-like="onLike"
                  :on-reply="openReply"
                  :on-delete="onDeleteComment"
                  :on-resolve="onResolve"
                  :on-skill-chip-click="onSkillChipClick"
                  :reply-draft="replyDraft"
                  :replying="replying"
                  :on-reply-input="(v: string) => (replyDraft = v)"
                  :on-post-reply="postReply"
                />
              </div>
              <div v-if="commentsHasMore" class="srd-comments-more">
                <PrismButton variant="secondary" :loading="commentsLoading" @click="loadComments()">
                  Load more comments
                </PrismButton>
              </div>
            </div>

            <!-- Pinned comment composer -->
            <div class="srd-composer">
              <CommentEditor v-model="commentDraft" placeholder="Share your thoughts…" :max-length="1000" />
              <div v-if="pendingSkill" class="srd-linked-chip">
                <Link2 :size="12" aria-hidden="true" />
                {{ pendingSkill.name }}
                <PrismBadge :tone="skillStatusTone(pendingSkill.status)">
                  {{ skillStatusLabel(pendingSkill.status) }}
                </PrismBadge>
                <button type="button" aria-label="Remove linked skill" @click="pendingSkill = null">
                  <X :size="12" aria-hidden="true" />
                </button>
              </div>
              <div class="srd-composer-actions">
                <PrismButton variant="secondary" @click="linkPopupOpen = true">
                  <Link2 :size="14" aria-hidden="true" /> Link a skill
                </PrismButton>
                <PrismButton :loading="posting" :disabled="!commentDraft.trim()" @click="postComment">
                  Post
                </PrismButton>
              </div>
            </div>
          </aside>
        </div>
      </template>
    </div>

    <LinkSkillPopup v-model:open="linkPopupOpen" @selected="onSkillSelected" />
    <SkillDetailPopup :skill-id="detailSkillId" @closed="detailSkillId = null" />
    <ImageLightbox
      :images="imageFiles.map((f) => ({ url: f.url!, name: f.fileName }))"
      :index="lightboxIndex"
      :open="lightboxOpen"
      @update:index="lightboxIndex = $event"
      @close="lightboxOpen = false"
    />
  </div>
</template>

<style scoped>
.srd {
  width: 100%;
}
.srd-wrap {
  display: grid;
  gap: var(--ds-space-4);
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
}
.srd-back {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-2);
  justify-self: start;
  border: none;
  background: none;
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  padding: 4px 0;
  transition: color var(--ds-duration-fast) var(--ds-ease);
}
@media (hover: hover) {
  .srd-back:hover {
    color: var(--ds-color-accent);
  }
}
.srd-loading {
  color: var(--ds-color-text-muted);
  font-size: 14px;
  padding: var(--ds-space-6) 0;
}

/* Two columns: post (main) + comments sidebar (internal scroll, sticky) */
.srd-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
  gap: var(--ds-space-4);
  align-items: start;
}
@media (max-width: 999px) {
  .srd-cols {
    grid-template-columns: 1fr;
  }
}

/* Post card */
.srd-post {
  padding: var(--ds-space-6);
  display: grid;
  gap: var(--ds-space-4);
}
.srd-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
.srd-who {
  display: grid;
  gap: 2px;
}
.srd-name {
  font-weight: 600;
  font-size: 16px;
}
.srd-time {
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.srd-resolved {
  margin-left: auto;
}
.srd-title {
  margin: 0;
  font-size: 24px;
  font-weight: 500;
  line-height: 1.35;
}
.srd-desc {
  margin: 0;
  color: var(--ds-color-text-secondary);
  font-size: 15px;
  line-height: 1.6;
}
.srd-desc :deep(p) {
  margin: 0 0 var(--ds-space-2);
}
.srd-desc :deep(p:last-child) {
  margin-bottom: 0;
}
.srd-desc :deep(a) {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.srd-tags {
  display: flex;
  gap: var(--ds-space-2);
  flex-wrap: wrap;
}

/* Attachments: image grid */
.attach-grid {
  display: grid;
  gap: var(--ds-space-2);
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
}
.attach-grid--single {
  grid-template-columns: 1fr;
}
.attach-tile {
  position: relative;
  margin: 0;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  overflow: hidden;
  background: var(--ds-input-background);
  cursor: zoom-in;
  aspect-ratio: 4 / 3;
  padding: 0;
  transition: border-color var(--ds-duration-fast) var(--ds-ease),
    transform var(--ds-duration-fast) var(--ds-ease);
}
@media (hover: hover) {
  .attach-tile:hover {
    border-color: var(--ds-color-border-strong);
    transform: translateY(-1px);
  }
}
.attach-tile:focus-visible {
  outline: 2px solid var(--ds-color-accent);
  outline-offset: 2px;
}
.attach-tile img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.attach-tile-count {
  position: absolute;
  right: var(--ds-space-2);
  bottom: var(--ds-space-2);
  font-size: 11px;
  font-family: var(--ds-font-mono);
  color: var(--ds-color-text-primary);
  background: rgb(5 8 26 / .72);
  border: 1px solid var(--ds-color-border);
  padding: 2px 8px;
  border-radius: 20px;
  pointer-events: none;
}

/* Attachments: non-image files (mirrors Skill Detail popup .dtl-file) */
.attach-files {
  display: grid;
  gap: var(--ds-space-2);
  justify-items: start;
}
.attach-file {
  display: flex;
  align-items: center;
  justify-content: start;
  gap: var(--ds-space-1);
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
.attach-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.attach-file-download {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-accent);
  font: inherit;
  font-size: 13px;
  flex-shrink: 0;
}
@media (hover: hover) {
  .attach-file-download:hover {
    text-decoration: underline;
  }
}

/* Action bar */
.srd-actions {
  display: flex;
  align-items: center;
  gap: var(--ds-space-4);
  padding-top: var(--ds-space-3);
  border-top: 1px solid var(--ds-color-border);
}
.srd-action {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  border: none;
  background: none;
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--ds-radius-sm);
}
@media (hover: hover) {
  .srd-action:not(.srd-action--static):hover {
    background: rgb(103 232 249 / .10);
    color: var(--ds-color-text-primary);
  }
}
.srd-action.is-voted {
  color: var(--ds-color-accent);
}
.srd-action--static {
  cursor: default;
}

/* Comments sidebar: fixed height, internal scroll, sticky on desktop */
.srd-side {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  height: calc(100vh - var(--nx-app-header-height) - 2 * var(--ds-space-4));
  min-height: 480px;
  max-height: 820px;
  padding: var(--ds-space-4) var(--ds-space-5);
  position: sticky;
  top: 78px;
}
@media (max-width: 999px) {
  .srd-side {
    position: static;
    height: 480px;
    min-height: 420px;
  }
}
.srd-side-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.srd-side-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.srd-side-count {
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
.srd-comments {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--ds-color-border-strong) transparent;
  padding-right: 4px;
  margin-right: -4px;
  display: grid;
  gap: var(--ds-space-2);
  align-content: start;
}
.srd-comments::-webkit-scrollbar {
  width: 6px;
}
.srd-comments::-webkit-scrollbar-track {
  background: transparent;
}
.srd-comments::-webkit-scrollbar-thumb {
  background: var(--ds-color-border-strong);
  border-radius: 3px;
}
.srd-comments-loading,
.srd-comments-empty {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-muted);
  padding: var(--ds-space-2) 0;
}
.srd-comments-list {
  display: grid;
  gap: var(--ds-space-2);
}
.srd-comments-more {
  display: flex;
  justify-content: center;
}

/* Pinned composer */
.srd-composer {
  display: grid;
  gap: var(--ds-space-2);
  padding-top: var(--ds-space-3);
  border-top: 1px solid var(--ds-color-border);
}
.srd-composer-actions {
  display: flex;
  gap: var(--ds-space-2);
  justify-content: flex-end;
}
.srd-linked-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: 13px var(--ds-font-mono);
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .07);
  border: 1px solid rgb(103 232 249 / .24);
  padding: 5px 8px;
  border-radius: 5px;
  width: fit-content;
}
.srd-linked-chip button {
  border: none;
  background: none;
  color: var(--ds-color-text-muted);
  cursor: pointer;
  padding: 0;
  display: inline-flex;
}
@media (hover: hover) {
  .srd-linked-chip button:hover {
    color: var(--ds-color-danger);
  }
}
</style>
