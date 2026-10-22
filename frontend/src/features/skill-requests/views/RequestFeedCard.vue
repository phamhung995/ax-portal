<script setup lang="ts">
import { computed, ref } from 'vue'
import { ThumbsUp, MessageSquare, Check } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismBadge from '@/design-system/components/PrismBadge.vue'
import ImageLightbox from '@/features/skill-requests/components/ImageLightbox.vue'
import { skillRequestApi } from '@/features/skill-requests/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import { sanitizeHtml } from '@/lib/sanitize'
import type { SkillRequestFeedItem } from '@/features/skill-requests/types'

const props = defineProps<{ item: SkillRequestFeedItem }>()
const router = useRouter()

// ── Local state (mirrors the feed item, updated locally on actions) ─────────
const voteCount = ref(props.item.voteCount)
const hasVoted = ref(props.item.hasVoted)

const descLong = computed(() => (props.item.description ?? '').length > 160)
const descHtml = computed(() => sanitizeHtml(props.item.description ?? ''))
const descExpanded = ref(false)

// ── Attachments (image grid + non-image download links) ───────────────────
const imageFiles = computed(() => props.item.files.filter((f) => f.isImage && f.url))
const nonImageFiles = computed(() => props.item.files.filter((f) => !f.isImage))
const lightboxOpen = ref(false)
const lightboxIndex = ref(0)

function openLightbox(index: number) {
  lightboxIndex.value = index
  lightboxOpen.value = true
}

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

// ── Vote / unvote (toggle) ──────────────────────────────────────────────────
async function onVote() {
  try {
    if (hasVoted.value) {
      await skillRequestApi.unvote(props.item.id)
      hasVoted.value = false
      voteCount.value = Math.max(0, voteCount.value - 1)
    } else {
      await skillRequestApi.vote(props.item.id)
      hasVoted.value = true
      voteCount.value += 1
    }
  } catch (err) {
    toastError(toUserError(err).message)
  }
}

// ── Navigate to the detail screen (full comments live there) ───────────────
function openDetail() {
  void router.push({ name: 'skill-request-detail', params: { id: props.item.id } })
}
</script>

<template>
  <article class="card p-glass">
    <!-- Header -->
    <header class="card-head">
      <PrismAvatar :name="item.creatorName ?? ''" :size="40" />
      <div class="card-who">
        <span class="card-name">{{ item.creatorName ?? 'Unknown' }}</span>
        <span class="card-time-row">
          <span class="card-time">{{ timeAgo(item.createdAt) }}</span>
          <PrismBadge v-if="item.hasResolvedComment" tone="success" class="card-resolved">
            <Check :size="12" aria-hidden="true" /> Resolved
          </PrismBadge>
        </span>
      </div>
    </header>

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
      {{ descExpanded ? 'less' : 'more' }}
    </button>
    <div v-if="item.hashtags.length" class="card-tags">
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
        <button type="button" class="attach-file-download" :aria-label="`Download ${f.fileName}`" @click="onDownloadFile(f.id)">
          Download
        </button>
      </div>
    </div>

    <!-- Reaction bar -->
    <div class="reaction-bar">
      <button
        type="button"
        class="reaction"
        :class="{ 'is-voted': hasVoted }"
        :aria-label="hasVoted ? 'Unvote' : 'Vote'"
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
          <PrismAvatar :name="c.userName ?? ''" :size="24" />
          <div class="comment-preview-body">
            <div class="comment-preview-head">
              <span class="comment-user">{{ c.userName ?? 'Unknown' }}</span>
              <span class="comment-time">{{ timeAgo(c.createdAt) }}</span>
              <span v-if="c.resolvedYn === 'Y'" class="comment-resolved">
                <Check :size="12" aria-hidden="true" /> Resolved
              </span>
            </div>
            <p class="comment-body" v-html="sanitizeHtml(c.content)"></p>
          </div>
        </div>
      </div>
      <button type="button" class="view-all" @click="openDetail">
        {{ item.commentCount > 2 ? `View all ${item.commentCount} comments` : 'View comments' }} →
      </button>
    </div>

    <!-- Image lightbox (opened from the attachment grid) -->
    <ImageLightbox
      :images="imageFiles.map((f) => ({ url: f.url!, name: f.fileName }))"
      :index="lightboxIndex"
      :open="lightboxOpen"
      @update:index="lightboxIndex = $event"
      @close="lightboxOpen = false"
    />
  </article>
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

/* ── Attachment image grid ─────────────────────────────────────────────── */
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

/* ── Non-image attachment rows (mirrors Skill Detail popup .dtl-file) ──── */
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
  .reaction:hover {
    background: rgb(103 232 249 / .10);
    color: var(--ds-color-text-primary);
  }
}
.reaction.is-voted {
  color: var(--ds-color-accent);
}

/* ── Comments preview (read-only) ──────────────────────────────────────── */
.comments {
  display: grid;
  gap: var(--ds-space-2);
}
.comments-list {
  display: grid;
  gap: var(--ds-space-2);
}
.comment-preview {
  display: flex;
  align-items: flex-start;
  gap: var(--ds-space-2);
  padding: var(--ds-space-2) var(--ds-space-3);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-input-background);
}
.comment-preview.is-resolved {
  border-color: rgb(156 232 208 / .35);
}
.comment-preview-body {
  min-width: 0;
}
.comment-preview-head {
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
.comment-body {
  margin: 2px 0 0;
  font-size: 14px;
  color: var(--ds-color-text-primary);
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
