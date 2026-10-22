<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Building2, Inbox, LayoutGrid, Lightbulb, LogOut, Menu, Newspaper, Settings, Users, X, Sparkles } from 'lucide-vue-next'
import PrismAmbientBackground from '@/design-system/components/PrismAmbientBackground.vue'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismDropdownMenu from '@/design-system/components/PrismDropdownMenu.vue'
import MyVocPopup from '@/features/feedback/views/MyVocPopup.vue'
import MyFeedbackPopup from '@/features/feedback/views/MyFeedbackPopup.vue'
import { useAuthStore } from '@/stores/auth'
import { notificationApi } from '@/features/notification/api'
import { authApi } from '@/features/auth/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import type { NotificationItem } from '@/features/notification/types'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

// ── VOC (Feedback) popup ────────────────────────────────────────────────────
const vocOpen = ref(false)
const myFeedbackId = ref<number | null>(null)
const myFeedbackOpen = computed({
  get: () => myFeedbackId.value !== null,
  set: (v: boolean) => {
    if (!v) myFeedbackId.value = null
  },
})

const isNarrow = ref(false)
const sidebarOpen = ref(false)

function onResize() {
  isNarrow.value = window.matchMedia('(max-width: 1199px)').matches
  if (!isNarrow.value) sidebarOpen.value = false
}
onMounted(onResize)
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const navItems = computed(() => [
  { to: '/home', label: 'Home', icon: LayoutGrid },
  { to: '/ai-news', label: 'AI News', icon: Newspaper },
  { to: '/skills', label: 'Skill Management', icon: Sparkles },
  { to: '/skill-requests', label: 'Skill Forum', icon: Lightbulb },
  ...(auth.isAdmin
    ? [
        { to: '/users', label: 'User Management', icon: Users },
        { to: '/organizations', label: 'Organization Management', icon: Building2 },
        { to: '/feedback', label: 'VOC Management', icon: Inbox },
      ]
    : []),
])

// Breadcrumb (GNB §4.2): reflects the current screen, visible on lg and above.
const breadcrumbLabel = computed(() => {
  for (const item of navItems.value) {
    if (isActive(item)) return item.label
  }
  if (route.path.startsWith('/settings')) return 'My Settings'
  return ''
})

function isActive(item: { to: string }): boolean {
  if (item.to === '/home') return route.path === '/home'
  return route.path.startsWith(item.to)
}

function onNavigate(item: { to: string; placeholder?: boolean }) {
  sidebarOpen.value = false
  if (item.placeholder) return // Skill Management module ships in a later phase
  router.push(item.to)
}

// ── Notification bell ───────────────────────────────────────────────────────
const unreadCount = ref(0)
const notifications = ref<NotificationItem[]>([])
let unreadTimer: number | undefined
const bellOpen = ref(false)
const bellButton = ref<HTMLElement | null>(null)
const bellPanel = ref<HTMLElement | null>(null)
const bellPos = ref({ top: 0, left: 0 })

async function loadUnread() {
  try {
    const data = await notificationApi.unreadCount()
    unreadCount.value = data.unreadCount
    // Keep the list fresh while the bell panel is open.
    if (bellOpen.value) {
      notifications.value = await notificationApi.list(10)
    }
  } catch {
    /* non-critical */
  }
}

async function toggleBell() {
  const opening = !bellOpen.value
  bellOpen.value = opening
  if (opening) {
    // Position the panel BEFORE it renders — otherwise the first open
    // flashes at top-left (0,0) until the API round-trip finishes.
    positionBell()
    await loadUnread()
    nextTickPosition()
  }
}

/** Synchronous first pass: known button rect + fixed panel width (340px CSS). */
function positionBell() {
  const btn = bellButton.value
  if (!btn) return
  const rect = btn.getBoundingClientRect()
  const width = 340
  const estHeight = 420 // CSS max-height, used for the flip-up check
  let left = rect.right - width
  left = Math.max(8, Math.min(left, window.innerWidth - width - 8))
  let top = rect.bottom + 8
  if (top + estHeight > window.innerHeight - 8) top = Math.max(8, rect.top - estHeight - 8)
  bellPos.value = { top, left }
}

/** Fine-tune pass after render: uses the panel's real size. */
function nextTickPosition() {
  requestAnimationFrame(() => {
    const btn = bellButton.value
    const panel = bellPanel.value
    if (!btn || !panel) return
    const rect = btn.getBoundingClientRect()
    const width = Math.max(panel.offsetWidth, 320)
    let left = rect.right - width
    left = Math.max(8, Math.min(left, window.innerWidth - width - 8))
    let top = rect.bottom + 8
    if (top + panel.offsetHeight > window.innerHeight - 8) top = Math.max(8, rect.top - panel.offsetHeight - 8)
    bellPos.value = { top, left }
  })
}

function onDocClick(event: MouseEvent) {
  if (!bellOpen.value) return
  if (bellPanel.value?.contains(event.target as Node)) return
  if (bellButton.value?.contains(event.target as Node)) return
  bellOpen.value = false
}

function onDocKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && bellOpen.value) bellOpen.value = false
}

onMounted(() => {
  loadUnread()
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onDocKeydown)
  // Poll the unread badge so new notifications appear without a page reload.
  unreadTimer = window.setInterval(loadUnread, 30_000)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onDocKeydown)
  window.clearInterval(unreadTimer)
})

async function markAllRead() {
  try {
    await notificationApi.markAllRead()
    unreadCount.value = 0
    notifications.value = notifications.value.map((n) => ({ ...n, isRead: true }))
  } catch (error) {
    toastError(toUserError(error).message)
  }
}

async function onBellItemClick(n: NotificationItem) {
  if (n.isRead) return
  try {
    await notificationApi.markRead(n.notificationId)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    notifications.value = notifications.value.map((item) =>
      item.notificationId === n.notificationId ? { ...item, isRead: true } : item,
    )
  } catch {
    /* non-critical */
  }
  // Deep-link: feedback notifications open the feedback detail popup
  if (n.feedbackId != null) {
    myFeedbackId.value = n.feedbackId
  }
}

function formatTime(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleString(undefined, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ── User menu ───────────────────────────────────────────────────────────────
const userMenuItems = [
  { label: 'My Settings', value: 'settings' },
  { label: 'VOC', value: 'voc' },
  { label: 'Logout', value: 'logout', danger: true },
]

async function onUserSelect(item: { value: string }) {
  if (item.value === 'settings') {
    router.push('/settings')
    return
  }
  if (item.value === 'voc') {
    vocOpen.value = true
    return
  }
  if (item.value === 'logout') {
    try {
      await authApi.logout()
    } catch {
      /* best-effort */
    }
    auth.clearSession()
    router.push('/login')
  }
}

const displayName = computed(() => auth.user?.userName ?? 'User')
const avatarSrc = computed(() => auth.user?.avatarUrl ?? undefined)
</script>

<template>
  <div class="nx-shell">
    <PrismAmbientBackground />

    <header class="nx-header">
      <button
        v-if="isNarrow"
        type="button"
        class="p-icon-button nx-menu-toggle"
        :aria-label="sidebarOpen ? 'Đóng menu' : 'Mở menu'"
        :aria-expanded="sidebarOpen"
        @click="sidebarOpen = !sidebarOpen"
      >
        <component :is="sidebarOpen ? X : Menu" :size="20" />
      </button>

      <button type="button" class="nx-brand" @click="router.push('/home')">
        <Sparkles :size="18" aria-hidden="true" />
        <span>AX PORTAL</span>
      </button>

      <nav v-if="breadcrumbLabel" class="nx-breadcrumb" aria-label="Breadcrumb">
        <span class="nx-breadcrumb-current">{{ breadcrumbLabel }}</span>
      </nav>

      <div class="nx-header-actions">
        <div class="nx-bell">
          <button
            ref="bellButton"
            type="button"
            class="p-icon-button nx-bell-button"
            aria-label="Thông báo"
            :aria-expanded="bellOpen"
            @click="toggleBell"
          >
            <Bell :size="20" />
            <span v-if="unreadCount > 0" class="nx-bell-badge" aria-hidden="true">
              {{ unreadCount > 99 ? '99+' : unreadCount }}
            </span>
          </button>
          <Teleport to="body">
            <div
              v-if="bellOpen"
              ref="bellPanel"
              class="p-glass nx-bell-panel"
              :style="{ top: `${bellPos.top}px`, left: `${bellPos.left}px` }"
              role="dialog"
              aria-label="Thông báo"
            >
              <header class="nx-bell-header">
                <strong>Notifications</strong>
                <button
                  v-if="unreadCount > 0"
                  type="button"
                  class="nx-bell-markall"
                  @click="markAllRead"
                >
                  Mark all read
                </button>
              </header>
              <ul class="nx-bell-list">
                <li
                  v-for="n in notifications"
                  :key="n.notificationId"
                  class="nx-bell-item"
                  :class="{ 'is-unread': !n.isRead }"
                  role="button"
                  tabindex="0"
                  @click="onBellItemClick(n)"
                  @keydown.enter="onBellItemClick(n)"
                >
                  <span class="nx-bell-dot" aria-hidden="true"></span>
                  <div>
                    <p class="nx-bell-title">{{ n.title }}</p>
                    <time class="nx-bell-time">{{ formatTime(n.createdAt) }}</time>
                  </div>
                </li>
                <li v-if="!notifications.length" class="nx-bell-empty">No notifications yet.</li>
              </ul>
            </div>
          </Teleport>
        </div>

        <PrismDropdownMenu :items="userMenuItems" label="User menu" @select="onUserSelect">
          <template #trigger>
            <span class="nx-user">
              <PrismAvatar :name="displayName" :src="avatarSrc" :size="32" />
              <span class="nx-user-name">{{ displayName }}</span>
            </span>
          </template>
          <template #icon-settings>
            <Settings :size="16" aria-hidden="true" />
          </template>
          <template #icon-voc>
            <Inbox :size="16" aria-hidden="true" />
          </template>
          <template #icon-logout>
            <LogOut :size="16" aria-hidden="true" />
          </template>
        </PrismDropdownMenu>
      </div>
    </header>

    <div class="nx-body">
      <aside
        class="nx-sidebar"
        :class="{ 'is-open': sidebarOpen }"
        :aria-hidden="isNarrow && !sidebarOpen ? 'true' : undefined"
      >
        <nav aria-label="Chính">
          <button
            v-for="item in navItems"
            :key="item.to"
            type="button"
            class="nx-nav-item"
            :class="{ 'is-active': isActive(item) }"
            :aria-current="isActive(item) ? 'page' : undefined"
            @click="onNavigate(item)"
          >
            <component :is="item.icon" :size="18" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </button>
        </nav>
      </aside>

      <div v-if="isNarrow && sidebarOpen" class="nx-scrim" @click="sidebarOpen = false"></div>

      <main class="nx-main">
        <RouterView />
      </main>
    </div>

    <MyVocPopup v-model:open="vocOpen" />
    <MyFeedbackPopup v-model:open="myFeedbackOpen" :feedback-id="myFeedbackId" />
  </div>
</template>

<style scoped>
.nx-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
}

.nx-header {
  position: sticky;
  top: 0;
  z-index: 40;
  height: var(--nx-app-header-height);
  display: flex;
  align-items: center;
  gap: var(--ds-space-4);
  padding: 0 var(--ds-space-4);
  border-bottom: 1px solid var(--ds-color-border);
  background: rgb(16 13 43 / 0.72);
  backdrop-filter: blur(10px) saturate(120%);
}

.nx-menu-toggle {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--ds-radius-sm);
}
.nx-menu-toggle:hover {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}

.nx-brand {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  border: none;
  background: none;
  cursor: pointer;
  font: 600 13px var(--ds-font-sans);
  letter-spacing: 0.14em;
  color: var(--ds-color-text-primary);
  padding: var(--ds-space-2);
  border-radius: var(--ds-radius-sm);
}
.nx-brand:hover {
  color: var(--ds-color-accent);
}
.nx-brand svg {
  color: var(--ds-color-accent);
}

.nx-header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
}

/* Breadcrumb (GNB §4.2) — visible on lg (≥1200px) and above */
.nx-breadcrumb {
  display: none;
}
@media (min-width: 1200px) {
  .nx-breadcrumb {
    display: flex;
    align-items: center;
    gap: var(--ds-space-2);
    margin-left: var(--ds-space-2);
  }
}
.nx-breadcrumb-current {
  font: 13px var(--ds-font-sans);
  color: var(--ds-color-text-secondary);
}

.nx-bell {
  position: relative;
}
.nx-bell-button {
  position: relative;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--ds-radius-sm);
}
.nx-bell-button:hover {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}
.nx-bell-badge {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  font: 700 10px var(--ds-font-mono);
  color: var(--ds-color-space-950);
  background: var(--ds-color-accent);
  border-radius: 9px;
}

.nx-bell-panel {
  position: fixed;
  z-index: 90;
  width: 340px;
  max-height: 420px;
  display: flex;
  flex-direction: column;
  /* Opaque surface like dialogs/toasts — the default .p-glass tint (10%) is
     too see-through for a large floating panel. */
  --ds-surface-tint: var(--ds-dialog-background);
  --ds-surface-blur: var(--ds-dialog-blur);
  border-color: var(--ds-color-border-strong);
}
.nx-bell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ds-space-3) var(--ds-space-4);
  border-bottom: 1px solid var(--ds-color-border);
  font-size: 13px;
}
.nx-bell-markall {
  border: none;
  background: none;
  color: var(--ds-color-accent);
  font: 12px var(--ds-font-sans);
  cursor: pointer;
}
.nx-bell-markall:hover {
  text-decoration: underline;
}
.nx-bell-list {
  list-style: none;
  margin: 0;
  padding: var(--ds-space-1);
  overflow-y: auto;
}
.nx-bell-item {
  display: flex;
  gap: var(--ds-space-2);
  padding: var(--ds-space-2) var(--ds-space-3);
  border-radius: var(--ds-radius-sm);
  cursor: pointer;
}
@media (hover: hover) { .nx-bell-item:hover { background: var(--ds-list-item-hover-accent); } }
.nx-bell-item.is-unread {
  background: var(--ds-list-item-hover-accent);
}
.nx-bell-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
  background: transparent;
}
.nx-bell-item.is-unread .nx-bell-dot {
  background: var(--ds-color-accent);
}
.nx-bell-title {
  margin: 0;
  font-size: 13px;
  color: var(--ds-color-text-primary);
}
.nx-bell-time {
  font: 11px var(--ds-font-mono);
  color: var(--ds-color-text-muted);
}
.nx-bell-empty {
  padding: var(--ds-space-6);
  text-align: center;
  color: var(--ds-color-text-muted);
  font-size: 13px;
}

.nx-user {
  display: flex;
  align-items: center;
  gap: var(--ds-space-2);
  padding: 4px var(--ds-space-2);
  border-radius: var(--ds-radius-sm);
}
.nx-user-name {
  font-size: 13px;
  color: var(--ds-color-text-secondary);
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nx-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.nx-sidebar {
  width: var(--nx-app-sidebar-width);
  flex-shrink: 0;
  padding: var(--ds-space-4) var(--ds-space-3);
  border-right: 1px solid var(--ds-color-border);
  background: rgb(16 13 43 / 0.5);
  position: relative;
  z-index: 1;
}
@media (min-width: 1536px) {
  .nx-sidebar {
    width: var(--nx-app-sidebar-width-wide);
  }
}

.nx-nav-item {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--ds-space-3);
  padding: 10px var(--ds-space-3);
  margin-bottom: var(--ds-space-1);
  border: none;
  border-radius: var(--ds-radius-sm);
  background: none;
  color: var(--ds-color-text-secondary);
  font: 14px var(--ds-font-sans);
  cursor: pointer;
  transition: background var(--ds-duration-fast) var(--ds-ease),
    color var(--ds-duration-fast) var(--ds-ease);
}
.nx-nav-item:hover {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}
.nx-nav-item.is-active {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}
.nx-nav-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 2px;
  background: var(--ds-color-accent);
  box-shadow: var(--ds-shadow-glow);
}
.nx-nav-item.is-placeholder {
  opacity: 0.55;
  cursor: default;
}
.nx-nav-tag {
  margin-left: auto;
  font: 10px var(--ds-font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ds-color-text-muted);
  border: 1px solid var(--ds-color-border);
  border-radius: 4px;
  padding: 2px 5px;
}

.nx-main {
  flex: 1;
  min-width: 0;
  padding: var(--ds-space-4);
  position: relative;
  z-index: 1;
}

.nx-scrim {
  position: fixed;
  inset: var(--nx-app-header-height) 0 0 0;
  z-index: 45;
  background: rgb(5 8 26 / 0.5);
}

@media (max-width: 1199px) {
  .nx-sidebar {
    position: fixed;
    top: var(--nx-app-header-height);
    bottom: 0;
    left: 0;
    z-index: 46;
    transform: translateX(-100%);
    transition: transform var(--ds-duration-normal) var(--ds-ease);
    background: var(--ds-color-space-950);
  }
  .nx-sidebar.is-open {
    transform: translateX(0);
  }
  .nx-user-name {
    display: none;
  }
  .nx-main {
    padding: var(--ds-space-4);
  }
}
</style>
