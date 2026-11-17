<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ThumbsUp, MessageSquare, Link2, Check, X, MoreVertical, Pencil, Trash2 } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import ReputationBadge from '@/features/skills/components/ReputationBadge.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismEmptyState from '@/design-system/components/PrismEmptyState.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import type { MentionSuggestionItem } from '@/components/RichTextEditor.vue'
import CommentTreeNode from '@/features/skill-market/views/CommentTreeNode.vue'
import LinkSkillPopup from './LinkSkillPopup.vue'
import EditSkillRequestPopup from './EditSkillRequestPopup.vue'
import SkillDetailPopup from '@/features/skills/views/SkillDetailPopup.vue'
import { skillRequestApi } from '@/features/skill-market/api'
import { toUserError } from '@/lib/api'
import { toastError, toastSuccess } from '@/lib/toast'
import { sanitizeHtml, stripHtml } from '@/lib/sanitize'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type {
  SkillRequestFeedItem,
  SkillRequestCommentItem,
  SkillRequestStatsResult,
} from '@/features/skill-market/types'
import type { SkillListItem } from '@/features/skills/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18nStore()
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

// ── Comment form (pinned at the bottom of the sidebar) ────────────────────
const commentDraft = ref('')
const posting = ref(false)
const pendingSkill = ref<SkillListItem | null>(null)
const linkPopupOpen = ref(false)
const detailSkillId = ref<number | null>(null)

// ── Fulfillment (GĐ5, Q8) ────────────────────────────────────────────────
// LinkSkillPopup is shared with the comment linker: proposeMode tells
// onSkillSelected to propose the picked skill as the answer instead of
// attaching it to a comment draft.
const proposeMode = ref(false)
const fulfillmentBusy = ref(false)

function formatDeadline(iso: string | null): string {
  if (!iso) return ''
  return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

// ── Reply state (one open reply form at a time) ───────────────────────────
const replyParentId = ref<number | null>(null)
const replyDraft = ref('')
const replying = ref(false)

// ── Edit comment state (one open edit form at a time) ─────────────────────
const editCommentId = ref<number | null>(null)
const editCommentDraft = ref('')
const savingComment = ref(false)

// ── Sidebar stats (same widgets as the main feed) ─────────────────────────
const stats = ref<SkillRequestStatsResult | null>(null)

async function loadStats() {
  try {
    stats.value = await skillRequestApi.stats()
  } catch {
    /* non-critical */
  }
}

function openRequestDetail(id: number) {
  void router.push({ name: 'skill-market-detail', params: { id } })
}

function openContributor(userId: number) {
  void router.push({ name: 'contributor-profile', params: { userId } })
}

// ── Kebab menu on the post (creator only: Edit / Delete) ──────────────────
const postMenuOpen = ref(false)
const postMenuPos = ref({ top: 0, left: 0 })
const postKebabBtn = ref<HTMLButtonElement | null>(null)
const editOpen = ref(false)
const deleteConfirmOpen = ref(false)
const deleting = ref(false)

function togglePostMenu() {
  if (postMenuOpen.value) { closePostMenu(); return }
  const rect = postKebabBtn.value?.getBoundingClientRect()
  if (!rect) return
  const MENU_W = 148
  const MENU_H = 92
  postMenuPos.value = {
    top: Math.min(rect.bottom + 4, window.innerHeight - MENU_H - 8),
    left: Math.max(8, rect.right - MENU_W),
  }
  postMenuOpen.value = true
  window.addEventListener('scroll', closePostMenu, true)
  window.addEventListener('resize', closePostMenu)
}
function closePostMenu() {
  postMenuOpen.value = false
  window.removeEventListener('scroll', closePostMenu, true)
  window.removeEventListener('resize', closePostMenu)
}
function onPostEdit() {
  closePostMenu()
  editOpen.value = true
}
function onPostDelete() {
  closePostMenu()
  deleteConfirmOpen.value = true
}
function onPostMenuDocClick(event: MouseEvent) {
  if (!postMenuOpen.value) return
  const target = event.target
  if (target instanceof HTMLElement && (target.closest('.srd-kebab-menu') || target.closest('.srd-kebab-btn'))) return
  closePostMenu()
}
function onPostMenuEsc(event: KeyboardEvent) {
  if (event.key === 'Escape' && postMenuOpen.value) closePostMenu()
}

function onEditSaved() {
  void loadDetail()
}

async function confirmDelete() {
  if (!item.value) return
  deleting.value = true
  try {
    await skillRequestApi.delete(item.value.id)
    toastSuccess(t('app.skillMarket.deletedToastDetail'))
    deleteConfirmOpen.value = false
    goBack()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    deleting.value = false
  }
}

// ── Attachments (all files as download rows) ───────────────────────────────
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

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const descHtml = computed(() => sanitizeHtml(item.value?.description ?? ''))

// ── @mention autocomplete (same pipeline as AI News comments) ─────────────
async function searchMentionUsers(query: string, signal?: AbortSignal): Promise<MentionSuggestionItem[]> {
  try {
    return await skillRequestApi.mentionUsers(query, signal)
  } catch {
    return []
  }
}

// Rich text: the Post button is enabled by the PLAIN TEXT of the draft
// (HTML tags and embedded images don't count) — same as AI News.
const hasCommentText = computed(() => stripHtml(commentDraft.value).trim().length > 0)
// The editor's maxLength counter is display-only — the Post button must
// also be disabled when the plain text exceeds the limit (otherwise an
// over-limit draft reaches the backend and 400s).
const commentEditor = ref<InstanceType<typeof RichTextEditor> | null>(null)
const commentOverLimit = computed(() => commentEditor.value?.isOverLimit ?? false)

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
    toastError(t('app.skillMarket.notApproved'))
    return
  }
  detailSkillId.value = c.linkedSkillId
}

function onResolvedSkillClick(s: { skillId: number; skillStatus: string | null }) {
  if (s.skillStatus && s.skillStatus !== 'APPROVED') {
    toastError(t('app.skillMarket.notApproved'))
    return
  }
  detailSkillId.value = s.skillId
}

function onSkillSelected(skill: SkillListItem) {
  if (proposeMode.value) {
    proposeMode.value = false
    void onProposeFulfillment(skill)
    return
  }
  pendingSkill.value = skill
}

// ── Fulfillment (GĐ5, Q8): propose / accept / reject ──────────────────────
function openProposePopup() {
  proposeMode.value = true
  linkPopupOpen.value = true
}

async function onProposeFulfillment(skill: SkillListItem) {
  const it = item.value
  if (!it || fulfillmentBusy.value) return
  fulfillmentBusy.value = true
  try {
    await skillRequestApi.proposeFulfillment(it.id, skill.id)
    toastSuccess('Đã đề xuất skill đáp ứng — người đăng có 7 ngày để xác nhận.')
    await loadDetail()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    fulfillmentBusy.value = false
  }
}

async function onFulfillmentDecision(accept: boolean) {
  const it = item.value
  if (!it || fulfillmentBusy.value) return
  fulfillmentBusy.value = true
  try {
    await skillRequestApi.fulfillmentDecision(it.id, accept)
    toastSuccess(accept ? 'Đã xác nhận request được đáp ứng.' : 'Đã từ chối — request mở lại để nhận đề xuất khác.')
    await loadDetail()
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    fulfillmentBusy.value = false
  }
}

// ── Vote (toggle) ─────────────────────────────────────────────────────────
async function onVote() {
  const it = item.value
  if (!it) return
  const wasVoted = it.hasVoted
  // Optimistic update: flip the UI immediately, roll back on failure.
  it.hasVoted = !wasVoted
  it.voteCount = Math.max(0, it.voteCount + (wasVoted ? -1 : 1))
  try {
    if (wasVoted) {
      await skillRequestApi.unvote(it.id)
    } else {
      await skillRequestApi.vote(it.id)
    }
  } catch (err) {
    it.hasVoted = wasVoted
    it.voteCount = Math.max(0, it.voteCount + (wasVoted ? 1 : -1))
    toastError(toUserError(err).message)
  }
}

// ── Comments: post / like / delete / resolve / reply ──────────────────────
async function postComment() {
  // Rich text: validate the plain text (tags/images don't count).
  const content = commentDraft.value
  if (!stripHtml(content).trim() || posting.value || !validId.value) return
  posting.value = true
  const linkedSkill = pendingSkill.value
  try {
    await skillRequestApi.addComment(requestId.value, content, linkedSkill?.id)
    commentDraft.value = ''
    pendingSkill.value = null
    if (item.value) item.value.commentCount += 1
    await loadComments(true)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    posting.value = false
  }
}

async function onLike(comment: SkillRequestCommentItem) {
  if (!validId.value) return
  const wasLiked = comment.likedByMe
  // Optimistic update: flip the UI immediately, roll back on failure.
  comment.likedByMe = !wasLiked
  comment.likeCount = Math.max(0, comment.likeCount + (wasLiked ? -1 : 1))
  try {
    if (wasLiked) {
      await skillRequestApi.unlikeComment(requestId.value, comment.id)
    } else {
      await skillRequestApi.likeComment(requestId.value, comment.id)
    }
  } catch (err) {
    comment.likedByMe = wasLiked
    comment.likeCount = Math.max(0, comment.likeCount + (wasLiked ? 1 : -1))
    toastError(toUserError(err).message)
  }
}

function openReply(comment: SkillRequestCommentItem) {
  if (editCommentId.value === comment.id) {
    editCommentId.value = null
    editCommentDraft.value = ''
  }
  if (replyParentId.value === comment.id) {
    replyParentId.value = null
    replyDraft.value = ''
    return
  }
  replyParentId.value = comment.id
  replyDraft.value = ''
}

function openEditComment(comment: SkillRequestCommentItem) {
  if (replyParentId.value === comment.id) {
    replyParentId.value = null
    replyDraft.value = ''
  }
  if (editCommentId.value === comment.id) {
    editCommentId.value = null
    editCommentDraft.value = ''
    return
  }
  editCommentId.value = comment.id
  editCommentDraft.value = comment.content
}

async function saveEditedComment(comment: SkillRequestCommentItem) {
  const content = editCommentDraft.value
  if (!stripHtml(content).trim() || savingComment.value || !validId.value) return
  savingComment.value = true
  try {
    await skillRequestApi.updateComment(requestId.value, comment.id, content)
    comment.content = content
    editCommentId.value = null
    editCommentDraft.value = ''
    toastSuccess(t('app.skillMarket.commentUpdated'))
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    savingComment.value = false
  }
}

async function postReply() {
  const content = replyDraft.value
  if (!stripHtml(content).trim() || !replyParentId.value || replying.value || !validId.value) return
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
    toastSuccess(t('app.skillMarket.commentResolved'))
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

function goBack() {
  void router.push({ name: 'skill-market' })
}

// Reload when the route param changes (e.g. navigating between details).
watch(requestId, (id, oldId) => {
  if (id !== oldId && Number.isInteger(id)) {
    void loadDetail()
  }
})

onMounted(() => {
  void loadDetail()
  void loadStats()
  document.addEventListener('click', onPostMenuDocClick)
  document.addEventListener('keydown', onPostMenuEsc)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onPostMenuDocClick)
  document.removeEventListener('keydown', onPostMenuEsc)
  closePostMenu()
})
</script>

<template>
  <div class="srd">
    <div v-if="!validId" class="srd-wrap">
      <PrismEmptyState :title="t('app.skillMarket.invalidTitle')" :description="t('app.skillMarket.invalidDesc')" />
      <PrismButton variant="secondary" @click="goBack">{{ t('app.skillMarket.backToFeed') }}</PrismButton>
    </div>

    <div v-else class="srd-wrap">
      <button type="button" class="srd-back" @click="goBack">
        <ArrowLeft :size="16" aria-hidden="true" /> {{ t('app.skillMarket.backToFeed') }}
      </button>

      <div v-if="loading" class="srd-skeleton">
        <div class="srd-skel-main">
          <div class="srd-skel-row">
            <div class="srd-skel srd-skel-avatar"></div>
            <div class="srd-skel-lines">
              <div class="srd-skel srd-skel-line" style="width: 180px"></div>
              <div class="srd-skel srd-skel-line" style="width: 110px"></div>
            </div>
          </div>
          <div class="srd-skel srd-skel-line" style="width: 70%; height: 28px"></div>
          <div class="srd-skel srd-skel-line" style="width: 95%"></div>
          <div class="srd-skel srd-skel-line" style="width: 88%"></div>
          <div class="srd-skel srd-skel-line" style="width: 45%"></div>
          <div class="srd-skel srd-skel-line" style="width: 120px; margin-top: var(--ds-space-4)"></div>
          <div class="srd-skel-row">
            <div class="srd-skel srd-skel-avatar-xs"></div>
            <div class="srd-skel-lines">
              <div class="srd-skel srd-skel-line" style="width: 90%"></div>
              <div class="srd-skel srd-skel-line" style="width: 55%"></div>
            </div>
          </div>
          <div class="srd-skel-row">
            <div class="srd-skel srd-skel-avatar-xs"></div>
            <div class="srd-skel-lines">
              <div class="srd-skel srd-skel-line" style="width: 85%"></div>
              <div class="srd-skel srd-skel-line" style="width: 50%"></div>
            </div>
          </div>
        </div>
      </div>
      <PrismEmptyState
        v-else-if="loadError"
        :title="t('app.skillMarket.loadErrorDetail')"
        :description="loadError"
      >
        <PrismButton variant="secondary" @click="loadDetail">{{ t('app.common.retry') }}</PrismButton>
      </PrismEmptyState>

      <template v-else-if="item">
        <div class="srd-cols">
          <!-- Main column: the post + comments -->
          <div class="srd-main">
            <article class="srd-post">
              <header class="srd-head">
                <PrismAvatar :name="item.creatorName ?? ''" :src="item.creatorAvatarUrl ?? undefined" :size="40" />
                <div class="srd-who">
                  <button type="button" class="srd-name srd-name--link" @click="openContributor(item.creatorId)">
                    {{ item.creatorName ?? t('app.common.unknown') }}
                  </button>
                  <span class="srd-time">{{ timeAgo(item.createdAt) }}</span>
                </div>
                <PrismBadge v-if="item.hasResolvedComment" tone="success" class="srd-resolved">
                  <Check :size="12" aria-hidden="true" /> {{ t('app.skillMarket.resolved') }}
                </PrismBadge>
                <PrismBadge v-if="item.fulfillmentStatus === 'FULFILLED'" tone="success" class="srd-resolved">
                  <Check :size="12" aria-hidden="true" /> Đã đáp ứng
                </PrismBadge>
                <ReputationBadge
                  :tier="item.reputationTier"
                  :score="item.reputationScore"
                  new-hint="Mới: chưa đủ lượt bình chọn để xếp hạng (cần 5)"
                />
                <!-- Kebab menu (creator only) -->
                <div v-if="item.isRequestCreator" class="srd-kebab">
                  <button
                    ref="postKebabBtn"
                    type="button"
                    class="srd-kebab-btn"
                    :class="{ 'is-open': postMenuOpen }"
                    :aria-label="t('app.skillMarket.requestActionsAria')"
                    aria-haspopup="menu"
                    :aria-expanded="postMenuOpen"
                    @click.stop="togglePostMenu"
                  >
                    <MoreVertical :size="18" aria-hidden="true" />
                  </button>
                </div>
              </header>

              <!-- Kebab dropdown (teleported: escapes the .p-glass stacking context) -->
              <teleport to="body">
                <div
                  v-if="postMenuOpen"
                  class="srd-kebab-menu p-glass"
                  role="menu"
                  :style="{ top: postMenuPos.top + 'px', left: postMenuPos.left + 'px' }"
                  @click.stop
                >
                  <button type="button" class="srd-kebab-item" role="menuitem" @click="onPostEdit">
                    <Pencil :size="14" aria-hidden="true" /> {{ t('app.common.edit') }}
                  </button>
                  <button type="button" class="srd-kebab-item srd-kebab-danger" role="menuitem" @click="onPostDelete">
                    <Trash2 :size="14" aria-hidden="true" /> {{ t('app.common.delete') }}
                  </button>
                </div>
              </teleport>

              <h1 class="srd-title">{{ item.title }}</h1>
              <div v-if="item.description" class="srd-desc" v-html="descHtml"></div>

              <div v-if="item.hashtags.length" class="srd-tags">
                <PrismBadge v-for="tag in item.hashtags" :key="tag" tone="info">#{{ tag }}</PrismBadge>
              </div>
              <div v-if="item.files.length" class="srd-files">
                <span
                  v-for="f in item.files"
                  :key="f.id"
                  class="srd-file"
                  :title="f.fileName"
                >
                  📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})
                  <button type="button" class="srd-file-download" :aria-label="t('app.skillMarket.downloadFileAria', { name: f.fileName })" @click="onDownloadFile(f.id)">
                    {{ t('app.common.download') }}
                  </button>
                </span>
              </div>

              <!-- Resolved skills (from resolved comments) -->
              <div v-if="item.resolvedSkills.length" class="resolved-skills">
                <span class="resolved-label">
                  <Check :size="13" aria-hidden="true" /> {{ t('app.skillMarket.resolvedColon') }}
                </span>
                <button
                  v-for="s in item.resolvedSkills"
                  :key="s.skillId"
                  type="button"
                  class="resolved-chip"
                  :title="t('app.skillMarket.viewSkill', { name: s.skillName ?? t('app.skillMarket.viewSkillDetail') })"
                  @click="onResolvedSkillClick(s)"
                >
                  <Link2 :size="12" aria-hidden="true" />
                  <span v-if="s.commenterName" class="resolved-by">
                    {{ s.commenterName }}<span v-if="s.commenterKnoxId"> ({{ s.commenterKnoxId }})</span>
                  </span>
                  {{ s.skillName ?? t('app.skillMarket.viewSkillDetail') }}
                  <span v-if="s.skillStatus" class="resolved-status" :class="`is-${skillStatusTone(s.skillStatus)}`">
                    {{ skillStatusLabel(s.skillStatus) }}
                  </span>
                </button>
                <span v-if="item.resolvedSkillCount > item.resolvedSkills.length" class="resolved-more">
                  {{ t('app.skillMarket.more', { n: item.resolvedSkillCount - item.resolvedSkills.length }) }}
                </span>
              </div>

              <!-- Fulfillment (GĐ5, Q8): propose / pending decision / fulfilled -->
              <div v-if="item.fulfillmentStatus === 'FULFILLED'" class="fulfill-box fulfill-box--done">
                <Check :size="14" aria-hidden="true" />
                <span>
                  Đã đáp ứng bởi
                  <button
                    v-if="item.fulfilledSkillId"
                    type="button"
                    class="fulfill-skill"
                    @click="detailSkillId = item.fulfilledSkillId"
                  >
                    {{ item.fulfilledSkillName ?? 'skill' }}
                  </button>
                  <span v-else>{{ item.fulfilledSkillName ?? 'skill' }}</span>
                </span>
              </div>
              <div v-else-if="item.fulfillmentStatus === 'PENDING_CONFIRM'" class="fulfill-box">
                <Link2 :size="14" aria-hidden="true" />
                <span class="fulfill-text">
                  Đang chờ xác nhận: <strong>{{ item.fulfilledSkillName ?? 'skill' }}</strong>
                  <span v-if="item.fulfillmentDeadline"> — hạn xác nhận {{ formatDeadline(item.fulfillmentDeadline) }} (quá hạn sẽ tự xác nhận nếu skill vẫn đạt chuẩn)</span>
                </span>
                <span v-if="item.isRequestCreator" class="fulfill-actions">
                  <PrismButton variant="secondary" :loading="fulfillmentBusy" @click="onFulfillmentDecision(true)">
                    Xác nhận đã đáp ứng
                  </PrismButton>
                  <PrismButton variant="ghost" :loading="fulfillmentBusy" @click="onFulfillmentDecision(false)">
                    Từ chối
                  </PrismButton>
                </span>
              </div>
              <div v-else-if="!item.isRequestCreator" class="fulfill-box fulfill-box--propose">
                <PrismButton variant="ghost" :loading="fulfillmentBusy" @click="openProposePopup">
                  <Link2 :size="14" aria-hidden="true" /> Đề xuất skill đáp ứng
                </PrismButton>
              </div>

              <!-- Action bar: flat icon buttons -->
              <div class="srd-actions">
                <button
                  type="button"
                  class="srd-action"
                  :class="{ 'is-voted': item.hasVoted }"
                  :aria-label="item.hasVoted ? t('app.skillMarket.unvote') : t('app.skillMarket.vote')"
                  @click="onVote"
                >
                  <ThumbsUp :size="15" :fill="item.hasVoted ? 'currentColor' : 'none'" aria-hidden="true" />
                  <span class="srd-action-count">{{ item.voteCount }}</span>
                </button>
                <span class="srd-action srd-action--static">
                  <MessageSquare :size="15" aria-hidden="true" />
                  <span class="srd-action-count">{{ item.commentCount }}</span>
                </span>
              </div>
            </article>

            <div class="srd-divider">
              {{ t('app.skillMarket.comments') }} <span class="srd-divider-count">{{ item.commentCount }}</span>
            </div>

            <!-- Composer: thin bar at the top of the comments -->
            <div class="srd-composer">
              <PrismAvatar class="srd-composer-avatar" :name="auth.user?.userName ?? ''" :src="auth.user?.avatarUrl ?? undefined" :size="26" />
              <div class="srd-composer-body">
                <RichTextEditor ref="commentEditor" v-model="commentDraft" :placeholder="t('app.skillMarket.sharePh')" :rows="1" :max-length="1000" mention-enabled :mention-suggestion="searchMentionUsers" />
                <div v-if="pendingSkill" class="srd-linked-chip">
                  <Link2 :size="12" aria-hidden="true" />
                  {{ pendingSkill.name }}
                  <PrismBadge :tone="skillStatusTone(pendingSkill.status)">
                    {{ skillStatusLabel(pendingSkill.status) }}
                  </PrismBadge>
                  <button type="button" :aria-label="t('app.skillMarket.removeLinkedSkillAria')" @click="pendingSkill = null">
                    <X :size="12" aria-hidden="true" />
                  </button>
                </div>
              </div>
              <div class="srd-composer-actions">
                <PrismButton v-if="!item.isRequestCreator" variant="ghost" @click="linkPopupOpen = true">
                  <Link2 :size="14" aria-hidden="true" /> {{ t('app.skillMarket.linkSkill') }}
                </PrismButton>
                <PrismButton :loading="posting" :disabled="!hasCommentText || commentOverLimit" @click="postComment">
                  {{ t('app.skillMarket.post') }}
                </PrismButton>
              </div>
            </div>

            <!-- Comments: timeline (no card boxes) -->
            <div class="srd-comments">
              <!-- Skeleton while loading the first page -->
              <div v-if="commentsLoading && !comments.length" class="srd-skel-list">
                <div v-for="i in 3" :key="i" class="srd-skel-row">
                  <div class="srd-skel srd-skel-avatar-xs"></div>
                  <div class="srd-skel-lines">
                    <div class="srd-skel srd-skel-line" style="width: 90%"></div>
                    <div class="srd-skel srd-skel-line" style="width: 55%"></div>
                  </div>
                </div>
              </div>
              <!-- Empty state -->
              <div v-else-if="commentsLoaded && !comments.length" class="srd-comments-empty">
                <MessageSquare :size="28" aria-hidden="true" />
                <p>{{ t('app.skillMarket.noComments') }}</p>
                <span>{{ t('app.skillMarket.noCommentsDesc') }}</span>
              </div>
              <TransitionGroup v-else name="srd-comment" tag="div" class="srd-comments-list">
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
                  :on-edit="openEditComment"
                  :on-resolve="onResolve"
                  :on-skill-chip-click="onSkillChipClick"
                  :reply-draft="replyDraft"
                  :replying="replying"
                  :on-reply-input="(v: string) => (replyDraft = v)"
                  :on-post-reply="postReply"
                  :edit-comment-id="editCommentId"
                  :edit-comment-draft="editCommentDraft"
                  :saving-comment="savingComment"
                  :on-edit-input="(v: string) => (editCommentDraft = v)"
                  :on-save-edit="saveEditedComment"
                  :mention-suggestion="searchMentionUsers"
                />
              </TransitionGroup>
              <div v-if="commentsHasMore" class="srd-comments-more">
                <PrismButton variant="secondary" :loading="commentsLoading" @click="loadComments()">
                  {{ t('app.skillMarket.loadMoreComments') }}
                </PrismButton>
              </div>
            </div>
          </div>

          <!-- Sidebar: 3 top-10 cards (same as the main feed) -->
          <aside class="side-rail" :aria-label="t('app.skillMarket.highlightsAria')">
            <section class="widget p-glass" :aria-label="t('app.skillMarket.topVotedAria')">
              <h3>{{ t('app.skillMarket.topVoted') }}</h3>
              <div class="widget-list">
                <div v-for="(r, i) in stats?.topVoted ?? []" :key="r.id" class="trend-row">
                  <span class="trend-rank">{{ i === 0 ? '🏆' : i === 1 ? '🥈' : i === 2 ? '🥉' : i + 1 }}</span>
                  <div>
                    <button type="button" class="trend-title" :title="r.title" @click="openRequestDetail(r.id)">
                      {{ r.title }}
                    </button>
                    <div class="trend-meta">{{ t('app.skillMarket.votesComments', { n: r.voteCount, m: r.commentCount }) }}</div>
                  </div>
                </div>
                <p v-if="!stats?.topVoted?.length" class="widget-empty">{{ t('app.skillMarket.noRequests') }}</p>
              </div>
            </section>

            <section class="widget widget--fixed p-glass" :aria-label="t('app.skillMarket.topContributorsAria')">
              <h3>{{ t('app.skillMarket.topContributors') }}</h3>
              <div class="widget-list">
                <div v-for="(m, i) in stats?.topContributors ?? []" :key="m.userId" class="member-row">
                  <span class="member-rank">{{ i === 0 ? '🏆' : i === 1 ? '🥈' : i === 2 ? '🥉' : i + 1 }}</span>
                  <PrismAvatar :name="m.userName ?? ''" :src="m.userAvatarUrl ?? undefined" :size="32" />
                  <div class="member-info">
                    <button type="button" class="member-name member-name--link" @click="openContributor(m.userId)">
                      {{ m.userName ?? t('app.common.unknown') }}
                    </button>
                    <div class="member-role">{{ t('app.skillMarket.skillsLinked', { n: m.skillLinkCount }) }}</div>
                  </div>
                </div>
                <p v-if="!stats?.topContributors?.length" class="widget-empty">{{ t('app.skillMarket.noContributions') }}</p>
              </div>
            </section>

            <section class="widget widget--tags p-glass" :aria-label="t('app.skillMarket.popularHashtagsAria')">
              <h3>{{ t('app.skillMarket.popularHashtags') }}</h3>
              <div class="widget-list">
                <div class="tag-cloud">
                  <span
                    v-for="h in stats?.popularHashtags ?? []"
                    :key="h.tag"
                    class="tag-chip tag-chip--static"
                  >
                    #{{ h.tag }} <span class="tag-count">{{ h.count }}</span>
                  </span>
                </div>
                <p v-if="!stats?.popularHashtags?.length" class="widget-empty">{{ t('app.skillMarket.noHashtags') }}</p>
              </div>
            </section>
          </aside>
        </div>
      </template>
    </div>

    <LinkSkillPopup v-model:open="linkPopupOpen" @selected="onSkillSelected" />
    <SkillDetailPopup :skill-id="detailSkillId" @closed="detailSkillId = null" />

    <!-- Edit request -->
    <EditSkillRequestPopup v-model:open="editOpen" :item="item" @updated="onEditSaved" />

    <!-- Delete confirm -->
    <PrismDialog
      :model-value="deleteConfirmOpen"
      :title="t('app.skillMarket.deleteTitle')"
      size="sm"
      :close-on-overlay="false"
      @update:model-value="deleteConfirmOpen = $event"
    >
      <p class="srd-confirm-text">
        {{ t('app.skillMarket.deleteDesc', { title: item?.title ?? '' }) }}
      </p>
      <template #footer>
        <PrismButton variant="secondary" :disabled="deleting" @click="deleteConfirmOpen = false">{{ t('app.common.cancel') }}</PrismButton>
        <PrismButton variant="danger" :loading="deleting" @click="confirmDelete">{{ t('app.common.delete') }}</PrismButton>
      </template>
    </PrismDialog>
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
/* Skeleton loading */
.srd-skeleton {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--ds-space-4);
  align-items: start;
}
@media (max-width: 1199px) {
  .srd-skeleton {
    grid-template-columns: 1fr;
  }
}
.srd-skel-main {
  display: grid;
  gap: var(--ds-space-4);
  padding-bottom: var(--ds-space-4);
}
.srd-skel {
  border-radius: var(--ds-radius-sm);
  background: linear-gradient(90deg, rgb(133 234 255 / .08), rgb(133 234 255 / .16), rgb(133 234 255 / .08));
  background-size: 200% 100%;
  animation: srd-shimmer 1.4s ease-in-out infinite;
}
@keyframes srd-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.srd-skel-line {
  height: 14px;
}
.srd-skel-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  flex-shrink: 0;
}
.srd-skel-avatar-xs {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  flex-shrink: 0;
}
.srd-skel-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
.srd-skel-lines {
  flex: 1;
  display: grid;
  gap: var(--ds-space-2);
}
.srd-skel-list {
  display: grid;
  gap: var(--ds-space-4);
}
.srd-skel-list .srd-skel-avatar-xs {
  width: 26px;
  height: 26px;
}

/* Two columns: post + comments (main) + stats sidebar (sticky) — same 360px rail + 16px gap as the feed */
.srd-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--ds-space-4);
  align-items: start;
}
@media (max-width: 1199px) {
  .srd-cols {
    grid-template-columns: 1fr;
  }
}
.srd-main {
  display: grid;
  gap: 0;
  min-width: 0;
}

/* Post: flat, no card border (variant B) */
.srd-post {
  padding: 0 0 var(--ds-space-4);
  display: grid;
  gap: var(--ds-space-3);
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
  font-size: 15px;
}
.srd-name--link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
@media (hover: hover) {
  .srd-name--link:hover {
    color: var(--ds-color-accent);
  }
}
.srd-time {
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.srd-resolved {
  margin-left: auto;
}
/* Kebab menu on the post (creator only) */
.srd-kebab {
  margin-left: auto;
}
/* When the kebab is present it takes the right edge; the badge sits next to it */
.srd-head:has(.srd-kebab) .srd-resolved {
  margin-left: 0;
}
.srd-kebab-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-muted);
  padding: 4px;
  border-radius: var(--ds-radius-sm);
  display: grid;
  place-items: center;
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease;
}
.srd-kebab-btn.is-open {
  background: rgb(103 232 249 / .10);
  color: var(--ds-color-text-primary);
}
@media (hover: hover) {
  .srd-kebab-btn:hover {
    background: rgb(103 232 249 / .10);
    color: var(--ds-color-text-primary);
  }
}
.srd-kebab-menu {
  position: fixed;
  min-width: 148px;
  display: grid;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  z-index: 1200;
  transform-origin: top right;
  animation: srd-menu-in 120ms ease;
}
@keyframes srd-menu-in {
  from {
    opacity: 0;
    transform: scale(.95) translateY(-3px);
  }
}
.srd-kebab-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 14px;
  padding: 8px 10px;
  border-radius: var(--ds-radius-sm);
  text-align: left;
  width: 100%;
  transition: background var(--ds-duration-fast) ease, color var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .srd-kebab-item:hover {
    background: rgb(103 232 249 / .10);
    color: var(--ds-color-text-primary);
  }
  .srd-kebab-danger:hover {
    background: rgb(255 172 196 / .12);
    color: var(--ds-color-danger);
  }
}
.srd-kebab-danger {
  color: var(--ds-color-danger);
}
.srd-confirm-text {
  color: var(--ds-color-text-primary);
}
.srd-title {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  line-height: 1.3;
}
.srd-desc {
  margin: 0;
  color: var(--ds-color-text-secondary);
  font-size: 14.5px;
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
.srd-desc :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--ds-radius-sm);
}
.srd-tags {
  display: flex;
  gap: var(--ds-space-2);
  flex-wrap: wrap;
}
/* Attachment list: each file on its own row */
.srd-files {
  display: grid;
  gap: var(--ds-space-1);
  justify-items: start;
}
.srd-file {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ds-color-text-secondary);
  max-width: 100%;
}
.srd-file-download {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-accent);
  font: inherit;
  font-size: 12px;
  flex-shrink: 0;
}
@media (hover: hover) {
  .srd-file-download:hover {
    text-decoration: underline;
  }
}

/* Resolved skills (from resolved comments) */
.resolved-skills {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-wrap: wrap;
}
.resolved-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ds-color-success);
}
.resolved-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: 12px var(--ds-font-mono);
  padding: 3px 9px;
  border: 1px solid rgb(156 232 208 / .30);
  border-radius: 6px;
  color: var(--ds-color-success);
  background: rgb(156 232 208 / .07);
  cursor: pointer;
  white-space: nowrap;
  transition: border-color var(--ds-duration-fast) ease, background var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .resolved-chip:hover {
    border-color: rgb(156 232 208 / .55);
    background: rgb(156 232 208 / .14);
  }
}
.resolved-more {
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}

/* Fulfillment (GĐ5, Q8) */
.fulfill-box {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-wrap: wrap;
  margin-top: var(--ds-space-3);
  padding: var(--ds-space-3) var(--ds-space-4);
  border: 1px solid var(--ds-color-border);
  border-radius: 8px;
  background: rgb(103 232 249 / .05);
  font-size: 13.5px;
  color: var(--ds-color-text-secondary);
}
.fulfill-box--done {
  border-color: rgb(156 232 208 / .30);
  background: rgb(156 232 208 / .07);
  color: var(--ds-color-success);
}
.fulfill-box--propose {
  padding: var(--ds-space-2) var(--ds-space-3);
  background: transparent;
}
.fulfill-text {
  flex: 1;
  min-width: 200px;
}
.fulfill-actions {
  display: inline-flex;
  gap: var(--ds-space-2);
  margin-left: auto;
}
.fulfill-skill {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-weight: 600;
  color: var(--ds-color-success);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.resolved-by {
  font: 11px var(--ds-font-sans);
  color: var(--ds-color-text-muted);
}
.resolved-status {
  font: 10.5px var(--ds-font-mono);
  padding: 1px 6px;
  border-radius: 10px;
  border: 1px solid var(--ds-color-border);
  color: var(--ds-color-text-muted);
}
.resolved-status.is-success {
  color: var(--ds-color-success);
  border-color: rgb(156 232 208 / .35);
}
.resolved-status.is-warning {
  color: var(--ds-color-warning);
  border-color: rgb(243 210 140 / .35);
}
.resolved-status.is-danger {
  color: var(--ds-color-danger);
  border-color: rgb(255 172 196 / .35);
}

/* Action bar: flat icon buttons (variant B) */
.srd-actions {
  display: flex;
  align-items: center;
  gap: var(--ds-space-1);
  margin-top: var(--ds-space-1);
}
.srd-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: none;
  background: none;
  color: var(--ds-color-text-muted);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--ds-radius-sm);
  transition: all var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .srd-action:not(.srd-action--static):hover {
    background: rgb(103 232 249 / .08);
    color: var(--ds-color-text-primary);
  }
}
.srd-action.is-voted {
  color: var(--ds-color-accent);
}
.srd-action-count {
  font: 12px var(--ds-font-mono);
}
.srd-action--static {
  cursor: default;
}

/* Divider label above the comments (variant B) */
.srd-divider {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  margin: var(--ds-space-3) 0 var(--ds-space-3);
  color: var(--ds-color-text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: .08em;
  text-transform: uppercase;
}
.srd-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--ds-color-border);
}
.srd-divider-count {
  font: 12px var(--ds-font-mono);
}

.srd-comments {
  position: relative;
  display: grid;
  gap: var(--ds-space-2);
  align-content: start;
}
.srd-comments-empty {
  display: grid;
  justify-items: center;
  gap: var(--ds-space-1);
  padding: var(--ds-space-6) 0;
  color: var(--ds-color-text-muted);
  text-align: center;
}
.srd-comments-empty svg {
  color: var(--ds-color-border-strong);
}
.srd-comments-empty p {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-color-text-secondary);
}
.srd-comments-empty span {
  font-size: 12.5px;
}
.srd-comments-list {
  display: grid;
  gap: 2px;
}
/* Comment list enter/leave */
.srd-comment-enter-active {
  transition: opacity var(--ds-duration-normal) ease, transform var(--ds-duration-normal) ease;
}
.srd-comment-leave-active {
  transition: opacity var(--ds-duration-fast) ease;
  position: absolute;
  width: 100%;
}
.srd-comment-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}
.srd-comment-leave-to {
  opacity: 0;
}
.srd-comment-move {
  transition: transform var(--ds-duration-normal) ease;
}
.srd-comments-more {
  display: flex;
  justify-content: center;
}

/* Composer: thin pill bar at the top of the comments (variant B) */
.srd-composer {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  margin-bottom: var(--ds-space-3);
  padding: 6px 6px 6px 14px;
  border: 1px solid var(--ds-color-border);
  border-radius: 24px;
  background: var(--ds-input-background);
  transition: border-color var(--ds-duration-fast) ease, box-shadow var(--ds-duration-fast) ease;
}
.srd-composer:focus-within {
  border-color: rgb(103 232 249 / .42);
  box-shadow: var(--ds-shadow-glow);
}
.srd-composer-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: var(--ds-space-1);
}
/* Merge the tiptap editor into the composer (drop its own border/bg) */
.srd-composer :deep(.rte) {
  border: none;
  background: transparent;
}
.srd-composer :deep(.rte-toolbar) {
  gap: 2px;
  padding: 4px;
  border-radius: var(--ds-radius-sm);
}
.srd-composer :deep(.rte-btn) {
  width: 26px;
  height: 26px;
  font-size: 12px;
  padding: 0;
}
.srd-composer :deep(.rte-content) {
  min-height: 32px;
  max-height: 160px;
  overflow-y: auto;
  font-size: 13.5px;
}
.srd-composer-actions {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-shrink: 0;
  /* Align the Post button with the editor toolbar (toolbar center =
     6px composer padding + 17px toolbar half-height; button center =
     margin-top + 16px button half-height) */
  margin-top: 7px;
}
/* Align the avatar with the editor toolbar (avatar center = margin-top + 13px) */
.srd-composer-avatar {
  margin-top: 10px;
}
/* Compact composer buttons: same visual weight as the toolbar */
.srd-composer-actions :deep(.p-button) {
  min-height: 32px;
  padding: 0 14px;
  font-size: 13px;
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

/* Side rail: 3 cards share exactly one screen height, each scrolls internally;
   sticky so the cards stay in view while the post/comments scroll */
.side-rail {
  position: sticky;
  top: calc(var(--nx-app-header-height) + var(--ds-space-4));
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto auto;
  gap: var(--ds-space-4);
  height: calc(100vh - var(--nx-app-header-height) - 3 * var(--ds-space-4));
  min-height: 480px;
  /* Vertical divider separating the sidebar from the main column */
  border-left: 1px solid var(--ds-color-border);
  padding-left: var(--ds-space-5);
}
/* Top Contributors: sized to exactly 5 rows */
.widget--fixed {
  grid-template-rows: auto auto;
}
.widget--fixed .widget-list {
  overflow: visible;
}
/* Popular Hashtags: compact — only as much height as its chips need */
.widget--tags {
  grid-template-rows: auto minmax(0, auto);
}
.widget--tags .widget-list {
  max-height: 160px;
}
@media (max-width: 1199px) {
  .side-rail {
    position: static;
    height: auto;
    grid-template-rows: none;
    border-left: none;
    padding-left: 0;
  }
}
.widget {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--ds-space-3);
  padding: var(--ds-space-4) var(--ds-space-5);
  min-height: 0;
  overflow: hidden;
}
.widget h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: .02em;
}
.widget-list {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--ds-color-border-strong) transparent;
  padding-right: 4px;
  margin-right: -4px;
}
.widget-list::-webkit-scrollbar {
  width: 6px;
}
.widget-list::-webkit-scrollbar-track {
  background: transparent;
}
.widget-list::-webkit-scrollbar-thumb {
  background: var(--ds-color-border-strong);
  border-radius: 3px;
}
@media (max-width: 1199px) {
  .widget-list {
    max-height: 320px;
  }
}
.widget-empty {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-muted);
  padding: var(--ds-space-2) 0;
}

/* trending rows */
.trend-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: var(--ds-space-2);
  padding: 6px 0;
}
.trend-rank {
  font: 700 15px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  text-align: center;
  align-self: start;
  line-height: 1.4;
}
.trend-title {
  width: 100%;
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-size: 13.5px;
  color: var(--ds-color-text-primary);
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (hover: hover) {
  .trend-title:hover {
    color: var(--ds-color-accent);
  }
}
.trend-meta {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  margin-top: 2px;
}

/* member rows */
.member-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 6px 0;
}
.member-rank {
  flex: none;
  width: 20px;
  font: 700 14px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
  text-align: center;
}
.member-info {
  flex: 1;
  min-width: 0;
}
.member-name {
  font-size: 13.5px;
  color: var(--ds-color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-name--link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  text-align: left;
  cursor: pointer;
  display: block;
  width: 100%;
}
@media (hover: hover) {
  .member-name--link:hover {
    color: var(--ds-color-accent);
  }
}
.member-role {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}

/* tag cloud */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(103 232 249 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color var(--ds-duration-fast), color var(--ds-duration-fast);
}
@media (hover: hover) {
  .tag-chip:hover {
    border-color: var(--ds-color-border-strong);
    color: var(--ds-color-text-primary);
  }
}
.tag-chip--static {
  cursor: default;
}
@media (hover: hover) {
  .tag-chip--static:hover {
    border-color: var(--ds-color-border);
    color: var(--ds-color-text-secondary);
  }
}
.tag-count {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
</style>
