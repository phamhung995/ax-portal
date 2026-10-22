import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ensureSession, TOKEN_KEY } from '@/stores/auth'
import { errorMessage } from './errorMessages'

/**
 * API client for the skill-management backend.
 * Envelope: { success, data | errorCode, message }
 */

export interface ApiEnvelope<T> {
  success: boolean
  data: T
  errorCode: string | null
  message: string | null
}

export class ApiError extends Error {
  code: string
  status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const status = error.response?.status ?? 0
    const envelope = error.response?.data
    const code = envelope?.errorCode ?? (status === 403 ? 'FORBIDDEN' : 'NETWORK_ERROR')
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined

    // 401: the server rejected the current token (expired, blacklisted, or
    // signed with a different key). Force a silent refresh — the client-side
    // exp pre-check is skipped because the token may be unexpired locally but
    // invalid server-side — then retry the original request once.
    if (status === 401 && original && !original._retried && original.url !== '/auth/refresh') {
      original._retried = true
      const refreshed = await ensureSession(true)
      if (refreshed) {
        const newToken = localStorage.getItem(TOKEN_KEY)
        if (newToken) {
          original.headers.Authorization = `Bearer ${newToken}`
          return api(original)
        }
      }
    }

    if (status === 401 || (status === 403 && code === 'FORBIDDEN')) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      if (!window.location.pathname.startsWith(`${import.meta.env.BASE_URL}login`)) {
        window.location.assign(`${import.meta.env.BASE_URL}login`)
      }
      return Promise.reject(new ApiError(code, 'Your session has expired. Please log in again.', status))
    }

    const message =
      envelope?.message ??
      (status === 0 ? 'Unable to connect to the server. Please try again.' : 'System error. Please try again later.')

    return Promise.reject(new ApiError(code, message, status))
  }
)

/** Unwraps the standard envelope or throws ApiError. */
async function request<T>(promise: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const response = await promise
  const body = response.data as ApiEnvelope<T> | null | undefined | ''
  // 204 No Content: no envelope, nothing to unwrap.
  if (body == null || body === '') {
    return undefined as T
  }
  if (!body.success) {
    throw new ApiError(body.errorCode ?? 'UNKNOWN', body.message ?? 'Unknown error', 500)
  }
  return body.data
}

/** Throws ApiError with a user-facing message already resolved from the code catalog. */
export function toUserError(error: unknown): ApiError {
  if (error instanceof Error && 'code' in error) {
    const apiError = error as ApiError
    apiError.message = errorMessage(apiError.code, apiError.message)
    return apiError
  }
  return new ApiError('UNKNOWN', errorMessage(undefined), 0)
}

export { api, request }
