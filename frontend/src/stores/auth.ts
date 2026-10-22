import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import axios from 'axios'
import type { LoginResponse } from '@/features/auth/types'
import type { OrgRef } from '@/lib/types'

export interface AuthUser {
  userId: number
  userName: string
  email: string
  role: string
  orgs: OrgRef[]
  avatarUrl: string | null
}

export const TOKEN_KEY = 'accessToken'
export const REFRESH_KEY = 'refreshToken'
const USER_KEY = 'nx.user'

function loadUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

/** Decode the JWT `exp` claim without verifying the signature (client-side pre-check only). */
function tokenExpiresAt(token: string): number | null {
  try {
    const payloadPart = token.split('.')[1]
    if (!payloadPart) return null
    const payload = JSON.parse(atob(payloadPart.replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' ? payload.exp : null
  } catch {
    return null
  }
}

/**
 * Returns true when the session is (or can be made) valid:
 * - access token still valid, or
 * - access token expired but a silent refresh succeeded.
 * Clears the session when nothing works. Safe to call repeatedly — only one
 * refresh runs at a time.
 *
 * Pass `force = true` when the server already rejected the current token
 * (401): the client-side `exp` pre-check is then skipped and a refresh is
 * attempted unconditionally, because the token may be invalid server-side
 * (rotated secret, blacklisted) while still unexpired client-side.
 */
let refreshPromise: Promise<boolean> | null = null
export async function ensureSession(force = false): Promise<boolean> {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!force && token) {
    const exp = tokenExpiresAt(token)
    if (exp == null || exp * 1000 > Date.now() + 30_000) return true
  }
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) return false

  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        // Raw axios (not the shared api client): its 401 interceptor would
        // clear the session and redirect to /login before we can decide.
        // Same baseURL as the shared client so it hits the same proxy/route
        // in dev and prod (NOT BASE_URL-prefixed — that bypasses the /api proxy).
        const response = await axios.post(
          '/api/v1/auth/refresh',
          { refreshToken },
          { timeout: 15000 },
        )
        const body = response.data as { success: boolean; data: { accessToken: string; refreshToken: string } } | null
        if (body?.success && body.data?.accessToken) {
          localStorage.setItem(TOKEN_KEY, body.data.accessToken)
          localStorage.setItem(REFRESH_KEY, body.data.refreshToken)
          return true
        }
        return false
      } catch {
        return false
      } finally {
        refreshPromise = null
      }
    })()
  }
  const ok = await refreshPromise
  if (!ok) {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  }
  return ok
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY))
  const user = ref<AuthUser | null>(loadUser())

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN_USER')

  function setSession(payload: LoginResponse): void {
    token.value = payload.accessToken
    refreshToken.value = payload.refreshToken
    user.value = {
      userId: payload.userId,
      userName: payload.userName,
      email: payload.email,
      role: payload.role,
      orgs: payload.orgs ?? [],
      avatarUrl: payload.avatarUrl,
    }
    localStorage.setItem(TOKEN_KEY, payload.accessToken)
    localStorage.setItem(REFRESH_KEY, payload.refreshToken)
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  function setAvatarUrl(url: string | null): void {
    if (!user.value) return
    user.value.avatarUrl = url
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  /** Sync the display name after a profile update (header shows it). */
  function setUserName(name: string): void {
    if (!user.value || !name) return
    user.value.userName = name
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  function clearSession(): void {
    token.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, refreshToken, user, isAuthenticated, isAdmin, setSession, setAvatarUrl, setUserName, clearSession }
})
