<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowDownWideNarrow, ArrowUpNarrowWide, Flame, Heart, MessageCircle, Send, ThumbsUp, Trash2 } from 'lucide-vue-next'
import PrismButton from '@/design-system/components/PrismButton.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { sanitizeHtml, stripHtml } from '@/lib/sanitize'
import { useI18nStore } from '@/stores/i18n'
import { aiNewsApi } from '@/features/ai-news/api'
import type { AiNewsCommentItem } from '@/features/ai-news/types'
import type { MentionSuggestionItem } from '@/components/RichTextEditor.vue'

const { t, formatDateTime } = useI18nStore()

// Render-only component for the comment STATE: the parent (AiNewsView) owns
// all state and the API calls, and this component is used in TWO places —
// under the email body and in the comment sidebar — so both stay in sync
// (same state). The one exception is the @mention autocomplete below, which
// is a local, stateless read (no shared state involved).
async function searchMentionUsers(query: string, signal?: AbortSignal): Promise<MentionSuggestionItem[]> {
  try {
    return await aiNewsApi.mentionUsers(query, signal)
  } catch {
    return []
  }
}

const props = defineProps<{
  comments: AiNewsCommentItem[]
  commentSending: boolean
  replySending: boolean
  voteSendingIds: Set<number>
  deletingId: number | null
  isAdmin: boolean
  // Like (FR-15)
  liked: boolean
  likeCount: number
  likeSending: boolean
  /** Where the comment form renders: 'top' (drawer default) or 'bottom'
   *  (main pane — user scrolls past comments to write). */
  formPosition?: 'top' | 'bottom'
  /** Show the "Comments (N)" header + sort toggle. Drawer = true (default),
   *  main pane = false (the header is redundant under the email body). */
  showHeader?: boolean
  /** Read-only mode (no comment/reply editors). Used by the comment drawer:
   *  the main pane under the body already has the full form, so the drawer
   *  is a lightweight view — mounting a second Tiptap editor + toolbar per
   *  drawer open was the point of this prop. */
  compact?: boolean
  /** Monotonic counter incremented by the parent after each successful
   *  comment send — triggers the form to collapse. Using a tick avoids
   *  the false-positive of watching commentText for ''. */
  justSent?: number
}>()
const _showHeader = computed(() => props.showHeader !== false)

// Two-way bindings for the text inputs (v-model from the parent).
const commentText = defineModel<string>('commentText', { required: true })
const replyText = defineModel<string>('replyText', { required: true })

// replyTo is a plain prop + emit (the parent toggles it).
const replyTo = defineModel<number | null>('replyTo', { required: true })

// Sort mode (default: oldest first). Shared via v-model so the block under
// the body and the comment sidebar (same parent state) stay in sync.
const sortOrder = defineModel<'oldest' | 'newest' | 'likesAsc' | 'likesDesc' | 'trending'>('sortOrder', { required: true })

// Stable sort: createdAt, then commentId as tie-breaker (same-second posts).
// Parse to epoch ms — lexicographic string comparison of ISO timestamps is
// unreliable when fractional-second precision varies (e.g. ".7Z" vs ".78Z").
const byDate = (a: AiNewsCommentItem, b: AiNewsCommentItem) => {
  const ta = new Date(a.createdAt).getTime()
  const tb = new Date(b.createdAt).getTime()
  return ta === tb ? a.commentId - b.commentId : ta < tb ? -1 : 1
}
// Sort by helpfulCount (ties broken by date).
const byLikes = (a: AiNewsCommentItem, b: AiNewsCommentItem) =>
  a.helpfulCount === b.helpfulCount ? byDate(a, b) : a.helpfulCount - b.helpfulCount

// Trending: total likes of a top-level thread = the top-level comment's
// own helpfulCount + sum of all its replies' helpfulCounts (depth-2 AND
// depth-3, FR-17 v1.21). Precomputed in a Map so the sort comparator is
// O(1) per comparison.
const threadTotalLikes = computed(() => {
  const byId = new Map<number, AiNewsCommentItem>()
  for (const c of props.comments) byId.set(c.commentId, c)
  const map = new Map<number, number>()
  const deletedRoots = new Set<number>()
  for (const c of props.comments) {
    if (c.parentId == null) {
      // Skip deleted top-level — a tombstoned thread shouldn't trend.
      map.set(c.commentId, c.deleted ? 0 : c.helpfulCount)
      if (c.deleted) deletedRoots.add(c.commentId)
      continue
    }
    if (c.deleted) continue
    // Find the thread root (at most 2 hops: depth-2 → root, depth-3 →
    // depth-2 → root).
    let rootId = c.parentId
    const parent = byId.get(rootId)
    if (parent && parent.parentId != null) rootId = parent.parentId
    // Don't accumulate likes under a deleted root — it shouldn't trend
    // even if its replies have votes.
    if (deletedRoots.has(rootId)) continue
    map.set(rootId, (map.get(rootId) ?? 0) + c.helpfulCount)
  }
  return map
})
const byTrending = (a: AiNewsCommentItem, b: AiNewsCommentItem) => {
  const ta = threadTotalLikes.value.get(a.commentId) ?? 0
  const tb = threadTotalLikes.value.get(b.commentId) ?? 0
  return ta === tb ? byDate(a, b) : ta - tb
}

function sortFn(mode: 'oldest' | 'newest' | 'likesAsc' | 'likesDesc' | 'trending') {
  switch (mode) {
    case 'oldest': return byDate
    case 'newest': return (a: AiNewsCommentItem, b: AiNewsCommentItem) => -byDate(a, b)
    case 'likesAsc': return byLikes
    case 'likesDesc': return (a: AiNewsCommentItem, b: AiNewsCommentItem) => -byLikes(a, b)
    case 'trending': return (a: AiNewsCommentItem, b: AiNewsCommentItem) => -byTrending(a, b)
  }
}

const emit = defineEmits<{
  send: []
  reply: [parentId: number]
  helpful: [comment: AiNewsCommentItem]
  delete: [comment: AiNewsCommentItem]
  like: []
}>()

// FR-23: rich text — the send buttons are enabled by the PLAIN TEXT of the
// draft (HTML tags and embedded images don't count).
const hasCommentText = computed(() => stripHtml(commentText.value).trim().length > 0)
const hasReplyText = computed(() => stripHtml(replyText.value).trim().length > 0)

// The editor's maxLength counter is display-only — the Send button must
// also be disabled when the plain text exceeds the limit (otherwise an
// over-limit draft reaches the backend and 400s).
const commentOverLimit = computed(() => commentEditor.value?.isOverLimit ?? false)
const replyOverLimit = computed(() =>
  replyEditor.value.some((e) => e?.isOverLimit === true))

// Threaded structure: top-level list + replies grouped by parent (FR-17).
// Both are sorted by the shared sortOrder (default asc = oldest first).
const topLevel = computed(() =>
  props.comments.filter((c) => c.parentId == null).sort(sortFn(sortOrder.value)),
)
// Replies grouped by parent in ONE pass (O(n log n)) — the template calls
// this per top-level comment, so a per-call filter/sort would be O(n²).
const repliesByParent = computed(() => {
  // Trending sort applies to top-level threads only. For replies, use
  // byLikes (their own helpfulCount) in trending mode — byTrending would
  // return 0 for all replies (they're never keys in threadTotalLikes).
  const cmp = sortOrder.value === 'trending' ? byLikes : sortFn(sortOrder.value)
  const map = new Map<number, AiNewsCommentItem[]>()
  for (const c of props.comments) {
    if (c.parentId == null) continue
    const arr = map.get(c.parentId)
    if (arr) arr.push(c)
    else map.set(c.parentId, [c])
  }
  for (const arr of map.values()) arr.sort(cmp)
  return map
})
const repliesOf = (commentId: number) => repliesByParent.value.get(commentId) ?? []

// Sanitized HTML per comment, cached in a PERSISTENT map keyed by
// commentId+content. sanitizeHtml re-parses the whole DOM — a computed that
// rebuilds the whole map on every comments-array change (every vote/comment/
// delete re-fetch) would re-parse every comment, in TWO instances (main pane
// + drawer). The persistent map only sanitizes new/changed comments.
const sanitizeCache = new Map<string, string>()
const sanitizedByComment = computed(() => {
  const map = new Map<number, string>()
  for (const c of props.comments) {
    const key = c.commentId + ':' + (c.content ?? '')
    let html = sanitizeCache.get(key)
    if (html === undefined) {
      html = sanitizeHtml(c.content ?? '')
      // Bound the cache (one entry per distinct comment content per session).
      if (sanitizeCache.size > 2000) sanitizeCache.clear()
      sanitizeCache.set(key, html)
    }
    map.set(c.commentId, html)
  }
  return map
})
const sanitizedContent = (commentId: number) => sanitizedByComment.value.get(commentId) ?? ''

// Auto-focus the reply editor when the reply form opens (the click on the
// "Reply" button would otherwise keep focus on the button). The ref lives
// inside a v-for, so Vue collects it as an ARRAY (one slot per comment);
// only the open form has a defined instance.
const replyEditor = ref<(InstanceType<typeof RichTextEditor> | null)[]>([])
watch(
  replyTo,
  (id) => {
    if (id == null) return
    void nextTick(() => replyEditor.value.find((e) => e)?.focus())
  },
)

/** Toggle the reply form. Only clears the draft when switching to a
    DIFFERENT thread — closing the form (or re-clicking the same thread)
    keeps the typed text so a misclick doesn't lose a long draft. */
function toggleReply(commentId: number) {
  if (replyTo.value === commentId) {
    replyTo.value = null
  } else {
    if (replyTo.value != null) replyText.value = ''
    replyTo.value = commentId
  }
}
const commentCount = computed(() => props.comments.filter((c) => !c.deleted).length)

// Sort dropdown state
const sortMenuOpen = ref(false)
const sortLabel = computed(() => {
  switch (sortOrder.value) {
    case 'oldest': return t('aiNews.sortOldestFirst')
    case 'newest': return t('aiNews.sortNewestFirst')
    case 'likesDesc': return t('aiNews.sortMostLiked')
    case 'likesAsc': return t('aiNews.sortLeastLiked')
    case 'trending': return t('aiNews.sortTrending')
  }
})

// ── Collapsible comment form ───────────────────────────────────────────────
// Starts as a 1-line placeholder; click → expand to a full editor.
// Collapses back after a successful send via the `justSent` prop.
// Used by BOTH the main pane (4 rows) and the drawer (3 rows).
const formExpanded = ref(false)
const commentEditor = ref<InstanceType<typeof RichTextEditor> | null>(null)

function expandForm() {
  formExpanded.value = true
  void nextTick(() => commentEditor.value?.focus())
}

// Collapse only when the parent signals a successful send (justSent tick).
// Watching commentText for '' would also collapse when the user manually
// clears the draft mid-typing — undesirable.
watch(() => props.justSent, () => {
  formExpanded.value = false
})
</script>

<template>
  <!-- Like row (FR-15) + comment count + sort dropdown -->
  <div class="ainews-like-row">
    <button
      type="button"
      class="ainews-like-btn"
      :class="{ 'is-liked': liked }"
      :disabled="likeSending"
      @click="emit('like')"
    >
      <Heart :size="16" :fill="liked ? 'currentColor' : 'none'" aria-hidden="true" />
      <span>{{ t(liked ? 'aiNews.liked' : 'aiNews.like') }}</span>
      <span class="ainews-like-count">{{ likeCount }}</span>
    </button>
    <span class="ainews-comment-total">
      <MessageCircle :size="15" aria-hidden="true" />
      {{ t('aiNews.commentCount', { n: commentCount }) }}
    </span>
    <!-- Sort dropdown: oldest / newest / likes ↑ / likes ↓ -->
    <div class="ainews-sort-dropdown">
      <button
        type="button"
        class="ainews-sort-btn"
        :aria-expanded="sortMenuOpen"
        @click="sortMenuOpen = !sortMenuOpen"
      >
        <ArrowDownWideNarrow v-if="sortOrder === 'oldest'" :size="14" aria-hidden="true" />
        <ArrowUpNarrowWide v-else-if="sortOrder === 'newest'" :size="14" aria-hidden="true" />
        <Flame v-else-if="sortOrder === 'trending'" :size="14" aria-hidden="true" />
        <ThumbsUp v-else :size="14" aria-hidden="true" />
        <span>{{ sortLabel }}</span>
      </button>
      <div v-if="sortMenuOpen" class="ainews-sort-menu" @click.away="sortMenuOpen = false">
        <button type="button" :class="{ 'is-active': sortOrder === 'oldest' }" @click="sortOrder = 'oldest'; sortMenuOpen = false">{{ t('aiNews.sortOldestFirst') }}</button>
        <button type="button" :class="{ 'is-active': sortOrder === 'newest' }" @click="sortOrder = 'newest'; sortMenuOpen = false">{{ t('aiNews.sortNewestFirst') }}</button>
        <button type="button" :class="{ 'is-active': sortOrder === 'likesDesc' }" @click="sortOrder = 'likesDesc'; sortMenuOpen = false">{{ t('aiNews.sortMostLiked') }}</button>
        <button type="button" :class="{ 'is-active': sortOrder === 'likesAsc' }" @click="sortOrder = 'likesAsc'; sortMenuOpen = false">{{ t('aiNews.sortLeastLiked') }}</button>
        <button type="button" :class="{ 'is-active': sortOrder === 'trending' }" @click="sortOrder = 'trending'; sortMenuOpen = false">{{ t('aiNews.sortTrending') }}</button>
      </div>
    </div>
  </div>

  <hr class="ainews-divider" />

  <!-- Comment section header (drawer only — shows title + count) -->
  <div v-if="_showHeader" class="ainews-comments-header">
    <h3 class="ainews-comments-title">
      <MessageCircle :size="16" aria-hidden="true" />
      {{ t('aiNews.commentCount', { n: commentCount }) }}
    </h3>
  </div>

  <!-- Comment form (drawer — sticky at top, collapsed by default) -->
  <div
    v-if="formPosition !== 'bottom' && !formExpanded && !compact"
    class="ainews-comment-collapsed is-sticky"
    role="button"
    tabindex="0"
    @click="expandForm"
    @keydown.enter="expandForm"
    @keydown.space.prevent="expandForm"
  >
    <MessageCircle :size="15" aria-hidden="true" />
    <span>{{ t('aiNews.writeCommentPh') }}</span>
  </div>
  <div v-if="formPosition !== 'bottom' && formExpanded && !compact" class="ainews-comment-form is-sticky">
    <RichTextEditor
      ref="commentEditor"
      v-model="commentText"
      :placeholder="t('aiNews.writeCommentPh')"
      :rows="3"
      :max-length="1000"
      mention-enabled
      :mention-suggestion="searchMentionUsers"
    />
    <PrismButton
      variant="primary"
      :disabled="!hasCommentText || commentSending || commentOverLimit"
      @click="emit('send')"
    >
      <Send :size="15" aria-hidden="true" /> {{ t('aiNews.send') }}
    </PrismButton>
  </div>

  <div v-if="!topLevel.length" class="ainews-no-comments">
    <p class="ainews-muted">{{ t('aiNews.noComments') }}</p>
    <p class="ainews-muted-sm">{{ t('aiNews.noCommentsDesc') }}</p>
  </div>

  <ul v-else class="ainews-comments" role="list">
    <li v-for="c in topLevel" :key="c.commentId" :id="`ainews-comment-${c.commentId}`" class="ainews-comment">
      <div class="ainews-comment-head">
        <span class="ainews-comment-user">
          {{ c.deleted ? t('aiNews.deletedUser') : c.userName }}
        </span>
        <time class="ainews-comment-date">{{ formatDateTime(c.createdAt) }}</time>
        <span class="ainews-comment-actions">
          <button
            type="button"
            class="ainews-vote-btn"
            :class="{ 'is-voted': c.voted }"
            :disabled="c.own || c.deleted || voteSendingIds.has(c.commentId)"
            :aria-label="t('aiNews.helpful')"
            @click="emit('helpful', c)"
          >
            <ThumbsUp :size="13" aria-hidden="true" /> {{ t('aiNews.helpful') }} ({{ c.helpfulCount }})
          </button>
          <button
            v-if="!c.deleted && !compact"
            type="button"
            class="ainews-link-btn"
            @click="toggleReply(c.commentId)"
          >
            {{ t('aiNews.reply') }}
          </button>
          <button
            v-if="!c.deleted && (c.own || isAdmin)"
            type="button"
            class="ainews-link-btn is-danger"
            :disabled="deletingId === c.commentId"
            @click="emit('delete', c)"
          >
            <Trash2 :size="12" aria-hidden="true" /> {{ t('aiNews.delComment') }}
          </button>
        </span>
      </div>
      <p v-if="c.deleted" class="ainews-tombstone">{{ t('aiNews.deletedComment') }}</p>
      <!-- div (not p): Tiptap content contains block elements (<p>, <img>),
           which are invalid inside a <p> — the browser would auto-close the
           outer <p> and push images out of .ainews-comment-content. -->
      <div v-else class="ainews-comment-content" v-html="sanitizedContent(c.commentId)"></div>

      <!-- Replies (depth 2, indented) — rendered BEFORE the reply form
           so the form appears at the BOTTOM of the thread, not the top. -->
      <ul
        v-if="repliesOf(c.commentId).length"
        class="ainews-replies"
        role="list"
      >
        <li v-for="r in repliesOf(c.commentId)" :key="r.commentId" :id="`ainews-comment-${r.commentId}`" class="ainews-comment is-reply">
          <div class="ainews-comment-head">
            <span class="ainews-comment-user">
              {{ r.deleted ? t('aiNews.deletedUser') : r.userName }}
            </span>
            <time class="ainews-comment-date">{{ formatDateTime(r.createdAt) }}</time>
            <span class="ainews-comment-actions">
              <button
                type="button"
                class="ainews-vote-btn"
                :class="{ 'is-voted': r.voted }"
                :disabled="r.own || r.deleted || voteSendingIds.has(r.commentId)"
                :aria-label="t('aiNews.helpful')"
                @click="emit('helpful', r)"
              >
                <ThumbsUp :size="13" aria-hidden="true" /> {{ t('aiNews.helpful') }} ({{ r.helpfulCount }})
              </button>
              <button
                v-if="!r.deleted && !compact"
                type="button"
                class="ainews-link-btn"
                @click="toggleReply(r.commentId)"
              >
                {{ t('aiNews.reply') }}
              </button>
              <button
                v-if="!r.deleted && (r.own || isAdmin)"
                type="button"
                class="ainews-link-btn is-danger"
                :disabled="deletingId === r.commentId"
                @click="emit('delete', r)"
              >
                <Trash2 :size="12" aria-hidden="true" /> {{ t('aiNews.delComment') }}
              </button>
            </span>
          </div>
          <p v-if="r.deleted" class="ainews-tombstone">{{ t('aiNews.deletedComment') }}</p>
          <div v-else class="ainews-comment-content" v-html="sanitizedContent(r.commentId)"></div>

          <!-- Depth-3 replies (FR-17 v1.21: nested depth 3, no Reply button
               at level 3 — the depth cap). Hidden when the depth-2 parent is
               deleted (matches the backend: a reply under a deleted parent
               is not returned). -->
          <ul
            v-if="!r.deleted && repliesOf(r.commentId).length"
            class="ainews-replies"
            role="list"
          >
            <li v-for="d in repliesOf(r.commentId)" :key="d.commentId" :id="`ainews-comment-${d.commentId}`" class="ainews-comment is-reply">
              <div class="ainews-comment-head">
                <span class="ainews-comment-user">
                  {{ d.deleted ? t('aiNews.deletedUser') : d.userName }}
                </span>
                <time class="ainews-comment-date">{{ formatDateTime(d.createdAt) }}</time>
                <span class="ainews-comment-actions">
                  <button
                    type="button"
                    class="ainews-vote-btn"
                    :class="{ 'is-voted': d.voted }"
                    :disabled="d.own || d.deleted || voteSendingIds.has(d.commentId)"
                    :aria-label="t('aiNews.helpful')"
                    @click="emit('helpful', d)"
                  >
                    <ThumbsUp :size="13" aria-hidden="true" /> {{ t('aiNews.helpful') }} ({{ d.helpfulCount }})
                  </button>
                  <button
                    v-if="!d.deleted && (d.own || isAdmin)"
                    type="button"
                    class="ainews-link-btn is-danger"
                    :disabled="deletingId === d.commentId"
                    @click="emit('delete', d)"
                  >
                    <Trash2 :size="12" aria-hidden="true" /> {{ t('aiNews.delComment') }}
                  </button>
                </span>
              </div>
              <p v-if="d.deleted" class="ainews-tombstone">{{ t('aiNews.deletedComment') }}</p>
              <div v-else class="ainews-comment-content" v-html="sanitizedContent(d.commentId)"></div>
            </li>
          </ul>

          <!-- Inline reply form for a depth-2 comment (FR-17 v1.21) -->
          <div v-if="replyTo === r.commentId && !r.deleted && !compact" class="ainews-reply-form">
            <RichTextEditor
              ref="replyEditor"
              v-model="replyText"
              :placeholder="t('aiNews.writeReplyPh')"
              :rows="2"
              :max-length="1000"
              mention-enabled
              :mention-suggestion="searchMentionUsers"
            />
            <PrismButton
              variant="secondary"
              :disabled="!hasReplyText || replySending || replyOverLimit"
              @click="emit('reply', r.commentId)"
            >
              {{ t('aiNews.send') }}
            </PrismButton>
          </div>
        </li>
      </ul>

      <!-- Inline reply form (FR-17) — at the BOTTOM of the thread -->
      <div v-if="replyTo === c.commentId && !c.deleted && !compact" class="ainews-reply-form">
        <RichTextEditor
          ref="replyEditor"
          v-model="replyText"
          :placeholder="t('aiNews.writeReplyPh')"
          :rows="2"
          :max-length="1000"
          mention-enabled
          :mention-suggestion="searchMentionUsers"
        />
        <PrismButton
          variant="secondary"
          :disabled="!hasReplyText || replySending || replyOverLimit"
          @click="emit('reply', c.commentId)"
        >
          {{ t('aiNews.send') }}
        </PrismButton>
      </div>
    </li>
  </ul>

  <!-- Comment form at bottom (main pane). Collapsed = 1-line placeholder;
       click → expand to a full 4-row editor. Collapses after send. -->
  <div
    v-if="formPosition === 'bottom' && !formExpanded"
    class="ainews-comment-collapsed"
    role="button"
    tabindex="0"
    @click="expandForm"
    @keydown.enter="expandForm"
    @keydown.space.prevent="expandForm"
  >
    <MessageCircle :size="15" aria-hidden="true" />
    <span>{{ t('aiNews.writeCommentPh') }}</span>
  </div>
  <div v-if="formPosition === 'bottom' && formExpanded" class="ainews-comment-form">
    <RichTextEditor
      ref="commentEditor"
      v-model="commentText"
      :placeholder="t('aiNews.writeCommentPh')"
      :rows="4"
      :max-length="1000"
      mention-enabled
      :mention-suggestion="searchMentionUsers"
    />
    <PrismButton
      variant="primary"
      :disabled="!hasCommentText || commentSending || commentOverLimit"
      @click="emit('send')"
    >
      <Send :size="15" aria-hidden="true" /> {{ t('aiNews.send') }}
    </PrismButton>
  </div>
</template>

<style scoped>
/* Like row (FR-15) — borderless, hover bg (demo) */
.ainews-like-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}
/* Comment count — pushed to the far right of the like row */
.ainews-comment-total {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  white-space: nowrap;
}
.ainews-like-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  cursor: pointer;
  background: none;
  border: 0;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background .15s ease, color .15s ease;
}
.ainews-like-btn:hover {
  background: rgb(154 157 166 / .12);
}
.ainews-like-btn.is-liked {
  color: var(--ds-color-accent);
}
.ainews-like-btn:disabled {
  opacity: .5;
  cursor: not-allowed;
}
.ainews-like-count {
  font-family: var(--ds-font-mono);
  font-size: 12px;
}

.ainews-divider {
  border: none;
  border-top: 1px solid rgb(154 157 166 / .1);
  margin: var(--ds-space-1) 0;
}

/* Comments (FR-16..FR-20) — boxed items (demo) */
.ainews-comments-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 0 8px;
}
.ainews-comments-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
/* Sort toggle — sits right of the comment count */
.ainews-sort-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid rgb(154 157 166 / .2);
  border-radius: 999px;
  background: none;
  color: var(--ds-color-text-secondary);
  font-size: 11px;
  cursor: pointer;
  transition: background .15s ease, color .15s ease;
}
.ainews-sort-btn:hover {
  background: rgb(154 157 166 / .12);
  color: var(--ds-color-text-primary);
}
/* Sort dropdown */
.ainews-sort-dropdown {
  position: relative;
}
.ainews-sort-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  min-width: 140px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: rgb(9 20 45 / .98);
  box-shadow: 0 6px 20px rgb(0 0 0 / .4);
}
.ainews-sort-menu button {
  display: block;
  width: 100%;
  padding: 6px 10px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ds-color-text-secondary);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
}
.ainews-sort-menu button:hover {
  background: rgb(154 157 166 / .12);
  color: var(--ds-color-text-primary);
}
.ainews-sort-menu button.is-active {
  color: var(--ds-color-accent);
  font-weight: 600;
}
.ainews-comment-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  margin-top: 12px;
}
/* Drawer: keep the form visible while scrolling through comments.
   position: sticky works here because the drawer's .p-dialog-body is
   overridden to display:block (not grid) with a single scroll container. */
.ainews-comment-form.is-sticky,
.ainews-comment-collapsed.is-sticky {
  position: sticky;
  top: 0;
  z-index: 10;
  /* Solid-ish background (no backdrop-filter — blur is GPU-expensive
     and causes scroll jank when the sticky element repaints every frame). */
  background: rgb(9 20 45 / .98);
  padding: 10px 12px;
  margin-top: 0;
  margin-bottom: 12px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  /* Prevent the sticky element from triggering layout recalculation
     in the scroll container's content area. */
  contain: layout style;
}
.ainews-comment-form.is-sticky {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
/* Main pane: collapsed 1-line placeholder */
.ainews-comment-collapsed {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: rgb(9 20 45 / .25);
  color: var(--ds-color-text-muted);
  font-size: 13px;
  cursor: text;
  margin-top: 12px;
  transition: border-color .15s ease, background .15s ease;
}
.ainews-comment-collapsed:hover {
  border-color: var(--ds-color-border-strong);
  background: rgb(9 20 45 / .35);
}
.ainews-form-label {
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
/* FR-23: RichTextEditor occupies the full form width */
.ainews-comment-form .rte,
.ainews-reply-form .rte {
  width: 100%;
}
/* Send button right-aligned (demo .send-row { justify-content: flex-end }) */
.ainews-comment-form .p-button {
  align-self: flex-end;
}

.ainews-no-comments {
  padding: var(--ds-space-3) 0;
}
.ainews-muted {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-muted);
  padding: 12px 0;
}
.ainews-muted-sm {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--ds-color-text-muted);
}

.ainews-comments,
.ainews-replies {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  /* Không giới hạn chiều cao riêng — comments cuộn cùng vùng chứa (detail
     scroll hoặc drawer), luôn nằm ở dưới cùng của nội dung news. */
}
/* Ẩn thanh scroll nhưng vẫn scroll được (comments) */
.ainews-comments {
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge legacy */
}
.ainews-comments::-webkit-scrollbar {
  display: none; /* Chrome/Safari */
}
.ainews-replies {
  margin-left: 24px;
  padding-left: 10px;
  border-left: 2px solid rgb(154 157 166 / .15);
  gap: 6px;
  max-height: none;
  overflow: visible;
}
/* Depth-3 nesting: lighter indent than depth-2 so narrow panes don't
   push the third level off-screen. */
.ainews-replies .ainews-replies {
  margin-left: 18px;
  padding-left: 8px;
}
.ainews-comment {
  padding: 10px 12px;
  border: 1px solid rgb(154 157 166 / .12);
  border-radius: var(--ds-radius-sm);
  background: rgb(9 20 45 / .25);
}
.ainews-comment.is-reply {
  background: rgb(9 20 45 / .15);
}
.ainews-comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  flex-wrap: wrap;
}
.ainews-comment-user {
  font-size: 12px;
  font-weight: 600;
  color: var(--ds-color-text-primary);
}
.ainews-comment-date {
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.ainews-comment-actions {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ainews-vote-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 4px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ds-color-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.ainews-vote-btn:hover:not(:disabled) {
  background: rgb(154 157 166 / .12);
}
.ainews-vote-btn.is-voted {
  color: var(--ds-color-accent);
}
.ainews-vote-btn:disabled {
  opacity: .4;
  cursor: not-allowed;
}
.ainews-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 4px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ds-color-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.ainews-link-btn:hover {
  background: rgb(154 157 166 / .12);
}
.ainews-link-btn.is-danger {
  color: var(--ds-color-danger);
}
.ainews-link-btn.is-danger:hover {
  color: var(--ds-color-danger);
}
.ainews-comment-content {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  word-break: break-word;
}
/* FR-23: rendered rich text (sanitized HTML from Tiptap) */
.ainews-comment-content :deep(p) {
  margin: 0 0 4px;
  white-space: pre-wrap;
}
.ainews-comment-content :deep(p:last-child) {
  margin-bottom: 0;
}
.ainews-comment-content :deep(a) {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.ainews-comment-content :deep(img) {
  max-width: 100%;
  max-height: 420px;
  object-fit: contain;
  height: auto;
  border-radius: var(--ds-radius-sm);
}
.ainews-comment-content :deep(ul),
.ainews-comment-content :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}
.ainews-comment-content :deep(pre) {
  margin: 4px 0;
  padding: 6px 10px;
  background: rgb(9 20 45 / .35);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  font-family: var(--ds-font-mono);
  font-size: 12px;
  overflow-x: auto;
}
.ainews-comment-content :deep(blockquote) {
  margin: 4px 0;
  padding-left: 10px;
  border-left: 3px solid var(--ds-color-border-strong);
  color: var(--ds-color-text-muted);
}
.ainews-comment-content :deep(table) {
  max-width: 100%;
  border-collapse: collapse;
  margin: 4px 0;
}
.ainews-comment-content :deep(th),
.ainews-comment-content :deep(td) {
  border: 1px solid var(--ds-color-border);
  padding: 3px 8px;
  font-size: 12px;
}
.ainews-comment-content :deep(mark) {
  background: rgb(250 204 21 / .35);
  border-radius: 2px;
}
.ainews-comment-content :deep(.mention),
.ainews-comment-content :deep(span[data-type="mention"]) {
  color: var(--ds-color-accent);
  background: rgb(94 106 210 / .12);
  border-radius: 3px;
  padding: 0 3px;
  font-weight: 500;
  white-space: nowrap;
}
.ainews-tombstone {
  margin: 6px 0 0;
  font-size: 13px;
  font-style: italic;
  color: var(--ds-color-text-muted);
}
.ainews-comment:has(.ainews-tombstone) {
  opacity: .65;
  border-style: dashed;
}
.ainews-comment:has(.ainews-tombstone) .ainews-comment-user {
  color: var(--ds-color-text-muted);
  font-style: italic;
}
.ainews-reply-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  margin-top: var(--ds-space-2);
  /* demo .reply-form: indented under the parent comment */
  margin-left: 24px;
  padding-left: 10px;
}
/* demo .reply-form .send-row { justify-content: flex-end } */
.ainews-reply-form .p-button {
  align-self: flex-end;
}
/* Bell deep-link (?comment=): flash highlight after scrollIntoView.
   Added/removed by JS (AiNewsView.scrollToPendingComment). */
.ainews-comment.is-flash {
  animation: ainews-comment-flash 2s ease-out;
}
@keyframes ainews-comment-flash {
  0% {
    box-shadow: 0 0 0 2px var(--ds-color-accent);
    background: rgb(94 106 210 / .18);
  }
  100% {
    box-shadow: 0 0 0 2px transparent;
  }
}
</style>
