import type { OrgRef } from '@/lib/types'

export interface SettingsBlock {
  language: string
  timezone: string
  dateFormat: string
  emailNotify: boolean
  inappNotify: boolean
}

export interface MeResponse {
  id: number
  displayName: string
  email: string
  avatarPath: string | null
  orgs: OrgRef[]
  settings: SettingsBlock
}

export interface UserListItem {
  userId: number
  userName: string
  email: string
  orgs: OrgRef[]
  role: string
  status: string
  createdAt: string
}

export interface UserSearchResult {
  totalCount: number
  page: number
  pageSize: number
  items: UserListItem[]
}

export interface UserDetailResponse {
  userId: number
  userName: string
  email: string
  orgs: OrgRef[]
  role: string
  status: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface UserSearchRequest {
  userName?: string
  email?: string
  part?: string
  group?: string
  organization?: string
  roles?: string[]
  statuses?: string[]
  createdDateFrom?: string
  createdDateTo?: string
  ids?: number[]
  page?: number
  pageSize?: number
  sortBy?: string
  sortDirection?: string
}

export interface CreateUserRequest {
  userName: string
  email: string
  password: string
  confirmPassword: string
}

export interface UpdateUserRequest {
  userName?: string | null
  role?: string | null
  status?: string | null
  expectedVersion?: number
}

export interface BulkRowResult {
  rowNumber: number
  userName: string
  email: string
  password: string
  status: 'SUCCESS' | 'ERROR'
  errors: string | null
}

export interface BulkUploadResponse {
  totalRows: number
  successCount: number
  errorCount: number
  rows: BulkRowResult[]
}

export interface BulkSaveResult {
  savedCount: number
  skippedCount: number
}
