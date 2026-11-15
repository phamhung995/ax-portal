import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn() },
  request: vi.fn(),
}))

vi.mock('@/features/users/api', () => ({
  settingsApi: {
    me: vi.fn(),
    updatePreferences: vi.fn(),
  },
}))

import { api, request } from '@/lib/api'
import { settingsApi } from '@/features/users/api'
import { useI18nStore } from './i18n'

const mockedRequest = request as unknown as ReturnType<typeof vi.fn>
const mockedApiGet = api.get as unknown as ReturnType<typeof vi.fn>
const mockedMe = settingsApi.me as unknown as ReturnType<typeof vi.fn>
const mockedUpdatePrefs = settingsApi.updatePreferences as unknown as ReturnType<typeof vi.fn>

function meResponse(overrides: Record<string, unknown> = {}) {
  return {
    userId: 1,
    userName: 'U',
    email: 'u@t.local',
    avatarUrl: null,
    orgs: [],
    settings: {
      language: 'en',
      timezone: 'Asia/Ho_Chi_Minh',
      dateFormat: 'DD/MM/YYYY',
      emailNotify: true,
      inappNotify: true,
      ...overrides,
    },
  }
}

describe('i18n store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setActivePinia(createPinia())
    mockedMe.mockResolvedValue(meResponse())
    mockedApiGet.mockReturnValue(Promise.resolve())
    mockedRequest.mockResolvedValue({ lang: 'en', messages: {} })
  })

  it('t returns active lang value', () => {
    const i18n = useI18nStore()
    i18n.messages = { 'home.title': 'Trang chủ' }
    expect(i18n.t('home.title')).toBe('Trang chủ')
  })

  it('t falls back to en then key', () => {
    const i18n = useI18nStore()
    i18n.messages = { 'app.nav.home': 'Home' }
    expect(i18n.t('app.nav.home')).toBe('Home') // FALLBACK_EN
    expect(i18n.t('never.seen.key')).toBe('never.seen.key')
  })

  it('t interpolates vars', () => {
    const i18n = useI18nStore()
    i18n.messages = { 'home.hello': 'Xin chào, {name}' }
    expect(i18n.t('home.hello', { name: 'Anh' })).toBe('Xin chào, Anh')
  })

  it('setLanguage persists and reloads messages', async () => {
    mockedUpdatePrefs.mockResolvedValue(meResponse({ language: 'ko' }))
    mockedRequest.mockResolvedValue({ lang: 'ko', messages: { 'app.nav.home': '홈' } })
    const i18n = useI18nStore()

    await i18n.setLanguage('ko')

    expect(mockedUpdatePrefs).toHaveBeenCalledWith({
      language: 'ko',
      timezone: 'Asia/Ho_Chi_Minh',
      dateFormat: 'DD/MM/YYYY',
    })
    expect(i18n.language).toBe('ko')
    expect(i18n.t('app.nav.home')).toBe('홈')
    expect(localStorage.getItem('app.lang')).toBe('ko')
  })

  it('loadMessages has an in-flight guard', async () => {
    let resolveFetch: (v: unknown) => void
    mockedRequest.mockReturnValue(
      new Promise((r) => {
        resolveFetch = r
      }),
    )
    const i18n = useI18nStore()
    const p1 = i18n.loadMessages()
    const p2 = i18n.loadMessages()
    expect(mockedRequest).toHaveBeenCalledTimes(1)
    resolveFetch!({ lang: 'en', messages: {} })
    await Promise.all([p1, p2])
  })

  it('ignores a stale fetch that resolves after the language switches', async () => {
    let resolveEn: (v: unknown) => void
    let call = 0
    mockedRequest.mockImplementation(() => {
      call++
      if (call === 1) return new Promise((r) => { resolveEn = r })
      return Promise.resolve({ lang: 'ko', messages: { 'app.nav.home': '홈' } })
    })
    mockedUpdatePrefs.mockResolvedValue(meResponse({ language: 'ko' }))
    const i18n = useI18nStore()

    const p1 = i18n.loadMessages() // en fetch in flight
    await i18n.setLanguage('ko') // switch while en fetch is pending
    resolveEn!({ lang: 'en', messages: { 'app.nav.home': 'Home' } })
    await p1

    expect(i18n.language).toBe('ko')
    // stale en payload must not overwrite the active (ko) messages
    expect(i18n.t('app.nav.home')).toBe('홈')
    // the ko cache must not be poisoned with the en payload
    const koCache = JSON.parse(localStorage.getItem('app.messages.ko') ?? 'null')
    expect(koCache).not.toEqual({ 'app.nav.home': 'Home' })
  })

  it('loadMessages uses local cache first (stale-while-revalidate)', async () => {
    localStorage.setItem('app.lang', 'vi')
    localStorage.setItem('app.messages.vi', JSON.stringify({ 'home.title': 'Cached' }))
    mockedRequest.mockImplementation(
      () =>
        new Promise((r) => setTimeout(() => r({ lang: 'vi', messages: { 'home.title': 'Fresh' } }), 10)),
    )
    const i18n = useI18nStore()
    const p = i18n.loadMessages()
    expect(i18n.t('home.title')).toBe('Cached') // cache rendered before fetch resolves
    await p
    expect(i18n.t('home.title')).toBe('Fresh')
  })

  it('formatDate and formatDateTime match existing formats', () => {
    const i18n = useI18nStore()
    const iso = '2026-10-31T09:05:00Z'
    // date only — timezone-independent check via local date parts
    i18n.dateFormat = 'YYYY-MM-DD'
    expect(/^\d{4}-\d{2}-\d{2}$/.test(i18n.formatDate(iso))).toBe(true)
    i18n.dateFormat = 'MM/DD/YYYY'
    expect(/^\d{2}\/\d{2}\/\d{4}$/.test(i18n.formatDate(iso))).toBe(true)
    i18n.dateFormat = 'DD/MM/YYYY'
    expect(/^\d{2}\/\d{2}\/\d{4}$/.test(i18n.formatDate(iso))).toBe(true)
    expect(i18n.formatDate(null)).toBe('')
    expect(i18n.formatDate('not-a-date')).toBe('')

    i18n.dateFormat = 'DD/MM/YYYY'
    const dt = i18n.formatDateTime(iso)
    expect(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/.test(dt)).toBe(true)
    expect(i18n.formatDateTime(null)).toBe('')
  })

  it('init starts the messages fetch in parallel with /users/me', async () => {
    let resolveMe: (v: unknown) => void
    let resolveFetch: (v: unknown) => void
    mockedMe.mockReturnValue(new Promise((r) => { resolveMe = r }))
    mockedRequest.mockReturnValue(new Promise((r) => { resolveFetch = r }))
    const i18n = useI18nStore()

    const p = i18n.init()
    // the messages refresh must already be in flight while /users/me is pending
    expect(mockedRequest).toHaveBeenCalledTimes(1)

    resolveMe!(meResponse()) // server lang = en, same as local
    resolveFetch!({ lang: 'en', messages: { 'home.title': 'Fresh' } })
    await p
    expect(i18n.t('home.title')).toBe('Fresh')
  })

  it('init re-fetches messages when the server language differs from local', async () => {
    let resolveMe: (v: unknown) => void
    let resolveEnFetch: (v: unknown) => void
    let call = 0
    mockedMe.mockReturnValue(new Promise((r) => { resolveMe = r }))
    mockedRequest.mockImplementation(() => {
      call++
      if (call === 1) return new Promise((r) => { resolveEnFetch = r })
      return Promise.resolve({ lang: 'vi', messages: { 'home.title': 'Trang chủ' } })
    })
    const i18n = useI18nStore()

    const p = i18n.init()
    resolveMe!(meResponse({ language: 'vi' }))
    resolveEnFetch!({ lang: 'en', messages: { 'home.title': 'Home' } }) // stale en payload
    await p

    expect(i18n.language).toBe('vi')
    expect(mockedRequest).toHaveBeenCalledTimes(2)
    // the stale en payload must not win over the fresh vi one
    expect(i18n.t('home.title')).toBe('Trang chủ')
  })

  it('init applies server language and loads messages once', async () => {
    mockedMe.mockResolvedValue(meResponse({ language: 'vi', dateFormat: 'YYYY-MM-DD' }))
    mockedRequest.mockResolvedValue({ lang: 'vi', messages: { 'home.title': 'Trang chủ' } })
    const i18n = useI18nStore()

    await i18n.init()
    await i18n.init() // idempotent

    expect(mockedMe).toHaveBeenCalledTimes(1)
    expect(i18n.language).toBe('vi')
    expect(i18n.dateFormat).toBe('YYYY-MM-DD')
    expect(i18n.t('home.title')).toBe('Trang chủ')
  })
})
