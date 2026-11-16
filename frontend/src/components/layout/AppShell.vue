<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Building2, Check, Inbox, Info, Languages, LayoutGrid, Lightbulb, LogOut, Menu, MonitorSmartphone, Newspaper, Settings, Users, X, Sparkles, Wrench, Plug } from 'lucide-vue-next'
import PrismAmbientBackground from '@/design-system/components/PrismAmbientBackground.vue'
import PrismAvatar from '@/design-system/components/PrismAvatar.vue'
import PrismDropdownMenu from '@/design-system/components/PrismDropdownMenu.vue'
// Async: MyVocPopup pulls in the whole tiptap rich-text editor chain,
// which would otherwise block the critical path of every page.
const AppPromoPopup = defineAsyncComponent(() => import('@/components/AppPromoPopup.vue'))
const MyVocPopup = defineAsyncComponent(() => import('@/features/feedback/views/MyVocPopup.vue'))
const MyFeedbackPopup = defineAsyncComponent(() => import('@/features/feedback/views/MyFeedbackPopup.vue'))
import { useAuthStore } from '@/stores/auth'
import { useI18nStore, LANGUAGES, type AppLang } from '@/stores/i18n'
import { isIntroductionEnabled } from '@/config/introduction'
import { notificationApi } from '@/features/notification/api'
import { authApi } from '@/features/auth/api'
import { toUserError } from '@/lib/api'
import { toastError } from '@/lib/toast'
import type { NotificationItem } from '@/features/notification/types'

const auth = useAuthStore()
const i18n = useI18nStore()
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

// Introduction page button — all environments (runtime hostname check,
// see config/introduction.ts). Computed once: the hostname never changes
// without a reload.
const showIntroduction = computed(() => isIntroductionEnabled())

// On the Introduction page the sidebar is hidden so the embedded page
// gets the full width below the GNB.
const isIntroduction = computed(() => route.path === '/introduction')

// CTA navigation from the Introduction iframe — the page posts
// { type: 'ax-intro-nav', path } with the app origin as targetOrigin.
function onIntroNavMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin) return
  const data = event.data as { type?: string; path?: string } | null
  if (data?.type !== 'ax-intro-nav' || typeof data.path !== 'string') return
  if (!data.path.startsWith('/') || data.path.startsWith('//')) return
  void router.push(data.path)
}

function onResize() {
  isNarrow.value = window.matchMedia('(max-width: 1199px)').matches
  if (!isNarrow.value) sidebarOpen.value = false
  onBellResize()
}
onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const navItems = computed(() => [
  { to: '/home', label: i18n.t('app.nav.home'), icon: LayoutGrid },
  { to: '/ai-news', label: i18n.t('app.nav.aiNews'), icon: Newspaper },
  { to: '/skills', label: i18n.t('app.nav.skills'), icon: Sparkles },
  { to: '/mcps', label: i18n.t('app.nav.mcps'), icon: Plug },
  { to: '/skill-market', label: i18n.t('app.nav.skillMarket'), icon: Lightbulb },
  { to: '/ai-tools', label: i18n.t('app.nav.aiTools'), icon: Wrench },
])

// Admin-only menu group (User/Organization/VOC management).
const adminNavItems = computed(() => auth.isAdmin ? [
  { to: '/users', label: i18n.t('app.nav.users'), icon: Users },
  { to: '/organizations', label: i18n.t('app.nav.organizations'), icon: Building2 },
  { to: '/feedback', label: i18n.t('app.nav.feedback'), icon: Inbox },
  { to: '/translations', label: i18n.t('app.nav.translations'), icon: Languages },
] : [])

// Breadcrumb (GNB §4.2): reflects the current screen, visible on lg and above.
// The Introduction page has no breadcrumb — the "Introduction" button in the
// header already labels the screen.
const breadcrumbLabel = computed(() => {
  if (route.path === '/introduction') return ''
  for (const item of [...navItems.value, ...adminNavItems.value]) {
    if (isActive(item)) return item.label
  }
  if (route.path.startsWith('/settings')) return i18n.t('app.breadcrumb.settings')
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
const bellList = ref<HTMLElement | null>(null)
const bellPos = ref({ top: 0, left: 0 })
const BELL_PAGE_SIZE = 20
const hasMore = ref(false)
const loadingMore = ref(false)

async function loadUnread() {
  // Don't poll while the tab is hidden (the badge isn't visible anyway);
  // the visibilitychange handler refreshes immediately on return.
  if (document.hidden) return
  try {
    const data = await notificationApi.unreadCount()
    unreadCount.value = data.unreadCount
    // Keep the list fresh while the bell panel is open (first page only).
    // Skip the refresh when the user has scrolled down (pages 2..N loaded):
    // replacing the list with page 1 would wipe the loaded pages and jump
    // the scroll back to the top every 30s. The badge count still updates.
    if (bellOpen.value && (bellList.value?.scrollTop ?? 0) === 0) {
      const page = await notificationApi.list(BELL_PAGE_SIZE)
      notifications.value = page
      hasMore.value = page.length >= BELL_PAGE_SIZE
    }
  } catch {
    /* non-critical */
  }
}

/** Lazy load: scroll near the bottom loads the next 20 (keyset by id). */
async function onBellListScroll() {
  const el = bellList.value
  if (!el || loadingMore.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight < el.scrollHeight - 40) return
  loadingMore.value = true
  try {
    const last = notifications.value[notifications.value.length - 1]
    const page = await notificationApi.list(BELL_PAGE_SIZE, last?.notificationId)
    notifications.value = [...notifications.value, ...page]
    hasMore.value = page.length >= BELL_PAGE_SIZE
  } catch {
    /* non-critical */
  } finally {
    loadingMore.value = false
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

// ── Language switcher ───────────────────────────────────────────────────────
const langOpen = ref(false)
const langButton = ref<HTMLElement | null>(null)
const langPanel = ref<HTMLElement | null>(null)
const langPos = ref({ top: 0, left: 0 })

function positionLang() {
  const btn = langButton.value
  if (!btn) return
  const rect = btn.getBoundingClientRect()
  const width = 180
  const estHeight = 140
  let left = rect.right - width
  left = Math.max(8, Math.min(left, window.innerWidth - width - 8))
  let top = rect.bottom + 8
  if (top + estHeight > window.innerHeight - 8) top = Math.max(8, rect.top - estHeight - 8)
  langPos.value = { top, left }
}

function toggleLang() {
  const opening = !langOpen.value
  langOpen.value = opening
  if (opening) positionLang()
}

async function onLangSelect(lang: AppLang) {
  langOpen.value = false
  if (lang === i18n.language) return
  try {
    await i18n.setLanguage(lang)
  } catch {
    /* non-critical — keep the current language */
  }
}

function onDocClick(event: MouseEvent) {
  if (!bellOpen.value) {
    if (langOpen.value && !langPanel.value?.contains(event.target as Node) && !langButton.value?.contains(event.target as Node)) {
      langOpen.value = false
    }
    return
  }
  if (bellPanel.value?.contains(event.target as Node)) return
  if (bellButton.value?.contains(event.target as Node)) return
  bellOpen.value = false
}

function onDocKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && bellOpen.value) bellOpen.value = false
  if (event.key === 'Escape' && langOpen.value) langOpen.value = false
}

// While the bell panel is open, keep it anchored to the bell button on
// window resize (otherwise it stays at stale coordinates / goes off-screen).
function onBellResize() {
  if (bellOpen.value) nextTickPosition()
}

/** Refresh the badge when the tab becomes visible again (polls skip hidden tabs). */
function onVisibilityChange() {
  if (!document.hidden) loadUnread()
}

// Combobox panels are teleported to <body> — tag them when they appear so
// the compact panel styles (main.css, .search-combobox-panel) apply. The
// observer callback runs as a microtask (before paint), so the class lands
// before the panel becomes visible — no size flash. The panel's <ul> id is
// the input's aria-controls value, so the trigger can be found synchronously
// (no rect check needed — the panel is still at 0,0 until it is positioned).
// Tagged triggers: search areas and the Rows (page size) comboboxes.
let panelObserver: MutationObserver | null = null
function tagComboboxPanels() {
  document
    .querySelectorAll('.p-combobox-panel:not(.search-combobox-panel)')
    .forEach((el) => {
      const listId = el.querySelector('ul')?.id
      if (!listId) return
      const control = document.querySelector(`[aria-controls="${CSS.escape(listId)}"]`)
      if (control?.closest('.search, .ainews-search-body, .sm-pagesize, .um-pagesize, .fbm-pagesize, .om-pagesize')) {
        el.classList.add('search-combobox-panel')
      }
    })
}

onMounted(() => {
  loadUnread()
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onDocKeydown)
  // CTA buttons inside the Introduction iframe ("Vào hệ thống", "Vào AX
  // Portal ngay") post a message so the app navigates via the router
  // (SPA — no full page reload).
  window.addEventListener('message', onIntroNavMessage)
  // Poll the unread badge so new notifications appear without a page reload.
  unreadTimer = window.setInterval(loadUnread, 30_000)
  // loadUnread skips hidden tabs — refresh once when the tab comes back.
  document.addEventListener('visibilitychange', onVisibilityChange)
  // AppShell mounts on login — show the AX Desktop promo unless dismissed today.
  promoOpen.value = shouldShowPromo()
  // Combobox dropdown panels teleport to <body> — tag them so the compact
  // search-panel styles (main.css) apply. Panels are direct children of body.
  panelObserver = new MutationObserver(tagComboboxPanels)
  panelObserver.observe(document.body, { childList: true })
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onDocKeydown)
  window.removeEventListener('message', onIntroNavMessage)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.clearInterval(unreadTimer)
  panelObserver?.disconnect()
  panelObserver = null
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
  // Close the panel first so the deep-link target (page / detail popup)
  // is not covered by the fixed panel.
  bellOpen.value = false
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
  // Deep-link: skill notifications open the skill detail popup
  if (n.skillId != null) {
    const id = Number(n.skillId)
    if (Number.isInteger(id) && id > 0) {
      safeNav({ name: 'skills', query: { detail: String(id) } })
    }
  }
  // Deep-link: forum notifications open the skill request detail
  if (n.requestId != null) {
    safeNav({ name: 'skill-market-detail', params: { id: n.requestId } })
  }
  // Deep-link: AI News @mention notifications open the news item and
  // scroll to the mentioning comment (if it still exists).
  if (n.newsId != null) {
    safeNav({
      name: 'ai-news',
      query: { news: n.newsId, ...(n.commentId != null ? { comment: n.commentId } : {}) },
    })
  }
  // Deep-link: MCP notifications (e.g. submitted for approval) open the
  // MCP detail popup on the MCP Management screen.
  if (n.mcpId != null) {
    void router.push({ name: 'mcps', query: { detail: String(n.mcpId) } })
  }
}

/** Bell deep-link navigation with an unhandled-rejection guard. */
function safeNav(target: Parameters<typeof router.push>[0]) {
  router.push(target).catch(() => toastError(i18n.t('app.common.navError')))
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

// ── AX Desktop promo popup ──────────────────────────────────────────────────
// Shown on login (AppShell mount) unless the user ticked "don't show again
// today" — the dismissed date (YYYY-MM-DD) is stored in localStorage.
const APP_PROMO_KEY = 'ax-desktop-promo-dismissed'
const promoOpen = ref(false)

function shouldShowPromo(): boolean {
  const dismissed = localStorage.getItem(APP_PROMO_KEY)
  return dismissed !== new Date().toISOString().slice(0, 10)
}

// ── User menu ───────────────────────────────────────────────────────────────
const userMenuItems = computed(() => [
  { label: i18n.t('app.menu.settings'), value: 'settings' },
  { label: i18n.t('app.menu.voc'), value: 'voc' },
  { label: i18n.t('app.menu.logout'), value: 'logout', danger: true },
])

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
        :aria-label="i18n.t('app.header.menuToggle')"
        :aria-expanded="sidebarOpen"
        @click="sidebarOpen = !sidebarOpen"
      >
        <component :is="sidebarOpen ? X : Menu" :size="20" />
      </button>

      <button type="button" class="nx-brand" @click="router.push('/home')">
        <Sparkles :size="18" aria-hidden="true" />
        <span>AX PORTAL</span>
      </button>

      <!-- Introduction page (local + dev only — see config/introduction.ts).
           Routes to /introduction: the static /introduction.html is embedded
           in an iframe below the GNB, so the header (incl. the language
           switcher) stays visible and controls the page language. -->
      <button
        v-if="showIntroduction"
        type="button"
        class="nx-intro"
        :aria-label="i18n.t('app.header.introduction')"
        :title="i18n.t('app.header.introduction')"
        @click="router.push('/introduction')"
      >
        <Info :size="15" aria-hidden="true" />
        <span>{{ i18n.t('app.header.introduction') }}</span>
      </button>

      <nav v-if="breadcrumbLabel" class="nx-breadcrumb" aria-label="Breadcrumb">
        <span class="nx-breadcrumb-current">{{ breadcrumbLabel }}</span>
      </nav>

      <div class="nx-header-actions">
        <button
          type="button"
          class="p-icon-button nx-promo-button"
          :aria-label="i18n.t('app.header.promo')"
          :title="i18n.t('app.header.promo')"
          @click="promoOpen = true"
        >
          <MonitorSmartphone :size="20" />
        </button>
        <div class="nx-lang">
          <button
            ref="langButton"
            type="button"
            class="p-icon-button nx-lang-button"
            :aria-label="i18n.t('app.header.language')"
            :aria-expanded="langOpen"
            @click="toggleLang"
          >
            <Languages :size="20" />
          </button>
          <Teleport to="body">
            <div
              v-if="langOpen"
              ref="langPanel"
              class="p-glass nx-lang-panel"
              :style="{ top: `${langPos.top}px`, left: `${langPos.left}px` }"
              role="menu"
              :aria-label="i18n.t('app.header.language')"
            >
              <button
                v-for="lang in LANGUAGES"
                :key="lang.code"
                type="button"
                class="nx-lang-item"
                :class="{ 'is-active': i18n.language === lang.code }"
                role="menuitemradio"
                :aria-checked="i18n.language === lang.code"
                @click="onLangSelect(lang.code)"
              >
                <span>{{ lang.name }}</span>
                <Check v-if="i18n.language === lang.code" :size="16" aria-hidden="true" />
              </button>
            </div>
          </Teleport>
        </div>
        <div class="nx-bell">
          <button
            ref="bellButton"
            type="button"
            class="p-icon-button nx-bell-button"
            :aria-label="i18n.t('app.header.notifications')"
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
              :aria-label="i18n.t('app.header.notifications')"
            >
              <header class="nx-bell-header">
                <strong>{{ i18n.t('app.header.notifications') }}</strong>
                <button
                  v-if="unreadCount > 0"
                  type="button"
                  class="nx-bell-markall"
                  @click="markAllRead"
                >
                  {{ i18n.t('app.header.markAllRead') }}
                </button>
              </header>
              <ul ref="bellList" class="nx-bell-list" @scroll.passive="onBellListScroll">
                <li
                  v-for="n in notifications"
                  :key="n.notificationId"
                  class="nx-bell-item"
                  :class="{ 'is-unread': !n.isRead }"
                  role="button"
                  tabindex="0"
                  @click="onBellItemClick(n)"
                  @keydown.enter="onBellItemClick(n)"
                  @keydown.space.prevent="onBellItemClick(n)"
                >
                  <span class="nx-bell-dot" aria-hidden="true"></span>
                  <div>
                    <p class="nx-bell-title">{{ n.title }}</p>
                    <time class="nx-bell-time">{{ formatTime(n.createdAt) }}</time>
                  </div>
                </li>
                <li v-if="!notifications.length" class="nx-bell-empty">{{ i18n.t('app.header.noNotifications') }}</li>
              </ul>
            </div>
          </Teleport>
        </div>

        <PrismDropdownMenu :items="userMenuItems" :label="i18n.t('app.header.userMenu')" @select="onUserSelect">
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
        v-if="!isIntroduction"
        class="nx-sidebar"
        :class="{ 'is-open': sidebarOpen }"
        :aria-hidden="isNarrow && !sidebarOpen ? 'true' : undefined"
      >
        <nav :aria-label="i18n.t('app.nav.main')">
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
          <template v-if="adminNavItems.length">
            <div class="nx-nav-group-label">{{ i18n.t('app.nav.adminGroup') }}</div>
            <button
              v-for="item in adminNavItems"
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
          </template>
        </nav>
      </aside>

      <div v-if="isNarrow && sidebarOpen" class="nx-scrim" @click="sidebarOpen = false"></div>

      <main class="nx-main" :class="{ 'nx-main--intro': isIntroduction }">
        <RouterView />
      </main>
    </div>

    <MyVocPopup v-if="vocOpen" v-model:open="vocOpen" />
    <MyFeedbackPopup v-if="myFeedbackOpen" v-model:open="myFeedbackOpen" :feedback-id="myFeedbackId" />
    <AppPromoPopup v-if="promoOpen" v-model:open="promoOpen" />
  </div>
</template>

<style scoped>
.nx-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--ds-color-canvas);
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
  /* v2.0 Linear: flat header on the same plane as the background — no blur/glass. */
  background: var(--ds-color-canvas);
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
/* Introduction button (local + dev only) — subtle pill next to the brand. */
.nx-intro {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: 600 12px var(--ds-font-sans);
  letter-spacing: 0.04em;
  color: var(--ds-color-text-secondary);
  padding: 5px 12px;
  border: 1px solid var(--ds-color-border);
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: color .15s ease, border-color .15s ease;
}
.nx-intro:hover {
  color: var(--ds-color-accent);
  border-color: var(--ds-color-border-strong);
}
.nx-intro svg {
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
.nx-promo-button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--ds-radius-sm);
}
.nx-promo-button:hover {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
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

.nx-lang {
  position: relative;
}
.nx-lang-button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--ds-radius-sm);
}
.nx-lang-button:hover {
  background: var(--ds-list-item-hover-accent);
  color: var(--ds-color-text-primary);
}
.nx-lang-panel {
  position: fixed;
  z-index: 90;
  width: 180px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--ds-space-2);
  /* Opaque surface like dialogs/toasts — the default .p-glass tint (10%) is
     too see-through for a floating panel. */
  --ds-surface-tint: var(--ds-dialog-background);
  --ds-surface-blur: var(--ds-dialog-blur);
  border-color: var(--ds-color-border-strong);
}
.nx-lang-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-space-3);
  padding: var(--ds-space-2) var(--ds-space-3);
  border: none;
  background: none;
  border-radius: var(--ds-radius-sm);
  font: 500 13px var(--ds-font-sans);
  color: var(--ds-color-text-primary);
  cursor: pointer;
  text-align: left;
}
.nx-lang-item:hover {
  background: var(--ds-list-item-hover-accent);
}
.nx-lang-item.is-active {
  color: var(--ds-color-accent);
}
.nx-lang-item.is-active svg {
  color: var(--ds-color-accent);
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
  /* v2.0 Linear layer 0: sidebar is flat on the same plane as the background —
     same colour, no border, no shadow, no radius. */
  border-right: none;
  background: var(--ds-color-canvas);
  position: sticky;
  top: var(--nx-app-header-height);
  height: calc(100vh - var(--nx-app-header-height));
  overflow-y: auto;
  z-index: var(--ds-z-sidebar, 0);
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
}
.nx-nav-item.is-placeholder {
  opacity: 0.55;
  cursor: default;
}
/* Admin menu group label — separated from the main group by a divider line
   above the label. */
.nx-nav-group-label {
  margin: var(--ds-space-3) 0 var(--ds-space-1);
  padding: 0 var(--ds-space-3);
  font: 10px var(--ds-font-mono);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ds-color-text-muted);
}
.nx-nav-group-label::before {
  content: '';
  display: block;
  height: 1px;
  margin-bottom: var(--ds-space-2);
  background: var(--ds-color-border);
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
  /* v2.0 Linear layer 1: the main content is ONE elevated card. */
  z-index: var(--ds-z-main, 10);
  margin: 10px;
  background: var(--ds-color-surface);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-lg);
  box-shadow: var(--ds-shadow-card);
}
/* Introduction page: no padding — the embedded page manages its own spacing. */
.nx-main--intro {
  padding: 0;
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
