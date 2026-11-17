<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Bell, Star, Eye, ArrowDown, Link as LinkIcon } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismTabs from '@/design-system/components/PrismTabs.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismTextarea from '@/design-system/components/PrismTextarea.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import PrismTooltip from '@/design-system/components/PrismTooltip.vue'
import ReputationBadge from '@/features/skills/components/ReputationBadge.vue'
import { reputationApi } from '@/features/reputation/api'
import type { ReputationExplain } from '@/features/reputation/api'
import EditMcpPopup from './EditMcpPopup.vue'
import UpdateVersionPopup from './UpdateVersionPopup.vue'
import { mcpApi } from '@/features/mcp/api'
import { toUserError } from '@/lib/api'
import { sanitizeHtml } from '@/lib/sanitize'
import { toastError, toastSuccess } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import { useI18nStore } from '@/stores/i18n'
import type { McpDetailResponse, McpVersionItem, McpDependentSkillItem } from '@/features/mcp/types'

const props = defineProps<{ mcpId: number | null }>()
const emit = defineEmits(['close', 'updated'])

const auth = useAuthStore()
const { t, formatDate, formatDateTime } = useI18nStore()

const detail = ref<McpDetailResponse | null>(null)
const loading = ref(false)
const activeTab = ref('info')
const avgRating = ref<number | null>(null)
const totalRatings = ref(0)
const myRating = ref<number | null>(null)
const hoverRating = ref<number | null>(null)
const reputation = ref<ReputationExplain | null>(null)

const comments = ref<{ id: number; userId: number; userName: string | null; content: string; createdAt: string; canDelete: boolean }[]>([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentSize = 10
const commentText = ref('')
const sendingComment = ref(false)

const rejectOpen = ref(false)
const rejectReason = ref('')
const confirmOpen = ref(false)
const confirmAction = ref<'' | 'withdraw' | 'delete' | 'cancelVersion'>('')
const busy = ref(false)
const editOpen = ref(false)
const updateVersionOpen = ref(false)
const subscribing = ref(false)

// ── Dependent Skills tab (reverse reference — skills that pin this MCP) ─────
const dependentSkills = ref<McpDependentSkillItem[]>([])
const dependentLoading = ref(false)
const dependentLoaded = ref(false)

// ── Versions tab ────────────────────────────────────────────────────────────
const versions = ref<McpVersionItem[]>([])
const versionsLoading = ref(false)
const versionsLoaded = ref(false)

let refreshTimer: ReturnType<typeof setInterval> | null = null
let viewRecorded = false

const isCreator = computed(() => detail.value?.isCreator ?? false)
const isAdmin = computed(() => detail.value?.isAdmin ?? false)
const hasPendingVersion = computed(() => detail.value?.hasPendingVersion ?? false)
// While a version update is pending, the MCP still serves its approved
// version to everyone else — it is treated as live (approved) for them.
const liveStatus = computed(() =>
  hasPendingVersion.value && !isCreator.value && !isAdmin.value
    ? 'APPROVED' : detail.value?.status ?? ''
)
const isApproved = computed(() => liveStatus.value === 'APPROVED')
const canEdit = computed(() => detail.value?.canEdit ?? false)
const showSubscribe = computed(() => liveStatus.value === 'APPROVED' && !isCreator.value)
const showUpdateVersion = computed(
  () => isApproved.value && canEdit.value && !hasPendingVersion.value
)
const showCancelVersion = computed(
  () => detail.value?.status === 'PENDING_APPROVAL' && hasPendingVersion.value
    && (isCreator.value || isAdmin.value)
)
const showVersionRejected = computed(
  () => isApproved.value && !hasPendingVersion.value && !!detail.value?.rejectionReason
)
const descriptionHtml = computed(() => sanitizeHtml(detail.value?.description ?? ''))
const pendingDescriptionHtml = computed(() =>
  sanitizeHtml(detail.value?.pendingVersion?.description ?? '')
)
// Download rule: creator → any status; admin → PENDING_APPROVAL or later;
// other users → APPROVED (or a version-update-pending MCP, which stays live).
const canDownload = computed(() => {
  const status = liveStatus.value
  if (!status) return false
  if (isCreator.value) return true
  if (isAdmin.value) return status === 'PENDING_APPROVAL' || status === 'APPROVED'
  return status === 'APPROVED'
})
const commentTotalPages = computed(() => Math.max(1, Math.ceil(commentTotal.value / commentSize)))
// Tab label count — the detail response already carries the dependent
// skills list (reverse reference), so no extra request is needed.
const dependentCount = computed(() => detail.value?.dependentSkills?.length ?? 0)

const statusLabel = (code: string) =>
  code === 'PENDING_APPROVAL' ? t('app.common.statusPending') : code === 'DRAFT' ? t('app.common.statusDraft') : code === 'APPROVED' ? t('app.common.statusApproved') : code === 'REJECTED' ? t('app.common.statusRejected') : code
const statusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

// ── Data loading ────────────────────────────────────────────────────────────
async function loadDetail() {
  if (!props.mcpId) return
  try {
    detail.value = await mcpApi.detail(props.mcpId)
    if (!viewRecorded) {
      viewRecorded = true
      mcpApi.recordView(props.mcpId).then((r) => {
        if (detail.value) detail.value = { ...detail.value, viewCount: r.viewCount }
      }).catch(() => {})
    }
  } catch (err) {
    if (toUserError(err).code === 'MCP_NOT_FOUND') {
      toastError(t('app.mcp.notFoundOrNoAccess'))
      close()
    } else {
      toastError(t('app.mcp.loadErrorDetail'))
    }
  }
}

async function loadRating() {
  if (!props.mcpId) return
  try {
    const r = await mcpApi.rating(props.mcpId)
    avgRating.value = r.totalRatings > 0 ? r.averageRating : null
    totalRatings.value = r.totalRatings
    myRating.value = await mcpApi.myRating(props.mcpId)
  } catch {
    // non-fatal
  }
  void loadReputation()
}

async function loadReputation() {
  if (!props.mcpId) return
  try {
    reputation.value = await reputationApi.explain('MCP', props.mcpId)
  } catch {
    reputation.value = null
  }
}

async function loadComments(resetPage = false) {
  if (!props.mcpId) return
  if (resetPage) commentPage.value = 1
  try {
    const r = await mcpApi.comments(props.mcpId, commentPage.value, commentSize)
    comments.value = r.items
    commentTotal.value = r.totalCount
    if (commentPage.value > commentTotalPages.value) {
      commentPage.value = commentTotalPages.value
      const retry = await mcpApi.comments(props.mcpId, commentPage.value, commentSize)
      comments.value = retry.items
    }
  } catch {
    // non-fatal
  }
}

async function loadVersions() {
  if (!props.mcpId) return
  versionsLoading.value = true
  try {
    versions.value = await mcpApi.listVersions(props.mcpId)
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

async function loadDependentSkills() {
  if (!props.mcpId) return
  dependentLoading.value = true
  try {
    dependentSkills.value = await mcpApi.dependentSkills(props.mcpId)
    dependentLoaded.value = true
  } catch {
    // non-fatal
  } finally {
    dependentLoading.value = false
  }
}

function onDependentTabShown() {
  if (!dependentLoaded.value) loadDependentSkills()
}

function openFor(mcpId: number) {
  detail.value = null
  activeTab.value = 'info'
  avgRating.value = null
  totalRatings.value = 0
  myRating.value = null
  hoverRating.value = null
  reputation.value = null
  comments.value = []
  commentTotal.value = 0
  commentPage.value = 1
  commentText.value = ''
  rejectOpen.value = false
  rejectReason.value = ''
  confirmOpen.value = false
  editOpen.value = false
  updateVersionOpen.value = false
  versions.value = []
  versionsLoaded.value = false
  dependentSkills.value = []
  dependentLoaded.value = false
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
    if (document.visibilityState === 'visible' && props.mcpId) {
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
  if (document.visibilityState === 'visible' && props.mcpId && detail.value) {
    loadDetail()
    loadRating()
    loadComments(false)
  }
}

watch(
  () => props.mcpId,
  (id) => {
    if (id != null) openFor(id)
  },
  { immediate: true }
)

watch(activeTab, (tab) => {
  if (tab === 'versions') onVersionsTabShown()
  if (tab === 'skills') onDependentTabShown()
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
  emit('close')
}

// ── Rating ──────────────────────────────────────────────────────────────────
function starState(index: number): number {
  const current = hoverRating.value ?? myRating.value ?? 0
  return current >= index ? 1 : current >= index - 0.5 ? 0.5 : 0
}

async function setRating(value: number) {
  if (!props.mcpId || !detail.value) return
  if (!isApproved.value) {
    toastError(t('app.mcp.onlyApprovedRate'))
    return
  }
  const previous = myRating.value
  myRating.value = value // optimistic
  try {
    const r = await mcpApi.upsertRating(props.mcpId, value)
    avgRating.value = r.totalRatings > 0 ? r.averageRating : null
    totalRatings.value = r.totalRatings
  } catch (err) {
    myRating.value = previous
    toastError(toUserError(err).message)
  }
}

// ── Comments ────────────────────────────────────────────────────────────────
async function sendComment() {
  if (!props.mcpId || !detail.value) return
  const content = commentText.value.trim()
  if (!content) return
  if (!isApproved.value) {
    toastError(t('app.mcp.onlyApprovedComment'))
    return
  }
  sendingComment.value = true
  try {
    await mcpApi.addComment(props.mcpId, content)
    commentText.value = ''
    await loadComments(true)
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    sendingComment.value = false
  }
}

async function deleteComment(commentId: number) {
  if (!props.mcpId || !detail.value) return
  const wasLastOnPage = comments.value.length === 1 && commentPage.value > 1
  try {
    await mcpApi.deleteComment(props.mcpId, commentId)
    if (wasLastOnPage) commentPage.value -= 1
    await loadComments(false)
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

// ── Workflow ────────────────────────────────────────────────────────────────
async function transition(toStatus: string, rejectionReason?: string) {
  if (!props.mcpId) return
  busy.value = true
  try {
    await mcpApi.changeStatus(props.mcpId, toStatus, rejectionReason)
    await Promise.all([loadDetail(), loadRating()])
    refreshVersions()
    emit('updated')
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
    if (ok) toastSuccess(t('app.mcp.submittedToast'))
  })
}
function onApprove() {
  const wasVersionUpdate = hasPendingVersion.value
  void transition('APPROVED').then((ok) => {
    if (ok) toastSuccess(wasVersionUpdate ? t('app.mcp.versionApproved') : t('app.mcp.approvedToast'))
  })
}
function onRejectConfirm() {
  const reason = rejectReason.value.trim()
  if (!reason || reason.length > 500) {
    toastError(t('app.mcp.rejectReasonRequired'))
    return
  }
  const wasVersionUpdate = hasPendingVersion.value
  void transition('REJECTED', reason).then((ok) => {
    if (ok) {
      toastSuccess(wasVersionUpdate ? t('app.mcp.versionRejected') : t('app.mcp.rejectedToast'))
      rejectOpen.value = false
      rejectReason.value = ''
    }
  })
}
function onWithdrawConfirm() {
  void transition('DRAFT').then((ok) => {
    if (ok) {
      toastSuccess(t('app.mcp.withdrawnToast'))
      confirmOpen.value = false
    }
  })
}
function onDeleteConfirm() {
  if (!props.mcpId) return
  busy.value = true
  mcpApi
    .deleteBatch([props.mcpId])
    .then(() => {
      toastSuccess(t('app.mcp.deletedToastDetail'))
      confirmOpen.value = false
      close()
      emit('updated')
    })
    .catch((err) => toastError(toUserError(err).message))
    .finally(() => {
      busy.value = false
    })
}

function onEditSaved() {
  void Promise.all([loadDetail(), loadRating()])
  refreshVersions()
  emit('updated')
}

function onUpdateVersionSaved() {
  void Promise.all([loadDetail(), loadRating()])
  refreshVersions()
  emit('updated')
}

async function onCancelVersionConfirm() {
  if (!props.mcpId) return
  busy.value = true
  try {
    await mcpApi.cancelVersionUpdate(props.mcpId)
    toastSuccess(t('app.mcp.versionCancelled'))
    confirmOpen.value = false
    await Promise.all([loadDetail(), loadRating()])
    refreshVersions()
    emit('updated')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    busy.value = false
  }
}

function onDownloadVersionFile(version: number, fileId: number) {
  if (!props.mcpId) return
  mcpApi
    .downloadVersionFile(props.mcpId, version, fileId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadVersionAll(version: number) {
  if (!props.mcpId) return
  mcpApi
    .downloadVersionAll(props.mcpId, version)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadPendingFile(fileId: number) {
  if (!props.mcpId) return
  mcpApi
    .downloadPendingFile(props.mcpId, fileId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadPendingAll() {
  if (!props.mcpId) return
  mcpApi
    .downloadPendingAll(props.mcpId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownload(fileId: number) {
  if (!props.mcpId) return
  mcpApi
    .download(props.mcpId, fileId)
    .catch((err) => toastError(toUserError(err).message))
}

function onDownloadAll() {
  if (!props.mcpId) return
  mcpApi
    .downloadAll(props.mcpId)
    .catch((err) => toastError(toUserError(err).message))
}

function onEdit() {
  editOpen.value = true
}

// ── Copy detail link ─────────────────────────────────────────────────────────
// Deep link: /mcps?detail={id} — the target user opens the popup directly
// (visibility rules still apply server-side; not-visible MCPs 404).
function copyLink() {
  if (!props.mcpId) return
  const url = `${window.location.origin}/mcps?detail=${props.mcpId}`
  const done = () => toastSuccess(t('app.mcp.copyLinkCopied'))
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
      toastError(t('app.mcp.copyLinkFailed'))
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
  if (!props.mcpId || !detail.value) return
  subscribing.value = true
  const wasSubscribed = detail.value.subscribed
  try {
    if (wasSubscribed) {
      await mcpApi.unsubscribe(props.mcpId)
      detail.value = { ...detail.value, subscribed: false }
      toastSuccess(t('app.mcp.unsubscribedToast'))
    } else {
      await mcpApi.subscribe(props.mcpId)
      detail.value = { ...detail.value, subscribed: true }
      toastSuccess(t('app.mcp.subscribedToast'))
    }
    emit('updated')
  } catch (err) {
    toastError(toUserError(err).message)
  } finally {
    subscribing.value = false
  }
}
</script>

<template>
  <PrismDialog
    :model-value="mcpId != null"
    :title="t('app.mcp.detailTitle')"
    size="lg"
    dialog-class="skill-form-dialog"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && close()"
    @close="close"
  >
    <div v-if="loading || !detail" class="dtl-loading">{{ t('app.common.loading') }}</div>
    <div v-else class="dtl">
      <!-- Header line 1 -->
      <div class="dtl-header1">
        <h3 class="dtl-name">{{ detail.name }}</h3>
      </div>

      <!-- Header line 2 -->
      <div class="dtl-header2">
        <PrismBadge :tone="statusTone(liveStatus)">{{ statusLabel(liveStatus) }}</PrismBadge>
        <span class="dtl-scope" :title="t('app.mcp.mcpCode')">{{ detail.mcpCode }}</span>
        <span class="dtl-scope" :title="t('app.mcp.serverKey')">{{ detail.serverKey }}</span>
        <PrismTooltip :content="t('app.common.views', { n: detail.viewCount })">
          <span class="dtl-stat-icon" :aria-label="t('app.mcp.colViews')">
            <Eye :size="15" aria-hidden="true" />
            {{ detail.viewCount }}
          </span>
        </PrismTooltip>
        <PrismTooltip :content="t('app.common.downloads', { n: detail.downloadCount })">
          <span class="dtl-stat-icon" :aria-label="t('app.mcp.colDownloads')">
            <ArrowDown :size="15" aria-hidden="true" />
            {{ detail.downloadCount }}
          </span>
        </PrismTooltip>
        <span v-if="avgRating != null" class="dtl-rating">
          ★ {{ avgRating.toFixed(1) }}/5 ({{ totalRatings }})
        </span>
        <span v-else class="dtl-norating">{{ t('app.mcp.noRating') }}</span>
        <!-- Star rating (editable when APPROVED) -->
        <div class="dtl-rate" :class="{ 'is-disabled': !isApproved }">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="dtl-star"
            :disabled="!isApproved"
            :aria-label="t('app.mcp.rateStars', { n })"
            @mouseenter="isApproved && (hoverRating = n)"
            @mouseleave="hoverRating = null"
            @click="setRating(n)"
          >
            <Star :size="18" :fill="starState(n) === 0 ? 'none' : 'currentColor'" aria-hidden="true" />
          </button>
        </div>
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
            {{ detail.subscribed ? t('app.mcp.subscribed') : t('app.mcp.colSubscribe') }}
          </button>
          <button
            type="button"
            class="dtl-copy-link"
            :title="t('app.mcp.copyLinkTooltip')"
            :aria-label="t('app.mcp.copyLinkTooltip')"
            @click="copyLink"
          >
            <LinkIcon :size="14" aria-hidden="true" />
            {{ t('app.mcp.copyLink') }}
          </button>
          <!-- Reputation badge: same row as the MCP name, right of Copy link
               (same placement as SkillDetailPopup, 68e47e4). Tooltip on the
               badge itself (in-dialog: the popup is a native <dialog> in the
               top layer, so a body-teleported tooltip would paint behind it). -->
          <PrismTooltip v-if="reputation" in-dialog>
            <ReputationBadge :tier="reputation.tier" :score="reputation.score" />
            <template #content>
              <div v-for="reason in reputation.reasons" :key="reason">{{ reason }}</div>
            </template>
          </PrismTooltip>
        </div>
      </div>

      <!-- Meta -->
      <div class="dtl-meta">
        <span>{{ t('app.mcp.creator', { name: detail.creatorName ?? '—' }) }}</span>
        <span>{{ t('app.mcp.createdUpdated', { created: formatDate(detail.createdAt), updated: formatDate(detail.updatedAt) }) }}</span>
      </div>

      <!-- Files -->
      <div v-if="detail.files.length" class="dtl-mcpdeps">
        <div class="dtl-mcpdeps-head">
          📄 {{ t('app.mcp.file') }}
          <button v-if="canDownload" type="button" class="dtl-file-download is-all" @click="onDownloadAll">
            {{ t('app.mcp.downloadAll') }}
          </button>
        </div>
        <div v-for="f in detail.files" :key="f.id" class="dtl-file">
          <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
          <button
            v-if="canDownload"
            type="button"
            class="dtl-file-download"
            @click="onDownload(f.id)"
          >{{ t('app.common.download') }}</button>
        </div>
      </div>
      <div v-if="detail.hashtags.length" class="dtl-hashtags">
        <span v-for="tag in detail.hashtags" :key="tag" class="dtl-hashtag">#{{ tag }}</span>
      </div>

      <!-- Categories & process stages -->
      <div v-if="detail.categories.length" class="dtl-badges">
        <span class="dtl-badges-label">{{ t('app.mcp.categories') }}</span>
        <span class="dtl-badges-row">
          <span v-for="c in detail.categories" :key="c" class="dtl-badge dtl-badge--cat">{{ c }}</span>
        </span>
      </div>
      <div v-if="detail.stages.length" class="dtl-badges">
        <span class="dtl-badges-label">{{ t('app.mcp.processStages') }}</span>
        <span class="dtl-badges-row">
          <span v-for="st in detail.stages" :key="st" class="dtl-badge dtl-badge--stage">{{ st }}</span>
        </span>
      </div>

      <!-- Banners -->
      <PrismAlert v-if="detail.status === 'PENDING_APPROVAL' && !hasPendingVersion" tone="warning">
        {{ t('app.mcp.pendingBanner') }}
      </PrismAlert>
      <PrismAlert v-if="detail.status === 'PENDING_APPROVAL' && hasPendingVersion && (isCreator || isAdmin)" tone="warning">
        {{ t('app.mcp.pendingVersionBanner') }}
      </PrismAlert>
      <PrismAlert v-if="detail.status === 'REJECTED' && detail.rejectionReason" tone="danger">
        {{ t('app.mcp.rejectionReason', { reason: detail.rejectionReason }) }}
      </PrismAlert>
      <PrismAlert v-if="showVersionRejected" tone="warning">
        {{ t('app.mcp.lastRejected', { reason: detail.rejectionReason ?? '' }) }}
      </PrismAlert>

      <!-- Pending version (under review) — creator/admin only -->
      <div v-if="hasPendingVersion && detail.pendingVersion && (isCreator || isAdmin)" class="dtl-pending">
        <div class="dtl-pending-head">
          <span class="dtl-pending-title">{{ t('app.mcp.pendingVersion', { n: detail.version + 1 }) }}</span>
          <PrismBadge tone="warning">{{ t('app.mcp.underReview') }}</PrismBadge>
        </div>
        <div class="dtl-pending-meta">
          <span class="dtl-pending-name">{{ detail.pendingVersion.name }}</span>
        </div>
        <div v-if="detail.pendingVersion.categories.length" class="dtl-badges">
          <span class="dtl-badges-label">{{ t('app.mcp.categories') }}</span>
          <span class="dtl-badges-row">
            <span v-for="c in detail.pendingVersion.categories" :key="c" class="dtl-badge dtl-badge--cat">{{ c }}</span>
          </span>
        </div>
        <div v-if="detail.pendingVersion.stages.length" class="dtl-badges">
          <span class="dtl-badges-label">{{ t('app.mcp.processStages') }}</span>
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
            <span class="dtl-badges-label">{{ t('app.mcp.newFiles') }}</span>
            <button type="button" class="dtl-file-download is-all" @click="onDownloadPendingAll">
              {{ t('app.mcp.downloadAll') }}
            </button>
          </div>
          <div v-for="f in detail.pendingVersion.newFiles" :key="f.id" class="dtl-file">
            <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
            <button type="button" class="dtl-file-download" @click="onDownloadPendingFile(f.id)">{{ t('app.common.download') }}</button>
          </div>
        </div>
        <div v-if="detail.pendingVersion.removedFiles.length" class="dtl-pending-files">
          <span class="dtl-badges-label">{{ t('app.mcp.filesToRemove') }}</span>
          <div v-for="f in detail.pendingVersion.removedFiles" :key="f.id" class="dtl-file dtl-file--removed">
            <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <PrismTabs
        v-model="activeTab"
        :items="[
          { value: 'info', label: t('app.mcp.tabInfo') },
          { value: 'versions', label: t('app.mcp.tabVersions') },
          { value: 'comments', label: t('app.mcp.tabComments', { n: commentTotal }) },
          { value: 'skills', label: t('app.mcp.dependentSkillsTab', { n: dependentCount }) },
        ]"
      >
        <template #info>
          <div v-if="descriptionHtml" class="dtl-desc" v-html="descriptionHtml"></div>
          <div v-else class="dtl-desc">{{ t('app.mcp.noDescription') }}</div>
        </template>
        <template #versions>
          <div class="dtl-versions">
            <p v-if="versionsLoading" class="dtl-nocomments">{{ t('app.common.loading') }}</p>
            <template v-else>
              <!-- listVersions returns the current version first (served from
                   the live mcp_files), then every superseded snapshot. -->
              <p v-if="versions.length === 0" class="dtl-nocomments">
                {{ t('app.mcp.noVersions') }}
              </p>
              <div
                v-for="(v, i) in versions"
                :key="v.version"
                class="dtl-version"
                :class="{ 'dtl-version--current': i === 0 }"
              >
                <div class="dtl-version-head">
                  <span class="dtl-version-num">{{ t('app.mcp.version', { n: v.version }) }}</span>
                  <PrismBadge v-if="i === 0" tone="success">{{ t('app.mcp.current') }}</PrismBadge>
                </div>
                <div class="dtl-version-meta">
                  <span>{{ v.name }}</span>
                  <span>{{ t('app.mcp.creator', { name: v.creatorName ?? '—' }) }}</span>
                  <span>{{ formatDate(v.createdAt) }}</span>
                </div>
                <div v-if="v.files.length" class="dtl-version-files">
                  <div v-if="canDownload" class="dtl-version-files-head">
                    <button type="button" class="dtl-file-download is-all" @click="onDownloadVersionAll(v.version)">
                      {{ t('app.mcp.downloadAll') }}
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
          <!-- Comment list -->
          <div class="dtl-comments">
            <p v-if="comments.length === 0" class="dtl-nocomments">{{ t('app.mcp.noComments') }}</p>
            <ul v-else class="dtl-comment-list">
              <li v-for="c in comments" :key="c.id" class="dtl-comment">
                <div class="dtl-comment-head">
                  <span class="dtl-comment-user">{{ c.userName ?? t('app.common.unknown') }}</span>
                  <span class="dtl-comment-time">{{ formatDateTime(c.createdAt) }}</span>
                  <button v-if="c.canDelete" type="button" class="dtl-comment-delete" :aria-label="t('app.mcp.deleteCommentAria')" @click="deleteComment(c.id)">✕</button>
                </div>
                <p class="dtl-comment-content">{{ c.content }}</p>
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

          <!-- Comment form (APPROVED only) -->
          <div v-if="isApproved" class="dtl-comment-form">
            <PrismTextarea
              v-model="commentText"
              :rows="3"
              :maxlength="1000"
              :placeholder="t('app.mcp.writeComment')"
            />
            <div class="dtl-comment-form-actions">
              <PrismButton :disabled="!commentText.trim()" :loading="sendingComment" @click="sendComment">{{ t('app.mcp.send') }}</PrismButton>
            </div>
          </div>
        </template>
        <template #skills>
          <div class="dtl-comments">
            <p v-if="dependentLoading" class="dtl-nocomments">{{ t('app.common.loading') }}</p>
            <p v-else-if="dependentSkills.length === 0" class="dtl-nocomments">{{ t('app.mcp.noSkills') }}</p>
            <ul v-else class="dtl-comment-list">
              <li v-for="s in dependentSkills" :key="s.skillId" class="dtl-comment">
                <div class="dtl-comment-head">
                  <span class="dtl-comment-user">{{ s.skillName }}</span>
                  <span class="dtl-comment-time">v{{ s.skillVersion }}</span>
                </div>
                <p class="dtl-comment-content">{{ t('app.mcp.creator', { name: s.creatorName ?? '—' }) }}</p>
              </li>
            </ul>
          </div>
        </template>
      </PrismTabs>
    </div>

    <!-- Footer -->
    <template #footer>
      <template v-if="detail?.status === 'DRAFT' || detail?.status === 'REJECTED'">
        <PrismButton v-if="canEdit" variant="secondary" @click="onEdit">{{ t('app.common.edit') }}</PrismButton>
        <PrismButton v-if="canEdit" :loading="busy" @click="onSubmit">{{ t('app.mcp.submitForReview') }}</PrismButton>
        <PrismButton v-if="isCreator || isAdmin" variant="danger" @click="confirmAction = 'delete'; confirmOpen = true">{{ t('app.common.delete') }}</PrismButton>
        <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
      </template>
      <template v-else-if="detail?.status === 'PENDING_APPROVAL'">
        <template v-if="hasPendingVersion">
          <template v-if="isAdmin">
            <PrismButton :loading="busy" @click="onApprove">{{ t('app.mcp.approveVersion') }}</PrismButton>
            <PrismButton variant="danger" @click="rejectOpen = true">{{ t('app.mcp.rejectVersion') }}</PrismButton>
            <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'cancelVersion'; confirmOpen = true">{{ t('app.mcp.cancelVersionUpdate') }}</PrismButton>
            <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
          </template>
          <template v-else-if="isCreator">
            <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'cancelVersion'; confirmOpen = true">{{ t('app.mcp.cancelVersionUpdate') }}</PrismButton>
            <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
          </template>
          <PrismButton v-else variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
        </template>
        <template v-else-if="isAdmin">
          <PrismButton :loading="busy" @click="onApprove">{{ t('app.mcp.approve') }}</PrismButton>
          <PrismButton variant="danger" @click="rejectOpen = true">{{ t('app.mcp.reject') }}</PrismButton>
          <PrismButton v-if="isCreator" variant="secondary" :loading="busy" @click="confirmAction = 'withdraw'; confirmOpen = true">{{ t('app.mcp.withdraw') }}</PrismButton>
          <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
        </template>
        <template v-else-if="isCreator">
          <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'withdraw'; confirmOpen = true">{{ t('app.mcp.withdraw') }}</PrismButton>
          <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
        </template>
        <PrismButton v-else variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
      </template>
      <template v-else-if="detail?.status === 'APPROVED'">
        <PrismButton v-if="canEdit" variant="secondary" @click="onEdit">{{ t('app.common.edit') }}</PrismButton>
        <PrismButton v-if="showUpdateVersion" :loading="busy" @click="updateVersionOpen = true">{{ t('app.mcp.updateVersion') }}</PrismButton>
        <PrismButton v-if="isCreator || isAdmin" variant="danger" @click="confirmAction = 'delete'; confirmOpen = true">{{ t('app.common.delete') }}</PrismButton>
        <PrismButton variant="secondary" @click="close">{{ t('app.common.close') }}</PrismButton>
      </template>
    </template>
  </PrismDialog>

  <!-- Edit MCP -->
  <EditMcpPopup
    v-model:open="editOpen"
    :mcp-id="props.mcpId"
    :can-manage-files="detail?.status !== 'APPROVED'"
    @saved="onEditSaved"
  />

  <!-- Update version -->
  <UpdateVersionPopup
    v-model:open="updateVersionOpen"
    :mcp-id="props.mcpId"
    @saved="onUpdateVersionSaved"
  />

  <!-- Reject reason dialog -->
  <PrismDialog v-model="rejectOpen" :title="t('app.mcp.rejectTitle')" size="sm" :close-on-overlay="false">
    <PrismTextarea v-model="rejectReason" :rows="3" :maxlength="500" :placeholder="t('app.mcp.rejectReasonPh')" />
    <template #footer>
      <PrismButton variant="secondary" @click="rejectOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton variant="danger" :loading="busy" @click="onRejectConfirm">{{ t('app.common.confirm') }}</PrismButton>
    </template>
  </PrismDialog>

  <!-- Confirm dialog (withdraw / delete / cancel version) -->
  <PrismDialog
    v-model="confirmOpen"
    :title="confirmAction === 'withdraw' ? t('app.mcp.withdrawTitle') : confirmAction === 'cancelVersion' ? t('app.mcp.cancelUpdateTitle') : t('app.mcp.deleteTitle')"
    size="sm"
    :close-on-overlay="false"
  >
    <p class="dtl-confirm-text">
      {{ confirmAction === 'withdraw'
        ? t('app.mcp.withdrawDesc')
        : confirmAction === 'cancelVersion'
          ? t('app.mcp.cancelUpdateDesc')
          : t('app.mcp.deleteDesc', { n: 1 }) }}
    </p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmOpen = false">{{ t('app.common.cancel') }}</PrismButton>
      <PrismButton
        :variant="confirmAction === 'delete' ? 'danger' : 'secondary'"
        :loading="busy"
        @click="confirmAction === 'withdraw' ? onWithdrawConfirm() : confirmAction === 'cancelVersion' ? onCancelVersionConfirm() : onDeleteConfirm()"
      >
        {{ confirmAction === 'withdraw' ? t('app.mcp.withdraw') : confirmAction === 'cancelVersion' ? t('app.mcp.cancelUpdateBtn') : t('app.common.delete') }}
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
.dtl-header2 {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
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
.dtl-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-4);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
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
  border: 1px solid rgb(103 232 249 / .4);
  background: rgb(103 232 249 / .08);
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
    background: rgb(103 232 249 / .18);
    border-color: var(--ds-color-accent);
    box-shadow: var(--ds-shadow-glow);
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
  background: rgb(103 232 249 / .06);
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
  color: var(--ds-color-violet-400, #a58aef);
  background: rgb(165 138 239 / .08);
  border: 1px solid rgb(165 138 239 / .35);
}
.dtl-badge--stage {
  color: var(--ds-color-accent);
  background: rgb(103 232 249 / .07);
  border: 1px solid rgb(103 232 249 / .24);
}
.dtl-desc {
  overflow-y: auto;
  max-height: 420px;
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
.dtl-comment {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  padding: var(--ds-space-3);
}
.dtl-comment-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  margin-bottom: var(--ds-space-1);
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
.dtl-comment-delete {
  margin-left: auto;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-text-secondary);
  font-size: 14px;
}
.dtl-comment-delete:hover {
  color: var(--ds-color-danger);
}
.dtl-comment-content {
  margin: 0;
  font-size: 14px;
  color: var(--ds-color-text-primary);
  white-space: pre-wrap;
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
}
.dtl-version {
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  padding: var(--ds-space-3);
  display: grid;
  gap: var(--ds-space-2);
}
.dtl-version--current {
  border-color: rgb(103 232 249 / .4);
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
/* Bordered quick-view section (Files) */
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
.dtl-mcpdeps-head .dtl-file-download.is-all {
  margin-left: auto;
}
</style>
