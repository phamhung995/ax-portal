import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, request } from '@/lib/api'
import { settingsApi } from '@/features/users/api'

/**
 * Shared i18n store — DB-driven translations (en/vi/ko).
 * Messages come from GET /i18n/messages?lang= and are cached per language in
 * localStorage (stale-while-revalidate). The language preference is the
 * user's Settings value (user_settings.language).
 */

export type AppLang = 'en' | 'vi' | 'ko'

/**
 * Language names shown in the switcher are always the native endonym
 * (English / Tiếng Việt / 한국어) — per spec, NOT translated via the DB.
 */
export const LANGUAGES: { code: AppLang; name: string }[] = [
  { code: 'en', name: 'English' },
  { code: 'vi', name: 'Tiếng Việt' },
  { code: 'ko', name: '한국어' },
]

const LANG_KEY = 'app.lang'
const DATE_FORMAT_KEY = 'app.dateFormat'
const TIMEZONE_KEY = 'app.timezone'
const MESSAGES_KEY = (lang: string) => `app.messages.${lang}`

/**
 * Offline fallback for the app shell (nav/header/menu) so the UI renders
 * even before the first successful fetch. Feature keys fall back to the
 * key itself.
 */
const FALLBACK_EN: Record<string, string> = {
  'app.nav.home': 'Home',
  'app.nav.aiNews': 'AI News',
  'app.nav.skills': 'Skill Management',
  'app.nav.skillMarket': 'Skill Market',
  'app.nav.aiTools': 'AI Tools',
  'app.nav.users': 'User Management',
  'app.nav.organizations': 'Organization Management',
  'app.nav.feedback': 'VOC Management',
  'app.nav.translations': 'Translation Management',
  'app.header.promo': 'AX Desktop',
  'app.header.notifications': 'Notifications',
  'app.header.language': 'Language',
  'app.header.userMenu': 'User menu',
  'app.header.markAllRead': 'Mark all read',
  'app.header.noNotifications': 'No notifications yet.',
  'app.header.menuToggle': 'Menu',
  'app.nav.adminGroup': 'Admin',
  'app.nav.main': 'Main',
  'app.menu.settings': 'My Settings',
  'app.menu.voc': 'My VOC',
  'app.menu.logout': 'Log out',
  'app.breadcrumb.settings': 'My Settings',
  'app.pagination.status': 'Page {page} / {pages} · {total} items',
  'app.pagination.ariaLabel': 'Pagination',
  'app.pagination.prev': 'Previous page',
  'app.pagination.next': 'Next page',
  'app.table.loading': 'Loading…',
  'app.table.empty': 'No data.',
  'app.fileUpload.dragDrop': 'Drag & drop',
  'app.fileUpload.orSelect': 'or',
  'app.fileUpload.chooseFromDevice': 'choose from device',
  'app.fileUpload.maxSize': 'max {size}',
  'app.fileUpload.removeFile': 'Remove {name}',
  'app.fileUpload.filesCount': 'files',
  'app.fileUpload.clearAll': 'Clear all',
  'app.fileUpload.invalidType': 'Invalid file type: {name}',
  'app.fileUpload.tooLarge': 'File exceeds the allowed size: {name}',
  'app.codeBlock.copied': 'Copied',
  'app.codeBlock.copyFailed': 'Could not copy. Select the code and copy manually.',
  'app.codeBlock.copyAria': 'Copy code',
}

function readCachedMessages(lang: string): Record<string, string> {
  try {
    const raw = localStorage.getItem(MESSAGES_KEY(lang))
    return raw ? (JSON.parse(raw) as Record<string, string>) : {}
  } catch {
    return {}
  }
}

let initPromise: Promise<void> | null = null
let refreshTimer: number | null = null

export const useI18nStore = defineStore('i18n', () => {
  const language = ref<AppLang>((localStorage.getItem(LANG_KEY) as AppLang) ?? 'en')
  const dateFormat = ref<string>(localStorage.getItem(DATE_FORMAT_KEY) ?? 'DD/MM/YYYY')
  const timezone = ref<string>(localStorage.getItem(TIMEZONE_KEY) ?? 'Asia/Ho_Chi_Minh')
  const messages = ref<Record<string, string>>(readCachedMessages(language.value))
  let loading: Promise<boolean> | null = null
  let loadingLang: AppLang | null = null
  let bootstrapped = false
  initPromise = null

  /** Fetch messages for the active language. Resolves `true` when the
   *  payload was applied, `false` on failure (network/401) or when a
   *  language switch made the result stale. */
  async function loadMessages(): Promise<boolean> {
    // Reuse the in-flight fetch only when it is for the active language;
    // after a language switch a fresh fetch for the new language is needed.
    if (loading && loadingLang === language.value) return loading
    const lang = language.value
    loadingLang = lang
    const promise = (async () => {
      try {
        const data = await request<{ lang: string; messages: Record<string, string> }>(
          api.get('/i18n/messages', { params: { lang } }),
        )
        // A language switch may have happened while the fetch was in flight:
        // apply only the payload for the language that is still active.
        if (language.value !== lang) return false
        messages.value = data.messages
        localStorage.setItem(MESSAGES_KEY(lang), JSON.stringify(data.messages))
        return true
      } catch {
        /* non-critical — keep the cached/default messages */
        return false
      }
    })()
    // Clear the in-flight marker once this fetch settles — but only when
    // it is still the current one (a newer fetch replaced it).
    promise.finally(() => {
      if (loading === promise) loading = null
    })
    loading = promise
    return promise
  }

  /** Idempotent bootstrap: reads the user's language preference once.
   *  The messages refresh starts immediately (using the locally known
   *  language) and runs in parallel with /users/me; if the server
   *  preference differs, a fresh fetch for that language follows.
   *
   *  A FAILED bootstrap (messages not loaded, e.g. 401 when the first
   *  navigation runs before a valid session exists) is NOT cached: the
   *  next navigation retries. Once messages have loaded successfully
   *  (`bootstrapped`), init() resolves instantly forever — this also
   *  prevents a retry loop when the backend is down. */
  function init(): Promise<void> {
    if (!initPromise) {
      initPromise = (async () => {
        const localLang = language.value
        const messagesPromise = loadMessages()
        let meOk = false
        try {
          const me = await settingsApi.me()
          meOk = true
          const lang = me.settings.language
          if (lang === 'en' || lang === 'vi' || lang === 'ko') {
            language.value = lang
            localStorage.setItem(LANG_KEY, lang)
          }
          if (me.settings.dateFormat) {
            dateFormat.value = me.settings.dateFormat
            localStorage.setItem(DATE_FORMAT_KEY, me.settings.dateFormat)
          }
          if (me.settings.timezone) {
            timezone.value = me.settings.timezone
            localStorage.setItem(TIMEZONE_KEY, me.settings.timezone)
          }
        } catch {
          /* non-critical — keep the cached/default language */
        }
        // The parallel fetch above may be stale if the server returned a
        // different language; loadMessages() guards against applying it.
        let ok = await messagesPromise
        if (language.value !== localLang) ok = await loadMessages()
        if (ok) bootstrapped = true
        else initPromise = null
        if (bootstrapped) startRefresh()
      })()
    }
    return initPromise
  }

  /** Keep the messages fresh while the app is open: every 60 s (skipped
   *  while the tab is hidden) and on window focus. This is what makes
   *  Message Term (Translation Management) edits — add / change / delete —
   *  propagate to the rest of the app without a page reload; the backend
   *  message cache is already invalidated on every admin write. */
  function startRefresh() {
    if (refreshTimer) return
    refreshTimer = window.setInterval(() => {
      if (document.hidden) return
      void loadMessages()
    }, 60_000)
    window.addEventListener('focus', onFocusRefresh)
  }

  function onFocusRefresh() {
    void loadMessages()
  }

  /** Persist the new language preference and switch the UI immediately. */
  async function setLanguage(lang: AppLang): Promise<void> {
    await settingsApi.updatePreferences({
      language: lang,
      timezone: timezone.value,
      dateFormat: dateFormat.value,
    })
    language.value = lang
    localStorage.setItem(LANG_KEY, lang)
    messages.value = readCachedMessages(lang)
    await loadMessages()
  }

  function t(key: string, vars?: Record<string, string | number>): string {
    let text = messages.value[key] ?? FALLBACK_EN[key] ?? key
    if (vars) {
      for (const [k, v] of Object.entries(vars)) {
        text = text.replaceAll(`{${k}}`, String(v))
      }
    }
    return text
  }

  function formatDate(iso: string | null): string {
    if (!iso) return ''
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return ''
    const dd = String(d.getDate()).padStart(2, '0')
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const yyyy = String(d.getFullYear())
    switch (dateFormat.value) {
      case 'MM/DD/YYYY':
        return `${mm}/${dd}/${yyyy}`
      case 'YYYY-MM-DD':
        return `${yyyy}-${mm}-${dd}`
      case 'DD/MM/YYYY':
      default:
        return `${dd}/${mm}/${yyyy}`
    }
  }

  function formatDateTime(iso: string | null): string {
    const date = formatDate(iso)
    if (!date) return ''
    const d = new Date(iso!)
    const hh = String(d.getHours()).padStart(2, '0')
    const mi = String(d.getMinutes()).padStart(2, '0')
    return `${date} ${hh}:${mi}`
  }

  const activeLang = computed(() => language.value)

  return { language, dateFormat, timezone, messages, activeLang, init, loadMessages, setLanguage, t, formatDate, formatDateTime }
})
