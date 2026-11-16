<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ThumbsUp, MessageSquare, Check, MoreVertical, Pencil, Trash2, Link2 } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import SkillDetailPopup from '@/features/skills/views/SkillDetailPopup.vue'
import { skillRequestApi } from '@/features/skill-market/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import { sanitizeHtml } from '@/lib/sanitize'
import { useI18nStore } from '@/stores/i18n'
import type { SkillRequestFeedItem } from '@/features/skill-market/types'

const props = defineProps<{ item: SkillRequestFeedItem }>()
const emit = defineEmits(['edit', 'deleted'])
const router = useRouter()
const { t } = useI18nStore()
const detailSkillId = ref<number | null>(null)

// ── Kebab menu (creator only) ──────────────────────────────────────────────
// The menu is teleported to <body> and positioned with fixed coordinates:
// the card uses .p-glass (backdrop-filter), which makes it a containing
// block + stacking context that would clip/cover an in-card absolute menu.
const menuOpen = ref(false)
const menuPos = ref({ top: 0, left: 0 })
const kebabBtn = ref<HTMLButtonElement | null>(null)

function toggleMenu() {
  if (menuOpen.value) { closeMenu(); return }
  const rect = kebabBtn.value?.getBoundingClientRect()
  if (!rect) return
  const MENU_W = 148
  const MENU_H = 92
  menuPos.value = {
    top: Math.min(rect.bottom + 4, window.innerHeight - MENU_H - 8),
    left: Math.max(8, rect.right - MENU_W),
  }
  menuOpen.value = true
  window.addEventListener('scroll', closeMenu, true)
  window.addEventListener('resize', closeMenu)
}
function closeMenu() {
  menuOpen.value = false
  window.removeEventListener('scroll', closeMenu, true)
  window.removeEventListener('resize', closeMenu)
}
function onEdit() { closeMenu(); emit('edit', props.item) }
function onDelete() { closeMenu(); emit('deleted', props.item) }

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEscKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEscKey)
  closeMenu()
})
function onDocClick(event: MouseEvent) {
  if (!menuOpen.value) return
  const target = event.target
  if (target instanceof HTMLElement && (target.closest('.kebab-menu') || target.closest('.kebab-btn'))) return
  closeMenu()
}
function onEscKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && menuOpen.value) closeMenu()
}

// ── Local state (mirrors the feed item, updated locally on actions) ─────────
const voteCount = ref(props.item.voteCount)
const hasVoted = ref(props.item.hasVoted)

const descLong = computed(() => (props.item.description ?? '').length > 160)
const descHtml = computed(() => sanitizeHtml(props.item.description ?? ''))
const descExpanded = ref(false)

// ── Attachments (all files as download rows) ───────────────────────────────
function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function onDownloadFile(fileId: number) {
  try {
    await skillRequestApi.downloadFile(props.item.id, fileId)
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

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

// ── Vote / unvote (toggle) ──────────────────────────────────────────────────
async function onVote() {
  const wasVoted = hasVoted.value
  // Optimistic update: flip the UI immediately, roll back on failure.
  hasVoted.value = !wasVoted
  voteCount.value = Math.max(0, voteCount.value + (wasVoted ? -1 : 1))
  try {
    if (wasVoted) {
      await skillRequestApi.unvote(props.item.id)
    } else {
      await skillRequestApi.vote(props.item.id)
    }
  } catch (err) {
    hasVoted.value = wasVoted
    voteCount.value = Math.max(0, voteCount.value + (wasVoted ? 1 : -1))
    toastError(toUserError(err).message)
  }
}

// ── Navigate to the detail screen (full comments live there) ───────────────
function openDetail() {
  void router.push({ name: 'skill-market-detail', params: { id: props.item.id } })
}

// ── Resolved skill chips ────────────────────────────────────────────────────
const skillStatusLabel = (code: string) =>
  code === 'PENDING_APPROVAL' ? t('app.common.statusPending') : code === 'DRAFT' ? t('app.common.statusDraft') : code === 'APPROVED' ? t('app.common.statusApproved') : code === 'REJECTED' ? t('app.common.statusRejected') : code
const skillStatusTone = (code: string) =>
  code === 'APPROVED' ? 'success' : code === 'PENDING_APPROVAL' ? 'warning' : code === 'REJECTED' ? 'danger' : 'neutral'

function onResolvedSkillClick(s: { skillId: number; skillStatus: string | null }) {
  if (s.skillStatus && s.skillStatus !== 'APPROVED') {
    toastError(t('app.skillMarket.notApproved'))
    return
  }
  detailSkillId.value = s.skillId
}
</script>

<template>
  <article class="card p-glass">
    <!-- Header -->
    <header class="card-head">
      <PrismAvatar :name="item.creatorName ?? ''" :src="item.creatorAvatarUrl ?? undefined" :size="40" />
      <div class="card-who">
        <span class="card-name">{{ item.creatorName ?? t('app.common.unknown') }}</span>
        <span class="card-time-row">
          <span class="card-time">{{ timeAgo(item.createdAt) }}</span>
          <PrismBadge v-if="item.hasResolvedComment" tone="success" class="card-resolved">
            <Check :size="12" aria-hidden="true" /> {{ t('app.skillMarket.resolved') }}
          </PrismBadge>
          <PrismBadge v-if="item.bountyPoints > 0" tone="info" class="card-bounty" title="Treo thưởng: điểm được khóa khi đăng, trao cho người đáp ứng">
            🏆 {{ item.bountyPoints }} điểm
          </PrismBadge>
        </span>
      </div>
      <!-- Kebab menu (creator only) -->
      <div v-if="item.isRequestCreator" class="kebab">
        <button
          ref="kebabBtn"
          type="button"
          class="kebab-btn"
          :class="{ 'is-open': menuOpen }"
          :aria-label="t('app.skillMarket.requestActionsAria')"
          aria-haspopup="menu"
          :aria-expanded="menuOpen"
          @click.stop="toggleMenu"
        >
          <MoreVertical :size="18" aria-hidden="true" />
        </button>
      </div>
    </header>

    <!-- Kebab dropdown (teleported: escapes the .p-glass stacking context) -->
    <teleport to="body">
      <div
        v-if="menuOpen"
        class="kebab-menu p-glass"
        role="menu"
        :style="{ top: menuPos.top + 'px', left: menuPos.left + 'px' }"
        @click.stop
      >
        <button type="button" class="kebab-item" role="menuitem" @click="onEdit">
          <Pencil :size="14" aria-hidden="true" /> {{ t('app.common.edit') }}
        </button>
        <button type="button" class="kebab-item kebab-danger" role="menuitem" @click="onDelete">
          <Trash2 :size="14" aria-hidden="true" /> {{ t('app.common.delete') }}
        </button>
      </div>
    </teleport>

    <!-- Title (clickable → detail) + description + tags -->
    <h3 class="card-title">
      <button type="button" class="card-title-link" @click="openDetail">{{ item.title }}</button>
    </h3>
    <div
      v-if="item.description"
      class="card-desc"
      :class="{ 'is-clamped': !descExpanded }"
      v-html="descHtml"
    ></div>
    <button v-if="descLong" type="button" class="card-desc-toggle" @click="descExpanded = !descExpanded">
      {{ descExpanded ? t('app.skillMarket.less') : t('app.skillMarket.moreWord') }}
    </button>
    <div v-if="item.hashtags.length" class="card-tags">
      <PrismBadge v-for="tag in item.hashtags" :key="tag" tone="info">#{{ tag }}</PrismBadge>
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

    <!-- Attachments: all files as download rows -->
    <div v-if="item.files.length" class="attach-files">
      <div v-for="f in item.files" :key="f.id" class="attach-file">
        <span class="attach-file-name" :title="f.fileName">📄 {{ f.fileName }} ({{ formatSize(f.fileSize) }})</span>
        <button type="button" class="attach-file-download" :aria-label="t('app.skillMarket.downloadFileAria', { name: f.fileName })" @click="onDownloadFile(f.id)">
          {{ t('app.common.download') }}
        </button>
      </div>
    </div>

    <!-- Reaction bar -->
    <div class="reaction-bar">
      <button
        type="button"
        class="reaction"
        :class="{ 'is-voted': hasVoted }"
        :aria-label="hasVoted ? t('app.skillMarket.unvote') : t('app.skillMarket.vote')"
        @click="onVote"
      >
        <ThumbsUp :size="16" :fill="hasVoted ? 'currentColor' : 'none'" aria-hidden="true" />
        <span>{{ voteCount }}</span>
      </button>
      <button type="button" class="reaction" @click="openDetail">
        <MessageSquare :size="16" aria-hidden="true" />
        <span>{{ item.commentCount }}</span>
      </button>
    </div>

    <!-- Comments preview (read-only) + link to the detail screen -->
    <div class="comments">
      <div v-if="item.topComments.length" class="comments-list">
        <div
          v-for="c in item.topComments"
          :key="c.id"
          class="comment-preview"
          :class="{ 'is-resolved': c.resolvedYn === 'Y' }"
        >
          <PrismAvatar :name="c.userName ?? ''" :src="c.userAvatarUrl ?? undefined" :size="24" />
          <div class="comment-preview-body">
            <div class="comment-preview-head">
              <span class="comment-user">{{ c.userName ?? t('app.common.unknown') }}</span>
              <span class="comment-time">{{ timeAgo(c.createdAt) }}</span>
              <span v-if="c.resolvedYn === 'Y'" class="comment-resolved">
                <Check :size="12" aria-hidden="true" /> {{ t('app.skillMarket.resolved') }}
              </span>
            </div>
            <p class="comment-body" v-html="sanitizeHtml(c.content)"></p>
          </div>
        </div>
      </div>
      <button type="button" class="view-all" @click="openDetail">
        {{ item.commentCount > 2 ? t('app.skillMarket.viewAllComments', { n: item.commentCount }) : t('app.skillMarket.viewComments') }} →
      </button>
    </div>
  </article>

  <SkillDetailPopup :skill-id="detailSkillId" @closed="detailSkillId = null" />
</template>

<style scoped>
.card {
  padding: var(--ds-space-6);
  display: grid;
  gap: var(--ds-space-3);
}
.card-head {
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
}
/* Kebab menu */
.kebab {
  position: relative;
  margin-left: auto;
}
.kebab-btn {
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
.kebab-btn.is-open {
  background: rgb(94 106 210 / .10);
  color: var(--ds-color-text-primary);
}
@media (hover: hover) {
  .kebab-btn:hover {
    background: rgb(94 106 210 / .10);
    color: var(--ds-color-text-primary);
  }
}
.kebab-menu {
  position: fixed;
  min-width: 148px;
  display: grid;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  z-index: 1200;
}
.kebab-item {
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
  .kebab-item:hover {
    background: rgb(94 106 210 / .10);
    color: var(--ds-color-text-primary);
  }
  .kebab-danger:hover {
    background: rgb(229 83 75 / .12);
    color: var(--ds-color-danger);
  }
}
.kebab-danger {
  color: var(--ds-color-danger);
}
.card-who {
  display: grid;
  gap: 2px;
}
.card-name {
  font-weight: 600;
  font-size: 15px;
}
.card-time-row {
  display: inline-flex;
  align-items: center;
  gap: var(--ds-space-2);
}
.card-time {
  font-size: 12px;
  color: var(--ds-color-text-muted);
}
.card-title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  line-height: 1.35;
}
.card-title-link {
  border: none;
  background: none;
  padding: 0;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: color var(--ds-duration-fast) var(--ds-ease);
}
@media (hover: hover) {
  .card-title-link:hover {
    color: var(--ds-color-accent);
  }
}
.card-title-link:focus-visible {
  outline: 2px solid var(--ds-color-accent);
  outline-offset: 2px;
  border-radius: var(--ds-radius-sm);
}
.card-desc {
  margin: 0;
  color: var(--ds-color-text-secondary);
  font-size: 15px;
  line-height: 1.6;
}
.card-desc :deep(p) {
  margin: 0 0 var(--ds-space-2);
}
.card-desc :deep(p:last-child) {
  margin-bottom: 0;
}
.card-desc :deep(a) {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
.card-desc :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--ds-radius-sm);
}
.card-desc.is-clamped {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-desc-toggle {
  border: none;
  background: none;
  padding: 0;
  color: var(--ds-color-accent);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  justify-self: start;
}
.card-tags {
  display: flex;
  gap: var(--ds-space-2);
  flex-wrap: wrap;
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
  border: 1px solid rgb(76 195 138 / .30);
  border-radius: 6px;
  color: var(--ds-color-success);
  background: rgb(76 195 138 / .07);
  cursor: pointer;
  white-space: nowrap;
  transition: border-color var(--ds-duration-fast) ease, background var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .resolved-chip:hover {
    border-color: rgb(76 195 138 / .55);
    background: rgb(76 195 138 / .14);
  }
}
.resolved-more {
  font: 12px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
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
  border-color: rgb(76 195 138 / .35);
}
.resolved-status.is-warning {
  color: var(--ds-color-warning);
  border-color: rgb(229 161 0 / .35);
}
.resolved-status.is-danger {
  color: var(--ds-color-danger);
  border-color: rgb(229 83 75 / .35);
}

/* ── Attachment rows (all files as download links) ──────────────────────── */
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

.reaction-bar {
  display: flex;
  align-items: center;
  gap: var(--ds-space-4);
  padding: var(--ds-space-2) 0;
  border-top: 1px solid var(--ds-color-border);
  border-bottom: 1px solid var(--ds-color-border);
}
.reaction {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--ds-color-border);
  border-radius: 20px;
  background: var(--ds-input-background);
  color: var(--ds-color-text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 6px 14px;
  transition: all var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .reaction:hover {
    border-color: var(--ds-color-border-strong);
    color: var(--ds-color-text-primary);
  }
}
.reaction.is-voted {
  color: var(--ds-color-accent);
  border-color: rgb(94 106 210 / .42);
  background: rgb(94 106 210 / .10);
}
.reaction span {
  font: 12px var(--ds-font-mono);
}

/* ── Comments preview (read-only, compact — matches the detail view) ────── */
.comments {
  display: grid;
  gap: var(--ds-space-2);
}
.comments-list {
  display: grid;
  gap: 2px;
}
.comment-preview {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 6px 8px;
  border-radius: var(--ds-radius-sm);
  transition: background var(--ds-duration-fast) ease;
}
@media (hover: hover) {
  .comment-preview:hover {
    background: rgb(94 106 210 / .04);
  }
}
.comment-preview.is-resolved {
  background: rgb(76 195 138 / .05);
}
@media (hover: hover) {
  .comment-preview.is-resolved:hover {
    background: rgb(76 195 138 / .08);
  }
}
.comment-preview-body {
  min-width: 0;
  display: grid;
  gap: 2px;
}
.comment-preview-head {
  display: flex;
  align-items: baseline;
  gap: var(--ds-space-2);
}
.comment-user {
  font-weight: 600;
  font-size: 13px;
}
.comment-time {
  font-size: 11.5px;
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
  font-size: 13.5px;
  color: var(--ds-color-text-secondary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.comment-body :deep(p) {
  margin: 0;
}
.comment-body :deep(a) {
  color: var(--ds-color-accent);
  text-decoration: underline;
}
/* @mention chips (same style as the detail view) */
.comment-body :deep(.mention),
.comment-body :deep(span[data-type="mention"]) {
  color: var(--ds-color-accent);
  background: rgb(94 106 210 / .12);
  border-radius: 3px;
  padding: 0 3px;
}
.view-all {
  border: none;
  background: none;
  color: var(--ds-color-accent);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  padding: 2px 0;
  text-align: left;
}
</style>
