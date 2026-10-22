<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, Heart, Mail, MessageCircle, Send, ThumbsUp, Trash2 } from 'lucide-vue-next'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismCard from '@/design-system/components/PrismCard.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import PrismSkeleton from '@/design-system/components/PrismSkeleton.vue'
import { aiNewsApi } from '@/features/ai-news/api'
import { formatDate, loadAiNewsPrefs, t } from '@/features/ai-news/i18n'
import { toUserError } from '@/lib/api'
import { toast } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import type { AiNewsCommentItem, AiNewsDetail, AiNewsItem } from '@/features/ai-news/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// ── List pane ───────────────────────────────────────────────────────────────
const listLoading = ref(true)
const listError = ref('')
const items = ref<AiNewsItem[]>([])
const selectedId = ref<number | null>(null)

const DOT_TONES = ['accent', 'violet', 'success', 'warning', 'teal', 'rose']

async function loadList() {
  listLoading.value = true
  listError.value = ''
  try {
    items.value = await aiNewsApi.list(50)
    // FR-03: ?news={newsId} → select it (if in list); otherwise the newest.
    const queryId = Number(route.query.news)
    const wanted = Number.isFinite(queryId) && queryId > 0 ? queryId : null
    const exists = wanted != null && items.value.some((n) => n.newsId === wanted)
    selectedId.value = exists ? wanted : (items.value[0]?.newsId ?? null)
    syncHash()
  } catch (error) {
    listError.value = toUserError(error).message
  } finally {
    listLoading.value = false
  }
}

function syncHash() {
  if (selectedId.value == null) return
  const target = `/ai-news?news=${selectedId.value}`
  if (route.fullPath !== target) {
    router.replace({ path: '/ai-news', query: { news: String(selectedId.value) } })
  }
}

function selectItem(newsId: number) {
  if (newsId === selectedId.value) return
  selectedId.value = newsId
  syncHash()
}

// FR-04: title + meta render immediately from the list data — the detail
// fetch only supplies the body (and fresh like state).
const selectedItem = computed(
  () => items.value.find((n) => n.newsId === selectedId.value) ?? null,
)
const headerTitle = computed(() => detail.value?.title ?? selectedItem.value?.title ?? '')
const headerMeta = computed(() => {
  const d = detail.value
  const s = selectedItem.value
  const sender = d ? d.sender ?? d.source : s ? s.sender ?? s.source : ''
  const createdAt = d?.createdAt ?? s?.createdAt ?? null
  return { sender, createdAt }
})

// FR-05: selecting a different item aborts the in-flight detail fetch.
let detailAbort: AbortController | null = null

// ── Detail pane ─────────────────────────────────────────────────────────────
const detailLoading = ref(false)
const detailState = ref<'idle' | 'loading' | 'error' | 'notfound' | 'empty' | 'ok'>('idle')
const detail = ref<AiNewsDetail | null>(null)

async function loadDetail() {
  const id = selectedId.value
  if (id == null) {
    detailState.value = 'idle'
    detail.value = null
    return
  }
  detailAbort?.abort()
  const controller = new AbortController()
  detailAbort = controller
  detailLoading.value = true
  detailState.value = 'loading'
  detail.value = null
  try {
    const data = await aiNewsApi.detail(id, controller.signal)
    if (controller.signal.aborted) return
    detail.value = data
    detailState.value = data.body ? 'ok' : 'empty'
    loadComments()
  } catch (error) {
    if (controller.signal.aborted) return
    const code = (error as { code?: string })?.code
    detailState.value = code === 'NEWS_NOT_FOUND' ? 'notfound' : 'error'
  } finally {
    if (!controller.signal.aborted) detailLoading.value = false
  }
}

// ── Like (FR-15 — silent, no toast) ────────────────────────────────────────
const likeSending = ref(false)
async function onLike() {
  const id = selectedId.value
  if (id == null || !detail.value || likeSending.value) return
  likeSending.value = true
  const prev = detail.value
  try {
    const { likeCount } = await aiNewsApi.toggleLike(id)
    if (selectedId.value === id && detail.value) {
      detail.value = { ...detail.value, likeCount, liked: !prev.liked }
    }
  } catch {
    /* silent toggle — keep previous state on failure */
  } finally {
    likeSending.value = false
  }
}

// ── Comments (FR-16..FR-20) ─────────────────────────────────────────────────
const comments = ref<AiNewsCommentItem[]>([])
const commentText = ref('')
const commentSending = ref(false)
const replyTo = ref<number | null>(null)
const replyText = ref('')
const replySending = ref(false)
const deletingId = ref<number | null>(null)

// Monotonic counter to discard stale comment responses when the user
// switches news items while a fetch is in flight (race condition guard).
let commentsReqId = 0

async function loadComments() {
  const id = selectedId.value
  if (id == null) return
  const reqId = ++commentsReqId
  try {
    const data = await aiNewsApi.comments(id)
    if (reqId !== commentsReqId) return // superseded by a newer selection
    comments.value = data
  } catch {
    if (reqId !== commentsReqId) return
    comments.value = []
  }
}

const commentCounterWarn = computed(() => commentText.value.length >= 900)

async function sendComment() {
  const id = selectedId.value
  if (id == null || commentSending.value) return
  if (!commentText.value.trim()) return
  commentSending.value = true
  try {
    await aiNewsApi.addComment(id, commentText.value)
    commentText.value = ''
    toast({ title: t('aiNews.commentSent'), tone: 'success' })
    await loadComments()
  } catch (error) {
    toast({ title: toUserError(error).message, tone: 'danger' })
  } finally {
    commentSending.value = false
  }
}

async function sendReply(parentId: number) {
  const id = selectedId.value
  if (id == null || replySending.value) return
  if (!replyText.value.trim()) return
  // FR-17: replying to a depth-2 comment is flattened to the thread root by
  // the backend — the user gets the "flattened" info toast instead of the
  // normal "reply sent" toast.
  const parent = comments.value.find((c) => c.commentId === parentId)
  const willFlatten = parent?.parentId != null
  replySending.value = true
  try {
    await aiNewsApi.addComment(id, replyText.value, parentId)
    replyText.value = ''
    replyTo.value = null
    toast({ title: t(willFlatten ? 'aiNews.replyFlattened' : 'aiNews.replySent'), tone: 'info' })
    await loadComments()
  } catch (error) {
    toast({ title: toUserError(error).message, tone: 'danger' })
  } finally {
    replySending.value = false
  }
}

const voteSendingIds = ref<Set<number>>(new Set())
async function onHelpful(comment: AiNewsCommentItem) {
  const id = selectedId.value
  if (id == null || voteSendingIds.value.has(comment.commentId)) return
  if (comment.own) {
    toast({ title: t('aiNews.cannotVoteOwn'), tone: 'info' })
    return
  }
  voteSendingIds.value = new Set(voteSendingIds.value).add(comment.commentId)
  try {
    const { helpfulCount } = await aiNewsApi.toggleHelpful(id, comment.commentId)
    const wasVoted = comment.voted
    // Only apply the result if the user is still on the same news item —
    // otherwise the vote would be applied to a different item's comments.
    if (selectedId.value === id) {
      comments.value = comments.value.map((c) =>
        c.commentId === comment.commentId
          ? { ...c, helpfulCount, voted: !wasVoted }
          : c,
      )
      toast({ title: t(wasVoted ? 'aiNews.voteRemoved' : 'aiNews.voteRegistered'), tone: 'info' })
    }
  } catch (error) {
    if (selectedId.value === id) {
      toast({ title: toUserError(error).message, tone: 'danger' })
    }
  } finally {
    const next = new Set(voteSendingIds.value)
    next.delete(comment.commentId)
    voteSendingIds.value = next
  }
}

async function onDelete(comment: AiNewsCommentItem) {
  const id = selectedId.value
  if (id == null) return
  if (!window.confirm(t('aiNews.delCommentDesc'))) return
  deletingId.value = comment.commentId
  try {
    await aiNewsApi.deleteComment(id, comment.commentId)
    toast({ title: t('aiNews.commentDeleted'), tone: 'success' })
    await loadComments()
  } catch (error) {
    toast({ title: toUserError(error).message, tone: 'danger' })
  } finally {
    deletingId.value = null
  }
}

// Threaded structure: top-level list + replies grouped by parent (FR-17).
const topLevel = computed(() => comments.value.filter((c) => c.parentId === null))
const repliesOf = (commentId: number) => comments.value.filter((c) => c.parentId === commentId)
const commentCount = computed(() => comments.value.filter((c) => !c.deleted).length)

// FR-07: links inside the email body open in a new tab (target=_blank rel=noopener).
function onBodyLinkClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement).closest('a')
  if (!anchor) return
  const href = anchor.getAttribute('href')
  if (!href || href.startsWith('#') || href.startsWith('javascript:')) return
  event.preventDefault()
  if (href.startsWith('mailto:')) {
    // mailto: links must open the mail client, not a new tab.
    window.location.href = href
    return
  }
  window.open(href, '_blank', 'noopener,noreferrer')
}

// ── Lifecycle ───────────────────────────────────────────────────────────────
watch(selectedId, () => {
  // Reset comment state so the previous item's comments/reply form never
  // leak into the newly selected item while its data is loading.
  comments.value = []
  commentText.value = ''
  replyTo.value = null
  replyText.value = ''
  loadDetail()
})

// Back/forward + manual URL edits: keep the selection in sync with
// ?news={newsId} (ai-news_sumary.md §2). selectItem no-ops when the id
// equals the current selection, so this cannot fight the selectedId watcher.
watch(
  () => route.query.news,
  (raw) => {
    if (typeof raw !== 'string') return
    const id = Number(raw)
    if (Number.isFinite(id) && id > 0 && id !== selectedId.value) {
      selectItem(id)
    }
  },
)

onMounted(async () => {
  // Prefs load in the background — the list renders with the cached/default
  // language and re-renders when the server prefs arrive.
  loadAiNewsPrefs()
  await loadList()
  // loadDetail is triggered by the selectedId watcher when loadList sets
  // the initial selection — no explicit call needed (would double-fetch).
})

onBeforeUnmount(() => {
  detailAbort?.abort()
})
</script>

<template>
  <div class="ainews">
    <!-- Page header (AI_News_Screen_SRS.md §2.5) -->
    <div class="ainews-page-header">
      <h1 class="ainews-page-title">📰 {{ t('app.aiNews') }}</h1>
      <p class="ainews-page-sub">{{ t('aiNews.subtitle') }}</p>
    </div>
    <div class="ainews-grid">
      <!-- List pane -->
      <PrismCard class="ainews-list-card">
        <template #header>
          <h3>📰 {{ t('app.aiNews') }}</h3>
        </template>
        <div v-if="listLoading" class="ai-news-skeleton" aria-busy="true">
          <div v-for="i in 5" :key="i" class="ai-news-skeleton-row">
            <PrismSkeleton height="14px" width="70%" />
            <PrismSkeleton height="12px" width="45%" />
          </div>
        </div>
        <PrismAlert v-else-if="listError" tone="warning" class="ainews-list-error">
          {{ listError }}
          <template #actions>
            <PrismButton variant="secondary" @click="loadList">{{ t('aiNews.retry') }}</PrismButton>
          </template>
        </PrismAlert>
        <PrismEmptyState
          v-else-if="!items.length"
          :title="t('aiNews.listEmpty')"
        />
        <ul v-else class="ai-news-list" role="list">
          <li
            v-for="(item, idx) in items"
            :key="item.newsId"
            class="ai-news-item"
            :class="[`ai-news--${DOT_TONES[idx % DOT_TONES.length]}`, { selected: item.newsId === selectedId }]"
            :aria-current="item.newsId === selectedId ? 'true' : undefined"
            role="button"
            tabindex="0"
            @click="selectItem(item.newsId)"
            @keydown.enter="selectItem(item.newsId)"
            @keydown.space.prevent="selectItem(item.newsId)"
          >
            <span class="ai-news-dot" aria-hidden="true"></span>
            <div class="ai-news-body">
              <span class="ai-news-title" :title="item.title">{{ item.title }}</span>
              <p class="ai-news-desc">
                {{ item.sender ?? item.source }} · {{ formatDate(item.createdAt) }}
              </p>
            </div>
          </li>
        </ul>
      </PrismCard>

      <!-- Detail pane -->
      <PrismCard class="ainews-detail-card" :aria-busy="detailLoading || undefined">
        <template #header>
          <h3 class="ainews-detail-title" :title="headerTitle">
            {{ headerTitle || t('aiNews.selectNews') }}
          </h3>
        </template>
        <template v-if="selectedId == null">
          <PrismEmptyState
            :title="t('aiNews.selectNews')"
            :description="t('aiNews.selectNewsDesc')"
          />
        </template>

        <template v-else>
          <div v-if="headerMeta.sender" class="ainews-meta-row">
            <span class="ainews-sender">
              <Mail :size="14" aria-hidden="true" />
              {{ headerMeta.sender }}
            </span>
            <time class="ainews-date"><Calendar :size="13" aria-hidden="true" />{{ formatDate(headerMeta.createdAt) }}</time>
          </div>

          <!-- Body states (FR-08..FR-11) -->
          <div v-if="detailState === 'loading'" class="ainews-body-state">
            <PrismSkeleton height="14px" width="60%" />
            <p class="ainews-muted">{{ t('aiNews.loading') }}</p>
          </div>
          <PrismAlert v-else-if="detailState === 'error'" tone="warning">
            {{ t('aiNews.loadFail') }}
          </PrismAlert>
          <PrismAlert v-else-if="detailState === 'notfound'" tone="warning">
            {{ t('aiNews.notFound') }}
          </PrismAlert>
          <PrismAlert v-else-if="detailState === 'empty'" tone="info">
            {{ t('aiNews.noBody') }}
          </PrismAlert>
          <div
            v-else-if="detailState === 'ok' && detail"
            class="ainews-body"
            v-html="detail.body"
            @click="onBodyLinkClick"
          ></div>

          <!-- Like + comments (FR-15..FR-20) — shown when the detail loaded
               (both body-ok and empty-body states: FR-12 — like/comment are
               available regardless of body content) -->
          <template v-if="detail && (detailState === 'ok' || detailState === 'empty')">
            <!-- Like row (FR-15 — silent) -->
            <div class="ainews-like-row">
              <button
                type="button"
                class="ainews-like-btn"
                :class="{ 'is-liked': detail.liked }"
                :disabled="likeSending"
                @click="onLike"
              >
                <Heart :size="16" :fill="detail.liked ? 'currentColor' : 'none'" aria-hidden="true" />
                <span>{{ t(detail.liked ? 'aiNews.liked' : 'aiNews.like') }}</span>
                <span class="ainews-like-count">{{ detail.likeCount }}</span>
              </button>
            </div>

            <hr class="ainews-divider" />

            <!-- Comment section (FR-16..FR-20) -->
            <h3 class="ainews-comments-title">
              <MessageCircle :size="16" aria-hidden="true" />
              {{ t('aiNews.commentCount', { n: commentCount }) }}
            </h3>

            <div class="ainews-comment-form">
              <label class="ainews-form-label" for="ainews-comment">{{ t('aiNews.writeComment') }}</label>
              <textarea
                id="ainews-comment"
                v-model="commentText"
                class="p-control ainews-comment-input"
                rows="3"
                maxlength="1000"
                :placeholder="t('aiNews.writeCommentPh')"
              ></textarea>
              <small class="ainews-counter" :class="{ 'is-warn': commentCounterWarn }">
                {{ commentText.length }} / 1000
              </small>
              <PrismButton
                variant="primary"
                :disabled="!commentText.trim() || commentSending"
                @click="sendComment"
              >
                <Send :size="15" aria-hidden="true" /> {{ t('aiNews.send') }}
              </PrismButton>
            </div>

            <div v-if="!topLevel.length" class="ainews-no-comments">
              <p class="ainews-muted">{{ t('aiNews.noComments') }}</p>
              <p class="ainews-muted-sm">{{ t('aiNews.noCommentsDesc') }}</p>
            </div>

            <ul v-else class="ainews-comments" role="list">
              <li v-for="c in topLevel" :key="c.commentId" class="ainews-comment">
                <div class="ainews-comment-head">
                  <span class="ainews-comment-user">
                    {{ c.deleted ? t('aiNews.deletedUser') : c.userName }}
                  </span>
                  <time class="ainews-comment-date">{{ formatDate(c.createdAt) }}</time>
                  <span class="ainews-comment-actions">
                    <button
                      type="button"
                      class="ainews-vote-btn"
                      :class="{ 'is-voted': c.voted }"
                      :disabled="c.own || c.deleted || voteSendingIds.has(c.commentId)"
                      :aria-label="t('aiNews.helpful')"
                      @click="onHelpful(c)"
                    >
                      <ThumbsUp :size="13" aria-hidden="true" /> {{ t('aiNews.helpful') }} ({{ c.helpfulCount }})
                    </button>
                    <button
                      v-if="!c.deleted"
                      type="button"
                      class="ainews-link-btn"
                      @click="replyTo = replyTo === c.commentId ? null : c.commentId; replyText = ''"
                    >
                      {{ t('aiNews.reply') }}
                    </button>
                    <button
                      v-if="!c.deleted && (c.own || auth.isAdmin)"
                      type="button"
                      class="ainews-link-btn is-danger"
                      :disabled="deletingId === c.commentId"
                      @click="onDelete(c)"
                    >
                      <Trash2 :size="12" aria-hidden="true" /> {{ t('aiNews.delComment') }}
                    </button>
                  </span>
                </div>
                <p v-if="c.deleted" class="ainews-tombstone">{{ t('aiNews.deletedComment') }}</p>
                <p v-else class="ainews-comment-content">{{ c.content }}</p>

                <!-- Inline reply form (FR-17) -->
                <div v-if="replyTo === c.commentId && !c.deleted" class="ainews-reply-form">
                  <textarea
                    v-model="replyText"
                    class="p-control ainews-comment-input"
                    rows="2"
                    maxlength="1000"
                    :placeholder="t('aiNews.writeReplyPh')"
                    @keyup.enter.exact.prevent="sendReply(c.commentId)"
                  ></textarea>
                  <PrismButton
                    variant="secondary"
                    :disabled="!replyText.trim() || replySending"
                    @click="sendReply(c.commentId)"
                  >
                    {{ t('aiNews.send') }}
                  </PrismButton>
                </div>

                <!-- Replies (depth 2, indented, ASC) -->
                <ul
                  v-if="repliesOf(c.commentId).length"
                  class="ainews-replies"
                  role="list"
                >
                  <li v-for="r in repliesOf(c.commentId)" :key="r.commentId" class="ainews-comment is-reply">
                    <div class="ainews-comment-head">
                      <span class="ainews-comment-user">
                        {{ r.deleted ? t('aiNews.deletedUser') : r.userName }}
                      </span>
                      <time class="ainews-comment-date">{{ formatDate(r.createdAt) }}</time>
                      <span class="ainews-comment-actions">
                        <button
                          type="button"
                          class="ainews-vote-btn"
                          :class="{ 'is-voted': r.voted }"
                          :disabled="r.own || r.deleted || voteSendingIds.has(r.commentId)"
                          :aria-label="t('aiNews.helpful')"
                          @click="onHelpful(r)"
                        >
                          <ThumbsUp :size="13" aria-hidden="true" /> {{ t('aiNews.helpful') }} ({{ r.helpfulCount }})
                        </button>
                        <button
                          v-if="!r.deleted && (r.own || auth.isAdmin)"
                          type="button"
                          class="ainews-link-btn is-danger"
                          :disabled="deletingId === r.commentId"
                          @click="onDelete(r)"
                        >
                          <Trash2 :size="12" aria-hidden="true" /> {{ t('aiNews.delComment') }}
                        </button>
                      </span>
                    </div>
                    <p v-if="r.deleted" class="ainews-tombstone">{{ t('aiNews.deletedComment') }}</p>
                    <p v-else class="ainews-comment-content">{{ r.content }}</p>
                  </li>
                </ul>
              </li>
            </ul>
          </template>
        </template>
      </PrismCard>
    </div>
  </div>
</template>

<style scoped>
.ainews {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-4);
}

/* Page header (AI_News_Screen_SRS.md §2.5) */
.ainews-page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.ainews-page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--ds-color-text-primary);
}
.ainews-page-sub {
  margin: 0;
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.ainews-list-error {
  margin: 12px 16px;
}

/* 2-pane layout (NFR-05) */
.ainews-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
@media (max-width: 999px) {
  .ainews-grid {
    grid-template-columns: 1fr;
  }
}

/* ── List pane — timeline (demo style) ───────────────────────────────────── */
.ainews-list-card {
  padding: 0;
  overflow: hidden;
}
.ainews-list-card :deep(header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid rgb(154 182 219 / .1);
}
.ainews-list-card :deep(h3) {
  margin: 0;
  font-size: 15px;
}
.ainews-list-card .ai-news-list {
  list-style: none;
  margin: 0;
  padding: 0 16px;
  display: block;
}
.ai-news-item {
  position: relative;
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: var(--ds-space-3);
  align-items: start;
  padding-bottom: var(--ds-space-5);
  cursor: pointer;
}
.ai-news-item:last-child {
  padding-bottom: 0;
}
.ai-news-item::before {
  content: '';
  position: absolute;
  left: 24px;
  top: 18px;
  bottom: 0;
  width: 2px;
  background: var(--ds-color-border);
}
.ai-news-item:last-child::before {
  display: none;
}
.ai-news-item::after {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  position: absolute;
  left: 22px;
  top: 6px;
}
.ai-news-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--ds-color-border-strong);
  background: var(--ds-color-space-900);
  display: grid;
  place-items: center;
}
.ai-news--accent .ai-news-dot { border-color: var(--ds-color-accent); box-shadow: 0 0 8px rgb(103 232 249 / .4); }
.ai-news--accent::after { background: var(--ds-color-accent); }
.ai-news--violet .ai-news-dot { border-color: var(--ds-color-violet-400); box-shadow: 0 0 8px rgb(165 138 239 / .35); }
.ai-news--violet::after { background: var(--ds-color-violet-400); }
.ai-news--success .ai-news-dot { border-color: var(--ds-color-success); box-shadow: 0 0 8px rgb(156 232 208 / .3); }
.ai-news--success::after { background: var(--ds-color-success); }
.ai-news--warning .ai-news-dot { border-color: var(--ds-color-warning); box-shadow: 0 0 8px rgb(243 210 140 / .3); }
.ai-news--warning::after { background: var(--ds-color-warning); }
.ai-news--teal .ai-news-dot { border-color: var(--ds-color-teal-400); box-shadow: 0 0 8px rgb(73 220 207 / .3); }
.ai-news--teal::after { background: var(--ds-color-teal-400); }
.ai-news--rose .ai-news-dot { border-color: var(--ds-color-rose-300); box-shadow: 0 0 8px rgb(255 172 196 / .3); }
.ai-news--rose::after { background: var(--ds-color-rose-300); }
.ai-news-body {
  min-width: 0;
}
.ai-news-title {
  display: block;
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--ds-color-text-primary);
  cursor: pointer;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-news-title:hover {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.ai-news-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  line-height: 1.5;
}
/* Item đang được chọn (highlight) */
.ai-news-item.selected .ai-news-title {
  color: var(--ds-color-accent);
}
.ai-news-item.selected .ai-news-dot {
  box-shadow: 0 0 8px rgb(103 232 249 / .4);
}
.ai-news-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-2);
  padding: var(--ds-space-3) 16px;
}
.ai-news-skeleton-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ── Detail pane (demo: PrismCard) ───────────────────────────────────────── */
.ainews-detail-card {
  min-width: 0;
}
.ainews-detail-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--ds-color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ainews-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  margin-bottom: 10px;
}
.ainews-sender {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ds-color-accent);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ainews-date {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--ds-font-mono);
  font-size: 12px;
  color: var(--ds-color-text-muted);
  flex-shrink: 0;
}

/* Body container (FR-07): slate background, scroll, max-height */
.ainews-body {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: #64748b; /* slate — email bg-colors forced transparent */
  color: #fff;
  font-size: 14px;
  line-height: 1.65;
  padding: 20px 22px;
  overflow-y: auto;
  max-height: calc(100svh - 320px);
  word-break: break-word;
}
.ainews-body :deep(*) {
  background-color: transparent !important;
  /* background-image is NOT stripped — hero banners keep their text */
}
.ainews-body :deep(img) {
  max-width: 100%;
  height: auto;
}
.ainews-body :deep(a) {
  color: #ffd54a;
}
.ainews-body :deep(table) {
  max-width: 100%;
}
.ainews-body :deep(p) {
  margin: 0 0 10px;
}
.ainews-body :deep(p:last-child) {
  margin-bottom: 0;
}
@media (max-width: 999px) {
  .ainews-body {
    max-height: none;
  }
}

.ainews-body-state {
  display: flex;
  flex-direction: column;
  gap: var(--ds-space-2);
  padding: var(--ds-space-4);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-md);
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

/* Like row (FR-15) — borderless, hover bg (demo) */
.ainews-like-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
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
  background: rgb(154 182 219 / .12);
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
  border-top: 1px solid rgb(154 182 219 / .1);
  margin: var(--ds-space-1) 0;
}

/* Comments (FR-16..FR-20) — boxed items (demo) */
.ainews-comments-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 14px 0 8px;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.ainews-comment-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  margin-top: 12px;
}
.ainews-form-label {
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.ainews-comment-input {
  width: 100%;
  resize: vertical;
}
.ainews-counter {
  font-size: 11px;
  color: var(--ds-color-text-muted);
}
.ainews-counter.is-warn {
  color: var(--ds-color-rose-300);
}

.ainews-no-comments {
  padding: var(--ds-space-3) 0;
}

.ainews-comments,
.ainews-replies {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 260px;
  overflow-y: auto;
  padding-right: 4px;
}
.ainews-replies {
  margin-left: 24px;
  padding-left: 10px;
  border-left: 2px solid rgb(154 182 219 / .15);
  gap: 6px;
  max-height: none;
  overflow: visible;
}
.ainews-comment {
  padding: 10px 12px;
  border: 1px solid rgb(154 182 219 / .12);
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
  font: 11px var(--ds-font-mono);
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
  background: rgb(154 182 219 / .12);
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
  background: rgb(154 182 219 / .12);
}
.ainews-link-btn.is-danger:hover {
  color: var(--ds-color-rose-300);
}
.ainews-comment-content {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
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
.ainews-reply-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  margin-top: var(--ds-space-2);
}
</style>
