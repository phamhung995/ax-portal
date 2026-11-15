import { computed, nextTick, ref } from 'vue'
import { useRoute } from 'vue-router'
import { aiNewsApi } from '@/features/ai-news/api'
import { useI18nStore } from '@/stores/i18n'
import { toUserError } from '@/lib/api'
import { stripHtml } from '@/lib/sanitize'
import { toast } from '@/lib/toast'
import type { AiNewsCommentItem } from '@/features/ai-news/types'

/**
 * Comment state + mutations for the AI News detail pane (FR-16..FR-20).
 * Extracted from AiNewsView so the view stays focused on list/detail
 * selection. The returned state is shared by BOTH comment render sites
 * (block under the body + comment drawer), so they stay in sync.
 */
export function useAiNewsComments(selectedId: { value: number | null }) {
  const route = useRoute()
  const i18n = useI18nStore()
  const { t } = i18n

  const comments = ref<AiNewsCommentItem[]>([])
  const commentText = ref('')
  const commentSending = ref(false)
  // Monotonic tick incremented after each successful comment send — the
  // AiNewsComments form watches this to collapse the expanded editor.
  const commentJustSent = ref(0)
  const replyTo = ref<number | null>(null)
  const replyText = ref('')
  const replySending = ref(false)
  const deletingId = ref<number | null>(null)
  // Comment sort order — default oldest first (asc). Shared by the block under
  // the body and the comment sidebar so both stay in sync.
  const commentSortOrder = ref<'oldest' | 'newest' | 'likesAsc' | 'likesDesc' | 'trending'>('oldest')

  // FR-22: comment sidebar (PrismDrawer). Shares the same comment state as the
  // block under the body, so both stay in sync. Closes automatically when the
  // selected news changes (see reset()).
  const commentsDrawerOpen = ref(false)

  // Monotonic counter to discard stale comment responses when the user
  // switches news items while a fetch is in flight (race condition guard).
  let commentsReqId = 0
  // Aborts the in-flight comments fetch on item switch / unmount — a stale
  // response landing on an unmounted component would still fire the failure
  // toast on an unrelated screen.
  let commentsAbort: AbortController | null = null
  let flashTimer: number | null = null

  async function loadComments() {
    const id = selectedId.value
    if (id == null) return
    const reqId = ++commentsReqId
    commentsAbort?.abort()
    const controller = new AbortController()
    commentsAbort = controller
    try {
      const data = await aiNewsApi.comments(id, controller.signal)
      if (reqId !== commentsReqId) return // superseded by a newer selection
      comments.value = data
      scrollToPendingComment()
    } catch {
      if (reqId !== commentsReqId || controller.signal.aborted) return
      // Don't silently empty the list — a transient failure would make
      // existing comments disappear. Toast so the user knows to retry.
      toast({ title: t('aiNews.commentsLoadFail'), tone: 'warning' })
    }
  }

  // Bell deep-link (?comment={commentId}): scroll the mentioning comment into
  // view and flash it. Called after comments load and when the query changes
  // while the item is already open. The comment renders in TWO places (main
  // pane + comment drawer) — target the visible instance.
  function scrollToPendingComment() {
    const raw = route.query.comment
    if (typeof raw !== 'string') return
    const id = Number(raw)
    if (!Number.isFinite(id) || id <= 0) return
    if (!comments.value.some((c) => c.commentId === id)) return
    void nextTick(() => {
      // Both instances (main pane + drawer) share the id. The drawer instance
      // is lazy-mounted (v-if commentsDrawerOpen), so when the drawer is open
      // it is the one the user is looking at (teleported to <body>, last in
      // DOM). NOTE: offsetParent is ALWAYS null inside a native <dialog>
      // (position: fixed via the UA stylesheet), so visibility must be
      // checked with checkVisibility/getBoundingClientRect instead.
      const matches = Array.from(document.querySelectorAll(`#ainews-comment-${id}`))
      const visible = matches.filter((e) => {
        const el = e as HTMLElement
        if (typeof el.checkVisibility === 'function') {
          return el.checkVisibility({ checkOpacity: false, checkVisibilityCSS: true })
        }
        const rect = el.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      })
      const el = visible[visible.length - 1] ?? matches[matches.length - 1]
      if (!el) return
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      el.classList.add('is-flash')
      if (flashTimer != null) window.clearTimeout(flashTimer)
      flashTimer = window.setTimeout(() => {
        el.classList.remove('is-flash')
        flashTimer = null
      }, 2000)
    })
  }

  async function sendComment() {
    const id = selectedId.value
    if (id == null || commentSending.value) return
    // FR-23: rich text — validate the plain text (tags/images don't count).
    if (!stripHtml(commentText.value).trim()) return
    commentSending.value = true
    try {
      const created = await aiNewsApi.addComment(id, commentText.value)
      commentText.value = ''
      commentJustSent.value++
      toast({ title: t('aiNews.commentSent'), tone: 'success' })
      // Patch local instead of re-fetching the whole list (vote already works
      // this way) — the backend returns the full comment row.
      comments.value = [...comments.value, created]
      scrollToPendingComment()
    } catch (error) {
      toast({ title: toUserError(error).message, tone: 'danger' })
    } finally {
      commentSending.value = false
    }
  }

  async function sendReply(parentId: number) {
    const id = selectedId.value
    if (id == null || replySending.value) return
    // FR-23: rich text — validate the plain text (tags/images don't count).
    if (!stripHtml(replyText.value).trim()) return
    replySending.value = true
    try {
      const created = await aiNewsApi.addComment(id, replyText.value, parentId)
      replyText.value = ''
      replyTo.value = null
      toast({ title: t('aiNews.replySent'), tone: 'info' })
      // Patch local: append the new reply + bump the DIRECT parent's
      // replyCount (FR-17 v1.21: nested depth 3 — the backend keeps the
      // reply under its actual parent, no more flattening to the root).
      comments.value = comments.value.map((c) =>
        c.commentId === parentId ? { ...c, replyCount: c.replyCount + 1 } : c,
      )
      comments.value = [...comments.value, created]
      scrollToPendingComment()
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
      // Patch local (soft delete → tombstone). A deleted reply also decrements
      // the thread root's replyCount.
      const rootId = comment.parentId ?? comment.commentId
      comments.value = comments.value.map((c) =>
        c.commentId === comment.commentId
          ? { ...c, deleted: true, content: null }
          : c.commentId === rootId && comment.parentId != null
            ? { ...c, replyCount: Math.max(0, c.replyCount - 1) }
            : c,
      )
    } catch (error) {
      toast({ title: toUserError(error).message, tone: 'danger' })
    } finally {
      deletingId.value = null
    }
  }

  // FR-22: comment count for the detail-card header / drawer title.
  const commentCount = computed(() => comments.value.filter((c) => !c.deleted).length)

  /** Reset all comment state — called when the selected news changes. */
  function reset() {
    comments.value = []
    commentText.value = ''
    replyTo.value = null
    replyText.value = ''
    // FR-22: close the comment sidebar when switching news.
    commentsDrawerOpen.value = false
  }

  /** Abort in-flight work — called on unmount. */
  function dispose() {
    commentsAbort?.abort()
    commentsAbort = null
    if (flashTimer != null) {
      window.clearTimeout(flashTimer)
      flashTimer = null
    }
  }

  return {
    comments,
    commentText,
    commentSending,
    commentJustSent,
    replyTo,
    replyText,
    replySending,
    deletingId,
    commentSortOrder,
    commentsDrawerOpen,
    voteSendingIds,
    commentCount,
    loadComments,
    scrollToPendingComment,
    sendComment,
    sendReply,
    onHelpful,
    onDelete,
    reset,
    dispose,
  }
}
