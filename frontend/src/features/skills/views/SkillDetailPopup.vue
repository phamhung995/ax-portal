<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Bell, Star } from 'lucide-vue-next'
import PrismDialog from '@/design-system/components/PrismDialog.vue'
import PrismButton from '@/design-system/components/PrismButton.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import PrismTabs from '@/design-system/components/PrismTabs.vue'
import PrismPagination from '@/design-system/components/PrismPagination.vue'
import PrismTextarea from '@/design-system/components/PrismTextarea.vue'
import PrismAlert from '@/design-system/components/PrismAlert.vue'
import EditSkillPopup from './EditSkillPopup.vue'
import UpdateVersionPopup from './UpdateVersionPopup.vue'
import { skillApi } from '@/features/skills/api'
import { toUserError } from '@/lib/api'
import { sanitizeHtml } from '@/lib/sanitize'
import { toastError, toastSuccess } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import type { SkillDetailResponse, SkillVersionItem } from '@/features/skills/types'

const props = defineProps<{ skillId: number | null }>()
const emit = defineEmits(['closed', 'changed'])

const auth = useAuthStore()

const detail = ref<SkillDetailResponse | null>(null)
const loading = ref(false)
const activeTab = ref('info')
const avgRating = ref<number | null>(null)
const totalRatings = ref(0)
const myRating = ref<number | null>(null)
const hoverRating = ref<number | null>(null)

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

// ── Versions tab ────────────────────────────────────────────────────────────
const versions = ref<SkillVersionItem[]>([])
const versionsLoading = ref(false)
const versionsLoaded = ref(false)

let refreshTimer: ReturnType<typeof setInterval> | null = null
let viewRecorded = false

const isCreator = computed(() => detail.value?.isCreator ?? false)
const isAdmin = computed(() => detail.value?.isAdmin ?? false)
const hasPendingVersion = computed(() => detail.value?.hasPendingVersion ?? false)
// While a version update is pending, the skill still serves its approved
// version to everyone else — it is treated as live (approved) for them.
const liveStatus = computed(() =>
  hasPendingVersion.value && !isCreator.value && !isAdmin.value ? 'APPROVED' : detail.value?.status ?? ''
)
const isApproved = computed(() => liveStatus.value === 'APPROVED')
const canEdit = computed(() => detail.value?.canEdit ?? false)
const showSubscribe = computed(() => liveStatus.value === 'APPROVED' && !isCreator.value)
const showUpdateVersion = computed(
  () => isApproved.value && canEdit.value && !hasPendingVersion.value
)
const showCancelVersion = computed(
  () => detail.value?.status === 'PENDING_APPROVAL' && hasPendingVersion.value && (isCreator.value || isAdmin.value)
)
const showVersionRejected = computed(
  () => isApproved.value && !hasPendingVersion.value && !!detail.value?.rejectionReason
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
  code === 'PENDING_APPROVAL' ? 'Pending Approval' : code === 'DRAFT' ? 'Draft' : code === 'APPROVED' ? 'Approved' : code === 'REJECTED' ? 'Rejected' : code
const statusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'
const scopeMeta = (code: string) =>
  code === 'PRIVATE' ? { icon: '🔒', label: 'Private' }
    : code === 'PART' ? { icon: '🏛️', label: 'Part' }
    : code === 'GROUP' ? { icon: '🏢', label: 'Group' }
    : { icon: '🌍', label: 'Public' }

function formatDate(iso: string): string {
  const d = new Date(iso)
  const dd = String(d.getDate()).padStart(2, '0')
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  return `${dd}/${mm}/${d.getFullYear()}`
}
function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${formatDate(iso)} ${hh}:${mi}`
}
function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
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
      toastError('Skill has been deleted.')
      close()
    } else {
      toastError('Unable to load skill information.')
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
    toastError('Only approved skills can be rated.')
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
    toastError('Only approved skills can be commented.')
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
    if (ok) toastSuccess('Review request submitted successfully.')
  })
}
function onApprove() {
  const wasVersionUpdate = hasPendingVersion.value
  void transition('APPROVED').then((ok) => {
    if (ok) toastSuccess(wasVersionUpdate ? 'Version update approved successfully.' : 'Skill approved successfully.')
  })
}
function onRejectConfirm() {
  const reason = rejectReason.value.trim()
  if (!reason || reason.length > 500) {
    toastError('A rejection reason is required (max 500 characters).')
    return
  }
  const wasVersionUpdate = hasPendingVersion.value
  void transition('REJECTED', reason).then((ok) => {
    if (ok) {
      toastSuccess(wasVersionUpdate ? 'Version update rejected successfully.' : 'Skill rejected successfully.')
      rejectOpen.value = false
      rejectReason.value = ''
    }
  })
}
function onWithdrawConfirm() {
  void transition('DRAFT').then((ok) => {
    if (ok) {
      toastSuccess('Skill withdrawn successfully.')
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
      toastSuccess('Skill deleted successfully.')
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
    toastSuccess('Version update cancelled.')
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
  skillApi
    .downloadAll(props.skillId)
    .catch((err) => toastError(toUserError(err).message))
}

function onEdit() {
  editOpen.value = true
}

async function toggleSubscribe() {
  if (!props.skillId || !detail.value) return
  subscribing.value = true
  const wasSubscribed = detail.value.subscribed
  try {
    if (wasSubscribed) {
      await skillApi.unsubscribe(props.skillId)
      detail.value = { ...detail.value, subscribed: false }
      toastSuccess('Unsubscribed successfully.')
    } else {
      await skillApi.subscribe(props.skillId)
      detail.value = { ...detail.value, subscribed: true }
      toastSuccess('Subscribed successfully.')
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
    title="Skill Detail"
    size="lg"
    :close-on-overlay="true"
    :close-on-escape="true"
    @update:model-value="(v) => !v && close()"
    @close="close"
  >
    <div v-if="loading || !detail" class="dtl-loading">Loading…</div>
    <div v-else class="dtl">
      <!-- Header line 1 -->
      <div class="dtl-header1">
        <h3 class="dtl-name">{{ detail.name }}</h3>
        <span v-if="avgRating != null" class="dtl-rating">
          ★ {{ avgRating.toFixed(1) }}/5 ({{ totalRatings }})
        </span>
        <span v-else class="dtl-norating">No rating</span>
      </div>

      <!-- Header line 2 -->
      <div class="dtl-header2">
        <PrismBadge :tone="statusTone(liveStatus)">{{ statusLabel(liveStatus) }}</PrismBadge>
        <span class="dtl-scope">{{ scopeMeta(detail.scope).icon }} {{ scopeMeta(detail.scope).label }}</span>
        <button
          v-if="showSubscribe"
          type="button"
          class="dtl-subscribe"
          :class="{ 'is-subscribed': detail.subscribed }"
          :disabled="subscribing"
          @click="toggleSubscribe"
        >
          <Bell :size="14" :fill="detail.subscribed ? 'currentColor' : 'none'" aria-hidden="true" />
          {{ detail.subscribed ? 'Subscribed' : 'Subscribe' }}
        </button>
      </div>

      <!-- Meta -->
      <div class="dtl-meta">
        <span>Creator: {{ detail.creatorName ?? '—' }}</span>
        <span>Created: {{ formatDate(detail.createdAt) }} · Updated: {{ formatDate(detail.updatedAt) }}</span>
      </div>

      <!-- Files + hashtags -->
      <div v-if="detail.files.length" class="dtl-files">
        <div v-if="canDownload" class="dtl-files-head">
          <button type="button" class="dtl-file-download" @click="onDownloadAll">
            Download All
          </button>
        </div>
        <div v-for="f in detail.files" :key="f.id" class="dtl-file">
          <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
          <button
            v-if="canDownload"
            type="button"
            class="dtl-file-download"
            @click="onDownload(f.id)"
          >Download</button>
        </div>
      </div>
      <div v-if="detail.hashtags.length" class="dtl-hashtags">
        <span v-for="tag in detail.hashtags" :key="tag" class="dtl-hashtag">#{{ tag }}</span>
      </div>

      <!-- Categories & process stages -->
      <div v-if="detail.categories.length" class="dtl-badges">
        <span class="dtl-badges-label">Categories</span>
        <span class="dtl-badges-row">
          <span v-for="c in detail.categories" :key="c" class="dtl-badge dtl-badge--cat">{{ c }}</span>
        </span>
      </div>
      <div v-if="detail.stages.length" class="dtl-badges">
        <span class="dtl-badges-label">Process Stages</span>
        <span class="dtl-badges-row">
          <span v-for="st in detail.stages" :key="st" class="dtl-badge dtl-badge--stage">{{ st }}</span>
        </span>
      </div>

      <!-- Banners -->
      <PrismAlert v-if="detail.status === 'PENDING_APPROVAL' && !hasPendingVersion" tone="warning">
        This skill is pending approval.
      </PrismAlert>
      <PrismAlert v-if="detail.status === 'PENDING_APPROVAL' && hasPendingVersion && (isCreator || isAdmin)" tone="warning">
        A version update is pending review. The content above shows the current (live) version — the proposed changes are in the "Pending Version" section.
      </PrismAlert>
      <PrismAlert v-if="detail.status === 'REJECTED' && detail.rejectionReason" tone="danger">
        Rejection reason: {{ detail.rejectionReason }}
      </PrismAlert>
      <PrismAlert v-if="showVersionRejected" tone="warning">
        Last version update was rejected: {{ detail.rejectionReason }}
      </PrismAlert>

      <!-- Pending version (under review) — creator/admin only -->
      <div v-if="hasPendingVersion && detail.pendingVersion && (isCreator || isAdmin)" class="dtl-pending">
        <div class="dtl-pending-head">
          <span class="dtl-pending-title">Pending Version {{ detail.version + 1 }} — under review</span>
          <PrismBadge tone="warning">Under Review</PrismBadge>
        </div>
        <div class="dtl-pending-meta">
          <span class="dtl-pending-name">{{ detail.pendingVersion.name }}</span>
          <span>{{ scopeMeta(detail.pendingVersion.publicScope).icon }} {{ scopeMeta(detail.pendingVersion.publicScope).label }}</span>
        </div>
        <div v-if="detail.pendingVersion.categories.length" class="dtl-badges">
          <span class="dtl-badges-label">Categories</span>
          <span class="dtl-badges-row">
            <span v-for="c in detail.pendingVersion.categories" :key="c" class="dtl-badge dtl-badge--cat">{{ c }}</span>
          </span>
        </div>
        <div v-if="detail.pendingVersion.stages.length" class="dtl-badges">
          <span class="dtl-badges-label">Process Stages</span>
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
            <span class="dtl-badges-label">New files</span>
            <button type="button" class="dtl-file-download" @click="onDownloadPendingAll">
              Download All
            </button>
          </div>
          <div v-for="f in detail.pendingVersion.newFiles" :key="f.id" class="dtl-file">
            <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
            <button type="button" class="dtl-file-download" @click="onDownloadPendingFile(f.id)">Download</button>
          </div>
        </div>
        <div v-if="detail.pendingVersion.removedFiles.length" class="dtl-pending-files">
          <span class="dtl-badges-label">Files to be removed</span>
          <div v-for="f in detail.pendingVersion.removedFiles" :key="f.id" class="dtl-file dtl-file--removed">
            <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <PrismTabs
        v-model="activeTab"
        :items="[
          { value: 'info', label: 'Information' },
          { value: 'versions', label: 'Versions' },
          { value: 'comments', label: `Comments (${commentTotal})` },
        ]"
      >
        <template #info>
          <div v-if="descriptionHtml" class="dtl-desc" v-html="descriptionHtml"></div>
          <div v-else class="dtl-desc">No description.</div>
        </template>
        <template #versions>
          <div class="dtl-versions">
            <p v-if="versionsLoading" class="dtl-nocomments">Loading…</p>
            <template v-else>
              <div class="dtl-version dtl-version--current">
                <div class="dtl-version-head">
                  <span class="dtl-version-num">Version {{ detail.version }}</span>
                  <PrismBadge tone="success">Current</PrismBadge>
                </div>
                <div class="dtl-version-meta">
                  <span>{{ detail.name }}</span>
                  <span>{{ scopeMeta(detail.scope).icon }} {{ scopeMeta(detail.scope).label }}</span>
                  <span>Updated: {{ formatDate(detail.updatedAt) }}</span>
                </div>
                <div v-if="detail.files.length" class="dtl-version-files">
                  <div v-if="canDownload" class="dtl-version-files-head">
                    <button type="button" class="dtl-file-download" @click="onDownloadAll">
                      Download All
                    </button>
                  </div>
                  <div v-for="f in detail.files" :key="f.id" class="dtl-version-file">
                    <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
                    <button
                      v-if="canDownload"
                      type="button"
                      class="dtl-file-download"
                      @click="onDownload(f.id)"
                    >Download</button>
                  </div>
                </div>
              </div>
              <p v-if="versions.length === 0" class="dtl-nocomments">
                No previous versions. Versions are saved when a version update is approved.
              </p>
              <div v-for="v in versions" :key="v.version" class="dtl-version">
                <div class="dtl-version-head">
                  <span class="dtl-version-num">Version {{ v.version }}</span>
                </div>
                <div class="dtl-version-meta">
                  <span>{{ v.name }}</span>
                  <span>{{ scopeMeta(v.publicScope).icon }} {{ scopeMeta(v.publicScope).label }}</span>
                  <span>Creator: {{ v.creatorName ?? '—' }}</span>
                  <span>{{ formatDate(v.createdAt) }}</span>
                </div>
                <div v-if="v.files.length" class="dtl-version-files">
                  <div v-if="canDownload" class="dtl-version-files-head">
                    <button type="button" class="dtl-file-download" @click="onDownloadVersionAll(v.version)">
                      Download All
                    </button>
                  </div>
                  <div v-for="f in v.files" :key="f.id" class="dtl-version-file">
                    <span class="dtl-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
                    <button
                      v-if="canDownload"
                      type="button"
                      class="dtl-file-download"
                      @click="onDownloadVersionFile(v.version, f.id)"
                    >Download</button>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </template>
        <template #comments>
          <!-- Star rating (editable when PUBLISHED) -->
          <div class="dtl-rate">
            <span class="dtl-rate-label">Your rating:</span>
            <div class="dtl-stars" :class="{ 'is-disabled': !isApproved }">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="dtl-star"
                :disabled="!isApproved"
                :aria-label="`Rate ${n} stars`"
                @mouseenter="isApproved && (hoverRating = n)"
                @mouseleave="hoverRating = null"
                @click="setRating(n)"
              >
                <Star :size="20" :fill="starState(n) === 0 ? 'none' : 'currentColor'" aria-hidden="true" />
              </button>
            </div>
          </div>

          <!-- Comment list -->
          <div class="dtl-comments">
            <p v-if="comments.length === 0" class="dtl-nocomments">No comments yet</p>
            <ul v-else class="dtl-comment-list">
              <li v-for="c in comments" :key="c.id" class="dtl-comment">
                <div class="dtl-comment-head">
                  <span class="dtl-comment-user">{{ c.userName ?? 'Unknown' }}</span>
                  <span class="dtl-comment-time">{{ formatDateTime(c.createdAt) }}</span>
                  <button v-if="c.canDelete" type="button" class="dtl-comment-delete" aria-label="Delete comment" @click="deleteComment(c.id)">✕</button>
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

          <!-- Comment form (PUBLISHED only) -->
          <div v-if="isApproved" class="dtl-comment-form">
            <PrismTextarea
              v-model="commentText"
              :rows="3"
              :maxlength="1000"
              placeholder="Write a comment..."
            />
            <div class="dtl-comment-form-actions">
              <PrismButton :disabled="!commentText.trim()" :loading="sendingComment" @click="sendComment">Send</PrismButton>
            </div>
          </div>
        </template>
      </PrismTabs>
    </div>

    <!-- Footer -->
    <template #footer>
      <template v-if="detail?.status === 'DRAFT' || detail?.status === 'REJECTED'">
        <PrismButton v-if="canEdit" variant="secondary" @click="onEdit">Edit</PrismButton>
        <PrismButton v-if="canEdit" :loading="busy" @click="onSubmit">Submit for Review</PrismButton>
        <PrismButton v-if="canEdit" variant="danger" @click="confirmAction = 'delete'; confirmOpen = true">Delete</PrismButton>
        <PrismButton variant="secondary" @click="close">Close</PrismButton>
      </template>
      <template v-else-if="detail?.status === 'PENDING_APPROVAL'">
        <template v-if="hasPendingVersion">
          <template v-if="isAdmin">
            <PrismButton :loading="busy" @click="onApprove">Approve Version</PrismButton>
            <PrismButton variant="danger" @click="rejectOpen = true">Reject Version</PrismButton>
            <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'cancelVersion'; confirmOpen = true">Cancel Version Update</PrismButton>
            <PrismButton variant="secondary" @click="close">Close</PrismButton>
          </template>
          <template v-else-if="isCreator">
            <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'cancelVersion'; confirmOpen = true">Cancel Version Update</PrismButton>
            <PrismButton variant="secondary" @click="close">Close</PrismButton>
          </template>
          <PrismButton v-else variant="secondary" @click="close">Close</PrismButton>
        </template>
        <template v-else-if="isAdmin">
          <PrismButton :loading="busy" @click="onApprove">Approve</PrismButton>
          <PrismButton variant="danger" @click="rejectOpen = true">Reject</PrismButton>
          <PrismButton v-if="isCreator" variant="secondary" :loading="busy" @click="confirmAction = 'withdraw'; confirmOpen = true">Withdraw</PrismButton>
          <PrismButton variant="secondary" @click="close">Close</PrismButton>
        </template>
        <template v-else-if="isCreator">
          <PrismButton variant="secondary" :loading="busy" @click="confirmAction = 'withdraw'; confirmOpen = true">Withdraw</PrismButton>
          <PrismButton variant="secondary" @click="close">Close</PrismButton>
        </template>
        <PrismButton v-else variant="secondary" @click="close">Close</PrismButton>
      </template>
      <template v-else-if="detail?.status === 'APPROVED'">
        <PrismButton v-if="canEdit" variant="secondary" @click="onEdit">Edit</PrismButton>
        <PrismButton v-if="showUpdateVersion" :loading="busy" @click="updateVersionOpen = true">Update Version</PrismButton>
        <PrismButton v-if="canEdit" variant="danger" @click="confirmAction = 'delete'; confirmOpen = true">Delete</PrismButton>
        <PrismButton variant="secondary" @click="close">Close</PrismButton>
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

  <!-- Reject reason dialog -->
  <PrismDialog v-model="rejectOpen" :title="hasPendingVersion ? 'Reject version update' : 'Reject skill'" size="sm" :close-on-overlay="false">
    <PrismTextarea v-model="rejectReason" :rows="3" :maxlength="500" placeholder="Enter rejection reason (required)..." />
    <template #footer>
      <PrismButton variant="secondary" @click="rejectOpen = false">Cancel</PrismButton>
      <PrismButton variant="danger" :loading="busy" @click="onRejectConfirm">Confirm</PrismButton>
    </template>
  </PrismDialog>

  <!-- Confirm dialog (withdraw / delete / cancel version) -->
  <PrismDialog
    v-model="confirmOpen"
    :title="confirmAction === 'withdraw' ? 'Withdraw skill' : confirmAction === 'cancelVersion' ? 'Cancel version update' : 'Delete skill'"
    size="sm"
    :close-on-overlay="false"
  >
    <p class="dtl-confirm-text">
      {{ confirmAction === 'withdraw'
        ? 'Are you sure you want to withdraw this skill? The skill will return to Draft status.'
        : confirmAction === 'cancelVersion'
          ? 'Are you sure you want to cancel this version update? The staged changes will be discarded and the current version stays live.'
          : 'Are you sure you want to delete this skill? The skill will be hidden from the list.' }}
    </p>
    <template #footer>
      <PrismButton variant="secondary" @click="confirmOpen = false">Cancel</PrismButton>
      <PrismButton
        :variant="confirmAction === 'delete' ? 'danger' : 'secondary'"
        :loading="busy"
        @click="confirmAction === 'withdraw' ? onWithdrawConfirm() : confirmAction === 'cancelVersion' ? onCancelVersionConfirm() : onDeleteConfirm()"
      >
        {{ confirmAction === 'withdraw' ? 'Withdraw' : confirmAction === 'cancelVersion' ? 'Cancel Update' : 'Delete' }}
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
.dtl-subscribe {
  margin-left: auto;
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
.dtl-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ds-space-4);
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.dtl-files {
  display: grid;
  gap: var(--ds-space-2);
}
.dtl-files-head {
  display: flex;
  justify-content: flex-start;
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
.dtl-file-download {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--ds-color-accent);
  font: inherit;
  font-size: 13px;
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
  max-height: 240px;
  color: var(--ds-color-text-primary);
  line-height: 1.6;
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
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  margin-bottom: var(--ds-space-4);
}
.dtl-rate-label {
  font-size: 13px;
  color: var(--ds-color-text-secondary);
}
.dtl-stars {
  display: inline-flex;
  gap: 2px;
}
.dtl-stars.is-disabled .dtl-star {
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
</style>
