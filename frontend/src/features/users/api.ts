import { api, request } from '@/lib/api'
import type {
  MeResponse,
  UserDetailResponse,
  UserSearchRequest,
  UserSearchResult,
  CreateUserRequest,
  UpdateUserRequest,
  BulkUploadResponse,
  BulkSaveResult,
  BulkRowResult,
} from './types'

export interface PreferencesPayload {
  language: string
  timezone: string
  dateFormat: string
}

export interface NotificationsPayload {
  emailNotify: boolean
  inappNotify: boolean
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface ProfilePayload {
  displayName: string
}

export const adminUserApi = {
  search: (query: UserSearchRequest) =>
    request<UserSearchResult>(api.post('/admin/users/search', query)),
  detail: (userId: number) => request<UserDetailResponse>(api.get(`/admin/users/${userId}`)),
  create: (payload: CreateUserRequest) =>
    request<UserDetailResponse>(api.post('/admin/users', payload)),
  update: (userId: number, payload: UpdateUserRequest) =>
    request<UserDetailResponse>(api.put(`/admin/users/${userId}`, payload)),
  bulkUpload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<BulkUploadResponse>(api.post('/admin/users/bulk/upload', form))
  },
  bulkSave: (rows: BulkRowResult[]) =>
    request<BulkSaveResult>(api.post('/admin/users/bulk/save', { rows })),
  organizationOptions: () =>
    request<string[]>(api.get('/admin/users/organization-options')),
  exportUsers: async (query: UserSearchRequest): Promise<void> => {
    const response = await api.post('/admin/users/export', query, {
      responseType: 'blob',
      transformResponse: (data) => data,
    })
    const url = URL.createObjectURL(response.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'users.xlsx'
    anchor.click()
    URL.revokeObjectURL(url)
  },
}

export const settingsApi = {
  me: () => request<MeResponse>(api.get('/users/me')),
  updateProfile: (payload: ProfilePayload, avatar?: File, removeAvatar = false) => {
    const form = new FormData()
    form.append(
      'profile',
      new Blob([JSON.stringify(payload)], { type: 'application/json' }),
      'profile.json'
    )
    if (avatar) form.append('avatar', avatar)
    if (removeAvatar) form.append('removeAvatar', 'true')
    return request<MeResponse>(api.put('/users/me/profile', form))
  },
  updatePreferences: (payload: PreferencesPayload) =>
    request<MeResponse>(api.put('/users/me/preferences', payload)),
  updateNotifications: (payload: NotificationsPayload) =>
    request<MeResponse>(api.put('/users/me/notifications', payload)),
  changePassword: (payload: ChangePasswordPayload) =>
    request<void>(api.put('/users/me/password', payload)),
}
