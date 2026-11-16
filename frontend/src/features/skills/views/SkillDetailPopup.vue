<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Bell, Star, Eye, ArrowDown, Link as LinkIcon, Share2, FileText, HardDrive, ThumbsUp, MessageCircle } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismTabs from '@/design-system/components/PrismTabs.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismTextarea from '@/design-system/components/PrismTextarea.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import PrismTooltip from '@/design-system/components/PrismTooltip.vue'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import EditSkillPopup from './EditSkillPopup.vue'
import UpdateVersionPopup from './UpdateVersionPopup.vue'
import ShareSkillPopup from './ShareSkillPopup.vue'
import ReputationBadge from '@/features/skills/components/ReputationBadge.vue'
import { reputationApi } from '@/features/gamification/api'
import type { ReputationExplain } from '@/features/gamification/api'
import { skillApi } from '@/features/skills/api'
import McpDetailPopup from '@/features/mcp/views/McpDetailPopup.vue'
import { toUserError } from '@/lib/api'
import { sanitizeHtml } from '@/lib/sanitize'
import { toastError, toastSuccess } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { SkillCommentDTO, SkillDetailResponse, SkillMcpDependencyItem, SkillVersionItem } from '@/features/skills/types'

const props = defineProps<{ skillId: number | null }>()
const emit = defineEmits(['closed', 'changed'])

const auth = useAuthStore()
const { t, formatDate, formatDateTime } = useI18nStore()

const detail = ref<SkillDetailResponse | null>(null)
const loading = ref(false)
const activeTab = ref('info')
const avgRating = ref<number | null>(null)
const totalRatings = ref(0)
const myRating = ref<number | null>(null)
const hoverRating = ref<number | null>(null)
/** Reputation tier + "why this rank?" reasons (cached score, guide §2). */
const reputation = ref<ReputationExplain | null>(null)

const comments = ref<SkillCommentDTO[]>([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentSize = 10
const commentText = ref('')
const sendingComment = ref(false)
// 2026-11-08: reply + like + edit state
const replyTo = ref<SkillCommentDTO | null>(null)
const replyText = ref('')
const sendingReply = ref(false)
const likingIds = ref<Set<number>>(new Set())
const editingId = ref<number | null>(null)
const editText = ref('')
const savingEdit = ref(false)

const rejectOpen = ref(false)
const rejectReason = ref('')
const confirmOpen = ref(false)
const confirmAction = ref<'' | 'withdraw' | 'delete' | 'cancelVersion'>('')
const busy = ref(false)
const editOpen = ref(false)
const updateVersionOpen = ref(false)
const shareOpen = ref(false)
const subscribing = ref(false)

// ── MCPs tab (v1.31 — FR-MCP-015) ───────────────────────────────────────────
const mcpDetailId = ref<number | null>(null)
const downloadWarnOpen = ref(false)
const mcpDependencies = computed(() => detail.value?.mcpDependencies ?? [])
const unavailableMcps = computed(() => mcpDependencies.value.filter((d) => !d.availableForViewer))

// ── 2026-11-07: download hero (Option A + V1 stat chips) ───────────────────
// Hero = combined download (1 zip: skill files + every available MCP).
// Visible whenever the viewer can download and there is something to download.
const heroAvailableMcps = computed(() => mcpDependencies.value.filter((m) => m.availableForViewer))
const heroTotalSize = computed(() =>
  (detail.value?.files ?? []).reduce((sum, f) => sum + (f.fileSize ?? 0), 0)
)
const showHero = computed(
  () => canDownload.value && ((detail.value?.files.length ?? 0) > 0 || heroAvailableMcps.value.length > 0)
)

/** Reason an MCP dependency is not available (v1.31 warning list). */
function mcpUnavailableReason(item: SkillMcpDependencyItem): string {
  if (!item.mcpName) return t('app.skills.mcpUnavailableGone')
  if (item.mcpStatus !== 'APPROVED') return t('app.skills.mcpUnavailableNotApproved')
  return t('app.skills.mcpUnavailableScope')
}

/** v1.32: full inline warning for the quick-view section (wireframe phrasing). */
function mcpUnavailableWarning(item: SkillMcpDependencyItem): string {
  if (!item.mcpName) return t('app.skills.mcpUnavailableGoneWarn')
  if (item.mcpStatus !== 'APPROVED') return t('app.skills.mcpUnavailableNotApprovedWarn')
  return t('app.skills.mcpUnavailableScopeWarn')
}

/** v1.33: download one MCP (all files of the pinned version as a zip).
 *  2026-11-06: via the skill context — works for unapproved MCPs on
 *  PRIVATE/PROJECT skills (internal access). */
function onMcpDownloadOne(item: SkillMcpDependencyItem) {
  if (!props.skillId) return
  skillApi
    .downloadMcpDepVersionAll(props.skillId, item.mcpId, item.mcpVersion)
    .catch((err) => toastError(toUserError(err).message))
}

/** Download All (MCP section) — every available MCP, one zip per MCP. */
function onMcpDownloadAll() {
  const available = mcpDependencies.value.filter((m) => m.availableForViewer)
  available.forEach((m) => onMcpDownloadOne(m))
}

/** 2026-11-06: creator-row button — ONE zip = skill files + every available
 *  MCP dependency (each MCP in its own subfolder). */
function onDownloadAllWithMcps() {
  if (!props.skillId) return
  skillApi
    .downloadAllWithMcps(props.skillId)
    .catch((err) => toastError(toUserError(err).message))
}

// ── Versions tab ────────────────────────────────────────────────────────────
const versions = ref<SkillVersionItem[]>([])
const versionsLoading = ref(false)
const versionsLoaded = ref(false)

let refreshTimer: ReturnType<typeof setInterval> | null = null
let viewRecorded = false

const isCreator = computed(() => detail.value?.isCreator ?? false)
const isAdmin = computed(() => detail.value?.isAdmin ?? false)
const isCowork = computed(
  () => detail.value?.coworkUsers?.some((u) => u.id === auth.user?.userId) ?? false
)
const hasPendingVersion = computed(() => detail.value?.hasPendingVersion ?? false)
// Why the skill is pending (null = legacy submit or version update).
const pendingReason = computed(() => detail.value?.pendingReason ?? null)
const isSharePending = computed(() => pendingReason.value === 'SHARE')
const isScopePending = computed(() => pendingReason.value === 'SCOPE')
// While a version update is pending, the skill still serves its approved
// version to everyone else — it is treated as live (approved) for them.
// Same for a pending share/scope change: the skill stays live for its
// current audience, so it displays as APPROVED to non-creator/non-admin.
const liveStatus = computed(() =>
  (hasPendingVersion.value || pendingReason.value != null)
    && !isCreator.value && !isAdmin.value && !isCowork.value
    ? 'APPROVED' : detail.value?.status ?? ''
)
const isApproved = computed(() => liveStatus.value === 'APPROVED')
const canEdit = computed(() => detail.value?.canEdit ?? false)
const showSubscribe = computed(() => liveStatus.value === 'APPROVED' && !isCreator.value)
const showShare = computed(
  () => detail.value?.scope === 'PROJECT' && (isCreator.value || isAdmin.value)
)
const showUpdateVersion = computed(
  () => isApproved.value && canEdit.value && !hasPendingVersion.value
)
const showCancelVersion = computed(
  () => detail.value?.status === 'PENDING_APPROVAL' && hasPendingVersion.value
    && (isCreator.value || isAdmin.value || isCowork.value)
)
const showVersionRejected = computed(
  () => isApproved.value && !hasPendingVersion.value && !!detail.value?.rejectionReason
)
// A rejected share request: the skill went back to APPROVED but the reason
// is kept so the creator can see why the external shares were rejected.
const showShareRejected = computed(
  () => isApproved.value && !hasPendingVersion.value && pendingReason.value == null
    && !!detail.value?.rejectionReason
)
const descriptionHtml = computed(() => sanitizeHtml(detail.value?.description ?? ''))
const pendingDescriptionHtml = computed(() =>
  sanitizeHtml(detail.value?.pendingVersion?.description ?? '')
)
// Download rule: creator → any status; admin → PENDING_APPROVAL or later;
// other users → APPROVED (or a version-update-pending skill, which stays live).
const canDownload = computed(() => {
  const status = liveStatus.value
  if (!status) return false
  if (isCreator.value) return true
  if (isAdmin.value) return status === 'PENDING_APPROVAL' || status === 'APPROVED'
  return status === 'APPROVED'
})
const commentTotalPages = computed(() => Math.max(1, Math.ceil(commentTotal.value / commentSize)))

const statusLabel = (code: string) =>
  code === 'PENDING_APPROVAL' ? t('app.common.statusPending') : code === 'DRAFT' ? t('app.common.statusDraft') : code === 'APPROVED' ? t('app.common.statusApproved') : code === 'REJECTED' ? t('app.common.statusRejected') : code
const statusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'
const scopeMeta = (code: string) =>
  code === 'PRIVATE' ? { icon: '🔒', label: t('app.skill.scopePrivate') }
    : code === 'PROJECT' ? { icon: '📁', label: t('app.skill.scopeProject') }
    : code === 'PART' ? { icon: '🏛️', label: t('app.skill.scopePart') }
    : code === 'GROUP' ? { icon: '🏢', label: t('app.skill.scopeGroup') }
    : { icon: '🌍', label: t('app.skill.scopePublic') }

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

/** v1.32: total size of the pinned MCP version's files (quick view row). */
function mcpTotalSize(item: SkillMcpDependencyItem): number | null {
  if (!item.files?.length) return null
  return item.files.reduce((sum, f) => sum + (f.fileSize ?? 0), 0)
}

// ── Data loading ────────────────────────────────────────────────────────────
async function loadDetail() {
  if (!props.skillId) return
  try {
    detail.value = await skillApi.detail(props.skillId)
    if (!viewRecorded) {
      viewRecorded = true
      skillApi.recordView(props.skillId).then((r) => {
        if (detail.value) detail.value = { ...detail.value, viewCount: r.viewCount }
      }).catch(() => {})
    }
  } catch (err) {
    if (toUserError(err).code === 'SKILL_NOT_FOUND') {
      toastError(t('app.skill.notFoundOrNoAccess'))
      close()
    } else {
      toastError(t('app.skills.loadErrorDetail'))
    }
  }
}

async function loadRating() {
  if (!props.skillId) return
  try {
    const r = await skillApi.rating(props.skillId)
    avgRating.value = r.totalRatings > 0 ? r.averageRating : null
    totalRatings.value = r.totalRatings
    myRating.value = await skillApi.myRating(props.skillId)
  } catch {
    // non-fatal
  }
  void loadReputation()
}

async function loadReputation() {
  if (!props.skillId) return
  try {
    reputation.value = await reputationApi.explain('SKILL', props.skillId)
  } catch {
    reputation.value = null
  }
}

async function loadComments(resetPage = false) {
  if (!props.skillId) return
  if (resetPage) commentPage.value = 1
  try {
    const r = await skillApi.comments(props.skillId, commentPage.value, commentSize)
    comments.value = r.items
    commentTotal.value = r.totalCount
    if (commentPage.value > commentTotalPages.value) {
      commentPage.value = commentTotalPages.value
      const retry = await skillApi.comments(props.skillId, commentPage.value, commentSize)
      comments.value = retry.items
    }
  } catch {
    // non-fatal
  }
}

async function loadVersions() {
  if (!props.skillId) return
  versionsLoading.value = true
  try {
    versions.value = await skillApi.listVersions(props.skillId)
    versionsLoaded.value = true
  } catch {
    // non-fatal
  } finally {
    versionsLoading.value = false
  }
}

function onVersionsTabShown() {
  if (!versionsLoaded.value) loadVersions()
}

/** Reloads the version list (snapshot changes after approve/reject/cancel). */
function refreshVersions() {
  versionsLoaded.value = false
  if (activeTab.value === 'versions') loadVersions()
}

function openFor(skillId: number) {
  detail.value = null
  activeTab.value = 'info'
  avgRating.value = null
  totalRatings.value = 0
  myRating.value = null
  hoverRating.value = null
  comments.value = []
  commentTotal.value = 0
  commentPage.value = 1
  commentText.value = ''
  replyTo.value = null
  replyText.value = ''
  editingId.value = null
  editText.value = ''
  likingIds.value = new Set()
  rejectOpen.value = false
  rejectReason.value = ''
  confirmOpen.value = false
  editOpen.value = false
  updateVersionOpen.value = false
  versions.value = []
  versionsLoaded.value = false
  subscribing.value = false
  viewRecorded = false
  loading.value = true
  Promise.all([loadDetail(), loadRating(), loadComments()]).finally(() => {
    loading.value = false
  })
  startAutoRefresh()
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    if (document.visibilityState === 'visible' && props.skillId) {
      loadComments(false)
    }
  }, 30000)
}
function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible' && props.skillId && detail.value) {
    loadDetail()
    loadRating()
    loadComments(false)
  }
}

watch(
  () => props.skillId,
  (id) => {
    if (id != null) openFor(id)
  },
  { immediate: true }
)

watch(activeTab, (tab) => {
  if (tab === 'versions') onVersionsTabShown()
})

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
})
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  stopAutoRefresh()
})

function close() {
  stopAutoRefresh()
  detail.value = null
  emit('closed')
}

// ── Rating ──────────────────────────────────────────────────────────────────
function starState(index: number): number {
  const current = hoverRating.value ?? myRating.value ?? 0
  return current >= index ? 1 : current >= index - 0.5 ? 0.5 : 0
}

async function setRating(value: number) {
  if (!props.skillId || !detail.value) return
  if (!isApproved.value) {
    toastError(t('app.skills.onlyApprovedRate'))
    return
  }
  const previous = myRating.value
  myRating.value = value // optimistic
  try {
    const r = await skillApi.upsertRating(props.skillId, value)
    avgRating.value = r.totalRatings > 0 ? r.averageRating : null
    totalRatings.value = r.totalRatings
  } catch (err) {
    myRating.value = previous
    toastError(toUserError(err).message)
  }
}

// ── Comments ────────────────────────────────────────────────────────────────
async function sendComment() {
  if (!props.skillId || !detail.value) return
  const content = commentText.value.trim()
  if (!content) return
  if (!isApproved.value) {
    toastError(t('app.skills.onlyApprovedComment'))
    return
  }
  sendingComment.value = true
  try {
    await skillApi.addComment(props.skillId, content)
    commentText.value = ''
    await loadComments(true)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    sendingComment.value = false
  }
}

// ── 2026-11-08: reply / like / edit ─────────────────────────────────────────
function openReply(target: SkillCommentDTO) {
  replyTo.value = target
  replyText.value = ''
}

function cancelReply() {
  replyTo.value = null
  replyText.value = ''
}

async function sendReply() {
  if (!props.skillId || !replyTo.value) return
  const content = replyText.value.trim()
  if (!content) return
  sendingReply.value = true
  try {
    await skillApi.addComment(props.skillId, content, replyTo.value.id)
    cancelReply()
    await loadComments(false)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    sendingReply.value = false
  }
}

async function toggleLike(c: SkillCommentDTO) {
  if (!props.skillId || likingIds.value.has(c.id)) return
  likingIds.value = new Set(likingIds.value).add(c.id)
  try {
    const r = await skillApi.toggleCommentLike(props.skillId, c.id)
    c.liked = r.liked
    c.likeCount = r.likeCount
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    likingIds.value = new Set([...likingIds.value].filter((id) => id !== c.id))
  }
}

function startEdit(c: SkillCommentDTO) {
  editingId.value = c.id
  editText.value = c.content
}

function cancelEdit() {
  editingId.value = null
  editText.value = ''
}

async function saveEdit(c: SkillCommentDTO) {
  if (!props.skillId) return
  const content = editText.value.trim()
  if (!content) return
  savingEdit.value = true
  try {
    await skillApi.editComment(props.skillId, c.id, content)
    cancelEdit()
    await loadComments(false)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    savingEdit.value = false
  }
}

/** Relative time ("2h ago" style) for comment headers. */
function timeAgo(iso: string): string {
  const d = new Date(iso).getTime()
  if (Number.isNaN(d)) return formatDateTime(iso)
  const diff = Date.now() - d
  const min = Math.floor(diff / 60_000)
  if (min < 1) return t('app.skills.timeJustNow')
  if (min < 60) return t('app.skills.timeMinutesAgo', { n: min })
  const hours = Math.floor(min / 60)
  if (hours < 24) return t('app.skills.timeHoursAgo', { n: hours })
  const days = Math.floor(hours / 24)
  if (days < 30) return t('app.skills.timeDaysAgo', { n: days })
  return formatDate(iso)
}

async function deleteComment(commentId: number) {
  if (!props.skillId || !detail.value) return
  const wasLastOnPage = comments.value.length === 1 && commentPage.value > 1
  try {
    await skillApi.deleteComment(props.skillId, commentId)
    if (wasLastOnPage) commentPage.value -= 1
    await loadComments(false)
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

// ── Workflow ────────────────────────────────────────────────────────────────
async function transition(toStatus: string, rejectionReason?: string) {
  if (!props.skillId) return
  busy.value = true
  try {
    await skillApi.changeStatus(props.skillId, toStatus, rejectionReason)
    await Promise.all([loadDetail(), loadRating()])
    refreshVersions()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
    return false
  } finally {
    busy.value = false
  }
  return true
}

function onSubmit() {
  void transition('PENDING_APPROVAL').then((ok) => {
    if (ok) toastSuccess(t('app.skills.reviewSubmitted'))
  })
}
function onApprove() {
  const wasVersionUpdate = hasPendingVersion.value
  void transition('APPROVED').then((ok) => {
    if (ok) toastSuccess(wasVersionUpdate ? t('app.skills.versionApproved') : t('app.skills.skillApproved'))
  })
}
function onRejectConfirm() {
  const reason = rejectReason.value.trim()
  if (!reason || reason.length > 500) {
    toastError(t('app.skills.rejectReasonRequired'))
    return
  }
  const wasVersionUpdate = hasPendingVersion.value
  void transition('REJECTED', reason).then((ok) => {
    if (ok) {
      toastSuccess(wasVersionUpdate ? t('app.skills.versionRejected') : t('app.skills.skillRejected'))
      rejectOpen.value = false
      rejectReason.value = ''
    }
  })
}
function onWithdrawConfirm() {
  void transition('DRAFT').then((ok) => {
    if (ok) {
      toastSuccess(t('app.skills.withdrawn'))
      confirmOpen.value = false
    }
  })
}
function onDeleteConfirm() {
  if (!props.skillId) return
  busy.value = true
  skillApi
    .deleteBatch([props.skillId])
    .then(() => {
      toastSuccess(t('app.skills.skillDeleted'))
      confirmOpen.value = false
      close()
      emit('changed')
    })
    .catch((err) => toastError(toUserError(err).message))
    .finally(() => {
      busy.value = false
    })
}

function onEditSaved() {
  void Promise.all([loadDetail(), loadRating()])
  refreshVersions()
  emit('changed')
}

function onUpdateVersionSaved() {
  void Promise.all([loadDetail(), loadRating()])
  refreshVersions()
  emit('changed')
}

async function onCancelVersionConfirm() {
  if (!props.skillId) return
  busy.value = true
  try {
    await skillApi.cancelVersionUpdate(props.skillId)
    toastSuccess(t('app.skills.versionCancelled'))
    confirmOpen.value = false
    await Promise.all([loadDetail(), loadRating()])
    refreshVersions()
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    busy.value = false
  }
}

function onDownloadVersionFile(version: number, fileId: number) {
  if (!props.skillId) return
  skillApi
    .downloadVersionFile(props.skillId, version, fileId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadVersionAll(version: number) {
  if (!props.skillId) return
  skillApi
    .downloadVersionAll(props.skillId, version)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadPendingFile(fileId: number) {
  if (!props.skillId) return
  skillApi
    .downloadPendingFile(props.skillId, fileId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadPendingAll() {
  if (!props.skillId) return
  skillApi
    .downloadPendingAll(props.skillId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownload(fileId: number) {
  if (!props.skillId) return
  skillApi
    .download(props.skillId, fileId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadAll() {
  if (!props.skillId) return
  // v1.31 (FR-MCP-015): warn when some MCP dependencies are not available.
  if (unavailableMcps.value.length) {
    downloadWarnOpen.value = true
    return
  }
  doDownloadAll()
}

function doDownloadAll() {
  if (!props.skillId) return
  skillApi
    .downloadAll(props.skillId)
    .catch((err) => toastError(toUserError(err).message))
}

function onEdit() {
  editOpen.value = true
}

// ── Copy detail link ─────────────────────────────────────────────────────────
// Deep link: /skills?detail={id} — the target user opens the popup directly
// (visibility rules still apply server-side; not-visible skills 404).
function copyLink() {
  if (!props.skillId) return
  const url = `${window.location.origin}/skills?detail=${props.skillId}`
  const done = () => toastSuccess(t('app.skill.copyLinkCopied'))
  const fallback = () => {
    // Clipboard API needs a secure context (https/localhost) — fall back
    // to the legacy API when it is unavailable.
    const ta = document.createElement('textarea')
    ta.value = url
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      done()
    } catch {
      toastError(t('app.skill.copyLinkFailed'))
    }
    document.body.removeChild(ta)
  }
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(url).then(done).catch(fallback)
  } else {
    fallback()
  }
}

async function toggleSubscribe() {
  if (!props.skillId || !detail.value) return
  subscribing.value = true
  const wasSubscribed = detail.value.subscribed
  try {
    if (wasSubscribed) {
      await skillApi.unsubscribe(props.skillId)
      detail.value = { ...detail.value, subscribed: false }
      toastSuccess(t('app.skills.unsubscribedToast'))
    } else {
      await skillApi.subscribe(props.skillId)
      detail.value = { ...detail.value, subscribed: true }
      toastSuccess(t('app.skills.subscribedToast'))
    }
    emit('changed')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    subscribing.value = false
  }
}
</script>

<template>
  <PrismDialog
    :model-value="skillId != null"
    :title="t('app.skills.detailTitle')"
    size="lg"
    dialog-class="skill-form-dialog"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && close()"
    @close="close"
  >
    <div v-if="loading || !detail" class="dtl-loading">{{ t('app.common.loading') }}</div>
    <div v-else class="dtl">
      <!-- 2026-11-07: Download hero (Option A + V1 stat chips) — the first
           thing the viewer sees. Clicking anywhere in the hero downloads the
           combined zip (skill files + every available MCP). -->
      <div
        v-if="showHero"
        class="dtl-hero"
        role="button"
        tabindex="0"
        :title="t('app.skills.downloadAllWithMcpsTip')"
        @click="onDownloadAllWithMcps"
        @keydown.enter.prevent="onDownloadAllWithMcps"
        @keydown.space.prevent="onDownloadAllWithMcps"
      >
        <div class="dtl-hero-ic" aria-hidden="true">
          <ArrowDown :size="20" />
        </div>
        <div class="dtl-hero-slogan">
          {{ t('app.skills.heroSlogan') }}
          <span class="dtl-hero-sub">{{ t('app.skills.heroSub') }}</span>
        </div>
        <div class="dtl-hero-chips">
          <span class="dtl-hero-chip">
            <FileText :size="12" aria-hidden="true" />
            <b>{{ detail.files.length }}</b> {{ t('app.skills.chipFiles') }}
          </span>
          <span class="dtl-hero-chip">
            <HardDrive :size="12" aria-hidden="true" />
            <b>{{ formatSize(heroTotalSize) }}</b>
          </span>
          <span v-if="heroAvailableMcps.length" class="dtl-hero-chip">
            <LinkIcon :size="12" aria-hidden="true" />
            <b>{{ heroAvailableMcps.length }}</b> {{ t('app.skills.chipMcp') }}
          </span>
        </div>
        <button
          type="button"
          class="dtl-hero-cta"
          :title="t('app.skills.downloadAllWithMcpsTip')"
          @click.stop="onDownloadAllWithMcps"
        >
          <ArrowDown :size="16" aria-hidden="true" />
          {{ t('app.skills.downloadAll') }}
        </button>
      </div>

      <!-- 2026-11-07 (Option A): header card — row 1 = name + status + actions,
           row 2 = scope, system, creator, dates, views, downloads, rating + stars. -->
      <div class="dtl-header-card">
        <div class="dtl-header1">
          <h3 class="dtl-name">{{ detail.name }}</h3>
          <PrismBadge :tone="statusTone(liveStatus)">{{ statusLabel(liveStatus) }}</PrismBadge>
          <div class="dtl-actions">
            <button
              v-if="showSubscribe"
              type="button"
              class="dtl-subscribe"
              :class="{ 'is-subscribed': detail.subscribed }"
              :disabled="subscribing"
              @click="toggleSubscribe"
            >
              <Bell :size="14" :fill="detail.subscribed ? 'currentColor' : 'none'" aria-hidden="true" />
              {{ detail.subscribed ? t('app.skills.subscribed') : t('app.skills.colSubscribe') }}
            </button>
            <button
              v-if="showShare"
              type="button"
              class="dtl-copy-link"
              :title="t('app.skill.shareTooltip')"
              :aria-label="t('app.skill.shareTooltip')"
              @click="shareOpen = true"
            >
              <Share2 :size="14" aria-hidden="true" />
              {{ t('app.skill.shareButton') }}
            </button>
            <button
              type="button"
              class="dtl-copy-link"
              :title="t('app.skill.copyLinkTooltip')"
              :aria-label="t('app.skill.copyLinkTooltip')"
              @click="copyLink"
            >
              <LinkIcon :size="14" aria-hidden="true" />
              {{ t('app.skill.copyLink') }}
            </button>
          </div>
        </div>
        <div class="dtl-header-strip">
          <span class="dtl-scope">{{ scopeMeta(detail.scope).icon }} {{ scopeMeta(detail.scope).label }}</span>
          <span v-if="detail.systemKey" class="dtl-scope" :title="detail.systemKey">
            🖥️ {{ detail.systemName ?? detail.systemKey }}<template v-if="detail.subSystemCode"> / {{ detail.subSystemName ?? detail.subSystemCode }}</template>
          </span>
          <span class="dtl-scope">{{ t('app.skills.creator', { name: detail.creatorName ?? '—' }) }}</span>
          <span class="dtl-scope">{{ t('app.skills.createdUpdated', { created: formatDate(detail.createdAt), updated: formatDate(detail.updatedAt) }) }}</span>
          <!-- 2026-11-07: views / downloads / rating pushed to the right side. -->
          <div class="dtl-header-stats">
            <PrismTooltip :content="t('app.common.views', { n: detail.viewCount })">
              <span class="dtl-stat-icon" :aria-label="t('app.skills.colViews')">
                <Eye :size="15" aria-hidden="true" />
                {{ detail.viewCount }}
              </span>
            </PrismTooltip>
            <PrismTooltip :content="t('app.common.downloads', { n: detail.downloadCount })">
              <span class="dtl-stat-icon" :aria-label="t('app.skills.colDownloads')">
                <ArrowDown :size="15" aria-hidden="true" />
                {{ detail.downloadCount }}
              </span>
            </PrismTooltip>
            <span v-if="avgRating != null" class="dtl-rating">
              ★ {{ avgRating.toFixed(1) }}/5 ({{ totalRatings }})
            </span>
            <span v-else class="dtl-norating">{{ t('app.common.noRating') }}</span>
            <span v-if="reputation" class="dtl-reputation">
              <ReputationBadge :tier="reputation.tier" :score="reputation.score" />
              <PrismTooltip :content="reputation.reasons.join('\n')">
                <span class="dtl-reputation-why">Vì sao?</span>
              </PrismTooltip>
            </span>
            <!-- Star rating (editable when APPROVED) -->
            <div class="dtl-rate" :class="{ 'is-disabled': !isApproved }">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="dtl-star"
                :disabled="!isApproved"
                :aria-label="t('app.skills.rateStars', { n })"
                @mouseenter="isApproved && (hoverRating = n)"
                @mouseleave="hoverRating = null"
                @click="setRating(n)"
              >
                <Star :size="18" :fill="starState(n) === 0 ? 'none' : 'currentColor'" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 2026-11-07 (Option A): files/MCP/tags/categories/stages/cowork moved
           into the Overview tab sidebar (2-column layout). -->

      <!-- Banners -->
      <PrismAlert v-if="detail.status === 'PENDING_APPROVAL' && pendingReason === 'SHARE'" tone="warning">
        {{ t('app.skill.sharePendingAlert') }}
      </PrismAlert>
      <PrismAlert v-else-if="detail.status === 'PENDING_APPROVAL' && pendingReason === 'SCOPE'" tone="warning">
        {{ t('app.skill.scopePendingAlert', { scope: scopeMeta(detail.pendingScope ?? detail.scope).label }) }}
      </PrismAlert>
      <PrismAlert v-else-if="detail.status === 'PENDING_APPROVAL' && !hasPendingVersion" tone="warning">
        {{ t('app.skills.pendingBanner') }}
      </PrismAlert>

      <!-- Pending shares (what the admin is being asked to approve) -->
      <div v-if="detail.status === 'PENDING_APPROVAL' && pendingReason === 'SHARE' && detail.pendingShares.length" class="dtl-pending-shares">
        <div class="dtl-pending-shares-head">
          <span class="dtl-pending-title">{{ t('app.skill.pendingSharesTitle') }}</span>
          <PrismBadge tone="warning">{{ t('app.skill.pendingSharesCount', { count: detail.pendingShares.length }) }}</PrismBadge>
        </div>
        <div v-for="s in detail.pendingShares" :key="s.id" class="dtl-pending-share-row">
          <span class="dtl-pending-share-type">{{ s.shareType === 'USER' ? t('app.skill.shareTypeUser') : t('app.skill.shareTypeProjectKey') }}</span>
          <span class="dtl-pending-share-target">
            {{ s.shareType === 'USER' ? (s.userName ?? `User #${s.userId}`) : (s.projectKey ?? '—') }}
          </span>
        </div>
      </div>
      <PrismAlert v-if="detail.status === 'PENDING_APPROVAL' && hasPendingVersion && (isCreator || isAdmin || isCowork)" tone="warning">
        {{ t('app.skills.pendingVersionBanner') }}
      </PrismAlert>
      <PrismAlert v-if="detail.status === 'REJECTED' && detail.rejectionReason" tone="danger">
        {{ t('app.skills.rejectionReason', { reason: detail.rejectionReason }) }}
      </PrismAlert>
      <PrismAlert v-if="showShareRejected" tone="warning">
        {{ t('app.skill.shareRejectedAlert', { reason: detail.rejectionReason ?? '' }) }}
      </PrismAlert>
      <PrismAlert v-else-if="showVersionRejected" tone="warning">
        {{ t('app.skills.lastRejected', { reason: detail.rejectionReason ?? '' }) }}
      </PrismAlert>

      <!-- Pending version (under review) — creator/admin/cowork only -->
      <div v-if="hasPendingVersion && detail.pendingVersion && (isCreator || isAdmin || isCowork)" class="dtl-pending">
        <div class="dtl-pending-head">
          <span class="dtl-pending-title">{{ t('app.skills.pendingVersion', { n: detail.version + 1 }) }}</span>
          <PrismBadge tone="warning">{{ t('app.skills.underReview') }}</PrismBadge>
        </div>
        <div class="dtl-pending-meta">
          <span class="dtl-pending-name">{{ detail.pendingVersion.name }}</span>
          <span>{{ scopeMeta(detail.pendingVersion.publicScope).icon }} {{ scopeMeta(detail.pendingVersion.publicScope).label }}</span>
        </div>
        <div v-if="detail.pendingVersion.categories.length" class="dtl-badges">
          <span class="dtl-badges-label">{{ t('app.skills.categories') }}</span>
          <span class="dtl-badges-row">
            <span v-for="c in detail.pendingVersion.categories" :key="c" class="dtl-badge dtl-badge--cat">{{ c }}</span>
          </span>
        </div>
        <div v-if="detail.pendingVersion.stages.length" class="dtl-badges">
          <span class="dtl-badges-label">{{ t('app.skills.processStages') }}</span>
          <span class="dtl-badges-row">
            <span v-for="st in detail.pendingVersion.stages" :key="st" class="dtl-badge dtl-badge--stage">{{ st }}</span>
          </span>
        </div>
        <div v-if="detail.pendingVersion.hashtags.length" class="dtl-hashtags">
          <span v-for="tag in detail.pendingVersion.hashtags" :key="tag" class="dtl-hashtag">#{{ tag }}</span>
        </div>
        <div v-if="pendingDescriptionHtml" class="dtl-desc dtl-pending-desc" v-html="pendingDescriptionHtml"></div>
        <div v-if="detail.pendingVersion.newFiles.length" class="dtl-pending-files">
          <div class="dtl-pending-files-head">
            <span class="dtl-badges-label">{{ t('app.skills.newFiles') }}</span>
            <button type="button" class="dtl-file-download is-all" @click="onDownloadPendingAll">
              {{ t('app.skills.downloadAll') }}
            </button>
          </div>
          <div v-for="f in detail.pendingVersion.newFiles" :key="f.id" class="dtl-file">
            <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
            <button type="button" class="dtl-file-download" @click="onDownloadPendingFile(f.id)">{{ t('app.common.download') }}</button>
          </div>
        </div>
        <div v-if="detail.pendingVersion.removedFiles.length" class="dtl-pending-files">
          <span class="dtl-badges-label">{{ t('app.skills.filesToRemove') }}</span>
          <div v-for="f in detail.pendingVersion.removedFiles" :key="f.id" class="dtl-file dtl-file--removed">
            <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <PrismTabs
        v-model="activeTab"
        :items="[
          { value: 'info', label: t('app.skills.tabOverview') },
          { value: 'versions', label: t('app.skills.tabVersions') },
          { value: 'comments', label: t('app.skills.tabComments', { n: commentTotal }) },
        ]"
      >
        <template #info>
          <!-- 2026-11-07 (Option A): 2-column Overview — description left,
               files/MCP/tags/categories/stages/cowork sidebar right. -->
          <div class="dtl-overview">
            <div class="dtl-overview-main">
              <div v-if="descriptionHtml" class="dtl-desc" v-html="descriptionHtml"></div>
              <div v-else class="dtl-desc">{{ t('app.skills.noDescription') }}</div>
            </div>
            <div class="dtl-overview-side">
              <!-- Files -->
              <div v-if="detail.files.length" class="dtl-mcpdeps">
                <div class="dtl-mcpdeps-head">
                  <span class="dtl-mcpdeps-title">📄 Skills ({{ detail.files.length }})</span>
                  <button v-if="canDownload" type="button" class="dtl-file-download is-all" @click="onDownloadAll">
                    {{ t('app.skills.downloadSkillFiles') }}
                  </button>
                </div>
                <div v-for="f in detail.files" :key="f.id" class="dtl-file">
                  <!-- 2026-11-07: per-file download temporarily removed — use the
                       section "Download Skill" pill or the hero "Download All". -->
                  <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
                </div>
              </div>
              <!-- v1.33 (FR-MCP-015): MCP Dependencies quick view -->
              <div v-if="mcpDependencies.length" class="dtl-mcpdeps">
                <div class="dtl-mcpdeps-head">
                  <span class="dtl-mcpdeps-title">🔗 MCPs ({{ mcpDependencies.length }})</span>
                  <button
                    v-if="mcpDependencies.some((m) => m.availableForViewer)"
                    type="button"
                    class="dtl-file-download is-all"
                    @click="onMcpDownloadAll"
                  >{{ t('app.skills.downloadMcps') }}</button>
                </div>
                <div v-for="m in mcpDependencies" :key="m.mcpId" class="dtl-mcpdep">
                  <div class="dtl-file">
                    <button
                      v-if="m.mcpVisibleToViewer && !m.mcpDeleted"
                      type="button"
                      class="dtl-file-name mcp-name-link"
                      :title="m.mcpName ?? m.mcpCode"
                      @click="mcpDetailId = m.mcpId"
                    >🔗 {{ m.mcpName ?? m.mcpCode }} ({{ formatSize(mcpTotalSize(m)) }})</button>
                    <span v-else class="dtl-file-name" :title="m.mcpName ?? m.mcpCode">🔗 {{ m.mcpName ?? m.mcpCode }} ({{ formatSize(mcpTotalSize(m)) }})</span>
                    <!-- 2026-11-07: per-MCP download temporarily removed — use the
                         section "Download MCP" pill or the hero "Download All". -->
                    <span v-if="m.mcpDeleted" class="dtl-mcp-deleted-badge" :title="t('app.skills.mcpDeletedTip')">
                      {{ t('app.skills.mcpDeleted') }}
                    </span>
                    <!-- 2026-11-08: status badge — an unapproved MCP is flagged
                         right next to its name (the warning line below is easy
                         to miss). -->
                    <span v-else-if="m.mcpStatus !== 'APPROVED'" class="dtl-mcp-status-badge" :title="mcpUnavailableWarning(m)">
                      {{ t('app.skills.mcpUnavailableNotApproved') }}
                    </span>
                  </div>
                  <p v-if="!m.availableForViewer" class="dtl-mcp-warn">
                    {{ mcpUnavailableWarning(m) }}
                  </p>
                </div>
              </div>
              <!-- Tags -->
              <div v-if="detail.hashtags.length" class="dtl-side-block">
                <div class="dtl-side-label">{{ t('app.common.tags') }}</div>
                <div class="dtl-hashtags">
                  <span v-for="tag in detail.hashtags" :key="tag" class="dtl-hashtag">#{{ tag }}</span>
                </div>
              </div>
              <!-- Categories & process stages -->
              <div v-if="detail.categories.length" class="dtl-side-block">
                <div class="dtl-side-label">{{ t('app.skills.categories') }}</div>
                <span class="dtl-badges-row">
                  <span v-for="c in detail.categories" :key="c" class="dtl-badge dtl-badge--cat">{{ c }}</span>
                </span>
              </div>
              <div v-if="detail.stages.length" class="dtl-side-block">
                <div class="dtl-side-label">{{ t('app.skills.processStages') }}</div>
                <span class="dtl-badges-row">
                  <span v-for="st in detail.stages" :key="st" class="dtl-badge dtl-badge--stage">{{ st }}</span>
                </span>
              </div>
              <!-- Cowork -->
              <div v-if="detail.coworkUsers.length" class="dtl-side-block">
                <div class="dtl-side-label">{{ t('app.common.cowork') }}</div>
                <span class="dtl-badges-row">
                  <span v-for="u in detail.coworkUsers" :key="u.id" class="dtl-badge dtl-badge--cowork">{{ u.name ?? '—' }}</span>
                </span>
              </div>
            </div>
          </div>
        </template>
        <template #versions>
          <div class="dtl-versions">
            <p v-if="versionsLoading" class="dtl-nocomments">{{ t('app.common.loading') }}</p>
            <template v-else>
              <div class="dtl-version dtl-version--current">
                <div class="dtl-version-head">
                  <span class="dtl-version-num">{{ t('app.skills.version', { n: detail.version }) }}</span>
                  <PrismBadge tone="success">{{ t('app.skills.current') }}</PrismBadge>
                </div>
                <div class="dtl-version-meta">
                  <span>{{ detail.name }}</span>
                  <span>{{ scopeMeta(detail.scope).icon }} {{ scopeMeta(detail.scope).label }}</span>
                  <span>{{ t('app.skills.updated', { date: formatDate(detail.updatedAt) }) }}</span>
                </div>
                <!-- 2026-11-08: current version shows its files + downloads
                     again, same layout as the older versions below. -->
                <div v-if="detail.files.length" class="dtl-version-files">
                  <div v-if="canDownload" class="dtl-version-files-head">
                    <button type="button" class="dtl-file-download is-all" @click="onDownloadAll">
                      {{ t('app.skills.downloadAll') }}
                    </button>
                  </div>
                  <div v-for="f in detail.files" :key="f.id" class="dtl-version-file">
                    <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
                    <button
                      v-if="canDownload"
                      type="button"
                      class="dtl-file-download"
                      @click="onDownload(f.id)"
                    >{{ t('app.common.download') }}</button>
                  </div>
                </div>
              </div>
              <p v-if="versions.length === 0" class="dtl-nocomments">
                {{ t('app.skills.noVersions') }}
              </p>
              <div v-for="v in versions" :key="v.version" class="dtl-version">
                <div class="dtl-version-head">
                  <span class="dtl-version-num">{{ t('app.skills.version', { n: v.version }) }}</span>
                </div>
                <div class="dtl-version-meta">
                  <span>{{ v.name }}</span>
                  <span>{{ scopeMeta(v.publicScope).icon }} {{ scopeMeta(v.publicScope).label }}</span>
                  <span>{{ t('app.skills.creator', { name: v.creatorName ?? '—' }) }}</span>
                  <span>{{ formatDate(v.createdAt) }}</span>
                </div>
                <div v-if="v.files.length" class="dtl-version-files">
                  <div v-if="canDownload" class="dtl-version-files-head">
                    <button type="button" class="dtl-file-download is-all" @click="onDownloadVersionAll(v.version)">
                      {{ t('app.skills.downloadAll') }}
                    </button>
                  </div>
                  <div v-for="f in v.files" :key="f.id" class="dtl-version-file">
                    <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
                    <button
                      v-if="canDownload"
                      type="button"
                      class="dtl-file-download"
                      @click="onDownloadVersionFile(v.version, f.id)"
                    >{{ t('app.common.download') }}</button>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </template>
        <template #comments>
          <!-- Comment list (2026-11-08 v2: avatar, relative time, likes,
               depth-2 replies, self-edit, own-comment highlight). -->
          <div class="dtl-comments">
            <div v-if="comments.length === 0" class="dtl-comments-empty">
              <MessageCircle :size="28" aria-hidden="true" />
              <p class="dtl-comments-empty-title">{{ t('app.skills.noComments') }}</p>
              <p class="dtl-comments-empty-hint">{{ t('app.skills.commentEmptyHint') }}</p>
            </div>
            <ul v-else class="dtl-comment-list">
              <li v-for="c in comments" :key="c.id" class="dtl-comment" :class="{ 'is-own': c.own }">
                <PrismAvatar :name="c.userName ?? ''" :src="c.avatarUrl ?? undefined" :size="32" />
                <div class="dtl-comment-body">
                  <div class="dtl-comment-head">
                    <span class="dtl-comment-user">{{ c.userName ?? t('app.common.unknown') }}</span>
                    <span class="dtl-comment-time" :title="formatDateTime(c.createdAt)">{{ timeAgo(c.createdAt) }}</span>
                    <span v-if="c.updatedAt" class="dtl-comment-edited">{{ t('app.skills.commentEdited') }}</span>
                    <span class="dtl-comment-actions">
                      <button
                        type="button"
                        class="dtl-comment-like"
                        :class="{ 'is-liked': c.liked }"
                        :disabled="likingIds.has(c.id)"
                        :aria-label="t('app.skills.commentHelpful')"
                        @click="toggleLike(c)"
                      >
                        <ThumbsUp :size="13" aria-hidden="true" /> {{ c.likeCount }}
                      </button>
                      <button type="button" class="dtl-comment-linkbtn" @click="openReply(c)">
                        {{ t('app.skills.commentReply') }}
                      </button>
                      <button v-if="c.canEdit" type="button" class="dtl-comment-linkbtn" @click="startEdit(c)">
                        {{ t('app.common.edit') }}
                      </button>
                      <button v-if="c.canDelete" type="button" class="dtl-comment-linkbtn is-danger" :aria-label="t('app.skills.deleteCommentAria')" @click="deleteComment(c.id)">
                        {{ t('app.common.delete') }}
                      </button>
                    </span>
                  </div>
                  <div v-if="editingId === c.id" class="dtl-comment-edit">
                    <PrismTextarea v-model="editText" :rows="2" :maxlength="1000" />
                    <span class="dtl-comment-edit-actions">
                      <PrismButton :loading="savingEdit" :disabled="!editText.trim()" @click="saveEdit(c)">{{ t('app.common.save') }}</PrismButton>
                      <PrismButton variant="secondary" @click="cancelEdit">{{ t('app.common.cancel') }}</PrismButton>
                    </span>
                  </div>
                  <p v-else class="dtl-comment-content">{{ c.content }}</p>

                  <!-- Replies (depth 2, chronological) -->
                  <ul v-if="c.replies.length" class="dtl-comment-replies">
                    <li v-for="r in c.replies" :key="r.id" class="dtl-comment is-reply" :class="{ 'is-own': r.own }">
                      <PrismAvatar :name="r.userName ?? ''" :src="r.avatarUrl ?? undefined" :size="26" />
                      <div class="dtl-comment-body">
                        <div class="dtl-comment-head">
                          <span class="dtl-comment-user">{{ r.userName ?? t('app.common.unknown') }}</span>
                          <span class="dtl-comment-time" :title="formatDateTime(r.createdAt)">{{ timeAgo(r.createdAt) }}</span>
                          <span v-if="r.updatedAt" class="dtl-comment-edited">{{ t('app.skills.commentEdited') }}</span>
                          <span class="dtl-comment-actions">
                            <button
                              type="button"
                              class="dtl-comment-like"
                              :class="{ 'is-liked': r.liked }"
                              :disabled="likingIds.has(r.id)"
                              :aria-label="t('app.skills.commentHelpful')"
                              @click="toggleLike(r)"
                            >
                              <ThumbsUp :size="13" aria-hidden="true" /> {{ r.likeCount }}
                            </button>
                            <button v-if="r.canEdit" type="button" class="dtl-comment-linkbtn" @click="startEdit(r)">
                              {{ t('app.common.edit') }}
                            </button>
                            <button v-if="r.canDelete" type="button" class="dtl-comment-linkbtn is-danger" :aria-label="t('app.skills.deleteCommentAria')" @click="deleteComment(r.id)">
                              {{ t('app.common.delete') }}
                            </button>
                          </span>
                        </div>
                        <div v-if="editingId === r.id" class="dtl-comment-edit">
                          <PrismTextarea v-model="editText" :rows="2" :maxlength="1000" />
                          <span class="dtl-comment-edit-actions">
                            <PrismButton :loading="savingEdit" :disabled="!editText.trim()" @click="saveEdit(r)">{{ t('app.common.save') }}</PrismButton>
                            <PrismButton variant="secondary" @click="cancelEdit">{{ t('app.common.cancel') }}</PrismButton>
                          </span>
                        </div>
                        <p v-else class="dtl-comment-content">{{ r.content }}</p>
                      </div>
                    </li>
                  </ul>

                  <!-- Inline reply form -->
                  <div v-if="replyTo?.id === c.id" class="dtl-reply-form">
                    <PrismTextarea
                      v-model="replyText"
                      :rows="2"
                      :maxlength="1000"
                      :placeholder="t('app.skills.replyTo', { name: c.userName ?? '' })"
                    />
                    <span class="dtl-comment-edit-actions">
                      <PrismButton :loading="sendingReply" :disabled="!replyText.trim()" @click="sendReply">{{ t('app.skills.commentReply') }}</PrismButton>
                      <PrismButton variant="secondary" @click="cancelReply">{{ t('app.common.cancel') }}</PrismButton>
                    </span>
                  </div>
                </div>
              </li>
            </ul>
            <PrismPagination
              v-if="comments.length > 0"
              :page="commentPage"
              :page-size="commentSize"
              :total="commentTotal"
              @update:page="(p) => { commentPage = p; loadComments(false) }"
            />
          </div>

          <!-- Comment form (PUBLISHED only) -->
          <div v-if="isApproved" class="dtl-comment-form">
            <PrismTextarea
              v-model="commentText"
              :rows="3"
              :maxlength="1000"
              :placeholder="t('app.skills.writeComment')"
            />
            <div class="dtl-comment-form-actions">
              <PrismButton :disabled="!commentText.trim()" :loading="sendingComment" @click="sendComment">{{ t('app.skills.send') }}</PrismButton>
            </div>
          </div>
        </template>
      </PrismTabs>
    </div>

    <!-- Footer -->
    <template #footer>
      <template v-if="detail?.status === 'DRAFT' || detail?.status === 'REJECTED'">
        <PrismButton v-if="canEdit" variant="secondary" @click="onEdit">{{ t('app.common.edit') }}</PrismButton>
        <PrismButton v-if="canEdit" :loading="busy" @click="onSubmit">{{ t('app.skills.submitForReview') }}</PrismButton>
        <PrismButton v-if="isCreator || isAdmin" variant="danger" @click="confirmAction = 'delete'; confirmOpen = true">{{ t('app.common.delete') }}</PrismButton>
        <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
      </template>
      <template v-else-if="detail?.status === 'PENDING_APPROVAL'">
        <template v-if="hasPendingVersion && pendingReason == null">
          <template v-if="isAdmin">
            <PrismButton :loading="busy" @click="onApprove">{{ t('app.skills.approveVersion') }}</PrismButton>
            <PrismButton variant="danger" @click="rejectOpen = true">{{ t('app.skills.rejectVersion') }}</PrismButton>
            <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'cancelVersion'; confirmOpen = true">{{ t('app.skills.cancelVersionUpdate') }}</PrismButton>
            <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
          </template>
          <template v-else-if="isCreator || isCowork">
            <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'cancelVersion'; confirmOpen = true">{{ t('app.skills.cancelVersionUpdate') }}</PrismButton>
            <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
          </template>
          <PrismButton v-else variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
        </template>
        <template v-else-if="isAdmin">
          <PrismButton :loading="busy" @click="onApprove">{{ t('app.skills.approve') }}</PrismButton>
          <PrismButton variant="danger" @click="rejectOpen = true">{{ t('app.skills.reject') }}</PrismButton>
          <PrismButton v-if="isCreator" variant="secondary" :loading="busy" @click="confirmAction = 'withdraw'; confirmOpen = true">{{ t('app.skills.withdraw') }}</PrismButton>
          <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
        </template>
        <template v-else-if="isCreator">
          <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'withdraw'; confirmOpen = true">{{ t('app.skills.withdraw') }}</PrismButton>
          <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
        </template>
        <PrismButton v-else variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
      </template>
      <template v-else-if="detail?.status === 'APPROVED'">
        <PrismButton v-if="canEdit" variant="secondary" @click="onEdit">{{ t('app.common.edit') }}</PrismButton>
        <PrismButton v-if="showUpdateVersion" :loading="busy" @click="updateVersionOpen = true">{{ t('app.skills.updateVersion') }}</PrismButton>
        <PrismButton v-if="isCreator || isAdmin" variant="danger" @click="confirmAction = 'delete'; confirmOpen = true">{{ t('app.common.delete') }}</PrismButton>
        <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
      </template>
    </template>
  </PrismDialog>

  <!-- Edit skill -->
  <EditSkillPopup
    v-model:open="editOpen"
    :skill-id="props.skillId"
    :can-manage-files="detail?.status !== 'APPROVED'"
    @saved="onEditSaved"
  />

  <!-- Update version -->
  <UpdateVersionPopup
    v-model:open="updateVersionOpen"
    :skill-id="props.skillId"
    @saved="onUpdateVersionSaved"
  />

  <!-- Share (PROJECT scope) -->
  <ShareSkillPopup v-model:open="shareOpen" :skill-id="props.skillId" :skill-status="detail?.status ?? null" :admin-reviewed="detail?.adminReviewed ?? false" />

  <!-- v1.31 (FR-MCP-015): MCP Detail Popup (opened from the MCP name link in the MCPs tab) -->
  <McpDetailPopup
    v-if="mcpDetailId !== null"
    :mcpId="mcpDetailId"
    @close="mcpDetailId = null"
  />

  <!-- v1.31 (FR-MCP-015): Download All warning when some MCPs are not available -->
  <PrismDialog
    v-model="downloadWarnOpen"
    :title="t('app.skills.mcpDepsWarnTitle')"
    size="sm"
    :close-on-overlay="false"
  >
    <p class="dtl-confirm-text">
      {{ t('app.skills.mcpDepsWarnDesc') }}
    </p>
    <ul class="dtl-mcp-warn-list">
      <li v-for="m in unavailableMcps" :key="m.mcpId">
        {{ m.mcpName ?? m.mcpCode }} v{{ m.mcpVersion }} — {{ mcpUnavailableReason(m) }}
      </li>
    </ul>
    <p class="dtl-confirm-text">
      {{ t('app.skills.mcpDepsWarnNote') }}
    </p>
    <template #footer>
      <PrismButton variant="secondary" @click="downloadWarnOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton @click="downloadWarnOpen = false; doDownloadAll()">{{ t('app.skills.downloadAnyway') }}</PrismButton>
    </template>
  </PrismDialog>

  <!-- Reject reason dialog -->
  <PrismDialog v-model="rejectOpen" :title="hasPendingVersion ? t('app.skills.rejectTitle') : t('app.skills.rejectTitleSkill')" size="sm" :close-on-overlay="false">
    <PrismTextarea v-model="rejectReason" :rows="3" :maxlength="500" :placeholder="t('app.skills.rejectReasonPh')" />
    <template #footer>
      <PrismButton variant="secondary" @click="rejectOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton variant="danger" :loading="busy" @click="onRejectConfirm">{{ t('app.common.confirm') }}</PrismButton>
    </template>
  </PrismDialog>

  <!-- Confirm dialog (withdraw / delete / cancel version) -->
  <PrismDialog
    v-model="confirmOpen"
    :title="confirmAction === 'withdraw' ? t('app.skills.withdrawTitle') : confirmAction === 'cancelVersion' ? t('app.skills.cancelUpdateTitle') : t('app.skills.deleteSkillTitle')"
    size="sm"
    :close-on-overlay="false"
  >
    <p class="dtl-confirm-text">
      {{ confirmAction === 'withdraw'
        ? t('app.skills.withdrawDesc')
        : confirmAction === 'cancelVersion'
          ? t('app.skills.cancelUpdateDesc')
          : t('app.skills.deleteSkillDesc') }}
    </p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton
        :variant="confirmAction === 'delete' ? 'danger' : 'secondary'"
        :loading="busy"
        @click="confirmAction === 'withdraw' ? onWithdrawConfirm() : confirmAction === 'cancelVersion' ? onCancelVersionConfirm() : onDeleteConfirm()"
      >
        {{ confirmAction === 'withdraw' ? t('app.skills.withdraw') : confirmAction === 'cancelVersion' ? t('app.skills.cancelUpdateBtn') : t('app.common.delete') }}
      </PrismButton>
    </template>
  </PrismDialog>
</template>

<style scoped>
.dtl-loading {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-secondary);
}
.dtl {
  display: grid;
  gap: var(--ds-space-3);
}
.dtl-header1 {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
.dtl-name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--ds-color-text-primary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dtl-rating {
  color: var(--ds-color-accent);
  white-space: nowrap;
}
.dtl-norating {
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
/* 2026-11-07 (Option A): header card — name + status + actions on row 1,
   scope/system/creator/dates/stats/rating on row 2. */
.dtl-header-card {
  display: grid;
  gap: 10px;
  padding: 14px 18px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: var(--ds-surface-tint);
}
.dtl-header-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ds-space-3);
  padding-top: 10px;
  border-top: 1px solid var(--ds-color-border);
}
/* 2026-11-07: views / downloads / rating group — pushed to the right side. */
.dtl-header-stats {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-3);
  flex-wrap: wrap;
}
.dtl-scope {
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.dtl-stat-icon {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
  cursor: default;
}
.dtl-stat-icon svg {
  color: var(--ds-color-text-muted);
}
.dtl-actions {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.dtl-subscribe {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  border: 1px solid var(--ds-color-border);
  background: none;
  color: var(--ds-color-text-secondary);
  border-radius: var(--ds-radius-sm);
  padding: 4px 10px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.dtl-subscribe.is-subscribed {
  color: var(--ds-color-accent);
  border-color: var(--ds-color-accent);
}
.dtl-copy-link {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-1);
  border: 1px solid var(--ds-color-border);
  background: none;
  color: var(--ds-color-text-secondary);
  border-radius: var(--ds-radius-sm);
  padding: 4px 10px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.dtl-copy-link:hover {
  color: var(--ds-color-accent);
  border-color: var(--ds-color-accent);
}
/* 2026-11-07 (Option A + V1): download hero — the first element in the
   popup. Clicking anywhere (or the CTA) downloads the combined zip. */
.dtl-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 14px 18px;
  border: 1px solid rgb(94 106 210 / .45);
  border-radius: var(--ds-radius-md);
  background: var(--ds-surface-tint);
  cursor: pointer;
  transition: border-color var(--ds-duration-fast) var(--ds-ease),
    box-shadow var(--ds-duration-fast) var(--ds-ease);
}
@media (hover: hover) {
  .dtl-hero:hover {
    border-color: var(--ds-color-accent);
    box-shadow: var(--ds-shadow-card);
  }
}
.dtl-hero:focus-visible {
  outline: 2px solid var(--ds-color-accent);
  outline-offset: 2px;
}
.dtl-hero-ic {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgb(94 106 210 / 0.2);
  border: 1px solid rgb(94 106 210 / .5);
  color: var(--ds-color-text-primary);
}
.dtl-hero-slogan {
  flex: 1;
  min-width: 160px;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: .01em;
  color: var(--ds-color-text-primary);
}
.dtl-hero-sub {
  display: block;
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0;
  color: var(--ds-color-text-secondary);
  margin-top: 2px;
}
.dtl-hero-chips {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.dtl-hero-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--ds-color-border-strong);
  background: rgb(255 255 255 / .04);
  color: var(--ds-color-text-secondary);
}
.dtl-hero-chip svg {
  color: var(--ds-color-accent);
}
.dtl-hero-chip b {
  color: var(--ds-color-text-primary);
}
.dtl-hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font: 700 14px var(--ds-font-sans);
  cursor: pointer;
  border: 1px solid rgb(94 106 210 / .55);
  background: rgb(94 106 210 / 0.2);
  color: var(--ds-color-text-primary);
  padding: 10px 20px;
  border-radius: 10px;
  box-shadow: var(--ds-shadow-card);
  white-space: nowrap;
  transition: transform var(--ds-duration-fast) var(--ds-ease);
}
@media (hover: hover) {
  .dtl-hero-cta:hover {
    transform: translateY(-1px);
  }
}
/* 2026-11-07 (Option A): Overview tab — 2 columns (description + sidebar). */
.dtl-overview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 300px);
  gap: var(--ds-space-4);
  /* stretch (default): the description column grows to the sidebar's
     height when the sidebar is taller — otherwise the left column looks
     short next to a long file/MCP list. */
}
.dtl-overview-main {
  min-width: 0;
  /* Flex column: the grid stretches this item to the row height (set by
     the sidebar), and .dtl-desc (flex: 1) fills it. The description's
     min-height (400px) only grows the row when the sidebar is shorter. */
  display: flex;
}
.dtl-overview-side {
  display: grid;
  gap: 12px;
  min-width: 0;
  /* The row can be 400px tall (description min-height) while the sidebar
     has only a few short blocks — without this, grid's default
     align-items: stretch inflates each block to fill the column, making
     them look stretched and far apart. */
  align-items: start;
  /* Keep the sidebar at its natural height (top-aligned) so its blocks
     don't stretch when the description min-height makes the row taller. */
  align-self: start;
}
.dtl-side-block {
  display: grid;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  background: var(--ds-surface-tint);
}
/* File/MCP sections in the sidebar get a bit more breathing room.
   min-width: 0 is required on every grid item in the chain — otherwise a
   long nowrap file name inflates the min-content size, the sidebar column
   grows past its 300px cap and its right side gets clipped. */
.dtl-overview-side .dtl-mcpdeps {
  gap: var(--ds-space-3);
  padding: var(--ds-space-4);
  min-width: 0;
}
.dtl-overview-side .dtl-file {
  min-width: 0;
}
.dtl-side-label {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .05em;
  color: var(--ds-color-text-muted);
}
/* 2026-11-07: per-file download links removed from the sidebar — the name
   now gets the full row width. Long names are cut with an ellipsis at the
   right edge (min-width: 0 lets the flex item shrink so the ellipsis
   kicks in); the :title tooltip shows the full name. */
.dtl-overview-side .dtl-file-name,
.dtl-overview-side .mcp-name-link {
  max-width: none;
  min-width: 0;
}
/* Sidebar "Download All" pill — a touch smaller than the default. */
.dtl-overview-side .dtl-file-download.is-all {
  font-size: 11px;
  padding: 2px 10px;
  gap: 5px;
  white-space: nowrap;
  flex-shrink: 0;
}
.dtl-overview-side .dtl-file-download.is-all::before {
  font-size: 12px;
}
/* 2026-11-07: compact tab bar — the default PrismTabs tab is 44px tall with
   16px font + 24px panel padding; too heavy for a popup. Tighten it here
   (scoped override, design-system untouched). */
.dtl :deep(.p-tablist) {
  gap: var(--ds-space-4);
  padding-top: 0;
}
.dtl :deep(.p-tablist > button) {
  font-size: 13.5px;
  min-height: 32px;
  padding-bottom: var(--ds-space-2);
}
.dtl :deep(.p-tab-count) {
  font-size: 11px;
}
.dtl :deep(.p-tabpanel) {
  padding-top: var(--ds-space-3);
}
.dtl-file {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
.dtl-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}
/* Per-file download: small, unobtrusive link. */
.dtl-file-download {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 12px;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 2px;
}
@media (hover: hover) {
  .dtl-file-download:hover {
    color: var(--ds-color-accent);
  }
}
/* "Download All": prominent pill button. */
.dtl-file-download.is-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgb(94 106 210 / .4);
  background: rgb(94 106 210 / .08);
  color: var(--ds-color-accent);
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 20px;
  text-decoration: none;
  transition: background var(--ds-duration-fast) var(--ds-ease),
    box-shadow var(--ds-duration-fast) var(--ds-ease),
    border-color var(--ds-duration-fast) var(--ds-ease);
}
.dtl-file-download.is-all::before {
  content: '↓';
  font-size: 13px;
  line-height: 1;
}
@media (hover: hover) {
  .dtl-file-download.is-all:hover {
    background: rgb(94 106 210 / .18);
    border-color: var(--ds-color-accent);
    box-shadow: var(--ds-shadow-card);
  }
}
.dtl-hashtags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
}
.dtl-hashtag {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: rgb(94 106 210 / .06);
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.dtl-badges {
  display: flex;
  align-items: baseline;
  gap: var(--ds-space-3);
  flex-wrap: wrap;
}
.dtl-badges-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--ds-color-text-muted);
}
.dtl-badges-row {
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--ds-space-2);
}
.dtl-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 11px;
  white-space: nowrap;
}
.dtl-badge--cat {
  color: var(--ds-color-violet-400, #8b93e8);
  background: rgb(94 106 210 / .08);
  border: 1px solid rgb(94 106 210 / .35);
}
.dtl-badge--stage {
  color: var(--ds-color-accent);
  background: rgb(94 106 210 / .07);
  border: 1px solid rgb(94 106 210 / .24);
}
.dtl-badge--cowork {
  color: var(--ds-color-success, #4ade80);
  background: rgb(74 222 128 / .07);
  border: 1px solid rgb(74 222 128 / .24);
}
.dtl-desc {
  /* flex: 1 fills the .dtl-overview-main column (grid-stretched to the
     row height). min-height: 400px keeps long descriptions readable when
     the sidebar is short (1 file + 1 category + 1 stage ≈ 250px).
     Long text scrolls inside the box (scrollbar hidden). */
  flex: 1;
  min-height: 400px;
  overflow-y: auto;
  color: var(--ds-color-text-primary);
  line-height: 1.6;
  /* Hide the scrollbar but keep wheel/drag scrolling. */
  scrollbar-width: none;
}
.dtl-desc::-webkit-scrollbar {
  display: none;
}
.dtl-desc :deep(p) {
  margin: 0 0 var(--ds-space-2);
}
.dtl-desc :deep(p:last-child) {
  margin-bottom: 0;
}
.dtl-desc :deep(h2),
.dtl-desc :deep(h3) {
  margin: var(--ds-space-3) 0 var(--ds-space-2);
  color: var(--ds-color-text-primary);
}
.dtl-desc :deep(h1) {
  font-size: 20px;
}
.dtl-desc :deep(h2) {
  font-size: 16px;
}
.dtl-desc :deep(h3) {
  font-size: 14px;
}
.dtl-desc :deep(ul),
.dtl-desc :deep(ol) {
  margin: 0 0 var(--ds-space-2);
  padding-left: 22px;
}
.dtl-desc :deep(a) {
  color: var(--ds-color-accent);
}
.dtl-desc :deep(blockquote) {
  margin: 0 0 var(--ds-space-2);
  padding-left: var(--ds-space-3);
  border-left: 3px solid var(--ds-color-border-strong);
  color: var(--ds-color-text-secondary);
}
.dtl-desc :deep(pre) {
  margin: 0 0 var(--ds-space-2);
  padding: var(--ds-space-2) var(--ds-space-3);
  background: var(--ds-input-background);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  overflow-x: auto;
}
.dtl-desc :deep(code) {
  font-family: ui-monospace, monospace;
  font-size: 13px;
}
.dtl-desc :deep(pre code) {
  font-size: inherit;
}
.dtl-desc :deep(mark) {
  background: rgba(250, 204, 21, 0.35);
  color: inherit;
  border-radius: 2px;
}
.dtl-desc :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--ds-radius-sm);
}
.dtl-desc :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0 0 var(--ds-space-2);
  table-layout: fixed;
}
.dtl-desc :deep(th),
.dtl-desc :deep(td) {
  border: 1px solid var(--ds-color-border);
  padding: 4px 8px;
  vertical-align: top;
  text-align: left;
}
.dtl-desc :deep(th) {
  background: var(--ds-color-bg-secondary, rgba(255, 255, 255, 0.04));
  font-weight: 600;
}
.dtl-desc :deep(ul[data-type='taskList']) {
  list-style: none;
  padding-left: 4px;
}
.dtl-desc :deep(li[data-type='taskItem']) {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.dtl-desc :deep(li[data-type='taskItem'] input[type='checkbox']) {
  accent-color: var(--ds-color-accent);
}
.dtl-desc :deep(li[data-type='taskItem'][data-checked='true']) {
  text-decoration: line-through;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
}
.dtl-desc :deep(hr) {
  border: none;
  border-top: 1px solid var(--ds-color-border);
  margin: var(--ds-space-3) 0;
}
.dtl-rate {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.dtl-rate.is-disabled .dtl-star {
  cursor: not-allowed;
  opacity: 0.5;
}
.dtl-star {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-accent);
  padding: 0;
  display: inline-flex;
}
.dtl-comments {
  display: grid;
  gap: var(--ds-space-3);
  max-height: 320px;
  overflow-y: auto;
  /* Scrollable but the scrollbar is hidden (same pattern as .dtl-desc). */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* old Edge/IE */
}
.dtl-comments::-webkit-scrollbar {
  display: none; /* Chrome/Safari */
}
.dtl-nocomments {
  color: var(--ds-color-text-secondary);
  font-size: 13px;
}
.dtl-comment-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--ds-space-3);
}
/* 2026-11-08 v2: avatar + body layout (was a plain bordered card). */
.dtl-comment {
  display: flex;
  gap: var(--ds-space-2);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  padding: var(--ds-space-3);
}
/* Own comments get a subtle accent border so they stand out. */
.dtl-comment.is-own {
  border-color: var(--ds-color-accent, #5e6ad2);
  border-color: rgb(94 106 210 / .45);
}
.dtl-comment > .p-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}
.dtl-comment-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: var(--ds-space-1);
}
.dtl-comment-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  flex-wrap: wrap;
}
.dtl-comment-user {
  font-weight: 600;
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
.dtl-comment-time {
  font-size: 12px;
  color: var(--ds-color-text-secondary);
}
.dtl-comment-edited {
  font-size: 11px;
  color: var(--ds-color-text-muted, var(--ds-color-text-secondary));
  font-style: italic;
}
/* Action buttons (like / reply / edit / delete) — right-aligned, revealed
   on hover to keep the header clean (always visible on touch). */
.dtl-comment-actions {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  opacity: 0;
  transition: opacity .15s ease;
}
.dtl-comment:hover .dtl-comment-actions,
.dtl-comment:focus-within .dtl-comment-actions {
  opacity: 1;
}
@media (hover: none) {
  .dtl-comment-actions {
    opacity: 1;
  }
}
.dtl-comment-like {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font-size: 12px;
  padding: 2px 6px;
  border-radius: var(--ds-radius-sm);
}
.dtl-comment-like:hover {
  color: var(--ds-color-text-primary);
  background: var(--ds-list-item-hover-accent);
}
.dtl-comment-like.is-liked {
  color: var(--ds-color-accent, #5e6ad2);
}
.dtl-comment-linkbtn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font-size: 12px;
  padding: 2px 4px;
  border-radius: var(--ds-radius-sm);
}
.dtl-comment-linkbtn:hover {
  color: var(--ds-color-text-primary);
  background: var(--ds-list-item-hover-accent);
}
.dtl-comment-linkbtn.is-danger:hover {
  color: var(--ds-color-danger);
}
.dtl-comment-content {
  margin: 0;
  font-size: 14px;
  color: var(--ds-color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}
/* Inline edit form (textarea + save/cancel). */
.dtl-comment-edit {
  display: grid;
  gap: var(--ds-space-1);
}
.dtl-comment-edit-actions {
  display: inline-flex;
  gap: var(--ds-space-1);
}
/* Replies (depth 2) — indented list under the thread. */
.dtl-comment-replies {
  list-style: none;
  margin: var(--ds-space-1) 0 0;
  padding: 0 0 0 var(--ds-space-3);
  border-left: 2px solid var(--ds-color-border);
  display: grid;
  gap: var(--ds-space-2);
}
.dtl-comment.is-reply {
  border: none;
  padding: 0;
}
/* Inline reply form under a thread. */
.dtl-reply-form {
  display: grid;
  gap: var(--ds-space-1);
  margin-top: var(--ds-space-1);
}
/* Empty state (icon + hint). */
.dtl-comments-empty {
  display: grid;
  justify-items: center;
  gap: var(--ds-space-1);
  padding: var(--ds-space-5) var(--ds-space-3);
  color: var(--ds-color-text-secondary);
  border: 1px dashed var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  text-align: center;
}
.dtl-comments-empty-title {
  margin: 0;
  font-weight: 600;
  font-size: 14px;
  color: var(--ds-color-text-primary);
}
.dtl-comments-empty-hint {
  margin: 0;
  font-size: 13px;
}
.dtl-comment-form {
  margin-top: var(--ds-space-4);
  display: grid;
  gap: var(--ds-space-2);
}
.dtl-comment-form-actions {
  display: flex;
  justify-content: flex-end;
}
.dtl-confirm-text {
  color: var(--ds-color-text-primary);
}
.dtl-pending {
  display: grid;
  gap: var(--ds-space-2);
  padding: var(--ds-space-3) var(--ds-space-4);
  border: 1px dashed rgb(250 204 21 / .45);
  border-radius: var(--ds-radius-sm);
  background: rgb(250 204 21 / .04);
}
.dtl-pending-shares {
  display: grid;
  gap: var(--ds-space-2);
  padding: var(--ds-space-3) var(--ds-space-4);
  border: 1px dashed rgb(250 204 21 / .45);
  border-radius: var(--ds-radius-sm);
  background: rgb(250 204 21 / .04);
}
.dtl-pending-share-row {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  font-size: 13px;
}
.dtl-pending-share-type {
  flex: none;
  min-width: 70px;
  font-weight: 600;
  color: var(--ds-color-text-secondary);
}
.dtl-pending-share-target {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ds-color-text-primary);
}
.dtl-pending-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.dtl-pending-title {
  font-weight: 700;
  font-size: 14px;
  color: var(--ds-color-text-primary);
}
.dtl-pending-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--ds-space-4);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.dtl-pending-name {
  font-weight: 600;
  color: var(--ds-color-text-primary);
}
.dtl-pending-desc {
  max-height: 160px;
}
.dtl-pending-files {
  display: grid;
  gap: var(--ds-space-1);
}
.dtl-file--removed {
  color: var(--ds-color-text-muted);
  text-decoration: line-through;
}
.dtl-versions {
  display: grid;
  gap: var(--ds-space-3);
  max-height: 360px;
  overflow-y: auto;
  /* Scrollable but the scrollbar is hidden (same pattern as .dtl-desc). */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* old Edge/IE */
}
.dtl-versions::-webkit-scrollbar {
  display: none; /* Chrome/Safari */
}
.dtl-version {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  padding: var(--ds-space-3);
  display: grid;
  gap: var(--ds-space-2);
}
.dtl-version--current {
  border-color: rgb(94 106 210 / .4);
}
.dtl-version-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.dtl-version-num {
  font-weight: 700;
  font-size: 14px;
  color: var(--ds-color-text-primary);
}
.dtl-version-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-4);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.dtl-version-files {
  display: grid;
  gap: var(--ds-space-2);
}
.dtl-version-files-head {
  display: flex;
  justify-content: flex-start;
}
.dtl-pending-files-head {
  display: flex;
  align-items: baseline;
  justify-content: flex-start;
  gap: var(--ds-space-3);
}
.dtl-version-file {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
/* v1.33 (FR-MCP-015): MCP name link in the quick-view section */
.mcp-name-link {
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font: inherit;
  color: var(--ds-color-accent);
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}
@media (hover: hover) { .mcp-name-link:hover { text-decoration: underline; } }
.dtl-mcp-warn {
  margin: 0;
  font-size: 12px;
  color: var(--ds-color-warning);
}
.dtl-mcp-deleted-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--ds-color-danger, #ff8fa3);
  border: 1px solid rgb(255 143 163 / .4);
  background: rgb(255 143 163 / .08);
  padding: 1px 8px;
  border-radius: 999px;
  white-space: nowrap;
  flex-shrink: 0;
}
/* 2026-11-08: "not approved" status badge (warning tone, same shape as the
   deleted badge) — flags unapproved MCP dependencies next to their name. */
.dtl-mcp-status-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--ds-color-warning, #e5a100);
  border: 1px solid rgb(251 191 36 / .4);
  background: rgb(251 191 36 / .08);
  padding: 1px 8px;
  border-radius: 999px;
  white-space: nowrap;
  flex-shrink: 0;
}
.dtl-mcp-warn-list {
  margin: 0 0 var(--ds-space-2);
  padding-left: 22px;
  font-size: 13px;
  color: var(--ds-color-text-primary);
  display: grid;
  gap: var(--ds-space-1);
}
/* v1.32 (FR-MCP-015): bordered quick-view sections (Skill files + MCP Dependencies) */
.dtl-mcpdeps {
  display: grid;
  gap: var(--ds-space-2);
  padding: var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
}
.dtl-mcpdeps-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  font-weight: 600;
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
.dtl-mcpdeps-head .dtl-mcpdeps-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.dtl-mcpdeps-head .dtl-file-download.is-all {
  margin-left: auto;
}
.dtl-mcpdep {
  display: grid;
  gap: var(--ds-space-1);
  min-width: 0;
}
</style>
